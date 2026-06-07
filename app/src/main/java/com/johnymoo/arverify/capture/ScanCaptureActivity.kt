package com.johnymoo.arverify.capture

import android.Manifest
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
                CaptureScreen(
                    holder = holder,
                    glViewFactory = { ctx ->
                        GLSurfaceView(ctx).apply {
                            preserveEGLContextOnPause = true
                            setEGLContextClientVersion(2)
                            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
                            setRenderer(renderer)
                            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                            glView = this
                        }
                    },
                    onShutter = { renderer.captureRequested = true },
                    onFinish = { /* Plan 4: recognition upload / general export */
                        Toast.makeText(this, "接通在 Plan 4：${mode}", Toast.LENGTH_SHORT).show()
                    },
                    onBack = { finish() },
                    finishLabel = if (mode == CaptureMode.RECOGNITION) "上传" else "导出",
                )
            }
        }
    }

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

    override fun onDestroy() { session?.close(); session = null; super.onDestroy() }
}
