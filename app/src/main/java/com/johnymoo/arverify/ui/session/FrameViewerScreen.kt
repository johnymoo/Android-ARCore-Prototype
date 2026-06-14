package com.johnymoo.arverify.ui.session

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.johnymoo.arverify.R
import com.johnymoo.arverify.session.CaptureLibraryRepository
import com.johnymoo.arverify.ui.common.SessionPaths
import java.io.File

@Composable
fun FrameViewerScreen(dirPath: String, index: Int) {
    val context = LocalContext.current
    val repo = remember { CaptureLibraryRepository(SessionPaths.captureRoot(context)) }
    val vm = remember { SessionDetailViewModel(repo) }
    val dir = remember(dirPath) { File(dirPath) }
    val session = remember(dirPath) { vm.load(dir) } ?: return
    val frame = session.frames.getOrNull(index) ?: return

    var showDepth by remember { mutableStateOf(false) }
    val canDepth = frame.hasDepth

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = !showDepth, onClick = { showDepth = false },
                shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.frame_rgb)) }
            SegmentedButton(selected = showDepth, enabled = canDepth, onClick = { showDepth = true },
                shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.frame_depth)) }
        }
        Box(Modifier.fillMaxSize().padding(top = 10.dp), contentAlignment = Alignment.Center) {
            val file = if (showDepth && frame.depthFileName != null) File(dir, frame.depthFileName!!) else File(dir, frame.rgbFileName)
            AsyncImage(model = file, contentDescription = frame.slot.name,
                contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            Text(
                "第 ${index + 1}/${session.frames.size} 帧 · ${frame.slot.name}" +
                    (frame.distanceM?.let { " · ${"%.2f".format(it)}m" } ?: ""),
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            )
        }
    }
}
