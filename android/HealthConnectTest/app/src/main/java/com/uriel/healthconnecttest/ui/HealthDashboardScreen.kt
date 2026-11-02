package com.uriel.healthconnecttest.ui

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import com.uriel.healthconnecttest.R
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.uriel.healthconnecttest.data.ServerSettings
import com.uriel.healthconnecttest.data.ServerSettingsRepository
import com.uriel.healthconnecttest.health.HealthConnectAvailability
import com.uriel.healthconnecttest.health.HealthConnectManager
import com.uriel.healthconnecttest.health.HealthMetric
import com.uriel.healthconnecttest.health.MetricCardState
import com.uriel.healthconnecttest.health.MetricStatus
import com.uriel.healthconnecttest.sync.HealthSnapshotBuilder
import com.uriel.healthconnecttest.sync.HealthSyncApiClient
import com.uriel.healthconnecttest.sync.HealthSyncScheduler
import com.uriel.healthconnecttest.sync.MealLogRequest
import com.uriel.healthconnecttest.sync.MealType
import com.uriel.healthconnecttest.sync.ServerMeal
import com.uriel.healthconnecttest.sync.toNutritionInput
import com.uriel.healthconnecttest.ui.theme.HealthConnectTestTheme
import com.uriel.healthconnecttest.widget.NutritionWidgetStore
import com.uriel.healthconnecttest.widget.NutritionWidgetsUpdater
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.roundToLong
import kotlinx.coroutines.launch

private enum class AppTab(val label: String) {
    Today("Today"),
    Food("Food"),
    Trends("Trends"),
    Health("Health"),
    Settings("Settings")
}

@Composable
fun HealthDashboardScreen(modifier: Modifier = Modifier, openFood: Boolean = false, openTrends: Boolean = false) {
    val context = LocalContext.current
    val manager = remember { HealthConnectManager(context.applicationContext) }
    val repository = remember { ServerSettingsRepository(context.applicationContext) }
    val apiClient = remember { HealthSyncApiClient() }
    val updates = remember { com.uriel.healthconnecttest.update.AppUpdates(context) }
    val updateState by updates.state.collectAsState()
    LaunchedEffect(Unit) { updates.check() }
    val snapshotBuilder = remember { HealthSnapshotBuilder(context.applicationContext, manager) }
    val settings by repository.settings.collectAsState(initial = ServerSettings())
    val coroutineScope = rememberCoroutineScope()
    val cards = remember { mutableStateMapOf<HealthMetric, MetricCardState>() }

    var availability by remember { mutableStateOf(HealthConnectAvailability.Unsupported) }
    var grantedPermissions by remember { mutableStateOf(emptySet<String>()) }
    var backgroundReadAvailable by remember { mutableStateOf(false) }
    var backgroundReadGranted by remember { mutableStateOf(false) }
    var nutritionWriteGranted by remember { mutableStateOf(false) }
    var lastRefreshTime by remember { mutableStateOf<Instant?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var topError by remember { mutableStateOf<String?>(null) }
    var syncMessage by remember { mutableStateOf("") }
    var mealMessage by remember { mutableStateOf("") }
    var isSubmittingMeal by remember { mutableStateOf(false) }
    var lastSubmittedMeal by remember { mutableStateOf("") }

    var serverUrlInput by remember { mutableStateOf("") }
    var deviceIdInput by remember { mutableStateOf("") }
    var deviceNameInput by remember { mutableStateOf("") }
    var apiTokenInput by remember { mutableStateOf("") }
    var dashboardUsernameInput by remember { mutableStateOf("") }
    var dashboardPasswordInput by remember { mutableStateOf("") }
    var readTokenInput by remember { mutableStateOf("") }
    var allowHttpInput by remember { mutableStateOf(false) }
    var dashboardLoginMessage by remember { mutableStateOf("") }
    var isDashboardLoginInProgress by remember { mutableStateOf(false) }
    var serverMealSyncMessage by remember { mutableStateOf("") }
    var isSyncingServerMeals by remember { mutableStateOf(false) }
    var serverMeals by remember { mutableStateOf<List<ServerMeal>>(emptyList()) }
    var nutritionProgress by remember { mutableStateOf<com.uriel.healthconnecttest.widget.NutritionWidgetProgress?>(null) }
    var nutritionDateLabel by remember { mutableStateOf("") }
    var isLoadingServerMeals by remember { mutableStateOf(false) }
    var nutritionLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(settings.serverBaseUrl, settings.readToken) { nutritionLoaded = false }
    var mealListMessage by remember { mutableStateOf("") }
    var pendingMealDelete by remember { mutableStateOf<ServerMeal?>(null) }
    var deletingMealId by remember { mutableStateOf<Long?>(null) }
    var mealListRevision by remember { mutableStateOf(0) }
    var mealClientId by remember { mutableStateOf(generateClientMealId()) }
    var mealType by remember { mutableStateOf(MealType.Lunch) }
    var mealEatenAtInput by remember { mutableStateOf(defaultMealTime()) }
    var mealTitleInput by remember { mutableStateOf("") }
    var mealCaloriesInput by remember { mutableStateOf("") }
    var mealProteinInput by remember { mutableStateOf("") }
    var mealCarbsInput by remember { mutableStateOf("") }
    var mealFatInput by remember { mutableStateOf("") }
    var mealNotesInput by remember { mutableStateOf("") }
    var selectedTab by rememberSaveable { mutableStateOf(if (openTrends) AppTab.Trends else if (openFood) AppTab.Food else AppTab.Today) }
    var openSettingsGroup by rememberSaveable { mutableStateOf("") }
    var showDayInfo by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(settings.serverBaseUrl, settings.deviceId, settings.deviceName, settings.allowInsecureHttpForLan) {
        serverUrlInput = settings.serverBaseUrl
        deviceIdInput = settings.deviceId
        deviceNameInput = settings.deviceName
        allowHttpInput = settings.allowInsecureHttpForLan
    }

    LaunchedEffect(
        settings.backgroundSyncEnabled,
        settings.chargingSyncIntervalMinutes,
        settings.batterySyncIntervalMinutes,
        settings.syncHealthSnapshotsEnabled,
        settings.syncServerMealsEnabled,
        settings.updateNutritionWidgetEnabled
    ) {
        HealthSyncScheduler.schedulePeriodicSync(context.applicationContext, settings)
        repository.setBackgroundSyncScheduled(settings.backgroundSyncEnabled)
    }

    fun saveSyncScheduleSettings(updated: ServerSettings) {
        coroutineScope.launch {
            repository.saveSyncScheduleSettings(
                backgroundSyncEnabled = updated.backgroundSyncEnabled,
                syncOnAppOpenEnabled = updated.syncOnAppOpenEnabled,
                chargingSyncIntervalMinutes = updated.chargingSyncIntervalMinutes,
                batterySyncIntervalMinutes = updated.batterySyncIntervalMinutes,
                syncHealthSnapshotsEnabled = updated.syncHealthSnapshotsEnabled,
                syncServerMealsEnabled = updated.syncServerMealsEnabled,
                updateNutritionWidgetEnabled = updated.updateNutritionWidgetEnabled
            )
            HealthSyncScheduler.schedulePeriodicSync(context.applicationContext, updated)
            repository.setBackgroundSyncScheduled(updated.backgroundSyncEnabled)
        }
    }

    fun runBackgroundSyncNow() {
        HealthSyncScheduler.enqueueManualWorkerSync(context.applicationContext)
        syncMessage = "Background sync requested"
    }

    fun permissionFor(metric: HealthMetric): String = manager.permissionByMetric.getValue(metric)

    fun editedSettings(): ServerSettings {
        return settings.copy(
            serverBaseUrl = serverUrlInput,
            deviceId = deviceIdInput,
            deviceName = deviceNameInput,
            apiToken = apiTokenInput.ifBlank { settings.apiToken },
            readToken = readTokenInput.ifBlank { settings.readToken },
            allowInsecureHttpForLan = allowHttpInput
        )
    }

    suspend fun persistEditedSettings(): ServerSettings {
        val edited = editedSettings()
        repository.saveConnectionSettings(
            serverBaseUrl = edited.serverBaseUrl,
            deviceId = edited.deviceId,
            deviceName = edited.deviceName,
            apiToken = edited.apiToken,
            readToken = edited.readToken,
            allowInsecureHttpForLan = edited.allowInsecureHttpForLan
        )
        apiTokenInput = ""
        readTokenInput = ""
        return edited
    }

    fun updatePermissionMissingCards() {
        HealthMetric.entries.forEach { metric ->
            if (!grantedPermissions.contains(permissionFor(metric))) {
                cards[metric] = permissionMissingCard(metric)
            }
        }
    }

    fun refreshAll() {
        coroutineScope.launch {
            isRefreshing = true
            topError = null
            try {
                availability = manager.getAvailability()
                backgroundReadAvailable = manager.isBackgroundReadAvailable()
                if (availability != HealthConnectAvailability.Available) {
                    grantedPermissions = emptySet()
                    backgroundReadGranted = false
                    nutritionWriteGranted = false
                    updatePermissionMissingCards()
                    return@launch
                }

                grantedPermissions = manager.getGrantedPermissions()
                backgroundReadGranted = manager.hasBackgroundReadPermission(grantedPermissions)
                nutritionWriteGranted = manager.hasNutritionWritePermission(grantedPermissions)
                HealthMetric.entries.forEach { metric ->
                    cards[metric] = if (grantedPermissions.contains(permissionFor(metric))) {
                        readMetricCard(metric, manager)
                    } else {
                        permissionMissingCard(metric)
                    }
                }
                lastRefreshTime = Instant.now()
            } catch (exception: Exception) {
                topError = exception.message ?: "Unable to refresh dashboard"
            } finally {
                isRefreshing = false
            }
        }
    }

    fun syncNow() {
        coroutineScope.launch {
            isSyncing = true
            syncMessage = ""
            try {
                val syncSettings = persistEditedSettings()
                val snapshot = snapshotBuilder.build(syncSettings, requireBackgroundPermission = false)
                val result = apiClient.sendSnapshot(syncSettings, snapshot)
                val now = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                repository.updateManualSyncStatus(result.message, now)
                syncMessage = result.message
            } catch (exception: Exception) {
                val now = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                val message = exception.message ?: "Sync failed"
                repository.updateManualSyncStatus(message, now)
                syncMessage = message
            } finally {
                isSyncing = false
            }
        }
    }

    fun testConnection() {
        coroutineScope.launch {
            syncMessage = ""
            try {
                val syncSettings = persistEditedSettings()
                val result = apiClient.testConnection(syncSettings)
                syncMessage = result.message
            } catch (exception: Exception) {
                syncMessage = exception.message ?: "Connection test failed"
            }
        }
    }

    fun loginForDashboardSession() {
        coroutineScope.launch {
            isDashboardLoginInProgress = true
            dashboardLoginMessage = ""
            try {
                val syncSettings = editedSettings()
                val result = apiClient.loginForReadSession(
                    settings = syncSettings,
                    username = dashboardUsernameInput,
                    password = dashboardPasswordInput
                )
                dashboardLoginMessage = result.message
                if (result.success) {
                    repository.saveConnectionSettings(
                        serverBaseUrl = syncSettings.serverBaseUrl,
                        deviceId = syncSettings.deviceId,
                        deviceName = syncSettings.deviceName,
                        apiToken = syncSettings.apiToken,
                        readToken = result.sessionCookie,
                        allowInsecureHttpForLan = syncSettings.allowInsecureHttpForLan
                    )
                    dashboardPasswordInput = ""
                    readTokenInput = ""
                }
            } catch (exception: Exception) {
                dashboardLoginMessage = exception.message ?: "Dashboard login failed"
            } finally {
                isDashboardLoginInProgress = false
            }
        }
    }

    fun submitMeal() {
        coroutineScope.launch {
            isSubmittingMeal = true
            mealMessage = ""
            try {
                val title = mealTitleInput.trim()
                val eatenAt = parseMealTime(mealEatenAtInput)
                    ?: throw IllegalArgumentException("Meal date/time must be a valid ISO value like ${defaultMealTime()}")
                val calories = parseNonNegativeMealNumber(mealCaloriesInput, "Calories")
                val protein = parseNonNegativeMealNumber(mealProteinInput, "Protein")
                val carbs = parseNonNegativeMealNumber(mealCarbsInput, "Carbs")
                val fat = parseNonNegativeMealNumber(mealFatInput, "Fat")
                require(title.isNotBlank()) { "Meal title is required" }

                val syncSettings = persistEditedSettings()
                val meal = MealLogRequest(
                    clientMealId = mealClientId,
                    deviceId = syncSettings.deviceId,
                    eatenAt = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(eatenAt),
                    mealType = mealType,
                    title = title,
                    caloriesKcal = calories,
                    proteinG = protein,
                    carbsG = carbs,
                    fatG = fat,
                    notes = mealNotesInput.trim().takeIf { it.isNotBlank() }
                )
                val healthConnectMessage = writeNutritionToHealthConnect(manager, availability, meal)
                val serverResult = apiClient.sendMeal(syncSettings, meal)
                mealMessage = "Server upload: ${serverResult.message}\nHealth Connect nutrition: $healthConnectMessage"
                nutritionWriteGranted = if (availability == HealthConnectAvailability.Available) {
                    manager.hasNutritionWritePermission()
                } else {
                    false
                }
                if (serverResult.success) {
                    recordNutritionWidgetMeal(
                        context = context.applicationContext,
                        source = "manual-meal",
                        identity = "manual-${meal.clientMealId}",
                        eatenAt = meal.eatenAt,
                        caloriesKcal = meal.caloriesKcal,
                        proteinG = meal.proteinG,
                        fiberG = null
                    )
                    lastSubmittedMeal = "${meal.mealType.label}: ${meal.title}"
                    mealClientId = generateClientMealId()
                    mealEatenAtInput = defaultMealTime()
                    mealTitleInput = ""
                    mealCaloriesInput = ""
                    mealProteinInput = ""
                    mealCarbsInput = ""
                    mealFatInput = ""
                    mealNotesInput = ""
                    mealListRevision += 1
                }
            } catch (exception: Exception) {
                mealMessage = exception.message ?: "Meal upload failed"
            } finally {
                isSubmittingMeal = false
            }
        }
    }

    fun syncServerMealsToHealthConnect() {
        coroutineScope.launch {
            isSyncingServerMeals = true
            serverMealSyncMessage = ""
            try {
                availability = manager.getAvailability()
                if (availability != HealthConnectAvailability.Available) {
                    serverMealSyncMessage = "Server meals sync:\nHealth Connect unavailable"
                    return@launch
                }

                nutritionWriteGranted = manager.hasNutritionWritePermission()
                if (!nutritionWriteGranted) {
                    serverMealSyncMessage = "Server meals sync:\nNutrition write permission is missing"
                    return@launch
                }

                val syncSettings = persistEditedSettings()
                val today = LocalDate.now()
                val fetchResult = apiClient.fetchCurrentMeals(syncSettings)
                if (!fetchResult.success) {
                    serverMealSyncMessage = "Server meals sync:\n${fetchResult.message}"
                    return@launch
                }

                val syncedIds = repository.getSyncedServerMealIds().toMutableSet()
                com.uriel.healthconnecttest.sync.MealNotifications.observe(context.applicationContext, syncSettings, fetchResult.meals)
                var written = 0
                var skippedAlreadySynced = 0
                var failed = fetchResult.failedParseCount
                var firstError = fetchResult.firstParseError

                fetchResult.meals.forEach { serverMeal ->
                    if (syncedIds.contains(serverMeal.syncId)) {
                        recordNutritionWidgetMeal(
                            context = context.applicationContext,
                            source = "server-already-synced",
                            identity = "server-${serverMeal.syncId}",
                            eatenAt = serverMeal.eatenAt,
                            caloriesKcal = serverMeal.caloriesKcal,
                            proteinG = serverMeal.proteinG,
                            fiberG = serverMeal.fiberG
                        )
                        skippedAlreadySynced += 1
                    } else {
                        val result = writeServerMealToHealthConnect(manager, serverMeal)
                        when {
                            result.success -> {
                                repository.markServerMealSynced(serverMeal.syncId)
                                syncedIds += serverMeal.syncId
                                recordNutritionWidgetMeal(
                                    context = context.applicationContext,
                                    source = "server-written",
                                    identity = "server-${serverMeal.syncId}",
                                    eatenAt = serverMeal.eatenAt,
                                    caloriesKcal = serverMeal.caloriesKcal,
                                    proteinG = serverMeal.proteinG,
                                    fiberG = serverMeal.fiberG
                                )
                                written += 1
                            }
                            result.alreadyExists -> {
                                repository.markServerMealSynced(serverMeal.syncId)
                                syncedIds += serverMeal.syncId
                                recordNutritionWidgetMeal(
                                    context = context.applicationContext,
                                    source = "server-insert-duplicate",
                                    identity = "server-${serverMeal.syncId}",
                                    eatenAt = serverMeal.eatenAt,
                                    caloriesKcal = serverMeal.caloriesKcal,
                                    proteinG = serverMeal.proteinG,
                                    fiberG = serverMeal.fiberG
                                )
                                skippedAlreadySynced += 1
                            }
                            else -> {
                                failed += 1
                                if (firstError.isBlank()) {
                                    firstError = result.message
                                }
                            }
                        }
                    }
                }

                serverMealSyncMessage = buildString {
                    appendLine("Server meals sync:")
                    appendLine("Fetched: ${fetchResult.meals.size + fetchResult.failedParseCount}")
                    appendLine("Written to Health Connect: $written")
                    appendLine("Skipped already synced: $skippedAlreadySynced")
                    appendLine("Failed: $failed")
                    if (firstError.isNotBlank()) {
                        append("First error: $firstError")
                    }
                }.trimEnd()
            } catch (exception: Exception) {
                Log.e(HEALTH_DASHBOARD_TAG, "Server meal sync failed", exception)
                serverMealSyncMessage =
                    "Server meals sync:\nFailed: ${exception::class.java.simpleName}: ${exception.message ?: "no message"}"
            } finally {
                isSyncingServerMeals = false
            }
        }
    }

    fun loadTodayMeals(showResultMessage: Boolean = false) {
        coroutineScope.launch {
            isLoadingServerMeals = true
            if (showResultMessage) mealListMessage = ""
            try {
                val syncSettings = persistEditedSettings()
                val today = LocalDate.now()
                val result = apiClient.fetchCurrentMeals(syncSettings)
                if (result.success) {
                    com.uriel.healthconnecttest.sync.MealNotifications.observe(context.applicationContext, syncSettings, result.meals)
                    serverMeals = result.meals.sortedByDescending { it.eatenAt }
                    if (result.failedParseCount > 0) {
                        mealListMessage = "Some meals could not be read. Totals have not been updated."
                        return@launch
                    }
                    NutritionWidgetStore.replaceTodayMeals(context.applicationContext, result.meals, result.nutritionDay)
                    nutritionProgress = NutritionWidgetStore.getTodayProgress(context.applicationContext)
                    nutritionLoaded = true
                    mealListMessage = ""
                    nutritionDateLabel = result.nutritionDay?.let { "${it.date} | ${it.timezone} | resets ${it.resetHour}:00" }.orEmpty()
                    NutritionWidgetsUpdater.updateAll(context.applicationContext)
                    if (showResultMessage) {
                        mealListMessage = if (result.failedParseCount == 0) {
                            "Meals refreshed"
                        } else {
                            "Loaded ${result.meals.size} meals; ${result.failedParseCount} could not be read"
                        }
                    }
                } else {
                    mealListMessage = result.message
                }
            } catch (exception: Exception) {
                mealListMessage = exception.message ?: "Unable to load meals"
            } finally {
                isLoadingServerMeals = false
            }
        }
    }

    fun deletePendingMeal() {
        val meal = pendingMealDelete ?: return
        coroutineScope.launch {
            deletingMealId = meal.serverId
            mealListMessage = ""
            try {
                val syncSettings = persistEditedSettings()
                val result = apiClient.deleteMeal(syncSettings, meal.serverId)
                if (result.success) {
                    repository.forgetServerMealSynced(meal.syncId)
                    val remainingMeals = serverMeals.filterNot { it.serverId == meal.serverId }
                    serverMeals = remainingMeals
                    NutritionWidgetStore.replaceTodayMeals(context.applicationContext, remainingMeals)
                    nutritionProgress = NutritionWidgetStore.getTodayProgress(context.applicationContext)
                    NutritionWidgetsUpdater.updateAll(context.applicationContext)
                    mealListMessage = "${meal.title} deleted"
                } else {
                    mealListMessage = result.message
                }
            } catch (exception: Exception) {
                mealListMessage = exception.message ?: "Unable to delete meal"
            } finally {
                pendingMealDelete = null
                deletingMealId = null
            }
        }
    }

    val metricPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { permissions ->
        grantedPermissions = permissions
        refreshAll()
    }

    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { permissions ->
        backgroundReadGranted = permissions.contains(manager.backgroundReadPermission)
        refreshAll()
    }

    val nutritionPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { permissions ->
        nutritionWriteGranted = permissions.contains(manager.nutritionWritePermission)
        refreshAll()
    }

    LaunchedEffect(Unit) {
        HealthMetric.entries.forEach { metric ->
            cards[metric] = MetricCardState(
                metric = metric,
                value = "Not refreshed",
                unit = "",
                status = MetricStatus.NoData
            )
        }
        refreshAll()
    }

    LaunchedEffect(selectedTab, settings.readToken, settings.serverBaseUrl, mealListRevision) {
        if ((selectedTab == AppTab.Food || selectedTab == AppTab.Today) &&
            settings.readToken.isNotBlank() &&
            settings.serverBaseUrl.isNotBlank()
        ) {
            loadTodayMeals()
        }
    }

    val missingPermissions = manager.allRequiredPermissions - grantedPermissions
    val canUseHealthConnect = availability == HealthConnectAvailability.Available

    Scaffold(
        modifier = modifier
            .fillMaxSize(),
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Health Companion", style = MaterialTheme.typography.titleMedium)
                    if (selectedTab == AppTab.Today || selectedTab == AppTab.Food) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            IconButton(onClick = { showDayInfo = true }, modifier = Modifier.size(42.dp)) { Icon(CompanionIcons.Calendar, "Nutrition day information", Modifier.size(20.dp)) }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppTab.entries.forEach { tab ->
                        val active = selectedTab == tab
                        val hasUpdate = tab == AppTab.Settings && updateState.release != null
                        val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        Surface(onClick = { selectedTab = tab }, shape = RoundedCornerShape(12.dp),
                            color = if (active) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                            modifier = Modifier.weight(1f).semantics { role = Role.Tab; selected = active; contentDescription = tab.label; if (hasUpdate) stateDescription = "App update available" }) {
                            Column(Modifier.heightIn(min = 56.dp).padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                BadgedBox(badge = { if (hasUpdate) Badge(containerColor = MaterialTheme.colorScheme.error) }) {
                                    Icon(when (tab) { AppTab.Today -> CompanionIcons.Home; AppTab.Food -> CompanionIcons.Food;
                                        AppTab.Trends -> CompanionIcons.Chart; AppTab.Health -> CompanionIcons.Heart; AppTab.Settings -> CompanionIcons.Settings },
                                        null, Modifier.size(22.dp), tint = color)
                                }
                                if (LocalDensity.current.fontScale < 1.5f) Text(tab.label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(remember(selectedTab) { androidx.compose.foundation.ScrollState(0) })
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (selectedTab == AppTab.Settings) 0.dp else 12.dp)
        ) {
            if (updateState.release != null && selectedTab != AppTab.Settings) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(CompanionIcons.Update, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 10.dp)) {
                            Text("App update available", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Text("Version ${updateState.release?.versionName} is ready to install.", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { openSettingsGroup = "updates"; selectedTab = AppTab.Settings }) { Text("View update", style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
            when (selectedTab) {
                AppTab.Today -> {
                    NutritionDashboard(if (nutritionLoaded) nutritionProgress else null, nutritionDateLabel, serverMeals,
                        isLoadingServerMeals, mealListMessage,
                        onRefresh = { loadTodayMeals(true) }, onViewMeals = { selectedTab = AppTab.Food },
                        onEditGoals = { openSettingsGroup = "goals"; selectedTab = AppTab.Settings })
                    TodayScreen(
                    availability = availability,
                    nutritionWriteGranted = nutritionWriteGranted,
                    lastSyncStatus = settings.lastSyncStatus,
                    lastSyncTime = settings.lastSyncTime,
                    lastManualSyncTime = settings.lastManualSyncTime,
                    lastSyncError = settings.lastSyncError,
                    syncMessage = syncMessage,
                    mealMessage = mealMessage,
                    serverMealSyncMessage = serverMealSyncMessage,
                    cards = cards,
                    isSyncing = isSyncing,
                    onTestConnection = { testConnection() },
                    onSyncNow = { syncNow() }
                )
                }
                AppTab.Food -> FoodScreen(
                    mealRevision = mealListRevision,
                    canUseHealthConnect = canUseHealthConnect,
                    nutritionWriteGranted = nutritionWriteGranted,
                    onRequestNutritionPermission = {
                        nutritionPermissionLauncher.launch(setOf(manager.nutritionWritePermission))
                    },
                    mealType = mealType,
                    onMealTypeChange = { mealType = it },
                    eatenAt = mealEatenAtInput,
                    onEatenAtChange = { mealEatenAtInput = it },
                    title = mealTitleInput,
                    onTitleChange = { mealTitleInput = it },
                    calories = mealCaloriesInput,
                    onCaloriesChange = { mealCaloriesInput = it },
                    protein = mealProteinInput,
                    onProteinChange = { mealProteinInput = it },
                    carbs = mealCarbsInput,
                    onCarbsChange = { mealCarbsInput = it },
                    fat = mealFatInput,
                    onFatChange = { mealFatInput = it },
                    notes = mealNotesInput,
                    onNotesChange = { mealNotesInput = it },
                    mealMessage = mealMessage,
                    lastSubmittedMeal = lastSubmittedMeal,
                    isSubmittingMeal = isSubmittingMeal,
                    onSubmitMeal = { submitMeal() },
                    serverMealSyncMessage = serverMealSyncMessage,
                    isSyncingServerMeals = isSyncingServerMeals,
                    onSyncServerMeals = { syncServerMealsToHealthConnect() },
                    serverMeals = serverMeals,
                    isLoadingServerMeals = isLoadingServerMeals,
                    deletingMealId = deletingMealId,
                    mealListMessage = mealListMessage,
                    onRefreshMeals = { loadTodayMeals(showResultMessage = true) },
                    onDeleteMeal = { pendingMealDelete = it }
                )
                AppTab.Health -> HealthScreen(
                    cards = cards,
                    canUseHealthConnect = canUseHealthConnect,
                    isRefreshing = isRefreshing,
                    onRefreshAll = { refreshAll() }
                )
                AppTab.Trends -> NutritionTrendsScreen(settings)
                AppTab.Settings -> {
                    Text("Settings", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(12.dp))
                    SettingsGroup("updates", "App updates", updateState.release?.let { "Version ${it.versionName} available" }
                        ?: "Installed version ${updates.versionName}", openSettingsGroup, { openSettingsGroup = it },
                        attention = updateState.release != null) { AppUpdateSettings(updates) }
                    SettingsScreen(
                    openGroup = openSettingsGroup,
                    onGroupChange = { openSettingsGroup = it },
                    settingsExtras = {
                        SettingsGroup("goals", "Daily nutrition goals", "Calories, protein, carbs, fat, fiber", openSettingsGroup, { openSettingsGroup = it }) {
                            NutritionGoalsSettings(settings, onSaved = {
                                coroutineScope.launch { nutritionProgress = NutritionWidgetStore.getTodayProgress(context.applicationContext) }
                                loadTodayMeals()
                            })
                        }
                        SettingsGroup("preferences", "App preferences", "Appearance, widgets and notifications", openSettingsGroup, { openSettingsGroup = it }) {
                            AppearanceSettings()
                            HorizontalDivider()
                            MealNotificationSettings(settings, repository)
                            HorizontalDivider()
                            WidgetGallery()
                        }
                        SettingsGroup("about", "About", "App version, privacy, acknowledgments", openSettingsGroup, { openSettingsGroup = it }) {
                            Text("Health Companion ${updates.versionName}", style = MaterialTheme.typography.titleMedium)
                            Text(if (updates.production) "Production release" else "Development release")
                            Text("Your meals and goals are stored on your dashboard server. Health readings are accessed only with the permissions you grant. Background sync and meal notifications can be configured in this app.", style = MaterialTheme.typography.bodyMedium)
                            Text("Built with Kotlin, Jetpack Compose, Android Health Connect, WorkManager and OkHttp.", style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    availability = availability,
                    grantedCount = grantedPermissions.intersect(manager.allRequiredPermissions).size,
                    totalCount = manager.allRequiredPermissions.size,
                    lastRefreshTime = lastRefreshTime,
                    backgroundReadAvailable = backgroundReadAvailable,
                    backgroundReadGranted = backgroundReadGranted,
                    lastSyncStatus = settings.lastSyncStatus,
                    lastSyncTime = settings.lastSyncTime,
                    lastManualSyncTime = settings.lastManualSyncTime,
                    lastBackgroundSyncTime = settings.lastBackgroundSyncTime,
                    lastSyncError = settings.lastSyncError,
                    backgroundSyncScheduled = settings.backgroundSyncScheduled,
                    error = topError,
                    canUseHealthConnect = canUseHealthConnect,
                    missingPermissions = missingPermissions,
                    nutritionWriteGranted = nutritionWriteGranted,
                    onRequestMissingPermissions = { metricPermissionLauncher.launch(missingPermissions) },
                    onRequestBackgroundRead = {
                        backgroundPermissionLauncher.launch(setOf(manager.backgroundReadPermission))
                    },
                    onRequestNutritionPermission = {
                        nutritionPermissionLauncher.launch(setOf(manager.nutritionWritePermission))
                    },
                    settings = settings,
                    onSyncScheduleChange = { saveSyncScheduleSettings(it) },
                    onRunBackgroundSyncNow = { runBackgroundSyncNow() },
                    serverUrl = serverUrlInput,
                    onServerUrlChange = { serverUrlInput = it },
                    deviceId = deviceIdInput,
                    onDeviceIdChange = { deviceIdInput = it },
                    deviceName = deviceNameInput,
                    onDeviceNameChange = { deviceNameInput = it },
                    apiToken = apiTokenInput,
                    onApiTokenChange = { apiTokenInput = it },
                    maskedStoredToken = repository.maskToken(settings.apiToken),
                    dashboardUsername = dashboardUsernameInput,
                    onDashboardUsernameChange = { dashboardUsernameInput = it },
                    dashboardPassword = dashboardPasswordInput,
                    onDashboardPasswordChange = { dashboardPasswordInput = it },
                    dashboardLoginMessage = dashboardLoginMessage,
                    isDashboardLoginInProgress = isDashboardLoginInProgress,
                    onDashboardLogin = { loginForDashboardSession() },
                    readToken = readTokenInput,
                    onReadTokenChange = { readTokenInput = it },
                    maskedStoredReadToken = repository.maskToken(settings.readToken),
                    allowHttp = allowHttpInput,
                    onAllowHttpChange = { allowHttpInput = it }
                )
                }
            }
        }
    }

    }
    if (showDayInfo) AlertDialog(onDismissRequest = { showDayInfo = false },
        icon = { Icon(CompanionIcons.Calendar, null) }, title = { Text("Your nutrition day") },
        text = { Text(nutritionDateLabel.ifBlank { "Today's meals follow your account's timezone and nutrition-day reset hour. Refresh nutrition after signing in to see the exact day boundary." }) },
        confirmButton = { TextButton(onClick = { showDayInfo = false }) { Text("Done") } })
    pendingMealDelete?.let { meal ->
        AlertDialog(
            onDismissRequest = { pendingMealDelete = null },
            title = { Text("Delete meal?") },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            text = { Text("Delete “${meal.title}”?\n\nThis will remove the meal from the server. It will not remove any previously written Health Connect record.") },
            confirmButton = {
                Button(onClick = { deletePendingMeal() }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingMealDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun TodayScreen(
    availability: HealthConnectAvailability,
    nutritionWriteGranted: Boolean,
    lastSyncStatus: String,
    lastSyncTime: String,
    lastManualSyncTime: String,
    lastSyncError: String,
    syncMessage: String,
    mealMessage: String,
    serverMealSyncMessage: String,
    cards: Map<HealthMetric, MetricCardState>,
    isSyncing: Boolean,
    onTestConnection: () -> Unit,
    onSyncNow: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ReferenceCard {
        Surface(onClick = { expanded = !expanded }, color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Synchronization details", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = if (expanded) "Hide sync details" else "Show sync details")
            }
        }
        if (expanded) {
            DailyStatusCard(availability, nutritionWriteGranted, lastSyncStatus, lastSyncTime, lastManualSyncTime, lastSyncError)
            TodayActionsCard(syncMessage, isSyncing, onTestConnection, onSyncNow)
            if (mealMessage.isNotBlank()) Text(mealMessage, style = MaterialTheme.typography.bodySmall)
            if (serverMealSyncMessage.isNotBlank()) Text(serverMealSyncMessage, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun FoodScreen(
    mealRevision: Int,
    canUseHealthConnect: Boolean,
    nutritionWriteGranted: Boolean,
    onRequestNutritionPermission: () -> Unit,
    mealType: MealType,
    onMealTypeChange: (MealType) -> Unit,
    eatenAt: String,
    onEatenAtChange: (String) -> Unit,
    title: String,
    onTitleChange: (String) -> Unit,
    calories: String,
    onCaloriesChange: (String) -> Unit,
    protein: String,
    onProteinChange: (String) -> Unit,
    carbs: String,
    onCarbsChange: (String) -> Unit,
    fat: String,
    onFatChange: (String) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
    mealMessage: String,
    lastSubmittedMeal: String,
    isSubmittingMeal: Boolean,
    onSubmitMeal: () -> Unit,
    serverMealSyncMessage: String,
    isSyncingServerMeals: Boolean,
    onSyncServerMeals: () -> Unit,
    serverMeals: List<ServerMeal>,
    isLoadingServerMeals: Boolean,
    deletingMealId: Long?,
    mealListMessage: String,
    onRefreshMeals: () -> Unit,
    onDeleteMeal: (ServerMeal) -> Unit
) {
    var showMealForm by rememberSaveable { mutableStateOf(false) }
    var openedRevision by rememberSaveable { mutableIntStateOf(mealRevision) }
    LaunchedEffect(mealRevision) { if (showMealForm && openedRevision != mealRevision) showMealForm = false }
    TodayMealsCard(serverMeals.sortedBy { runCatching { OffsetDateTime.parse(it.eatenAt).toInstant() }.getOrDefault(Instant.EPOCH) }, isLoadingServerMeals, deletingMealId, mealListMessage, serverMealSyncMessage,
        isSyncingServerMeals, onRefreshMeals, onSyncServerMeals, onDeleteMeal)
    FilledTonalButton(onClick = { openedRevision = mealRevision; showMealForm = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
        Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Register meal manually")
    }
    ReferenceCard {
        Surface(onClick = onSyncServerMeals, color = MaterialTheme.colorScheme.surface, enabled = !isSyncingServerMeals) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(CompanionIcons.Cloud, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(if (isSyncingServerMeals) "Synchronizing meals…" else "Synchronize meals to Health Connect", style = MaterialTheme.typography.titleSmall)
                    Text("Write this nutrition day's server meals to Health Connect", style = MaterialTheme.typography.bodySmall)
                }
                Icon(CompanionIcons.Chevron, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        if (serverMealSyncMessage.isNotBlank()) Text(serverMealSyncMessage, style = MaterialTheme.typography.bodySmall)
        if (!nutritionWriteGranted) TextButton(onClick = onRequestNutritionPermission, enabled = canUseHealthConnect) { Text("Grant nutrition write permission") }
    }
    if (showMealForm) androidx.compose.ui.window.Dialog(onDismissRequest = { if (!isSubmittingMeal) showMealForm = false },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        ConfigureDialogSystemBars()
        Scaffold(modifier = Modifier.fillMaxSize(), topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showMealForm = false }, enabled = !isSubmittingMeal) { Icon(CompanionIcons.Back, "Back to meals") }
                Text("Register meal", style = MaterialTheme.typography.titleMedium)
            }
        }, bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { showMealForm = false }, enabled = !isSubmittingMeal) { Text("Cancel") }
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = onSubmitMeal, enabled = !isSubmittingMeal, shape = RoundedCornerShape(8.dp)) { Text(if (isSubmittingMeal) "Registering…" else "Register meal") }
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FoodDiaryCard(mealType, onMealTypeChange, eatenAt, onEatenAtChange, title, onTitleChange, calories, onCaloriesChange,
                    protein, onProteinChange, carbs, onCarbsChange, fat, onFatChange, notes, onNotesChange, mealMessage, lastSubmittedMeal, isSubmittingMeal, onSubmitMeal)
            }
        }
    }
}

@Composable
private fun HealthScreen(
    cards: Map<HealthMetric, MetricCardState>,
    canUseHealthConnect: Boolean,
    isRefreshing: Boolean,
    onRefreshAll: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Health", style = MaterialTheme.typography.headlineSmall)
            Text("Today (from Health Connect)", style = MaterialTheme.typography.bodyMedium)
        }
        IconButton(onClick = onRefreshAll, enabled = canUseHealthConnect && !isRefreshing) { Icon(Icons.Default.Refresh, "Refresh all health readings") }
    }
    if (isRefreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
    val ordered = listOf(HealthMetric.Steps, HealthMetric.ActiveCalories, HealthMetric.Distance, HealthMetric.LatestHeartRate,
        HealthMetric.TotalCalories, HealthMetric.RestingHeartRate, HealthMetric.Sleep, HealthMetric.Exercise, HealthMetric.Weight)
    ordered.forEach { MetricCard(cards[it] ?: permissionMissingCard(it)) }
}

@Composable
private fun SettingsScreen(
    openGroup: String,
    onGroupChange: (String) -> Unit,
    settingsExtras: @Composable () -> Unit,
    availability: HealthConnectAvailability,
    grantedCount: Int,
    totalCount: Int,
    lastRefreshTime: Instant?,
    backgroundReadAvailable: Boolean,
    backgroundReadGranted: Boolean,
    lastSyncStatus: String,
    lastSyncTime: String,
    lastManualSyncTime: String,
    lastBackgroundSyncTime: String,
    lastSyncError: String,
    backgroundSyncScheduled: Boolean,
    error: String?,
    canUseHealthConnect: Boolean,
    missingPermissions: Set<String>,
    nutritionWriteGranted: Boolean,
    onRequestMissingPermissions: () -> Unit,
    onRequestBackgroundRead: () -> Unit,
    onRequestNutritionPermission: () -> Unit,
    settings: ServerSettings,
    onSyncScheduleChange: (ServerSettings) -> Unit,
    onRunBackgroundSyncNow: () -> Unit,
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    deviceId: String,
    onDeviceIdChange: (String) -> Unit,
    deviceName: String,
    onDeviceNameChange: (String) -> Unit,
    apiToken: String,
    onApiTokenChange: (String) -> Unit,
    maskedStoredToken: String,
    dashboardUsername: String,
    onDashboardUsernameChange: (String) -> Unit,
    dashboardPassword: String,
    onDashboardPasswordChange: (String) -> Unit,
    dashboardLoginMessage: String,
    isDashboardLoginInProgress: Boolean,
    onDashboardLogin: () -> Unit,
    readToken: String,
    onReadTokenChange: (String) -> Unit,
    maskedStoredReadToken: String,
    allowHttp: Boolean,
    onAllowHttpChange: (Boolean) -> Unit
) {
    SettingsGroup("account", "Account & server", if (settings.readToken.isNotBlank()) "Connected" else "Sign in to your dashboard",
        openGroup, onGroupChange) {
    SyncSettingsCard(
        serverUrl = serverUrl,
        onServerUrlChange = onServerUrlChange,
        deviceId = deviceId,
        onDeviceIdChange = onDeviceIdChange,
        deviceName = deviceName,
        onDeviceNameChange = onDeviceNameChange,
        apiToken = apiToken,
        onApiTokenChange = onApiTokenChange,
        maskedStoredToken = maskedStoredToken,
        dashboardUsername = dashboardUsername,
        onDashboardUsernameChange = onDashboardUsernameChange,
        dashboardPassword = dashboardPassword,
        onDashboardPasswordChange = onDashboardPasswordChange,
        dashboardLoginMessage = dashboardLoginMessage,
        isDashboardLoginInProgress = isDashboardLoginInProgress,
        onDashboardLogin = onDashboardLogin,
        readToken = readToken,
        onReadTokenChange = onReadTokenChange,
        maskedStoredReadToken = maskedStoredReadToken,
        allowHttp = allowHttp,
        onAllowHttpChange = onAllowHttpChange
    )
    }
    SettingsGroup("permissions", "Health Connect", "Permissions and data access", openGroup, onGroupChange) {
    PermissionControlsCard(
        canUseHealthConnect = canUseHealthConnect,
        missingPermissions = missingPermissions,
        backgroundReadAvailable = backgroundReadAvailable,
        backgroundReadGranted = backgroundReadGranted,
        nutritionWriteGranted = nutritionWriteGranted,
        onRequestMissingPermissions = onRequestMissingPermissions,
        onRequestBackgroundRead = onRequestBackgroundRead,
        onRequestNutritionPermission = onRequestNutritionPermission
    )

    SyncScheduleCard(
        settings = settings,
        onSettingsChange = onSyncScheduleChange,
        onRunBackgroundSyncNow = onRunBackgroundSyncNow
    )

    DashboardStatus(
        availability = availability,
        grantedCount = grantedCount,
        totalCount = totalCount,
        lastRefreshTime = lastRefreshTime,
        backgroundReadAvailable = backgroundReadAvailable,
        backgroundReadGranted = backgroundReadGranted,
        lastSyncStatus = lastSyncStatus,
        lastSyncTime = lastSyncTime,
        lastManualSyncTime = lastManualSyncTime,
        lastBackgroundSyncTime = lastBackgroundSyncTime,
        lastSyncError = lastSyncError,
        backgroundSyncScheduled = backgroundSyncScheduled,
        error = error
    )

    }
    settingsExtras()
}

@Composable
private fun DailyStatusCard(
    availability: HealthConnectAvailability,
    nutritionWriteGranted: Boolean,
    lastSyncStatus: String,
    lastSyncTime: String,
    lastManualSyncTime: String,
    lastSyncError: String
) {
    val available = availability == HealthConnectAvailability.Available
    SyncDetailRow("Health Connect", if (available) "Available" else "Unavailable", available)
    SyncDetailRow("Nutrition write permission", if (nutritionWriteGranted) "Granted" else "Missing", nutritionWriteGranted)
    SyncDetailRow("Latest synchronization", lastSyncStatus, !lastSyncStatus.contains("failed", true))
    SyncDetailRow("Last synchronization time", formatStoredTime(lastSyncTime))
    SyncDetailRow("Last manual synchronization", formatStoredTime(lastManualSyncTime))
    SyncDetailRow("Latest error", if (lastSyncError.isBlank() || lastSyncError.contains("failed=0")) "None" else lastSyncError)
}

@Composable
private fun TodayActionsCard(
    syncMessage: String,
    isSyncing: Boolean,
    onTestConnection: () -> Unit,
    onSyncNow: () -> Unit
) {
    OutlinedButton(onClick = onTestConnection, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Text("Test server connection")
    }
    Button(onClick = onSyncNow, enabled = !isSyncing, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
        Text(if (isSyncing) "Synchronizing…" else "Synchronize now")
    }
    if (syncMessage.isNotBlank()) Text(syncMessage, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun TodaySummaryCard(cards: Map<HealthMetric, MetricCardState>) {
    val summaryMetrics = listOf(
        HealthMetric.Steps,
        HealthMetric.ActiveCalories,
        HealthMetric.Distance,
        HealthMetric.LatestHeartRate
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        summaryMetrics.chunked(if (LocalDensity.current.fontScale >= 1.5f) 1 else 2).forEach { metrics ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            metrics.forEach { metric ->
                val card = cards[metric] ?: permissionMissingCard(metric)
                val value = when (card.status) {
                    MetricStatus.Ready -> listOf(card.value, card.unit)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                    MetricStatus.NoData -> "No data"
                    MetricStatus.PermissionMissing -> "Permission missing"
                    MetricStatus.Error -> "Error"
                }
                Surface(modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(when (metric) {
                            HealthMetric.LatestHeartRate -> Icons.Default.Favorite
                            HealthMetric.Distance -> Icons.Default.Place
                            HealthMetric.Steps -> Icons.Default.Star
                            else -> Icons.Default.Info
                        },
                            contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp))
                        Text(value, style = MaterialTheme.typography.titleLarge)
                        Text(metric.title, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun NutritionPermissionCard(
    canUseHealthConnect: Boolean,
    nutritionWriteGranted: Boolean,
    onRequestNutritionPermission: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "Health Connect nutrition", style = MaterialTheme.typography.titleMedium)
            Text(text = "Nutrition write: ${if (nutritionWriteGranted) "Granted" else "Missing"}")
            if (!nutritionWriteGranted) {
                OutlinedButton(
                    onClick = onRequestNutritionPermission,
                    enabled = canUseHealthConnect
                ) {
                    Text("Request nutrition write")
                }
            }
        }
    }
}

@Composable
private fun PermissionControlsCard(
    canUseHealthConnect: Boolean,
    missingPermissions: Set<String>,
    backgroundReadAvailable: Boolean,
    backgroundReadGranted: Boolean,
    nutritionWriteGranted: Boolean,
    onRequestMissingPermissions: () -> Unit,
    onRequestBackgroundRead: () -> Unit,
    onRequestNutritionPermission: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = "Health Connect permissions", style = MaterialTheme.typography.titleMedium)
            Button(
                onClick = onRequestMissingPermissions,
                enabled = canUseHealthConnect && missingPermissions.isNotEmpty()
            ) {
                Text("Request missing permissions")
            }
            if (backgroundReadAvailable && !backgroundReadGranted) {
                OutlinedButton(
                    onClick = onRequestBackgroundRead,
                    enabled = canUseHealthConnect
                ) {
                    Text("Request background read")
                }
            }
            Text(text = "Nutrition write: ${if (nutritionWriteGranted) "Granted" else "Missing"}")
            if (!nutritionWriteGranted) {
                OutlinedButton(
                    onClick = onRequestNutritionPermission,
                    enabled = canUseHealthConnect
                ) {
                    Text("Request nutrition write")
                }
            }
        }
    }
}

@Composable
private fun SyncScheduleCard(
    settings: ServerSettings,
    onSettingsChange: (ServerSettings) -> Unit,
    onRunBackgroundSyncNow: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = "Sync schedule", style = MaterialTheme.typography.titleMedium)
            SettingSwitchRow(
                label = "Background sync",
                checked = settings.backgroundSyncEnabled,
                onCheckedChange = { onSettingsChange(settings.copy(backgroundSyncEnabled = it)) }
            )
            SettingSwitchRow(
                label = "Sync when app opens",
                checked = settings.syncOnAppOpenEnabled,
                onCheckedChange = { onSettingsChange(settings.copy(syncOnAppOpenEnabled = it)) }
            )
            IntervalDropdown(
                label = "Charging interval",
                value = settings.chargingSyncIntervalMinutes,
                options = listOf(15, 30, 60, 120),
                disabledLabel = null,
                onValueChange = { onSettingsChange(settings.copy(chargingSyncIntervalMinutes = it)) }
            )
            IntervalDropdown(
                label = "Battery interval",
                value = settings.batterySyncIntervalMinutes,
                options = listOf(0, 60, 180, 360),
                disabledLabel = "Disabled",
                onValueChange = { onSettingsChange(settings.copy(batterySyncIntervalMinutes = it)) }
            )
            SettingSwitchRow(
                label = "Health snapshot upload",
                checked = settings.syncHealthSnapshotsEnabled,
                onCheckedChange = { onSettingsChange(settings.copy(syncHealthSnapshotsEnabled = it)) }
            )
            SettingSwitchRow(
                label = "Server meals pull",
                checked = settings.syncServerMealsEnabled,
                onCheckedChange = { onSettingsChange(settings.copy(syncServerMealsEnabled = it)) }
            )
            SettingSwitchRow(
                label = "Nutrition widget update",
                checked = settings.updateNutritionWidgetEnabled,
                onCheckedChange = { onSettingsChange(settings.copy(updateNutritionWidgetEnabled = it)) }
            )
            OutlinedButton(onClick = onRunBackgroundSyncNow) {
                Text("Run sync now")
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
        Text(text = label)
    }
}

@Composable
private fun IntervalDropdown(
    label: String,
    value: Int,
    options: List<Int>,
    disabledLabel: String?,
    onValueChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val valueLabel = intervalLabel(value, disabledLabel)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { expanded = true }) {
            Text(valueLabel)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(intervalLabel(option, disabledLabel)) },
                    onClick = {
                        expanded = false
                        onValueChange(option)
                    }
                )
            }
        }
    }
}

private fun intervalLabel(minutes: Int, disabledLabel: String?): String {
    if (minutes <= 0 && disabledLabel != null) return disabledLabel
    return when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 -> "${minutes / 60} h"
        else -> "$minutes min"
    }
}

@Composable
private fun DashboardStatus(
    availability: HealthConnectAvailability,
    grantedCount: Int,
    totalCount: Int,
    lastRefreshTime: Instant?,
    backgroundReadAvailable: Boolean,
    backgroundReadGranted: Boolean,
    lastSyncStatus: String,
    lastSyncTime: String,
    lastManualSyncTime: String,
    lastBackgroundSyncTime: String,
    lastSyncError: String,
    backgroundSyncScheduled: Boolean,
    error: String?
) {
    val availabilityText = when (availability) {
        HealthConnectAvailability.Available -> "Health Connect available"
        HealthConnectAvailability.NotInstalledOrUpdateRequired ->
            "Health Connect not installed or needs update"
        HealthConnectAvailability.Unsupported -> "Health Connect unsupported"
    }
    Text(text = availabilityText)
    Text(text = "$grantedCount of $totalCount metric permissions granted")
    Text(
        text = "Background read: " + when {
            !backgroundReadAvailable -> "Unavailable"
            backgroundReadGranted -> "Granted"
            else -> "Missing"
        }
    )
    Text(text = "Last refresh: ${lastRefreshTime?.let { formatTime(it) } ?: "Not refreshed yet"}")
    Text(text = "Last sync: ${formatStoredTime(lastSyncTime)}")
    Text(text = "Last manual sync: ${formatStoredTime(lastManualSyncTime)}")
    Text(text = "Last background sync: ${formatStoredTime(lastBackgroundSyncTime)}")
    Text(text = "Background sync scheduled: ${if (backgroundSyncScheduled) "Yes" else "Not yet"}")
    Text(text = "Sync status: $lastSyncStatus")
    if (lastSyncError.isNotBlank() && !lastSyncError.contains("failed=0")) {
        Text(text = "Last sync error: $lastSyncError")
    }
    if (error != null) {
        Text(text = "Error: $error")
    }
}

@Composable
private fun MetricCard(card: MetricCardState) {
    var expanded by rememberSaveable(card.metric) { mutableStateOf(false) }
    val icon = when (card.metric) {
        HealthMetric.Steps -> CompanionIcons.Steps
        HealthMetric.Distance -> CompanionIcons.Pin
        HealthMetric.ActiveCalories, HealthMetric.TotalCalories -> CompanionIcons.Flame
        else -> CompanionIcons.Heart
    }
    val accent = when (card.metric) {
        HealthMetric.Steps -> MaterialTheme.colorScheme.secondary
        HealthMetric.ActiveCalories, HealthMetric.TotalCalories -> MaterialTheme.colorScheme.tertiary
        HealthMetric.LatestHeartRate, HealthMetric.RestingHeartRate -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    ReferenceCard {
        Surface(onClick = { expanded = !expanded }, color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = accent.copy(alpha = .08f), shape = RoundedCornerShape(24.dp)) { Icon(icon, null, Modifier.padding(10.dp).size(24.dp), tint = accent) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(card.metric.title.removePrefix("Today's ").replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium)
                    Text(when (card.status) { MetricStatus.Ready -> "${card.value} ${card.unit}"; MetricStatus.NoData -> "No data"; MetricStatus.PermissionMissing -> "Permission required"; MetricStatus.Error -> "Unavailable" }, style = MaterialTheme.typography.titleLarge)
                }
                Icon(if (expanded) Icons.Default.KeyboardArrowUp else CompanionIcons.Chevron, null, Modifier.size(18.dp))
            }
        }
        if (expanded) {
            Text(card.timeRangeLabel, style = MaterialTheme.typography.bodySmall)
            card.details.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun SyncSettingsCard(
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    deviceId: String,
    onDeviceIdChange: (String) -> Unit,
    deviceName: String,
    onDeviceNameChange: (String) -> Unit,
    apiToken: String,
    onApiTokenChange: (String) -> Unit,
    maskedStoredToken: String,
    dashboardUsername: String,
    onDashboardUsernameChange: (String) -> Unit,
    dashboardPassword: String,
    onDashboardPasswordChange: (String) -> Unit,
    dashboardLoginMessage: String,
    isDashboardLoginInProgress: Boolean,
    onDashboardLogin: () -> Unit,
    readToken: String,
    onReadTokenChange: (String) -> Unit,
    maskedStoredReadToken: String,
    allowHttp: Boolean,
    onAllowHttpChange: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = "Server sync", style = MaterialTheme.typography.titleMedium)
            TextField(
                value = serverUrl,
                onValueChange = onServerUrlChange,
                label = { Text("Server URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            TextField(
                value = deviceId,
                onValueChange = onDeviceIdChange,
                label = { Text("Device ID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            TextField(
                value = deviceName,
                onValueChange = onDeviceNameChange,
                label = { Text("Device name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            if (maskedStoredToken.isNotBlank()) {
                Text(text = "Stored ingest token: $maskedStoredToken")
            }
            TextField(
                value = apiToken,
                onValueChange = onApiTokenChange,
                label = { Text("New user ingest token") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            if (maskedStoredReadToken.isNotBlank()) {
                Text(text = "Stored dashboard session: $maskedStoredReadToken")
            }
            TextField(
                value = dashboardUsername,
                onValueChange = onDashboardUsernameChange,
                label = { Text("Dashboard username") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            TextField(
                value = dashboardPassword,
                onValueChange = onDashboardPasswordChange,
                label = { Text("Dashboard password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            Button(onClick = onDashboardLogin, enabled = !isDashboardLoginInProgress) {
                Text(if (isDashboardLoginInProgress) "Signing in" else "Sign in for reads")
            }
            if (dashboardLoginMessage.isNotBlank()) {
                Text(text = dashboardLoginMessage)
            }
            TextField(
                value = readToken,
                onValueChange = onReadTokenChange,
                label = { Text("Manual dashboard session cookie") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(checked = allowHttp, onCheckedChange = onAllowHttpChange)
                Text(text = "Allow LAN HTTP")
            }
        }
    }
}

@Composable
private fun TodayMealsCard(
    meals: List<ServerMeal>,
    isLoading: Boolean,
    deletingMealId: Long?,
    message: String,
    syncMessage: String,
    isSyncing: Boolean,
    onRefresh: () -> Unit,
    onSync: () -> Unit,
    onDelete: (ServerMeal) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    ReferenceCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Meals (${meals.size})", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onRefresh, enabled = !isLoading) { Icon(Icons.Default.Refresh, "Refresh meals", Modifier.size(20.dp)) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Meal list options", Modifier.size(20.dp)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Refresh meals") }, onClick = { menu = false; onRefresh() })
                    DropdownMenuItem(text = { Text("Sync to Health Connect") }, onClick = { menu = false; onSync() })
                }
            }
        }
        if (isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!isLoading && meals.isEmpty()) Text("No meals registered for this nutrition day.", style = MaterialTheme.typography.bodyMedium)
        meals.forEachIndexed { index, meal ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            var mealMenu by remember(meal.serverId) { mutableStateOf(false) }
            ReferenceMealRow(meal) {
                Box {
                    IconButton(onClick = { mealMenu = true }, enabled = deletingMealId == null) {
                        if (deletingMealId == meal.serverId) CircularProgressIndicator(Modifier.size(18.dp))
                        else Icon(Icons.Default.MoreVert, "Options for ${meal.title}", Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = mealMenu, onDismissRequest = { mealMenu = false }) {
                        DropdownMenuItem(text = { Text("Delete meal", color = MaterialTheme.colorScheme.error) }, onClick = { mealMenu = false; onDelete(meal) }, leadingIcon = { Icon(Icons.Default.Delete, null) })
                    }
                }
            }
        }
        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FoodDiaryCard(
    mealType: MealType,
    onMealTypeChange: (MealType) -> Unit,
    eatenAt: String,
    onEatenAtChange: (String) -> Unit,
    title: String,
    onTitleChange: (String) -> Unit,
    calories: String,
    onCaloriesChange: (String) -> Unit,
    protein: String,
    onProteinChange: (String) -> Unit,
    carbs: String,
    onCarbsChange: (String) -> Unit,
    fat: String,
    onFatChange: (String) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
    message: String,
    lastSubmittedMeal: String,
    isSubmitting: Boolean,
    onSubmit: () -> Unit
) {
    var typeMenu by remember { mutableStateOf(false) }
    Text("Meal type", style = MaterialTheme.typography.bodySmall)
    Box {
        OutlinedButton(onClick = { typeMenu = true }, enabled = !isSubmitting, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
            Icon(CompanionIcons.Food, null, Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)); Text(mealType.label, Modifier.weight(1f))
            Icon(Icons.Default.KeyboardArrowDown, null)
        }
        DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
            MealType.entries.forEach { item -> DropdownMenuItem(text = { Text(item.label) }, onClick = { typeMenu = false; onMealTypeChange(item) }) }
        }
    }
    MealDateTimeField(eatenAt, onEatenAtChange, !isSubmitting)
    OutlinedTextField(value = title, onValueChange = onTitleChange, label = { Text("Meal name *") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !isSubmitting)
    val fields = listOf(Triple("Calories", calories, onCaloriesChange), Triple("Protein", protein, onProteinChange),
        Triple("Carbohydrates", carbs, onCarbsChange), Triple("Fat", fat, onFatChange))
    fields.chunked(if (LocalDensity.current.fontScale >= 1.5f) 1 else 2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            pair.forEach { (label, value, change) -> OutlinedTextField(value = value, onValueChange = change,
                label = { Text(label) }, suffix = { Text(if (label == "Calories") "kcal" else "g") },
                modifier = Modifier.weight(1f), singleLine = true, enabled = !isSubmitting,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)) }
        }
    }
    OutlinedTextField(value = notes, onValueChange = onNotesChange, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2, enabled = !isSubmitting)
    if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun MealTypeSelector(
    selectedMealType: MealType,
    onMealTypeChange: (MealType) -> Unit
) {
    if (LocalDensity.current.fontScale >= 1.5f) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MealType.entries.forEach { MealTypeButton(it, selectedMealType, onMealTypeChange) }
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MealTypeButton(MealType.Breakfast, selectedMealType, onMealTypeChange)
            MealTypeButton(MealType.Lunch, selectedMealType, onMealTypeChange)
            MealTypeButton(MealType.Dinner, selectedMealType, onMealTypeChange)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MealTypeButton(MealType.Snack, selectedMealType, onMealTypeChange)
            MealTypeButton(MealType.Other, selectedMealType, onMealTypeChange)
        }
    }
}

@Composable
private fun MealTypeButton(
    mealType: MealType,
    selectedMealType: MealType,
    onMealTypeChange: (MealType) -> Unit
) {
    if (mealType == selectedMealType) {
        FilterChip(selected = true, onClick = { onMealTypeChange(mealType) }, label = { Text(mealType.label) })
    } else {
        FilterChip(selected = false, onClick = { onMealTypeChange(mealType) }, label = { Text(mealType.label) })
    }
}

private suspend fun writeNutritionToHealthConnect(
    manager: HealthConnectManager,
    availability: HealthConnectAvailability,
    meal: MealLogRequest
): String {
    if (availability != HealthConnectAvailability.Available) {
        return "Skipped: Health Connect is unavailable"
    }

    if (!manager.hasNutritionWritePermission()) {
        return "Skipped: Nutrition write permission is missing"
    }

    return runCatching {
        manager.writeNutritionRecord(meal.toNutritionInput())
        Log.i(
            HEALTH_DASHBOARD_TAG,
            "Health Connect NutritionRecord inserted for clientMealId=${meal.clientMealId}"
        )
        "Write succeeded"
    }.getOrElse { exception ->
        Log.e(
            HEALTH_DASHBOARD_TAG,
            "Health Connect NutritionRecord insert failed for clientMealId=${meal.clientMealId}",
            exception
        )
        "Write failed: ${exception::class.java.simpleName}: ${exception.message ?: "no message"}"
    }
}

private suspend fun writeServerMealToHealthConnect(
    manager: HealthConnectManager,
    serverMeal: ServerMeal
): HealthConnectWriteResult {
    return runCatching {
        manager.writeNutritionRecord(serverMeal.toNutritionInput())
        Log.i(
            HEALTH_DASHBOARD_TAG,
            "Server meal NutritionRecord inserted for syncId=${serverMeal.syncId}"
        )
        HealthConnectWriteResult(success = true, alreadyExists = false, message = "Write succeeded")
    }.getOrElse { exception ->
        Log.e(
            HEALTH_DASHBOARD_TAG,
            "Server meal NutritionRecord insert failed for syncId=${serverMeal.syncId}",
            exception
        )
        val message = "${exception::class.java.simpleName}: ${exception.message ?: "no message"}"
        HealthConnectWriteResult(
            success = false,
            alreadyExists = isDuplicateHealthConnectFailure(exception),
            message = message
        )
    }
}

private fun isDuplicateHealthConnectFailure(exception: Throwable): Boolean {
    val text = "${exception::class.java.simpleName} ${exception.message.orEmpty()}"
    return text.contains("duplicate", ignoreCase = true) ||
        text.contains("conflict", ignoreCase = true) ||
        text.contains("already", ignoreCase = true)
}

private suspend fun recordNutritionWidgetMeal(
    context: android.content.Context,
    source: String,
    identity: String,
    eatenAt: String,
    caloriesKcal: Double,
    proteinG: Double,
    fiberG: Double?
) {
    val recorded = NutritionWidgetStore.recordMeal(
        context = context,
        source = source,
        identity = identity,
        eatenAt = eatenAt,
        caloriesKcal = caloriesKcal,
        proteinG = proteinG,
        fiberG = fiberG
    )
    if (recorded) {
        NutritionWidgetsUpdater.updateAll(context)
    }
}

private suspend fun readMetricCard(
    metric: HealthMetric,
    manager: HealthConnectManager
): MetricCardState {
    return try {
        when (metric) {
            HealthMetric.Steps -> {
                val steps = manager.readTodaySteps()
                if (steps == 0L) noDataCard(metric) else readyCard(metric, steps.toString(), "steps")
            }
            HealthMetric.Distance -> {
                val kilometers = manager.readTodayDistanceKilometers()
                if (kilometers == null || kilometers == 0.0) {
                    noDataCard(metric)
                } else {
                    readyCard(metric, "%.2f".format(kilometers), "km")
                }
            }
            HealthMetric.ActiveCalories -> {
                val calories = manager.readTodayActiveCaloriesKilocalories()
                if (calories == null || calories == 0.0) {
                    noDataCard(metric)
                } else {
                    readyCard(metric, calories.roundToLong().toString(), "kcal")
                }
            }
            HealthMetric.TotalCalories -> {
                val calories = manager.readTodayTotalCaloriesKilocalories()
                if (calories == null || calories == 0.0) {
                    noDataCard(metric)
                } else {
                    readyCard(metric, calories.roundToLong().toString(), "kcal")
                }
            }
            HealthMetric.LatestHeartRate -> {
                val sample = manager.readLatestHeartRateSample()
                if (sample == null) {
                    noDataCard(metric)
                } else {
                    readyCard(
                        metric = metric,
                        value = sample.beatsPerMinute.toString(),
                        unit = "bpm",
                        details = listOf(formatTime(sample.time))
                    )
                }
            }
            HealthMetric.RestingHeartRate -> {
                val sample = manager.readLatestRestingHeartRate()
                if (sample == null) {
                    noDataCard(metric)
                } else {
                    readyCard(
                        metric = metric,
                        value = sample.beatsPerMinute.toString(),
                        unit = "bpm",
                        details = listOf(formatTime(sample.time))
                    )
                }
            }
            HealthMetric.Sleep -> {
                val sleep = manager.readLastNightSleep()
                if (sleep == null) {
                    noDataCard(metric)
                } else {
                    readyCard(
                        metric = metric,
                        value = formatDuration(sleep.duration),
                        unit = "",
                        details = listOf("${sleep.sessionCount} session(s)")
                    )
                }
            }
            HealthMetric.Exercise -> {
                val exercises = manager.readRecentExercises()
                if (exercises.isEmpty()) {
                    noDataCard(metric)
                } else {
                    readyCard(
                        metric = metric,
                        value = exercises.size.toString(),
                        unit = "recent",
                        details = exercises.map {
                            "${it.title} - ${formatDuration(it.duration)} - ${formatTime(it.startTime)}"
                        }
                    )
                }
            }
            HealthMetric.Weight -> {
                val kilograms = manager.readLatestWeightKilograms()
                if (kilograms == null || kilograms == 0.0) {
                    noDataCard(metric)
                } else {
                    readyCard(metric, "%.1f".format(kilograms), "kg")
                }
            }
        }
    } catch (exception: Exception) {
        MetricCardState(
            metric = metric,
            value = "",
            unit = "",
            status = MetricStatus.Error,
            details = listOf(exception.message ?: "Unable to read this metric")
        )
    }
}

private fun readyCard(
    metric: HealthMetric,
    value: String,
    unit: String,
    details: List<String> = emptyList()
): MetricCardState {
    return MetricCardState(
        metric = metric,
        value = value,
        unit = unit,
        status = MetricStatus.Ready,
        details = details
    )
}

private fun noDataCard(metric: HealthMetric): MetricCardState {
    return MetricCardState(
        metric = metric,
        value = "",
        unit = "",
        status = MetricStatus.NoData
    )
}

private fun permissionMissingCard(metric: HealthMetric): MetricCardState {
    return MetricCardState(
        metric = metric,
        value = "",
        unit = "",
        status = MetricStatus.PermissionMissing
    )
}

private fun formatDuration(duration: Duration): String {
    val hours = duration.toHours()
    val minutes = duration.minusHours(hours).toMinutes()
    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes}m"
    }
}

private fun formatTime(time: Instant): String {
    return LOCAL_TIME_FORMATTER.format(time.atZone(ZoneId.systemDefault()))
}

private fun formatStoredTime(time: String): String {
    if (time.isBlank()) return "Never"
    return runCatching {
        formatTime(Instant.parse(time))
    }.getOrElse {
        time
    }
}

private fun defaultMealTime(): String {
    return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
        OffsetDateTime.now().withSecond(0).withNano(0)
    )
}

private fun generateClientMealId(): String {
    val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(OffsetDateTime.now())
    val suffix = UUID.randomUUID().toString().take(8)
    return "android-$timestamp-$suffix"
}

private fun parseMealTime(value: String): OffsetDateTime? {
    return try {
        OffsetDateTime.parse(value.trim(), DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    } catch (_: DateTimeParseException) {
        null
    }
}

private fun parseNonNegativeMealNumber(value: String, label: String): Double {
    val number = value.trim().toDoubleOrNull()
        ?: throw IllegalArgumentException("$label must be a number")
    require(number >= 0.0) { "$label must be non-negative" }
    return number
}

private fun formatMealTime(value: String): String {
    return runCatching {
        OffsetDateTime.parse(value)
            .atZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    }.getOrDefault(value)
}

private fun formatMealNumber(value: Double): String {
    val roundedWhole = value.roundToLong()
    return if (value == roundedWhole.toDouble()) {
        roundedWhole.toString()
    } else {
        ((value * 10.0).roundToLong() / 10.0).toString()
    }
}

private val LOCAL_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
private const val HEALTH_DASHBOARD_TAG = "HealthDashboard"

private data class HealthConnectWriteResult(
    val success: Boolean,
    val alreadyExists: Boolean,
    val message: String
)

@Preview(showBackground = true)
@Composable
private fun HealthDashboardScreenPreview() {
    HealthConnectTestTheme {
        HealthDashboardScreen()
    }
}
