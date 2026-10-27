package com.uriel.healthconnecttest.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import android.widget.RemoteViews
import com.uriel.healthconnecttest.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NutritionWidgetUpdater {
    suspend fun updateAll(context: Context) {
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val widgetIds = manager.getAppWidgetIds(
                ComponentName(appContext, NutritionWidgetProvider::class.java)
            )
            if (widgetIds.isEmpty()) return@withContext

            val progress = NutritionWidgetStore.getTodayProgress(appContext)
            val accentColor = WidgetAccentStore.getAccentColor(appContext)
            Log.i(
                TAG,
                "Refreshing ${widgetIds.size} nutrition widget(s) with " +
                    "${progress.caloriesKcal}kcal/${progress.proteinG}g protein/${progress.fiberG}g fiber"
            )
            widgetIds.forEach { widgetId ->
                manager.updateAppWidget(widgetId, buildRemoteViews(appContext, progress, accentColor))
            }
        }
    }

    fun buildRemoteViews(
        context: Context,
        progress: NutritionWidgetProgress,
        accentColor: Int
    ): RemoteViews {
        Log.i(
            TAG,
            "Rendering nutrition widget values calories=${progress.caloriesKcal}/${progress.caloriesProgress}% " +
                "protein=${progress.proteinG}/${progress.proteinProgress}% fiber=${progress.fiberG}/${progress.fiberProgress}%"
        )
        val views = RemoteViews(context.packageName, R.layout.widget_nutrition_progress)
        views.setTextViewText(R.id.widget_calories_value, "${progress.caloriesKcal.toInt()}")
        views.setTextViewText(R.id.widget_calories_label, "of ${progress.caloriesGoal.toInt()} kcal")
        views.setContentDescription(R.id.widget_root,
            "Nutrition today: ${progress.caloriesKcal.toInt()} calories, ${progress.proteinG.toInt()} grams protein, ${progress.fiberG.toInt()} grams fiber. Open Health Connect.")
        views.setTextViewText(R.id.widget_protein_value, "${progress.proteinG.toInt()} / ${progress.proteinGoal.toInt()} g")
        views.setTextViewText(R.id.widget_fiber_value, "${progress.fiberG.toInt()} / ${progress.fiberGoal.toInt()} g")
        views.setInt(R.id.widget_accent_bar, "setBackgroundColor", accentColor)
        views.setContentDescription(R.id.widget_calories_progress, "Calories: ${progress.caloriesProgress}% of daily goal")
        views.setContentDescription(R.id.widget_protein_progress, "Protein: ${progress.proteinProgress}% of daily goal")
        views.setContentDescription(R.id.widget_fiber_progress, "Fiber: ${progress.fiberProgress}% of daily goal")
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            views.setColorStateList(R.id.widget_calories_progress, "setProgressTintList", android.content.res.ColorStateList.valueOf(accentColor))
        }
        views.setProgressBar(R.id.widget_calories_progress, 100, progress.caloriesProgress, false)
        views.setProgressBar(R.id.widget_protein_progress, 100, progress.proteinProgress, false)
        views.setProgressBar(R.id.widget_fiber_progress, 100, progress.fiberProgress, false)
        views.setOnClickPendingIntent(R.id.widget_root, WidgetLaunchIntent.openAppPendingIntent(context))
        return views
    }

    private fun caloriesLeft(progress: NutritionWidgetProgress): String {
        val remaining = (progress.caloriesGoal - progress.caloriesKcal).toInt()
        return if (remaining >= 0) {
            "$remaining kcal remaining"
        } else {
            "${-remaining} kcal over"
        }
    }

    private const val TAG = "NutritionWidgetUpdater"
}
