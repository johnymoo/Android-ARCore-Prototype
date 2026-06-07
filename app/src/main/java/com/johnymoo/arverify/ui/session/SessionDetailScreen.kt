package com.johnymoo.arverify.ui.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.johnymoo.arverify.ui.common.SessionPaths
import com.johnymoo.arverify.ui.nav.Routes
import java.io.File

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
        Text(
            r?.let { "${it.system ?: "-"} · ${it.kind ?: "-"} · ${it.unitsX ?: "-"}×${it.unitsY ?: "-"}" }
                ?: session.partId,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            r?.let { "pitch ${it.pitchMm ?: "-"}mm · 置信度 ${it.confidence ?: "-"}" } ?: "通用采集 · ${session.frames.size} 帧",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
        )

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
                Button(onClick = { /* Plan 4: re-upload via CaptureUploader */ }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_preview_model))
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
