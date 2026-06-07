package com.johnymoo.arverify

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.ar.core.ArCoreApk
import com.johnymoo.arverify.ar.CapabilityChecker
import com.johnymoo.arverify.databinding.ActivityMainBinding
import com.johnymoo.arverify.export.ReportExporter
import com.johnymoo.arverify.model.CapabilityReport

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var checker: CapabilityChecker
    private lateinit var exporter: ReportExporter
    private var lastReport: CapabilityReport? = null
    private var pendingCameraPermissionPurpose = CameraPermissionPurpose.CAPTURE

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val purpose = pendingCameraPermissionPurpose
        if (granted) {
            when (purpose) {
                CameraPermissionPurpose.CHECK_DEPTH -> refreshReport()
                CameraPermissionPurpose.CAPTURE -> launchCapture()
                CameraPermissionPurpose.SMART_CAPTURE -> launchSmartCapture()
            }
        } else {
            toast(purpose.deniedToast)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        checker = CapabilityChecker(this)
        exporter = ReportExporter(this)

        binding.btnCheckArcore.setOnClickListener { checkArcore() }
        binding.btnCheckDepth.setOnClickListener { onCheckDepthClicked() }
        binding.btnCapture.setOnClickListener { onCaptureClicked() }
        binding.btnExport.setOnClickListener { onExportClicked() }
        binding.btnSmartCapture.setOnClickListener { onSmartCaptureClicked() }
        binding.btnSmartCapture.setOnLongClickListener { showBaseUrlDialog(); true }
    }

    override fun onResume() {
        super.onResume()
        refreshReport()
    }

    private fun checkArcore() {
        when (checker.availability()) {
            ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED,
            ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD -> {
                try {
                    checker.requestInstall(this, true) // launches Play dialog; onResume re-runs
                } catch (e: Exception) {
                    toast("无法安装 ARCore：${e.javaClass.simpleName}")
                }
            }
            else -> refreshReport()
        }
    }

    private fun refreshReport() {
        val report = checker.buildReport(this)
        lastReport = report
        render(report)
    }

    private fun render(r: CapabilityReport) {
        val yes = getString(R.string.supported)
        val no = getString(R.string.not_supported)
        binding.tvArcore.text = getString(R.string.status_arcore, if (r.arcoreUsable) yes else no)
        binding.tvDepth.text = getString(R.string.status_depth, if (r.depthAutomaticSupported) yes else no)
        binding.tvRoute.text = getString(R.string.status_route, r.recommendedRoute.labelZh)
        binding.btnCapture.isEnabled = r.arcoreUsable && r.depthAutomaticSupported
        binding.tvNotes.text = r.notes.joinToString("\n")
    }

    private fun onCheckDepthClicked() {
        if (hasCameraPermission()) refreshReport()
        else requestCameraPermission(CameraPermissionPurpose.CHECK_DEPTH)
    }

    private fun onCaptureClicked() {
        if (hasCameraPermission()) launchCapture()
        else requestCameraPermission(CameraPermissionPurpose.CAPTURE)
    }

    private fun launchCapture() = startActivity(Intent(this, CaptureActivity::class.java))

    private fun onSmartCaptureClicked() {
        if (hasCameraPermission()) launchSmartCapture()
        else requestCameraPermission(CameraPermissionPurpose.SMART_CAPTURE)
    }

    private fun launchSmartCapture() =
        startActivity(Intent(this, com.johnymoo.arverify.capture.CaptureWizardActivity::class.java))

    private fun showBaseUrlDialog() {
        val prefs = com.johnymoo.arverify.config.AppPrefs(this)
        val input = android.widget.EditText(this).apply {
            setText(prefs.load().baseUrl)
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.settings_base_url)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                prefs.setBaseUrl(input.text.toString().trim())
                toast("已保存服务器地址")
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun onExportClicked() {
        val r = lastReport ?: checker.buildReport(this).also { lastReport = it }
        val files = exporter.writeReport(r)
        exporter.share(files, getString(R.string.btn_export))
        toast("已写入：${files.joinToString(" , ") { it.name }}")
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestCameraPermission(purpose: CameraPermissionPurpose) {
        pendingCameraPermissionPurpose = purpose
        cameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}

enum class CameraPermissionPurpose(val deniedToast: String) {
    CHECK_DEPTH("需要相机权限才能探测 Depth API"),
    CAPTURE("需要相机权限才能采集"),
    SMART_CAPTURE("需要相机权限才能智能采集"),
}
