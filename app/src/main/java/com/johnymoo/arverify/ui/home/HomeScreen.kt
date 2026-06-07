package com.johnymoo.arverify.ui.home

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavController
import com.johnymoo.arverify.R
import com.johnymoo.arverify.capture.CaptureWizardActivity
import com.johnymoo.arverify.session.CaptureLibraryRepository
import com.johnymoo.arverify.session.SessionEntry
import com.johnymoo.arverify.ui.common.SessionPaths
import com.johnymoo.arverify.ui.library.SessionRow
import com.johnymoo.arverify.ui.nav.Routes
import com.johnymoo.arverify.ui.theme.SfTeal

@Composable
fun HomeScreen(nav: NavController) {
    val context = LocalContext.current
    val repo = remember { CaptureLibraryRepository(SessionPaths.captureRoot(context)) }
    var recent by remember { mutableStateOf(emptyList<SessionEntry>()) }
    LifecycleResumeEffect(Unit) { recent = repo.listSessions().take(3); onPauseOrDispose {} }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                Text("  " + stringResource(R.string.brand_en),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            EntryCard(
                title = stringResource(R.string.home_recognition_title),
                desc = stringResource(R.string.home_recognition_desc),
                primary = true,
            ) {
                context.startActivity(Intent(context, CaptureWizardActivity::class.java))
            }
        }
        item {
            EntryCard(
                title = stringResource(R.string.home_general_title),
                desc = stringResource(R.string.home_general_desc),
                primary = false,
            ) {
                Toast.makeText(context, R.string.general_coming_soon, Toast.LENGTH_SHORT).show()
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.home_recent), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.home_recent_all), color = SfTeal,
                    modifier = Modifier.clickable { nav.navigate(Routes.LIBRARY) })
            }
        }
        items(recent) { entry ->
            SessionRow(entry) { nav.navigate(Routes.sessionDetail(entry.dir.absolutePath)) }
        }
    }
}

@Composable
private fun EntryCard(title: String, desc: String, primary: Boolean, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (primary) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                color = if (primary) SfTeal else MaterialTheme.colorScheme.onSurface)
            Text(desc, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
