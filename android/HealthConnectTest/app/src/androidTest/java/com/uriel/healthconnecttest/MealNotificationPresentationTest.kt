package com.uriel.healthconnecttest

import android.app.Notification
import android.app.NotificationManager
import android.content.res.Configuration
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.uriel.healthconnecttest.data.ServerSettings
import com.uriel.healthconnecttest.sync.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class MealNotificationPresentationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun localized(code: String) = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(code)) })
    private fun sample(id: Long) = ServerMeal(id,"$id","$id","2026-11-05T12:00:00+02:00",MealType.Other,
        "קציצות טלה — 2 יחידות",390.0,28.0,20.0,12.0,3.0,null,null)

    @Test fun localizedContentHasActualMacrosAndDistinctMealActions() {
        check(Build.HARDWARE in listOf("ranchu","goldfish"))
        val he = localized("he")
        val en = localized("en")
        val meal = sample(91001)
        assertEquals("Health Companion",he.getString(R.string.app_name))
        assertEquals("390 קלוריות · 28 גרם חלבון",MealNotificationContent.nutrients(he,meal))
        assertEquals("390 kcal · 28 g protein",MealNotificationContent.nutrients(en,meal))
        val first = MealNotificationContent.build(he,meal,"synthetic-account")
        val other = MealNotificationContent.build(he,sample(91002),"synthetic-account")
        assertTrue(first.extras.getCharSequence(Notification.EXTRA_TITLE).toString().contains(meal.title))
        assertFalse(first.extras.getCharSequence(Notification.EXTRA_TITLE).toString().contains("New meal:"))
        assertEquals("הצגת הארוחה",first.actions.single().title.toString())
        assertNotEquals(first.contentIntent,other.contentIntent)
        assertEquals(first.contentIntent,first.actions.single().actionIntent)
        assertEquals(Notification.VISIBILITY_PRIVATE,first.visibility)
    }

    @Test fun groupedNotificationsKeepPerMealChildrenAndAccurateSilentSummary() = runBlocking {
        check(Build.HARDWARE in listOf("ranchu","goldfish"))
        val he = localized("he")
        val manager = he.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        val settings = ServerSettings(serverBaseUrl="https://notification-fixture.invalid",readToken="synthetic-presentation",mealNotificationsEnabled=true)
        val seed = System.nanoTime()
        val first = sample(seed)
        val second = sample(seed+1).copy(title="חומוס למריחה",caloriesKcal=45.0,proteinG=2.0)
        MealNotifications.observe(he,settings,listOf(first,second),allowUninitialized=true)
        fun active() = manager.activeNotifications.toList()
        fun awaitSummary(expected: String?) {
            val deadline = System.currentTimeMillis()+5000
            do {
                val actual = active().firstOrNull {it.tag=="nutrition-summary"}?.notification?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                if(actual==expected) return
                Thread.sleep(50)
            } while(System.currentTimeMillis()<deadline)
            fail("Summary did not settle to $expected")
        }
        assertEquals(2,active().count { it.tag?.startsWith("meal-")==true })
        val summary = active().single { it.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0 }
        assertEquals("2 ארוחות חדשות",summary.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(Notification.GROUP_ALERT_CHILDREN,summary.notification.groupAlertBehavior)
        assertEquals("Health Companion",summary.notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        manager.cancel("meal-${first.serverId}",1)
        MealNotificationContent.updateSummary(he,"meal-${first.serverId}")
        awaitSummary("ארוחה חדשה אחת")
        assertEquals("ארוחה חדשה אחת",active().single {it.tag=="nutrition-summary"}.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        manager.cancel("meal-${second.serverId}",1)
        MealNotificationContent.updateSummary(he,"meal-${second.serverId}")
        awaitSummary(null)
        assertTrue(active().isEmpty())
    }
}
