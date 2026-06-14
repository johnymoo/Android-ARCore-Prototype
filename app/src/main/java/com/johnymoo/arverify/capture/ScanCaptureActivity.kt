package com.johnymoo.arverify.capture

import android.Manifest
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.johnymoo.arverify.config.AppPrefs
import com.johnymoo.arverify.depth.DepthOverlayRange
import com.johnymoo.arverify.session.CaptureMode
import com.johnymoo.arverify.ui.common.SessionPaths
import com.johnymoo.arverify.ui.theme.ScanForgeTheme
import java.util.UUID

class ScanCaptureActivity : ComponentActivity() {
    companion object { const val EXTRA_MODE = "mode" }

    private var session: Session? = null
    private var installRequested = false
    private var glView: GLSurfaceView? = null
    private val holder = CaptureHolder()
    private lateinit var renderer: CaptureRenderer
    private val io = java.util.concurrent.Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val mode = if (intent.getStringExtra(EXTRA_MODE) == "GENERAL") CaptureMode.GENERAL else CaptureMode.RECOGNITION
        val config = AppPrefs(this).load()
        val writer = CaptureSessionWriter(
            rootDir = SessionPaths.captureRoot(this),
            partId = "part-" + UUID.randomUUID().toString().take(8),
            mode = mode,
            createdAtEpochMs = System.currentTimeMillis(),
            deviceModel = android.os.Build.MODEL ?: "",
            depthRange = DepthOverlayRange.fromDistanceBand(config.minDistanceM, config.maxDistanceM),
        )
        renderer = CaptureRenderer({ session }, config, mode, holder, writer, windowManager,
            try { packageManager.getPackageInfo("com.google.ar.core", 0).versionName ?: "" } catch (e: Exception) { "" })

        setContent {
            ScanForgeTheme {
                var showKind by remember { mutableStateOf(false) }
                CaptureScreen(
                    holder = holder,
                    glViewFactory = { ctx -> makeGlView(ctx) },
                    onShutter = { renderer.captureRequested = true },
                    onFinish = {
                        if (mode == CaptureMode.RECOGNITION) showKind = true
                        else exportGeneral()
                    },
                    onBack = { finish() },
                    finishLabel = if (mode == CaptureMode.RECOGNITION) "上传" else "导出",
                )
                if (showKind) KindDialog(
                    onConfirm = { kind -> showKind = false; uploadRecognition(kind) },
                    onDismiss = { showKind = false },
                )
            }
        }
    }

    private fun makeGlView(ctx: android.content.Context): GLSurfaceView =
        GLSurfaceView(ctx).apply {
            preserveEGLContextOnPause = true
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            glView = this
        }

    private fun uploadRecognition(kind: String) {
        val dir = java.io.File(holder.state.value.sessionDir)
        val partId = dir.name.substringBeforeLast('_')
        val pkg = com.johnymoo.arverify.net.CaptureUploadAssembler.fromDir(dir, partId, kind, null)
            ?: run { toast("缺少识别帧，请先拍俯视凸点面"); return }
        val missingImages = com.johnymoo.arverify.net.CaptureUploadAssembler.missingImageCount(pkg)
        if (missingImages > 0) {
            toast("还需补拍 ${missingImages} 张角度图后再上传")
            return
        }
        val config = com.johnymoo.arverify.config.AppPrefs(this).load()
        toast("正在上传与识别…")
        io.execute {
            val outcome = com.johnymoo.arverify.net.CaptureUploader(
                com.johnymoo.arverify.net.HttpUrlConnectionTransport()
            ).upload(config.baseUrl, pkg)
            runOnUiThread { onUploadResult(outcome, config.baseUrl) }
        }
    }

    private fun onUploadResult(outcome: com.johnymoo.arverify.net.UploadOutcome, baseUrl: String) {
        com.johnymoo.arverify.net.UploadResultHolder.outcome = outcome
        com.johnymoo.arverify.net.UploadResultHolder.baseUrl = baseUrl
        when (outcome) {
            is com.johnymoo.arverify.net.UploadOutcome.Failure ->
                toast("上传失败：${outcome.message}（已本地留存，可在采集库重试）")
            is com.johnymoo.arverify.net.UploadOutcome.Success -> when (outcome.result.status) {
                com.johnymoo.arverify.net.RecognitionStatus.NEEDS_MEASUREMENT ->
                    startActivity(android.content.Intent(this, com.johnymoo.arverify.measure.MeasurementFormActivity::class.java))
                else ->
                    startActivity(android.content.Intent(this, com.johnymoo.arverify.result.ResultActivity::class.java))
            }
        }
    }

    private fun exportGeneral() {
        val dir = java.io.File(holder.state.value.sessionDir)
        val files = (dir.listFiles { f -> f.isFile } ?: emptyArray()).toList()
        if (files.isEmpty()) { toast("还没有可导出的帧"); return }
        com.johnymoo.arverify.export.ReportExporter(this).share(files, "导出采集")
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()

    override fun onResume() {
        super.onResume()
        if (session == null) {
            try {
                when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> { installRequested = true; return }
                    else -> {}
                }
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "需要相机权限", Toast.LENGTH_LONG).show(); finish(); return
                }
                session = Session(this).also { s ->
                    val c = s.config
                    c.depthMode = if (s.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) Config.DepthMode.AUTOMATIC else Config.DepthMode.DISABLED
                    c.focusMode = Config.FocusMode.AUTO
                    s.configure(c)
                }
            } catch (e: Exception) { Toast.makeText(this, "无法启动 AR：${e.javaClass.simpleName}", Toast.LENGTH_LONG).show(); finish(); return }
        }
        try { session?.resume() } catch (e: CameraNotAvailableException) { Toast.makeText(this, "相机不可用", Toast.LENGTH_LONG).show(); session = null; finish(); return }
        glView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        if (session != null) { glView?.onPause(); session?.pause() }
    }

    override fun onDestroy() { io.shutdown(); session?.close(); session = null; super.onDestroy() }
}
