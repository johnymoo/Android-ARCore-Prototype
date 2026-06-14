package com.johnymoo.arverify.ui.settings

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.johnymoo.arverify.ar.CapabilityChecker
import com.johnymoo.arverify.ui.components.SectionCard
import com.johnymoo.arverify.ui.components.StatusPill
import com.johnymoo.arverify.ui.theme.SfOk
import com.johnymoo.arverify.ui.theme.SfOkBg
import com.johnymoo.arverify.ui.theme.SfWait
import com.johnymoo.arverify.ui.theme.SfWaitBg

@Composable
fun DiagnosticsScreen() {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val activity = context as? Activity
        if (activity == null) {
            Text("设备诊断暂不可用")
            return@Column
        }
        val report = remember(activity) { CapabilityChecker(activity).buildReport(activity) }
        CapabilityRow("ARCore", report.arcoreUsable)
        CapabilityRow("Depth API", report.depthAutomaticSupported)
        SectionCard {
            Text("推荐路线", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            Text(report.recommendedRoute.labelZh, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 2.dp))
        }
        if (report.notes.isNotEmpty()) {
            SectionCard {
                Text(report.notes.joinToString("\n"), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CapabilityRow(label: String, ok: Boolean) {
    SectionCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (ok) StatusPill("✓ 支持", SfOkBg, SfOk) else StatusPill("不支持", SfWaitBg, SfWait)
        }
    }
}
