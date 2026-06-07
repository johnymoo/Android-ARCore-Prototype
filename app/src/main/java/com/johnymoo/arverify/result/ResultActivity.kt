package com.johnymoo.arverify.result

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import com.johnymoo.arverify.R
import com.johnymoo.arverify.databinding.ActivityResultBinding
import com.johnymoo.arverify.net.RecognitionStatus
import com.johnymoo.arverify.net.UploadOutcome
import com.johnymoo.arverify.net.UploadResultHolder

class ResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultBinding

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val outcome = UploadResultHolder.outcome
        if (outcome !is UploadOutcome.Success) {
            binding.tvResult.text = "无识别结果"
            return
        }
        val r = outcome.result
        binding.tvResult.text = buildString {
            appendLine("状态：${r.status}")
            r.recognized?.let {
                appendLine("system：${it.system ?: "-"}")
                appendLine("kind：${it.kind ?: "-"}")
                appendLine("units：${it.unitsX ?: "-"} × ${it.unitsY ?: "-"}")
                appendLine("pitch_mm：${it.pitchMm ?: "-"}")
                appendLine("confidence：${it.confidence ?: "-"}")
            }
        }

        val jobId = r.jobId
        if (r.status == RecognitionStatus.RECOGNIZED && !jobId.isNullOrBlank()) {
            // Contract note: confirm asset-id field with the pairing spec.
            val glbUrl = UploadResultHolder.baseUrl.trimEnd('/') + "/assets/" + jobId
            binding.btnPreview.isEnabled = true
            binding.btnPreview.setOnClickListener { showModel(glbUrl) }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun showModel(glbUrl: String) {
        binding.webView.visibility = View.VISIBLE
        binding.webView.settings.javaScriptEnabled = true
        binding.webView.settings.allowFileAccess = true
        binding.webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                view?.evaluateJavascript("loadModel('${glbUrl}')", null)
            }
        }
        binding.webView.loadUrl("file:///android_asset/viewer.html")
    }
}
