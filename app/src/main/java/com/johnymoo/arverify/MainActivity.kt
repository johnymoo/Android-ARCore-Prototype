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

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) launchCapture() else toast("需要相机权限才能采集") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        checker = CapabilityChecker(this)
        exporter = ReportExporter(this)

        binding.btnCheckArcore.setOnClickListener { checkArcore() }
        binding.btnCheckDepth.setOnClickListener { refreshReport() }
        binding.btnCapture.setOnClickListener { onCaptureClicked() }
        binding.btnExport.setOnClickListener { onExportClicked() }
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

    private fun onCaptureClicked() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) launchCapture()
        else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun launchCapture() = startActivity(Intent(this, CaptureActivity::class.java))

    private fun onExportClicked() {
        val r = lastReport ?: checker.buildReport(this).also { lastReport = it }
        val files = exporter.writeReport(r)
        exporter.share(files, getString(R.string.btn_export))
        toast("已写入：${files.joinToString(" , ") { it.name }}")
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
