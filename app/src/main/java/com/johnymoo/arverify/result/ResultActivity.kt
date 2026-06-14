package com.johnymoo.arverify.result

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.johnymoo.arverify.net.RecognitionStatus
import com.johnymoo.arverify.net.UploadOutcome
import com.johnymoo.arverify.net.UploadResultHolder
import com.johnymoo.arverify.ui.components.ScreenScaffold
import com.johnymoo.arverify.ui.components.SectionCard
import com.johnymoo.arverify.ui.components.StatusPill
import com.johnymoo.arverify.ui.theme.ScanForgeTheme
import com.johnymoo.arverify.ui.theme.SfMuted
import com.johnymoo.arverify.ui.theme.SfOk
import com.johnymoo.arverify.ui.theme.SfOkBg
import org.json.JSONObject

class ResultActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val outcome = UploadResultHolder.outcome as? UploadOutcome.Success
        val r = outcome?.result
        val jobId = r?.jobId
        // Contract note: confirm asset-id field with the pairing spec.
        val glbUrl = if (r?.status == RecognitionStatus.RECOGNIZED && !jobId.isNullOrBlank()) {
            UploadResultHolder.baseUrl.trimEnd('/') + "/assets/" + jobId
        } else {
            null
        }

        setContent {
            ScanForgeTheme {
                ScreenScaffold(title = "识别结果", onBack = { finish() }) { pad ->
                    Column(
                        Modifier
                            .padding(pad)
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (r == null) {
                            Text("无识别结果")
                            return@Column
                        }
                        SectionCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    "${r.recognized?.system ?: "-"} · ${r.recognized?.kind ?: "-"} · " +
                                        "${r.recognized?.unitsX ?: "-"}×${r.recognized?.unitsY ?: "-"}",
                                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (r.status == RecognitionStatus.RECOGNIZED) {
                                    StatusPill("已识别", SfOkBg, SfOk)
                                }
                            }
                            Text(
                                "pitch ${r.recognized?.pitchMm ?: "-"}mm · 置信度 ${r.recognized?.confidence ?: "-"}",
                                color = SfMuted,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        var showModel by remember { mutableStateOf(false) }
                        if (glbUrl != null) {
                            Button(onClick = { showModel = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("预览模型")
                            }
                        }
                        if (showModel && glbUrl != null) {
                            AndroidView(
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        settings.javaScriptEnabled = true
                                        settings.allowFileAccess = true
                                        webViewClient = object : android.webkit.WebViewClient() {
                                            override fun onPageFinished(view: WebView?, url: String?) {
                                                view?.evaluateJavascript("loadModel(${JSONObject.quote(glbUrl)})", null)
                                            }
                                        }
                                        loadUrl("file:///android_asset/viewer.html")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}
