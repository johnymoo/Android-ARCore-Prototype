package com.johnymoo.arverify.capture

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.NotYetAvailableException
import com.johnymoo.arverify.config.AppPrefs
import com.johnymoo.arverify.config.CaptureConfig
import com.johnymoo.arverify.databinding.ActivityCaptureWizardBinding
import com.johnymoo.arverify.debug.DebugGallerySaver
import com.johnymoo.arverify.imaging.Depth16PngWriter
import com.johnymoo.arverify.measure.MeasurementFormActivity
import com.johnymoo.arverify.metadata.ArMetadata
import com.johnymoo.arverify.metadata.ArMetadataSerializer
import com.johnymoo.arverify.metadata.CameraIntrinsics
import com.johnymoo.arverify.metadata.CameraPose
import com.johnymoo.arverify.metadata.DepthDims
import com.johnymoo.arverify.metadata.DeviceMeta
import com.johnymoo.arverify.metadata.RecognitionFrameMeta
import com.johnymoo.arverify.net.CapturePackage
import com.johnymoo.arverify.net.CaptureUploader
import com.johnymoo.arverify.net.FilePart
import com.johnymoo.arverify.net.HttpUrlConnectionTransport
import com.johnymoo.arverify.net.LocalRetention
import com.johnymoo.arverify.net.RecognitionStatus
import com.johnymoo.arverify.net.UploadOutcome
import com.johnymoo.arverify.net.UploadResultHolder
import com.johnymoo.arverify.render.BackgroundRenderer
import com.johnymoo.arverify.result.ResultActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class CaptureWizardActivity : AppCompatActivity(), GLSurfaceView.Renderer {

    private lateinit var binding: ActivityCaptureWizardBinding
    private val background = BackgroundRenderer()
    private var session: Session? = null
    private var installRequested = false

    private val controller = CaptureWizardController()
    private lateinit var config: CaptureConfig
    private lateinit var prefs: AppPrefs
    private lateinit var gate: FrameQualityGate
    private lateinit var gallerySaver: DebugGallerySaver
    private val partId: String = "part-" + UUID.randomUUID().toString().take(8)

    // Captured data
    private val images = mutableListOf<FilePart>()
    private var topRgb: FilePart? = null
    private var topDepthPng: FilePart? = null
    private var topIntrinsics: CameraIntrinsics? = null
    private var topPose: CameraPose? = null
    private var topDistanceM: Double = 0.0
    private var topDepthW = 0
    private var topDepthH = 0

    @Volatile private var captureRequested = false
    @Volatile private var lastReasonOk = false
    @Volatile private var lastQualityReason: QualityReason = QualityReason.NOT_TRACKING
    private var viewportWidth = 0
    private var viewportHeight = 0
    private var displayRotation = 0
    private val io = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCaptureWizardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = AppPrefs(this)
        config = prefs.load()
        gate = FrameQualityGate(config.thresholds())
        gallerySaver = DebugGallerySaver(this)

        binding.surfaceView.preserveEGLContextOnPause = true
        binding.surfaceView.setEGLContextClientVersion(2)
        binding.surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        binding.surfaceView.setRenderer(this)
        binding.surfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        binding.btnCaptureFrame.setOnClickListener {
            if (controller.state.step == WizardStep.READY) return@setOnClickListener
            if (!lastReasonOk) {
                toast(lastQualityReason.messageZh)
                return@setOnClickListener
            }
            captureRequested = true
        }
        binding.btnUpload.setOnClickListener { onUploadClicked() }
        refreshStepUi()
    }

    override fun onResume() {
        super.onResume()
        if (session == null) {
            try {
                when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> { installRequested = true; return }
                    else -> {}
                }
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED
                ) { toast("需要相机权限"); finish(); return }
                session = Session(this).also { s ->
                    val c = s.config
                    c.depthMode = if (s.isDepthModeSupported(Config.DepthMode.AUTOMATIC))
                        Config.DepthMode.AUTOMATIC else Config.DepthMode.DISABLED
                    c.focusMode = Config.FocusMode.AUTO
                    s.configure(c)
                }
            } catch (e: Exception) {
                toast("无法启动 AR 会话：${e.javaClass.simpleName}"); finish(); return
            }
        }
        try { session?.resume() } catch (e: CameraNotAvailableException) {
            toast("相机不可用"); session = null; finish(); return
        }
        binding.surfaceView.onResume()
    }

    override fun onPause() {
        super.onPause()
        if (session != null) { binding.surfaceView.onPause(); session?.pause() }
    }

    override fun onDestroy() {
        io.shutdown()
        session?.close(); session = null
        super.onDestroy()
    }

    // --- GLSurfaceView.Renderer ---

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        background.createOnGlThread()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        viewportWidth = width; viewportHeight = height
        @Suppress("DEPRECATION")
        displayRotation = windowManager.defaultDisplay.rotation
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val s = session ?: return
        if (background.textureId == -1) return
        s.setCameraTextureName(background.textureId)
        if (viewportWidth > 0) s.setDisplayGeometry(displayRotation, viewportWidth, viewportHeight)
        try {
            val frame = s.update()
            background.draw(frame)
            evaluateQuality(frame)
            if (captureRequested && lastReasonOk &&
                frame.camera.trackingState == TrackingState.TRACKING
            ) {
                captureRequested = false
                doCapture(frame)
            }
        } catch (e: CameraNotAvailableException) {
            runOnUiThread { toast("相机不可用") }
        }
    }

    private fun trackingLite(frame: Frame): TrackingStateLite = when (frame.camera.trackingState) {
        TrackingState.TRACKING -> TrackingStateLite.TRACKING
        TrackingState.PAUSED -> TrackingStateLite.PAUSED
        TrackingState.STOPPED -> TrackingStateLite.STOPPED
    }

    private fun evaluateQuality(frame: Frame) {
        var distance = topDistanceM
        var sharpness = 0.0
        try {
            frame.acquireDepthImage16Bits().use { depth ->
                val g = ArFrameExtractor.depthGridMm(depth)
                distance = ScaleMath.medianCenterDistanceMeters(g.values, g.width, g.height, 0.2)
            }
        } catch (e: NotYetAvailableException) { /* depth warming up */ }
        try {
            frame.acquireCameraImage().use { img ->
                val luma = ArFrameExtractor.lumaGrid(img)
                sharpness = Sharpness.varianceOfLaplacian(luma.values, luma.width, luma.height)
            }
        } catch (e: NotYetAvailableException) { /* image not ready */ }

        val reason = gate.evaluate(trackingLite(frame), distance, sharpness)
        lastQualityReason = reason
        lastReasonOk = reason == QualityReason.OK
        runOnUiThread {
            binding.tvQuality.text = if (lastReasonOk) "" else reason.messageZh
            binding.btnCaptureFrame.isEnabled = CaptureButtonPolicy.isEnabled(controller.state.step)
        }
    }

    private fun doCapture(frame: Frame) {
        val slot = when (controller.state.step) {
            WizardStep.NEED_TOP -> CaptureSlot.TOP
            WizardStep.NEED_SIDE -> CaptureSlot.SIDE
            WizardStep.NEED_ANGLES -> CaptureSlot.ANGLE
            WizardStep.READY -> return
        }
        try {
            val rgb = frame.acquireCameraImage().use { ArFrameExtractor.rgbJpeg(it) }
            val rgbPart = FilePart("frame_${images.size}.jpg", "image/jpeg", rgb)
            images.add(rgbPart)
            maybeSaveDebugRgb(slot, images.size, rgb)

            if (slot == CaptureSlot.TOP) {
                frame.acquireDepthImage16Bits().use { depth ->
                    val g = ArFrameExtractor.depthGridMm(depth)
                    topDepthW = g.width; topDepthH = g.height
                    topDistanceM = ScaleMath.medianCenterDistanceMeters(g.values, g.width, g.height, 0.2)
                    val png = Depth16PngWriter.encode(g.values, g.width, g.height)
                    topDepthPng = FilePart("recognition_depth.png", "image/png", png)
                }
                topRgb = rgbPart
                topIntrinsics = ArFrameExtractor.intrinsics(frame)
                topPose = ArFrameExtractor.pose(frame)
            }
            controller.record(slot)
            runOnUiThread { refreshStepUi() }
        } catch (e: NotYetAvailableException) {
            runOnUiThread { toast("深度/图像未就绪，请对准重试") }
        } catch (e: Exception) {
            runOnUiThread { toast("采集失败：${e.javaClass.simpleName}") }
        }
    }

    private fun maybeSaveDebugRgb(slot: CaptureSlot, frameNumber: Int, rgb: ByteArray) {
        if (!config.saveDebugRgbToGallery) return
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val name = "debug_${slot.name.lowercase(Locale.US)}_${frameNumber}_$stamp.jpg"
        if (gallerySaver.saveJpeg(rgb, name)) {
            runOnUiThread { toast("原图已保存到相册：$name") }
        }
    }

    private fun refreshStepUi() {
        val st = controller.state
        binding.tvStep.text = when (st.step) {
            WizardStep.NEED_TOP -> getString(com.johnymoo.arverify.R.string.wizard_need_top)
            WizardStep.NEED_SIDE -> getString(com.johnymoo.arverify.R.string.wizard_need_side)
            WizardStep.NEED_ANGLES -> getString(com.johnymoo.arverify.R.string.wizard_need_angles)
            WizardStep.READY -> getString(com.johnymoo.arverify.R.string.wizard_ready)
        }
        binding.tvCounter.text = getString(com.johnymoo.arverify.R.string.frames_counter, st.totalFrames)
        binding.btnCaptureFrame.isEnabled = CaptureButtonPolicy.isEnabled(st.step)
        binding.btnUpload.isEnabled = st.canUpload
    }

    private fun onUploadClicked() {
        if (!controller.state.canUpload) { toast("帧数不足"); return }
        val kinds = arrayOf("brick", "plate", "tile", "slope")
        var chosen = 0
        AlertDialog.Builder(this)
            .setTitle(com.johnymoo.arverify.R.string.kind_title)
            .setSingleChoiceItems(kinds, 0) { _, which -> chosen = which }
            .setPositiveButton(android.R.string.ok) { _, _ -> startUpload(kinds[chosen]) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun startUpload(kind: String) {
        val rgb = topRgb ?: run { toast("缺少俯视帧"); return }
        val depth = topDepthPng ?: run { toast("缺少深度帧"); return }
        val intr = topIntrinsics ?: return
        val pose = topPose ?: return

        val meta = ArMetadata(
            device = DeviceMeta(model = android.os.Build.MODEL ?: "", arcore = arcoreVersion()),
            recognitionFrame = RecognitionFrameMeta(
                imageIntrinsics = intr,
                depth = DepthDims(topDepthW, topDepthH),
                cameraPose = pose,
                distanceM = topDistanceM,
            ),
            coarseHints = null, // on-device stud counting deferred (plan scope)
        )
        val pkg = CapturePackage(
            partId = partId, kind = kind, systemHint = null,
            arMetadataJson = ArMetadataSerializer.toJson(meta),
            recognitionRgb = FilePart("recognition_rgb.jpg", "image/jpeg", rgb.bytes),
            recognitionDepth = depth,
            images = images.toList(),
        )
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        // Local retention first — never lose data on upload failure (spec §6).
        try {
            LocalRetention(File(getExternalFilesDir(null), "captures")).save(pkg, stamp)
        } catch (e: Exception) { /* best-effort */ }

        binding.progress.visibility = View.VISIBLE
        binding.btnUpload.isEnabled = false
        io.execute {
            val outcome = CaptureUploader(HttpUrlConnectionTransport()).upload(config.baseUrl, pkg)
            runOnUiThread { onUploadResult(outcome) }
        }
    }

    private fun onUploadResult(outcome: UploadOutcome) {
        binding.progress.visibility = View.GONE
        binding.btnUpload.isEnabled = true
        UploadResultHolder.outcome = outcome
        UploadResultHolder.baseUrl = config.baseUrl
        when (outcome) {
            is UploadOutcome.Failure -> toast("上传失败：${outcome.message}（已本地留存，可重试）")
            is UploadOutcome.Success -> when (outcome.result.status) {
                RecognitionStatus.NEEDS_MEASUREMENT ->
                    startActivity(Intent(this, MeasurementFormActivity::class.java))
                else ->
                    startActivity(Intent(this, ResultActivity::class.java))
            }
        }
    }

    private fun arcoreVersion(): String = try {
        packageManager.getPackageInfo("com.google.ar.core", 0).versionName ?: ""
    } catch (e: Exception) { "" }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
}
