package com.uriel.healthconnecttest.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.appearanceStore by preferencesDataStore("appearance")
enum class AppearanceMode { System, Light, Dark }
object AppearancePreferences {
    private val MODE = stringPreferencesKey("mode")
    fun mode(context: Context) = context.applicationContext.appearanceStore.data.map {
        runCatching { AppearanceMode.valueOf(it[MODE].orEmpty()) }.getOrDefault(AppearanceMode.System)
    }
    suspend fun setMode(context: Context, mode: AppearanceMode) {
        context.applicationContext.appearanceStore.edit { it[MODE] = mode.name }
    }
}
