package com.uriel.healthconnecttest.widget

import android.content.Context
import android.graphics.Color
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.widgetAccentDataStore by preferencesDataStore(name = "widget_accent")

object WidgetAccentStore {
    val DEFAULT_ACCENT_COLOR: Int = Color.rgb(112, 221, 183)

    suspend fun getAccentColor(context: Context): Int {
        val preferences = context.widgetAccentDataStore.data.first()
        return preferences[ACCENT_COLOR] ?: DEFAULT_ACCENT_COLOR
    }

    suspend fun setAccentColor(context: Context, color: Int) {
        context.widgetAccentDataStore.edit { preferences ->
            preferences[ACCENT_COLOR] = color
        }
    }

    private val ACCENT_COLOR = intPreferencesKey("accent_color")
}
