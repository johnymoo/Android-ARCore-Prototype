package com.johnymoo.arverify.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.navigation.NavController
import com.johnymoo.arverify.R
import com.johnymoo.arverify.config.AppPrefs
import com.johnymoo.arverify.ui.components.SectionCard
import com.johnymoo.arverify.ui.components.SettingRow
import com.johnymoo.arverify.ui.nav.Routes

@Composable
fun SettingsScreen(nav: NavController) {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    val initial = remember { prefs.load() }
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var debugGallery by remember { mutableStateOf(initial.saveDebugRgbToGallery) }

    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = baseUrl, onValueChange = { baseUrl = it },
            label = { Text("服务器地址") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SectionCard {
            SettingRow(
                label = "调试：保存原图到相册",
                trailing = { Switch(checked = debugGallery, onCheckedChange = { debugGallery = it }) },
            )
            SettingRow(
                label = stringResource(R.string.settings_diagnostics),
                onClick = { nav.navigate(Routes.DIAGNOSTICS) },
                showDivider = false,
                trailing = { Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            )
        }
        Button(onClick = {
            prefs.setBaseUrl(baseUrl.trim())
            prefs.setSaveDebugRgbToGallery(debugGallery)
        }) { Text("保存") }
    }
}
