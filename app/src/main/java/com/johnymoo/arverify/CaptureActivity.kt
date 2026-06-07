package com.johnymoo.arverify

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.View
import android.widget.Toast
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
import com.johnymoo.arverify.capture.ArFrameExtractor
import com.johnymoo.arverify.databinding.ActivityCaptureBinding
import com.johnymoo.arverify.debug.DebugGallerySaver
import com.johnymoo.arverify.depth.DepthColorizer
import com.johnymoo.arverify.export.PlyWriter
import com.johnymoo.arverify.render.BackgroundRenderer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class CaptureActivity : AppCompatActivity(), GLSurfaceView.Renderer {

    private lateinit var binding: ActivityCaptureBinding
    private val background = BackgroundRenderer()
    private lateinit var gallerySaver: DebugGallerySaver
    private lateinit var config: CaptureConfig
    private var session: Session? = null
    private var installRequested = false

    @Volatile private var captureRequested = false
    private var viewportWidth = 0
    private var viewportHeight = 0
    private var displayRotation = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)
        gallerySaver = DebugGallerySaver(this)
        config = AppPrefs(this).load()

        binding.surfaceView.preserveEGLContextOnPause = true
        binding.surfaceView.setEGLContextClientVersion(2)
        binding.surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        binding.surfaceView.setRenderer(this)
        binding.surfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        binding.btnShutter.setOnClickListener { captureRequested = true }
        binding.btnDone.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        if (session == null) {
            try {
                when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> { installRequested = true; return }
                    ArCoreApk.InstallStatus.INSTALLED -> {}
                    else -> {}
                }
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED
                ) { toast("需要相机权限"); finish(); return }

                session = Session(this).also { s ->
                    val config = s.config
                    config.depthMode =
                        if (s.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) Config.DepthMode.AUTOMATIC
                        else Config.DepthMode.DISABLED
                    config.focusMode = Config.FocusMode.AUTO
                    s.configure(config)
                }
            } catch (e: Exception) {
                toast("无法启动 AR 会话：${e.javaClass.simpleName}"); finish(); return
            }
        }
        try {
            session?.resume()
        } catch (e: CameraNotAvailableException) {
            toast("相机不可用"); session = null; finish(); return
        }
        binding.surfaceView.onResume()
    }

    override fun onPause() {
        super.onPause()
        if (session != null) {
            binding.surfaceView.onPause()
            session?.pause()
        }
    }

    override fun onDestroy() {
        session?.close()
        session = null
        super.onDestroy()
    }

    // --- GLSurfaceView.Renderer ---

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        background.createOnGlThread()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        viewportWidth = width
        viewportHeight = height
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
            if (captureRequested && frame.camera.trackingState == TrackingState.TRACKING) {
                captureRequested = false
                doCapture(frame)
            }
        } catch (e: CameraNotAvailableException) {
            runOnUiThread { toast("相机不可用") }
        }
    }

    private fun doCapture(frame: Frame) {
        try {
            val dir = File(getExternalFilesDir(null), "exports").apply { mkdirs() }
            val stamp = ts()
            val saved = mutableListOf<String>()

            if (config.saveDebugRgbToGallery) {
                try {
                    frame.acquireCameraImage().use { image ->
                        val jpg = ArFrameExtractor.rgbJpeg(image)
                        if (gallerySaver.saveJpeg(jpg, "debug_rgb_$stamp.jpg")) {
                            saved.add("相册/debug_rgb_$stamp.jpg")
                        }
                    }
                } catch (e: NotYetAvailableException) {
                    runOnUiThread { toast("原图未就绪") }
                }
            }

            try {
                frame.acquireDepthImage16Bits().use { depth ->
                    val bmp = DepthColorizer.colorize(depth)
                    val png = File(dir, "depth_$stamp.png")
                    png.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    saved.add(png.name)
                    runOnUiThread {
                        binding.ivPreview.setImageBitmap(bmp)
                        binding.ivPreview.visibility = View.VISIBLE
                    }
                }
            } catch (e: NotYetAvailableException) {
                runOnUiThread { toast("深度数据未就绪，请对准物体后重试") }
            }

            try {
                frame.acquirePointCloud().use { pc ->
                    val buf = pc.points
                    val arr = FloatArray(buf.remaining())
                    buf.get(arr)
                    val ply = File(dir, "points_$stamp.ply")
                    ply.writeText(PlyWriter.toAsciiPly(arr))
                    saved.add(ply.name)
                }
            } catch (e: NotYetAvailableException) {
                runOnUiThread { toast("点云数据未就绪") }
            }

            if (saved.isNotEmpty()) {
                runOnUiThread { toast("已保存：${saved.joinToString(" , ")}") }
            } else {
                runOnUiThread { toast("采集失败：深度和点云数据均未就绪，请对准物体后重试") }
            }
        } catch (e: Exception) {
            runOnUiThread { toast("采集失败：${e.javaClass.simpleName}") }
        }
    }

    private fun ts(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
}
