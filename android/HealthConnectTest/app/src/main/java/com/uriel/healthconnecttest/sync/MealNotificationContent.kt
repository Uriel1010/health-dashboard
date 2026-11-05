package com.uriel.healthconnecttest.sync

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.text.BidiFormatter
import com.uriel.healthconnecttest.MainActivity
import com.uriel.healthconnecttest.R
import com.uriel.healthconnecttest.ui.MealNotificationActivity
import java.text.NumberFormat

object MealNotificationContent {
    private const val GROUP = "health_companion_meals"
    private const val SUMMARY = "nutrition-summary"
    const val CHANNEL = "new_meals"

    fun title(context: Context, meal: ServerMeal): String =
        BidiFormatter.getInstance(context.resources.configuration.locales[0]).unicodeWrap(meal.title)

    fun nutrients(context: Context, meal: ServerMeal): String {
        val locale = context.resources.configuration.locales[0]
        val format = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }
        return context.getString(R.string.meal_notification_nutrients,
            format.format(meal.caloriesKcal), format.format(meal.proteinG))
    }

    fun build(context: Context, meal: ServerMeal, scope: String): Notification {
        val intent = Intent(context, MealNotificationActivity::class.java)
            .setData(Uri.parse("healthcompanion://meal/${meal.serverId}/$scope"))
            .putExtra("meal_id", meal.serverId).putExtra("account_scope", scope)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val dismissed = PendingIntent.getBroadcast(context, 0,
            Intent(context, MealNotificationDismissReceiver::class.java)
                .setData(Uri.parse("healthcompanion://dismiss/${meal.serverId}"))
                .putExtra("tag", "meal-${meal.serverId}"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_meal_notification)
            .setColor(Color.rgb(139, 40, 239))
            .setContentTitle(title(context, meal)).setContentText(nutrients(context, meal))
            .setStyle(NotificationCompat.BigTextStyle().bigText(nutrients(context, meal)))
            .setContentIntent(pending).setDeleteIntent(dismissed)
            .addAction(0, context.getString(R.string.meal_notification_view), pending)
            .setGroup(GROUP).setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true).setOnlyAlertOnce(true).build()
    }

    fun updateSummary(context: Context, excludeTag: String? = null) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val children = manager.activeNotifications.filter {
            it.tag?.startsWith("meal-") == true && it.tag != excludeTag && it.notification.group == GROUP
        }.sortedByDescending { it.postTime }
        if (children.isEmpty()) { manager.cancel(SUMMARY, 2); return }
        val count = context.resources.getQuantityString(R.plurals.meal_notification_count, children.size, children.size)
        val pending = PendingIntent.getActivity(context, 301,
            Intent(context, MainActivity::class.java).putExtra("open_food", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val inbox = NotificationCompat.InboxStyle().setSummaryText(count)
        children.take(5).forEach { inbox.addLine(it.notification.extras.getCharSequence(Notification.EXTRA_TITLE)) }
        val summary = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_meal_notification).setColor(Color.rgb(139, 40, 239))
            .setContentTitle(context.getString(R.string.app_name)).setContentText(count)
            .setStyle(inbox).setNumber(children.size).setContentIntent(pending)
            .setGroup(GROUP).setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setOnlyAlertOnce(true).setSilent(true).setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        try { manager.notify(SUMMARY, 2, summary) } catch (_: SecurityException) { }
    }
}

class MealNotificationDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        MealNotificationContent.updateSummary(context, intent.getStringExtra("tag"))
    }
}
