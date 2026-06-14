package com.johnymoo.arverify.capture

import android.opengl.GLSurfaceView
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.johnymoo.arverify.session.CapturedFrame
import com.johnymoo.arverify.ui.theme.SfWait

@Composable
fun CaptureScreen(
    holder: CaptureHolder,
    glViewFactory: (android.content.Context) -> GLSurfaceView,
    onShutter: () -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit,
    finishLabel: String,
) {
    val state by holder.state.collectAsStateWithLifecycle()
    val depthBmp by holder.depthBitmap.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = glViewFactory, modifier = Modifier.fillMaxSize())

        if (state.depthOn) {
            depthBmp?.let {
                Image(it.asImageBitmap(), contentDescription = "depth", contentScale = ContentScale.FillBounds,
                    alpha = 0.5f, modifier = Modifier.fillMaxSize())
            }
        }

        // Top chrome
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("‹", color = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.padding(end = 8.dp).clickableNoRipple(onBack))
            Text(stepText(state), color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.weight(1f))
            FilterChip(selected = state.modeUi == CaptureModeUi.GUIDED, onClick = { holder.setMode(CaptureModeUi.GUIDED) }, label = { Text("向导") })
            FilterChip(selected = state.modeUi == CaptureModeUi.FREE, onClick = { holder.setMode(CaptureModeUi.FREE) }, label = { Text("自由") }, modifier = Modifier.padding(start = 6.dp))
        }

        // Depth toggle chip (top-right, below chrome)
        Surface(color = androidx.compose.ui.graphics.Color(0x88000000), shape = MaterialTheme.shapes.large,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 56.dp, end = 12.dp)
                .clickableNoRipple { holder.toggleDepth() }) {
            Text(if (state.depthOn) "深度 ● 开" else "深度 ○ 关", color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp))
        }

        // Quality + prompt (center-bottom)
        TargetReticle(
            qualityReason = state.qualityReason,
            targetLocked = state.targetLocked,
            modifier = Modifier.align(Alignment.Center),
        )
        Column(
            modifier = Modifier.align(Alignment.Center).padding(top = 234.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                CaptureGuidanceText.primaryPrompt(state),
                color = guidanceColor(state.qualityReason),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                CaptureGuidanceText.statusLine(state),
                color = Color.White.copy(alpha = 0.86f),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        // Bottom chrome: thumbnails + shutter + finish
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                items(state.frames) { f -> ThumbnailDot(f) }
            }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp).clickableNoRipple(onShutter)) {}
            Button(onClick = onFinish, enabled = state.canFinish) { Text("$finishLabel ${state.frames.size}") }
        }
    }
}

@Composable private fun TargetReticle(
    qualityReason: QualityReason,
    targetLocked: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = when {
        qualityReason == QualityReason.OK -> MaterialTheme.colorScheme.primary
        targetLocked -> SfWait
        else -> Color.White.copy(alpha = 0.82f)
    }
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(2.dp, color),
        modifier = modifier.width(210.dp).height(210.dp),
    ) {}
}

@Composable private fun ThumbnailDot(f: CapturedFrame) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small,
        modifier = Modifier.size(width = 34.dp, height = 28.dp)) {
        Text(f.slot.name.first().toString(), style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(4.dp))
    }
}

private fun stepText(s: CaptureUiState): String =
    if (s.modeUi == CaptureModeUi.GUIDED) when (s.wizardStep) {
        WizardStep.NEED_TOP -> "向导 · 俯视凸点面"
        WizardStep.NEED_SIDE -> "向导 · 侧面"
        WizardStep.NEED_ANGLES -> "向导 · 补角度"
        WizardStep.READY -> "向导 · 已就绪"
    } else "自由 · 已采 ${s.frames.size}"

@Composable
private fun guidanceColor(reason: QualityReason): Color =
    if (reason == QualityReason.OK) MaterialTheme.colorScheme.primary else SfWait

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(clickable(
        interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
        indication = null, onClick = onClick))
