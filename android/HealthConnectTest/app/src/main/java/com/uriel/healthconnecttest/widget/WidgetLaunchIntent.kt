package com.uriel.healthconnecttest.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.uriel.healthconnecttest.MainActivity

object WidgetLaunchIntent {
    fun openTrendsPendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(context, 3,
        Intent(context, MainActivity::class.java).apply {
            putExtra("open_trends", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun openAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
