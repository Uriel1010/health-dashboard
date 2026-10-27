package com.uriel.healthconnecttest.widget

import android.content.Context

object NutritionWidgetsUpdater {
    suspend fun updateAll(context: Context) {
        NutritionWidgetUpdater.updateAll(context)
        NutritionRingsWidgetUpdater.updateAll(context)
    }
}
