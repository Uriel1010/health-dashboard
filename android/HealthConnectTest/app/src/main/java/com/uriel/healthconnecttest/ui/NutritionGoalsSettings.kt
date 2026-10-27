package com.uriel.healthconnecttest.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.uriel.healthconnecttest.data.ServerSettings
import com.uriel.healthconnecttest.sync.HealthSyncApiClient
import com.uriel.healthconnecttest.sync.NutritionGoals
import com.uriel.healthconnecttest.widget.NutritionWidgetStore
import com.uriel.healthconnecttest.widget.NutritionWidgetsUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun NutritionGoalsSettings(settings: ServerSettings, onSaved: () -> Unit) {
    key(settings.serverBaseUrl, settings.readToken) { GoalsForm(settings, onSaved) }
}

@Composable
private fun GoalsForm(settings: ServerSettings, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { HealthSyncApiClient() }
    var serverGoals by remember { mutableStateOf<NutritionGoals?>(null) }
    var draft by rememberSaveable { mutableStateOf(List(5) { "" }) }
    var dirty by rememberSaveable { mutableStateOf(false) }
    var touched by rememberSaveable { mutableStateOf(List(5) { false }) }
    var loading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    var reload by remember { mutableStateOf(0) }
    val connected = settings.serverBaseUrl.isNotBlank() && settings.readToken.isNotBlank()
    val errors = NutritionGoals.errors(draft)

    LaunchedEffect(reload) {
        if (!connected) return@LaunchedEffect
        loading = true
        notice = ""
        try {
            val goals = api.fetchNutritionGoals(settings)
            serverGoals = goals
            if (!dirty) draft = goals.values().map(Int::toString)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { notice = e.message ?: "Unable to load daily goals" }
        finally { loading = false }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Daily nutrition goals", style = MaterialTheme.typography.titleLarge)
        Text("Dashboard account", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!connected) {
            Text("Open Account & server to sign in and edit your goals.")
        } else {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (serverGoals != null) NutritionGoals.LABELS.forEachIndexed { index, label ->
                var focused by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = draft[index], onValueChange = { value ->
                        draft = draft.toMutableList().also { it[index] = value }
                        dirty = true
                        notice = ""
                    }, label = { Text(label) }, singleLine = true,
                    enabled = !saving && !loading,
                    isError = touched[index] && errors[index] != null,
                    supportingText = { Text(if (touched[index] && errors[index] != null) errors[index]!! else "Daily target | 0-${NutritionGoals.LIMITS[index]}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { state ->
                        if (focused && !state.isFocused) touched = touched.toMutableList().also { it[index] = true }
                        focused = state.isFocused
                    }
                )
            }
            if (notice.isNotBlank()) Text(notice, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (serverGoals != null) Button(enabled = dirty && !saving && !loading, onClick = {
                    touched = List(5) { true }
                    if (errors.any { it != null }) return@Button
                    scope.launch {
                        saving = true
                        try {
                            val goals = api.saveNutritionGoals(settings, NutritionGoals.parse(draft))
                            serverGoals = goals
                            draft = goals.values().map(Int::toString)
                            dirty = false
                            notice = "Goals saved to your dashboard account"
                            try {
                                NutritionWidgetStore.updateGoals(context.applicationContext, goals)
                                NutritionWidgetsUpdater.updateAll(context.applicationContext)
                                onSaved()
                            } catch (e: CancellationException) { throw e }
                            catch (_: Exception) { notice = "Goals saved. Refresh Today to update local progress." }
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { notice = e.message ?: "Unable to save goals. Your edits are still here." }
                        finally { saving = false }
                    }
                }) { Text(if (saving) "Saving..." else "Save goals") }
                if (dirty && serverGoals != null) TextButton(enabled = !saving && !loading, onClick = {
                    draft = serverGoals!!.values().map(Int::toString)
                    dirty = false
                    touched = List(5) { false }
                    notice = ""
                }) { Text("Cancel") }
                if (serverGoals == null) OutlinedButton(enabled = !loading, onClick = { reload++ }) { Text("Retry") }
            }
        }
        HorizontalDivider()
    }
}
