package com.johnymoo.arverify.capture

import android.view.WindowManager
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.NotYetAvailableException
import com.johnymoo.arverify.config.CaptureConfig
import com.johnymoo.arverify.depth.DepthHeatmap
import com.johnymoo.arverify.depth.DepthOverlayRange
import com.johnymoo.arverify.render.BackgroundRenderer
import com.johnymoo.arverify.session.CaptureMode
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class CaptureRenderer(
    private val sessionProvider: () -> Session?,
    private val config: CaptureConfig,
    private val mode: CaptureMode,
    private val holder: CaptureHolder,
    private val writer: CaptureSessionWriter,
    private val windowManager: WindowManager,
    private val arcoreVersion: String = "",
) : GLSurfaceView.Renderer {

    private val background = BackgroundRenderer()
    private val controller = CaptureWizardController()
    private val gate = FrameQualityGate(config.thresholds())
    private val depthRange = DepthOverlayRange.fromDistanceBand(config.minDistanceM, config.maxDistanceM)

    @Volatile var captureRequested = false
    private var vpW = 0; private var vpH = 0; private var rotation = 0
    private var frameTick = 0

    override fun onSurfaceCreated(gl: GL10?, c: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f); background.createOnGlThread()
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        GLES20.glViewport(0, 0, w, h); vpW = w; vpH = h
        @Suppress("DEPRECATION") run { rotation = windowManager.defaultDisplay.rotation }
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val s = sessionProvider() ?: return
        if (background.textureId == -1) return
        s.setCameraTextureName(background.textureId)
        if (vpW > 0) s.setDisplayGeometry(rotation, vpW, vpH)
        try {
            val frame = s.update()
            background.draw(frame)
            evaluate(frame)
            if (captureRequested && frame.camera.trackingState == TrackingState.TRACKING) {
                captureRequested = false
                capture(frame)
            }
        } catch (e: CameraNotAvailableException) { /* transient */ }
    }

    private fun evaluate(frame: Frame) {
        var distance = 0.0; var sharp = 0.0
        var targetLocked = false
        var targetCoverage = 0.0
        try {
            frame.acquireDepthImage16Bits().use { d ->
                val g = ArFrameExtractor.depthGridMm(d)
                val target = DepthTargetDetector.detect(g.values, g.width, g.height)
                distance = target.distanceM
                targetLocked = target.locked
                targetCoverage = target.coverage
                if ((frameTick++ % 2) == 0) {
                    holder.depthBitmap.value = DepthHeatmap.toBitmap(g.values, g.width, g.height, depthRange)
                }
            }
        } catch (e: NotYetAvailableException) { /* warming up */ }
        try {
            frame.acquireCameraImage().use { img ->
                val luma = ArFrameExtractor.lumaGrid(img)
                sharp = Sharpness.varianceOfLaplacian(luma.values, luma.width, luma.height)
            }
        } catch (e: NotYetAvailableException) { /* not ready */ }

        val tracking = when (frame.camera.trackingState) {
            TrackingState.TRACKING -> TrackingStateLite.TRACKING
            TrackingState.PAUSED -> TrackingStateLite.PAUSED
            TrackingState.STOPPED -> TrackingStateLite.STOPPED
        }
        val reason = gate.evaluate(tracking, distance, sharp, null, targetLocked)
        val st = controller.state
        holder.state.value = holder.state.value.copy(
            qualityReason = reason, distanceM = distance, targetLocked = targetLocked,
            targetCoverage = targetCoverage, sharpness = sharp,
            wizardStep = st.step, canFinish = st.canUpload,
        )
    }

    private fun capture(frame: Frame) {
        try {
            val rgb = frame.acquireCameraImage().use { ArFrameExtractor.rgbJpeg(it) }
            val slot = chooseSlot(frame)
            var gridVals: IntArray? = null; var gw = 0; var gh = 0; var dist: Double? = null
            try {
                frame.acquireDepthImage16Bits().use { d ->
                    val g = ArFrameExtractor.depthGridMm(d)
                    gridVals = g.values; gw = g.width; gh = g.height
                    dist = DepthTargetDetector.detect(g.values, g.width, g.height).distanceM
                }
            } catch (e: NotYetAvailableException) { /* RGB-only frame */ }

            val frames = writer.addFrame(slot, rgb, gridVals, gw, gh, dist, holder.state.value.sharpness)
            controller.record(slot)

            if (mode == CaptureMode.RECOGNITION && slot == CaptureSlot.TOP &&
                gridVals != null && gw > 0 && gh > 0) {
                val intr = ArFrameExtractor.intrinsics(frame)
                val pose = ArFrameExtractor.pose(frame)
                writer.writeRecognition(intr, pose, dist ?: 0.0, gridVals!!, gw, gh, rgb, arcoreVersion)
                holder.state.value = holder.state.value.copy(topReady = true)
            }

            if (mode == CaptureMode.GENERAL) {
                try {
                    frame.acquirePointCloud().use { pc ->
                        val buf = pc.points
                        val arr = FloatArray(buf.remaining()); buf.get(arr)
                        java.io.File(writer.dir, "points_${frames.size - 1}.ply")
                            .writeText(com.johnymoo.arverify.export.PlyWriter.toAsciiPly(arr))
                    }
                } catch (e: NotYetAvailableException) { /* no cloud yet */ }
            }

            holder.state.value = holder.state.value.copy(
                frames = frames, sessionDir = writer.dir.absolutePath,
                wizardStep = controller.state.step, canFinish = controller.state.canUpload,
            )
        } catch (e: Exception) { /* surfaced via toast in Activity if needed */ }
    }

    private fun chooseSlot(frame: Frame): CaptureSlot {
        if (holder.state.value.modeUi == CaptureModeUi.GUIDED) {
            return when (controller.state.step) {
                WizardStep.NEED_TOP -> CaptureSlot.TOP
                WizardStep.NEED_SIDE -> CaptureSlot.SIDE
                else -> CaptureSlot.ANGLE
            }
        }
        val q = frame.camera.pose.let { FloatArray(4).also { arr -> it.getRotationQuaternion(arr, 0) } }
        val pitch = CameraPitch.fromQuaternion(q[0].toDouble(), q[1].toDouble(), q[2].toDouble(), q[3].toDouble())
        return FrameRoleClassifier.classify(pitch)
    }
}
