package com.johnymoo.arverify.capture

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun KindDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val kinds = listOf("brick", "plate", "tile", "slope")
    var chosen by remember { mutableStateOf(kinds.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认类型") },
        text = {
            Column {
                kinds.forEach { k ->
                    Row(Modifier.selectable(selected = chosen == k, onClick = { chosen = k }).padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = chosen == k, onClick = { chosen = k })
                        Text(k, Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(chosen) }) { Text("上传") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
