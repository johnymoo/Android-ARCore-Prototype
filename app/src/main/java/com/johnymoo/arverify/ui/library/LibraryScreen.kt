package com.johnymoo.arverify.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import com.johnymoo.arverify.R
import com.johnymoo.arverify.session.CaptureLibraryRepository
import com.johnymoo.arverify.session.SessionEntry
import com.johnymoo.arverify.session.SessionStatus
import com.johnymoo.arverify.ui.common.SessionPaths
import com.johnymoo.arverify.ui.components.EmptyState
import com.johnymoo.arverify.ui.components.StatusPill
import com.johnymoo.arverify.ui.nav.Routes
import com.johnymoo.arverify.ui.theme.SfOk
import com.johnymoo.arverify.ui.theme.SfOkBg
import com.johnymoo.arverify.ui.theme.SfWait
import com.johnymoo.arverify.ui.theme.SfWaitBg

@Composable
fun LibraryScreen(nav: NavController) {
    val context = LocalContext.current
    val repo = remember { CaptureLibraryRepository(SessionPaths.captureRoot(context)) }
    val vm: LibraryViewModel = viewModel(factory = viewModelFactory {
        initializer { LibraryViewModel(repo) }
    })
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) { vm.refresh(); onPauseOrDispose {} }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterTab(stringResource(R.string.lib_filter_all), state.filter == LibraryFilter.ALL) { vm.setFilter(LibraryFilter.ALL) }
            FilterTab(stringResource(R.string.lib_filter_recognized), state.filter == LibraryFilter.RECOGNIZED) { vm.setFilter(LibraryFilter.RECOGNIZED) }
            FilterTab(stringResource(R.string.lib_filter_pending), state.filter == LibraryFilter.PENDING) { vm.setFilter(LibraryFilter.PENDING) }
        }
        if (!state.loading && state.visible.isEmpty()) {
            EmptyState(stringResource(R.string.lib_empty))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(state.visible) { entry ->
                    SessionRow(entry) { nav.navigate(Routes.sessionDetail(entry.dir.absolutePath)) }
                }
            }
        }
    }
}

@Composable
private fun FilterTab(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
fun SessionRow(entry: SessionEntry, onClick: () -> Unit) {
    val s = entry.session
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("${s.partId} · ${s.frames.size} 帧", style = MaterialTheme.typography.bodyMedium)
            Text("${SessionDisplay.captureTime(s.createdAtEpochMs)} · ${s.deviceModel}", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val (text, bg, fg) = statusColors(s.status)
        StatusPill(text, bg, fg)
    }
}

@Composable
private fun statusColors(status: SessionStatus): Triple<String, androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> =
    when (status) {
        SessionStatus.RECOGNIZED -> Triple("已识别", SfOkBg, SfOk)
        SessionStatus.EXPORTED -> Triple("已导出", SfOkBg, SfOk)
        SessionStatus.PENDING_UPLOAD -> Triple("待上传", SfWaitBg, SfWait)
        SessionStatus.NEEDS_MEASUREMENT -> Triple("待测量", SfWaitBg, SfWait)
        SessionStatus.FAILED -> Triple("失败", SfWaitBg, SfWait)
    }
