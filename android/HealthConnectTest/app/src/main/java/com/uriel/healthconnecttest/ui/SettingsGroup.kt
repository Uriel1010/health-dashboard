package com.uriel.healthconnecttest.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsGroup(
    id: String,
    title: String,
    summary: String,
    openGroup: String,
    onGroupChange: (String) -> Unit,
    attention: Boolean = false,
    content: @Composable () -> Unit
) {
    val expanded = openGroup == id
    val savedContent = rememberSaveableStateHolder()
    OutlinedCard(Modifier.fillMaxWidth()) {
        Surface(
            onClick = { onGroupChange(if (expanded) "" else id) },
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().semantics {
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            }
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(summary, style = MaterialTheme.typography.bodySmall,
                        color = if (attention) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (attention) Badge()
                Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null)
            }
        }
        if (expanded) savedContent.SaveableStateProvider(id) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        }
    }
}
