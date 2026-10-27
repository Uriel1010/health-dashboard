package com.uriel.healthconnecttest.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uriel.healthconnecttest.update.AppUpdates
import kotlinx.coroutines.launch

@Composable
fun AppUpdateSettings(updates: AppUpdates) {
    val state by updates.state.collectAsState()
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("App updates", style = MaterialTheme.typography.titleLarge)
        Text("Health Companion ${updates.versionName} | ${if (updates.production) "Production" else "Development"}", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(state.message, style = MaterialTheme.typography.bodyMedium)
        if (state.checking) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.downloading) {
            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
            Text("${(state.progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
        }
        state.release?.let { release ->
            Text("What's new in ${release.versionName}", style = MaterialTheme.typography.titleSmall)
            Text(release.notes.take(1800), style = MaterialTheme.typography.bodySmall)
            if (!state.downloading && !state.checking) {
                Button(onClick = {
                    if (state.ready != null) updates.install() else scope.launch { updates.download() }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.ready != null) "Install update" else "Download update")
                }
            }
        }
        OutlinedButton(onClick = { scope.launch { updates.check() } }, enabled = !state.checking && !state.downloading) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Check for updates")
        }
        HorizontalDivider()
    }
}
