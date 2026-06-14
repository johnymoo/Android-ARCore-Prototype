package com.johnymoo.arverify.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.johnymoo.arverify.ui.theme.SfBorder
import com.johnymoo.arverify.ui.theme.SfDivider
import com.johnymoo.arverify.ui.theme.SfMuted

/** Small status chip (已识别 / 待测量 / ✓ 支持 …). */
@Composable
fun StatusPill(text: String, background: Color, foreground: Color, modifier: Modifier = Modifier) {
    Surface(color = background, shape = MaterialTheme.shapes.small, modifier = modifier) {
        Text(
            text, color = foreground, fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** Elevated content card: solid surface + 1px border + rounded corners (style A). */
@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, SfBorder),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

/** A settings row: label on the left, trailing content on the right, optional click + hairline divider. */
@Composable
fun SettingRow(
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
    trailing: @Composable () -> Unit = {},
) {
    Column {
        Row(
            modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            trailing()
        }
        if (showDivider) {
            Surface(color = SfDivider, modifier = Modifier.fillMaxWidth().padding(start = 0.dp)) {
                Box(Modifier.fillMaxWidth().padding(top = 0.5.dp)) {}
            }
        }
    }
}

/** Centered empty-state message. */
@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = SfMuted, style = MaterialTheme.typography.bodyMedium)
    }
}
