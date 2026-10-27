package com.uriel.healthconnecttest.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.uriel.healthconnecttest.R
import com.uriel.healthconnecttest.data.ServerSettingsRepository
import com.uriel.healthconnecttest.sync.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.time.format.DateTimeFormatter
import java.text.NumberFormat

class NutritionAverageWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PIN_CONFIGURED) { super.onReceive(context, intent); return }
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider?.className != javaClass.name) return
        val period = intent.getIntExtra("period", 7).takeIf { it in listOf(7, 14, 30) } ?: 7
        val metric = runCatching { NutritionMetric.valueOf(intent.getStringExtra("metric").orEmpty()) }.getOrDefault(NutritionMetric.Calories)
        context.getSharedPreferences("average_widgets", Context.MODE_PRIVATE).edit()
            .putInt("period_$id", period).putString("metric_$id", metric.name).apply()
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch { try { updateAll(context) } finally { pending.finish() } }
    }
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = ServerSettingsRepository(context.applicationContext).settings.first()
                if (settings.readToken.isNotBlank()) runCatching {
                    NutritionStatisticsStore.save(context, settings, HealthSyncApiClient().fetchNutritionStatistics(settings))
                }
                updateAll(context)
            } finally { pending.finish() }
        }
    }
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val edit = context.getSharedPreferences("average_widgets", Context.MODE_PRIVATE).edit()
        appWidgetIds.forEach { edit.remove("period_$it").remove("metric_$it") }
        edit.apply()
    }
    companion object {
        const val PIN_CONFIGURED = "com.uriel.healthconnecttest.PIN_AVERAGE_CONFIGURED"
        suspend fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NutritionAverageWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val settings = ServerSettingsRepository(context.applicationContext).settings.first()
            val statistics = NutritionStatisticsStore.read(context, settings)
            val prefs = context.getSharedPreferences("average_widgets", Context.MODE_PRIVATE)
            ids.forEach { id ->
                val period = prefs.getInt("period_$id", 7)
                val metric = runCatching { NutritionMetric.valueOf(prefs.getString("metric_$id", "Calories")!!) }.getOrDefault(NutritionMetric.Calories)
                val window = statistics?.windows?.firstOrNull { it.days == period }
                val value = window?.values?.get(metric)
                val amount = value?.let { NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }.format(it) } ?: "—"
                val views = RemoteViews(context.packageName, R.layout.widget_nutrition_average)
                views.setTextViewText(R.id.widget_average_title, "${metric.label} · ${period}D AVG")
                views.setTextViewText(R.id.widget_average_value, "$amount ${metric.unit}")
                views.setTextViewText(R.id.widget_average_coverage, window?.let { "${it.coverage[metric]}/$period days with data" } ?: "Open Trends to sync")
                views.setTextViewText(R.id.widget_average_dates, window?.let {
                    val format = DateTimeFormatter.ofPattern("d MMM")
                    "${it.from.format(format)} – ${it.to.format(format)}"
                } ?: "Today excluded")
                views.setTextViewText(R.id.widget_average_status, if (window == null) "Sign in and refresh" else "Saved · Today excluded")
                views.setContentDescription(R.id.widget_average_root,
                    "${metric.label}, $period day average: $amount ${metric.unit}. ${window?.coverage?.get(metric) ?: 0} recorded days. Today excluded. Open Trends.")
                views.setOnClickPendingIntent(R.id.widget_average_root, WidgetLaunchIntent.openTrendsPendingIntent(context))
                manager.updateAppWidget(id, views)
            }
        }
    }
}
