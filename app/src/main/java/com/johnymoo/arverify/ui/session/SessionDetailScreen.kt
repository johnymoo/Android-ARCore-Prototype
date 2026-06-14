package com.johnymoo.arverify.ui.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.johnymoo.arverify.R
import com.johnymoo.arverify.session.CaptureLibraryRepository
import com.johnymoo.arverify.session.CaptureMode
import com.johnymoo.arverify.session.SessionStatus
import com.johnymoo.arverify.ui.common.SessionPaths
import com.johnymoo.arverify.ui.components.SectionCard
import com.johnymoo.arverify.ui.components.StatusPill
import com.johnymoo.arverify.ui.nav.Routes
import com.johnymoo.arverify.ui.theme.SfOk
import com.johnymoo.arverify.ui.theme.SfOkBg
import com.johnymoo.arverify.ui.theme.SfWait
import com.johnymoo.arverify.ui.theme.SfWaitBg
import java.io.File

private fun sessionPill(status: SessionStatus): Triple<String, androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> =
    when (status) {
        SessionStatus.RECOGNIZED -> Triple("已识别", SfOkBg, SfOk)
        SessionStatus.EXPORTED -> Triple("已导出", SfOkBg, SfOk)
        SessionStatus.PENDING_UPLOAD -> Triple("待上传", SfWaitBg, SfWait)
        SessionStatus.NEEDS_MEASUREMENT -> Triple("待测量", SfWaitBg, SfWait)
        SessionStatus.FAILED -> Triple("失败", SfWaitBg, SfWait)
    }

@Composable
fun SessionDetailScreen(nav: NavController, dirPath: String) {
    val context = LocalContext.current
    val repo = remember { CaptureLibraryRepository(SessionPaths.captureRoot(context)) }
    val vm: SessionDetailViewModel = viewModel(factory = viewModelFactory { initializer { SessionDetailViewModel(repo) } })
    val dir = remember(dirPath) { File(dirPath) }
    val session = remember(dirPath) { vm.load(dir) }

    if (session == null) {
        Text("会话已删除或损坏", Modifier.padding(20.dp)); return
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        val r = session.recognized
        SectionCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    r?.let { "${it.system ?: "-"} · ${it.kind ?: "-"} · ${it.unitsX ?: "-"}×${it.unitsY ?: "-"}" }
                        ?: session.partId,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.width(8.dp))
                val (t, bg, fg) = sessionPill(session.status)
                StatusPill(t, bg, fg)
            }
            Text(
                r?.let { "pitch ${it.pitchMm ?: "-"}mm · 置信度 ${it.confidence ?: "-"}" }
                    ?: "通用采集 · ${session.frames.size} 帧",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(10.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            verticalArrangement = Arrangement.spacedBy(7.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.weight(1f),
        ) {
            itemsIndexed(session.frames) { index, frame ->
                AsyncImage(
                    model = File(dir, frame.rgbFileName),
                    contentDescription = frame.slot.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1.2f)
                        .clickable { nav.navigate(Routes.frameViewer(dirPath, index)) },
                )
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (session.mode == CaptureMode.RECOGNITION) {
                Button(onClick = {
                    val pkg = com.johnymoo.arverify.net.CaptureUploadAssembler
                        .fromDir(dir, session.partId, session.recognized?.kind ?: "brick", null)
                    if (pkg == null) { android.widget.Toast.makeText(context, "缺少识别帧", android.widget.Toast.LENGTH_SHORT).show() }
                    else {
                        val missingImages = com.johnymoo.arverify.net.CaptureUploadAssembler.missingImageCount(pkg)
                        if (missingImages > 0) {
                            android.widget.Toast.makeText(context, "还需补拍 ${missingImages} 张角度图后再上传", android.widget.Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        val baseUrl = com.johnymoo.arverify.config.AppPrefs(context).load().baseUrl
                        android.widget.Toast.makeText(context, "正在重新上传…", android.widget.Toast.LENGTH_SHORT).show()
                        Thread {
                            val outcome = com.johnymoo.arverify.net.CaptureUploader(
                                com.johnymoo.arverify.net.HttpUrlConnectionTransport()
                            ).upload(baseUrl, pkg)
                            com.johnymoo.arverify.net.UploadResultHolder.outcome = outcome
                            com.johnymoo.arverify.net.UploadResultHolder.baseUrl = baseUrl
                            (context as? android.app.Activity)?.runOnUiThread {
                                if (outcome is com.johnymoo.arverify.net.UploadOutcome.Success) {
                                    when (outcome.result.status) {
                                        com.johnymoo.arverify.net.RecognitionStatus.NEEDS_MEASUREMENT ->
                                            context.startActivity(android.content.Intent(context, com.johnymoo.arverify.measure.MeasurementWizardActivity::class.java))
                                        else ->
                                            context.startActivity(android.content.Intent(context, com.johnymoo.arverify.result.ResultActivity::class.java))
                                    }
                                } else {
                                    val msg = (outcome as? com.johnymoo.arverify.net.UploadOutcome.Failure)?.message ?: "未知错误"
                                    android.widget.Toast.makeText(context, "上传失败：$msg", android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        }.start()
                    }
                }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_reupload))
                }
            }
            OutlinedButton(onClick = {
                vm.delete(dir, session)
                nav.popBackStack()
            }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.action_delete))
            }
        }
    }
}
