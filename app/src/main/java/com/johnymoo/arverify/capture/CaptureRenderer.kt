package com.johnymoo.arverify.capture

import android.view.WindowManager
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.NotYetAvailableException
import com.johnymoo.arverify.config.CaptureConfig
import com.johnymoo.arverify.depth.DepthOverlayRange
import com.johnymoo.arverify.depth.DepthPreviewOverlay
import com.johnymoo.arverify.render.BackgroundRenderer
import com.johnymoo.arverify.session.CaptureMode
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import java.io.File
import kotlin.math.sqrt
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
    diagnosticsRoot: File? = null,
    private val onDiagnosticSaved: (File) -> Unit = {},
    private val onDiagnosticError: (Throwable) -> Unit = {},
) : GLSurfaceView.Renderer {

    private val background = BackgroundRenderer()
    private val controller = CaptureWizardController()
    private val gate = FrameQualityGate(config.thresholds())
    private val depthRange = DepthOverlayRange.fromDistanceBand(config.minDistanceM, config.maxDistanceM)
    private val scalePolicy = ScaleEstimationPolicy(
        referenceModeEnabled = config.visualReferenceModeEnabled,
        visualMinDistanceM = config.visualReferenceMinDistanceM,
        visualMaxDistanceM = config.visualReferenceMaxDistanceM,
        maxDepthDisagreementM = config.visualReferenceMaxDepthDisagreementM,
    )
    private val diagnosticsWriter = diagnosticsRoot?.let { DiagnosticSnapshotWriter(it) }

    @Volatile var captureRequested = false
    @Volatile var diagnosticRequested = false
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
            if (diagnosticRequested) {
                diagnosticRequested = false
                saveDiagnostic(frame)
            }
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
        var depthTarget = DepthTarget(distanceM = 0.0, coverage = 0.0, locked = false)
        val fallbackDistanceM = centerReticleHitDistanceM(frame)
        try {
            frame.acquireDepthImage16Bits().use { d ->
                val g = ArFrameExtractor.depthGridMm(d)
                val target = DepthTargetDetector.detect(
                    g.values,
                    g.width,
                    g.height,
                    targetRange = depthRange,
                )
                depthTarget = target
                if ((frameTick++ % 2) == 0) {
                    holder.depthBitmap.value = DepthPreviewOverlay.toBitmap(g.values, g.width, g.height, depthRange)
                }
            }
        } catch (e: NotYetAvailableException) { /* warming up */ }
        val resolvedTarget = TargetDistanceResolver.resolve(depthTarget, fallbackDistanceM, depthRange)
        distance = resolvedTarget.distanceM
        targetLocked = resolvedTarget.locked
        targetCoverage = resolvedTarget.coverage
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
            distanceSource = resolvedTarget.source,
            fallbackDistanceM = resolvedTarget.fallbackDistanceM,
            targetCoverage = targetCoverage, sharpness = sharp,
            wizardStep = st.step, canFinish = st.canUpload,
        )
    }

    private fun capture(frame: Frame) {
        try {
            val rgb = frame.acquireCameraImage().use { ArFrameExtractor.rgbJpeg(it) }
            val slot = chooseSlot(frame)
            val fallbackDistanceM = centerReticleHitDistanceM(frame)
            var gridVals: IntArray? = null; var gw = 0; var gh = 0
            var depthTarget = DepthTarget(distanceM = 0.0, coverage = 0.0, locked = false)
            try {
                frame.acquireDepthImage16Bits().use { d ->
                    val g = ArFrameExtractor.depthGridMm(d)
                    gridVals = g.values; gw = g.width; gh = g.height
                    depthTarget = DepthTargetDetector.detect(
                        g.values,
                        g.width,
                        g.height,
                        targetRange = depthRange,
                    )
                }
            } catch (e: NotYetAvailableException) { /* RGB-only frame */ }
            val resolvedTarget = TargetDistanceResolver.resolve(depthTarget, fallbackDistanceM, depthRange)
            val dist = resolvedTarget.distanceM.takeIf { it > 0.0 }

            val frames = writer.addFrame(slot, rgb, gridVals, gw, gh, dist, holder.state.value.sharpness)
            controller.record(slot)

            if (mode == CaptureMode.RECOGNITION && slot == CaptureSlot.TOP &&
                gridVals != null && gw > 0 && gh > 0) {
                val intr = ArFrameExtractor.intrinsics(frame)
                val pose = ArFrameExtractor.pose(frame)
                val scaleDistance = VisualScaleEstimator.estimate(
                    image = ArFrameExtractor.rgbPixelsFromJpeg(rgb),
                    intrinsics = intr,
                    arcoreDistanceM = dist ?: 0.0,
                    arcoreDistanceSource = resolvedTarget.source,
                    policy = scalePolicy,
                )
                writer.writeRecognition(intr, pose, scaleDistance, gridVals!!, gw, gh, rgb, arcoreVersion)
                holder.state.value = holder.state.value.copy(
                    topReady = true,
                    scaleDistanceM = scaleDistance.distanceM,
                    visualDistanceM = scaleDistance.visualDistanceM,
                    visualReferenceWidthPx = scaleDistance.visualReferenceWidthPx,
                    visualReferenceWidthMm = scaleDistance.visualReferenceWidthMm,
                    visualReferenceStatus = scaleDistance.visualReferenceStatus,
                    distanceSourceForScale = scaleDistance.distanceSourceForScale,
                    distanceConfidence = scaleDistance.distanceConfidence,
                    referenceModeEnabled = scaleDistance.referenceModeEnabled,
                    manualMeasurementRecommended = scaleDistance.manualMeasurementRecommended,
                )
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

    private fun centerReticleHitDistanceM(frame: Frame): Double? {
        if (vpW <= 0 || vpH <= 0 || frame.camera.trackingState != TrackingState.TRACKING) return null
        val cameraPose = frame.camera.pose
        return ReticleHitTestSampler.samplePoints(vpW, vpH).mapNotNull { point ->
            frame.hitTest(point.x, point.y).firstNotNullOfOrNull { hit ->
                val trackable = hit.trackable
                val valid = when (trackable) {
                    is Plane -> trackable.trackingState == TrackingState.TRACKING &&
                        trackable.isPoseInPolygon(hit.hitPose)
                    is Point -> trackable.trackingState == TrackingState.TRACKING
                    else -> false
                }
                if (valid) distanceBetween(cameraPose, hit.hitPose) else null
            }
        }.minOrNull()
    }

    private fun distanceBetween(a: Pose, b: Pose): Double {
        val dx = b.tx() - a.tx()
        val dy = b.ty() - a.ty()
        val dz = b.tz() - a.tz()
        return sqrt((dx * dx + dy * dy + dz * dz).toDouble())
    }

    private fun saveDiagnostic(frame: Frame) {
        val writer = diagnosticsWriter ?: return
        try {
            var rgb: ByteArray? = null
            var depthValues: IntArray? = null
            var depthW = 0
            var depthH = 0
            var depthUnavailableReason: String? = null

            try {
                frame.acquireCameraImage().use { img ->
                    rgb = ArFrameExtractor.rgbJpeg(img)
                }
            } catch (e: NotYetAvailableException) {
                // RGB is useful but optional for diagnosing depth availability.
            }

            try {
                frame.acquireDepthImage16Bits().use { d ->
                    val g = ArFrameExtractor.depthGridMm(d)
                    depthValues = g.values
                    depthW = g.width
                    depthH = g.height
                }
            } catch (e: NotYetAvailableException) {
                depthUnavailableReason = e.javaClass.simpleName
            }

            val state = holder.state.value
            val diagnosticScaleDistance = VisualScaleEstimator.estimate(
                image = rgb?.let { ArFrameExtractor.rgbPixelsFromJpeg(it) },
                intrinsics = ArFrameExtractor.intrinsics(frame),
                arcoreDistanceM = state.distanceM,
                arcoreDistanceSource = state.distanceSource,
                policy = scalePolicy,
            )
            val tracking = frame.camera.trackingState.name
            val depthSummary = depthValues?.let {
                DepthDiagnostics.summarize(it, depthW, depthH, depthRange)
            }
            val report = DepthFrameDiagnostics(
                createdAtEpochMs = System.currentTimeMillis(),
                deviceModel = android.os.Build.MODEL ?: "",
                arcoreVersion = arcoreVersion,
                trackingState = tracking,
                qualityReason = state.qualityReason.name,
                targetLocked = state.targetLocked,
                distanceM = state.distanceM,
                distanceSource = state.distanceSource.name,
                fallbackDistanceM = state.fallbackDistanceM,
                scaleDistanceM = diagnosticScaleDistance.distanceM,
                arcoreDistanceM = diagnosticScaleDistance.arcoreDistanceM,
                arcoreDistanceSource = diagnosticScaleDistance.arcoreDistanceSource.name,
                visualDistanceM = diagnosticScaleDistance.visualDistanceM,
                visualReferenceWidthPx = diagnosticScaleDistance.visualReferenceWidthPx,
                visualReferenceWidthMm = diagnosticScaleDistance.visualReferenceWidthMm,
                visualReferenceStatus = diagnosticScaleDistance.visualReferenceStatus,
                distanceSourceForScale = diagnosticScaleDistance.distanceSourceForScale.name,
                distanceConfidence = diagnosticScaleDistance.distanceConfidence.name,
                sharpness = state.sharpness,
                depthAvailable = depthValues != null,
                depthUnavailableReason = depthUnavailableReason,
                depthWidth = depthW,
                depthHeight = depthH,
                depthRangeMinMm = depthRange.minMm,
                depthRangeMaxMm = depthRange.maxMm,
                roiFraction = 0.56,
                lockFailureReason = DepthDiagnostics.lockFailureReason(depthValues, depthW, depthH, depthRange),
                depthSummary = depthSummary,
            )
            val dir = writer.write(report, rgb, depthValues, depthW, depthH, depthRange)
            onDiagnosticSaved(dir)
        } catch (e: Throwable) {
            onDiagnosticError(e)
        }
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
