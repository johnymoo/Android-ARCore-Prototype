package com.johnymoo.arverify.ui.settings

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.johnymoo.arverify.ar.CapabilityChecker

@Composable
fun DiagnosticsScreen() {
    val context = LocalContext.current
    val yes = "支持"; val no = "不支持"
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("设备诊断", style = MaterialTheme.typography.titleLarge)
        val activity = context as? Activity
        if (activity == null) {
            Text("设备诊断暂不可用", modifier = Modifier.padding(top = 12.dp))
            return@Column
        }
        val report = remember(activity) { CapabilityChecker(activity).buildReport(activity) }
        Text("ARCore：${if (report.arcoreUsable) yes else no}", modifier = Modifier.padding(top = 12.dp))
        Text("Depth：${if (report.depthAutomaticSupported) yes else no}", modifier = Modifier.padding(top = 6.dp))
        Text("推荐路线：${report.recommendedRoute.labelZh}", style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 6.dp))
        Text(report.notes.joinToString("\n"), color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp))
    }
}
