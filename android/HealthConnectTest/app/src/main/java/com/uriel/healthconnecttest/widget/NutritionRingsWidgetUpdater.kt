package com.uriel.healthconnecttest.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import android.widget.RemoteViews
import com.uriel.healthconnecttest.R
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NutritionRingsWidgetUpdater {
    suspend fun updateAll(context: Context) {
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val widgetIds = manager.getAppWidgetIds(
                ComponentName(appContext, NutritionRingsWidgetProvider::class.java)
            )
            if (widgetIds.isEmpty()) return@withContext

            val progress = NutritionWidgetStore.getTodayProgress(appContext)
            val accentColor = WidgetAccentStore.getAccentColor(appContext)
            Log.i(
                TAG,
                "Refreshing ${widgetIds.size} nutrition rings widget(s) with " +
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
            "Rendering rings widget values calories=${progress.caloriesKcal}/${progress.caloriesProgress}% " +
                "protein=${progress.proteinG}/${progress.proteinProgress}% fiber=${progress.fiberG}/${progress.fiberProgress}%"
        )
        val views = RemoteViews(context.packageName, R.layout.widget_nutrition_rings)
        views.setImageViewBitmap(R.id.widget_rings_image, drawRings(progress, accentColor))
        views.setTextViewText(R.id.widget_rings_kcal, compactCalories(progress.caloriesKcal))
        views.setTextColor(R.id.widget_rings_kcal, Color.WHITE)
        views.setTextColor(R.id.widget_rings_unit, withAlpha(accentColor, 230))
        views.setContentDescription(R.id.widget_rings_root,
            "Nutrition today: ${progress.caloriesKcal.toInt()} calories, protein ${progress.proteinProgress} percent, fiber ${progress.fiberProgress} percent. Open Health Connect.")
        views.setOnClickPendingIntent(R.id.widget_rings_root, WidgetLaunchIntent.openAppPendingIntent(context))
        return views
    }

    private fun drawRings(progress: NutritionWidgetProgress, accentColor: Int): Bitmap {
        val size = 220
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        drawRing(canvas, paint, size, 94f, 12f, progress.caloriesProgress, accentColor)
        drawRing(canvas, paint, size, 75f, 10f, progress.proteinProgress, Color.rgb(155, 203, 235))
        drawRing(canvas, paint, size, 58f, 9f, progress.fiberProgress, Color.rgb(241, 181, 197))
        return bitmap
    }

    private fun compactCalories(caloriesKcal: Double): String {
        val calories = caloriesKcal.toInt()
        if (calories < 1000) return calories.toString()

        val thousands = calories / 1000.0
        val formatted = if (calories >= 10_000 || calories % 1000 == 0) {
            String.format(Locale.US, "%.0fk", thousands)
        } else {
            String.format(Locale.US, "%.1fk", thousands)
        }
        return formatted
    }

    private fun drawRing(
        canvas: Canvas,
        paint: Paint,
        size: Int,
        radius: Float,
        strokeWidth: Float,
        progress: Int,
        color: Int
    ) {
        val center = size / 2f
        val rect = RectF(center - radius, center - radius, center + radius, center + radius)
        paint.strokeWidth = strokeWidth
        paint.color = withAlpha(color, 44)
        canvas.drawArc(rect, 0f, 360f, false, paint)
        if (progress > 0) {
            paint.color = color
            canvas.drawArc(rect, -90f, progress * 3.6f, false, paint)
        }
    }

    private fun blend(first: Int, second: Int, amount: Float): Int {
        val inverse = 1f - amount
        return Color.rgb(
            (Color.red(first) * inverse + Color.red(second) * amount).toInt(),
            (Color.green(first) * inverse + Color.green(second) * amount).toInt(),
            (Color.blue(first) * inverse + Color.blue(second) * amount).toInt()
        )
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private const val TAG = "NutritionRingsWidget"
}
