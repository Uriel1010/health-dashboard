package com.uriel.healthconnecttest.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.RemoteViews
import com.uriel.healthconnecttest.R
import com.uriel.healthconnecttest.data.*
import com.uriel.healthconnecttest.sync.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class CompanionProgressWidget : CompanionWidgetProvider()
class CompanionRingsWidget : CompanionWidgetProvider()
class CompanionAverageWidget : CompanionWidgetProvider()
class CompanionMiniProgressWidget : CompanionWidgetProvider()
class CompanionMiniRingsWidget : CompanionWidgetProvider()
class CompanionMiniAverageWidget : CompanionWidgetProvider()

open class CompanionWidgetProvider : AppWidgetProvider() {
    private fun refresh(context: Context) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch { try { updateAll(context) } finally { pending.finish() } }
    }
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = refresh(context)
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PIN_CONFIGURED) { super.onReceive(context, intent); return }
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        if (AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider != ComponentName(context, javaClass)) return
        val period = intent.getIntExtra("period", 7).takeIf { it in listOf(7, 14, 30) } ?: 7
        val metric = runCatching { NutritionMetric.valueOf(intent.getStringExtra("metric").orEmpty()) }.getOrDefault(NutritionMetric.Calories)
        context.getSharedPreferences("average_widgets", Context.MODE_PRIVATE).edit()
            .putInt("period_$id", period).putString("metric_$id", metric.name).apply()
        refresh(context)
    }
    override fun onDeleted(context: Context, ids: IntArray) {
        val edit = context.getSharedPreferences("average_widgets", Context.MODE_PRIVATE).edit()
        ids.forEach { edit.remove("period_$it").remove("metric_$it") }; edit.apply()
    }
    companion object {
        const val PIN_CONFIGURED = "com.uriel.healthconnecttest.PIN_COMPANION_CONFIGURED"
        val providers = listOf(CompanionProgressWidget::class.java, CompanionRingsWidget::class.java,
            CompanionAverageWidget::class.java, CompanionMiniProgressWidget::class.java,
            CompanionMiniRingsWidget::class.java, CompanionMiniAverageWidget::class.java)
        suspend fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val instances = providers.flatMapIndexed { kind, type -> manager.getAppWidgetIds(ComponentName(context, type)).map { kind to it } }
            if (instances.isEmpty()) return
            val settings = ServerSettingsRepository(context).settings.first()
            val stats = NutritionStatisticsStore.read(context, settings)
            val progress = NutritionWidgetStore.getTodayProgress(context)
            val mode = AppearancePreferences.mode(context).first()
            val dark = mode == AppearanceMode.Dark || (mode == AppearanceMode.System &&
                context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
            val prefs = context.getSharedPreferences("average_widgets", Context.MODE_PRIVATE)
            instances.forEach { (kind, id) ->
                val options = manager.getAppWidgetOptions(id)
                val mini = kind >= 3
                val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, if (mini) 100 else 300).coerceIn(if (mini) 48 else 180, 600)
                val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, if (mini) 100 else 220).coerceIn(if (mini) 48 else 140, 500)
                val period = prefs.getInt("period_$id", 7).takeIf { it in listOf(7,14,30) } ?: 7
                val metric = runCatching { NutritionMetric.valueOf(prefs.getString("metric_$id", "Calories")!!) }.getOrDefault(NutritionMetric.Calories)
                val bitmap = CompanionWidgetRenderer.render(kind, dark, width, height, progress, stats, period, metric)
                val views = RemoteViews(context.packageName, R.layout.widget_companion)
                views.setImageViewBitmap(R.id.companion_image, bitmap)
                val window = stats?.windows?.firstOrNull { it.days == period }
                val description = if (kind % 3 == 2) "${metric.label}, $period completed day average: ${window?.values?.get(metric) ?: "unavailable"} ${metric.unit}. Coverage ${window?.coverage?.get(metric) ?: 0}/$period. Today excluded. Open Trends."
                    else "Today's nutrition: ${progress.caloriesKcal.toInt()} of ${progress.caloriesGoal.toInt()} calories; protein ${progress.proteinG.toInt()} grams; fiber ${progress.fiberG.toInt()} grams. Open Health Companion."
                views.setContentDescription(R.id.companion_image, description)
                views.setOnClickPendingIntent(R.id.companion_image, if (kind % 3 == 2) WidgetLaunchIntent.openTrendsPendingIntent(context) else WidgetLaunchIntent.openAppPendingIntent(context))
                manager.updateAppWidget(id, views)
            }
        }
    }
}
