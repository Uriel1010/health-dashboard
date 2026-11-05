package com.uriel.healthconnecttest

import android.content.Intent
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.uriel.healthconnecttest.data.ServerSettingsRepository
import com.uriel.healthconnecttest.sync.*
import com.uriel.healthconnecttest.ui.MealNotificationActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test

class MealNotificationActionTest {
    @Test fun viewMealActionFetchesSpecificMealAndRejectsWrongAccount() = runBlocking {
        check(Build.HARDWARE in listOf("ranchu","goldfish"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = OkHttpClient().newCall(Request.Builder().url("http://127.0.0.1:18016/fixture/info").build()).execute().use { it.body!!.string() }
        check(fixture.contains("health-companion-reference-fixture"))
        val repo = ServerSettingsRepository(context)
        repo.saveConnectionSettings("http://127.0.0.1:18016","emulator-fixture","Synthetic","synthetic-ingest","health_session=synthetic-emulator-session",true)
        val settings = repo.settings.first()
        val meal = HealthSyncApiClient().fetchCurrentMeals(settings).meals.first { it.serverId==1L }
        fun visible(node: android.view.accessibility.AccessibilityNodeInfo?): String {
            if(node==null) return ""
            return "${node.text ?: ""} ${node.contentDescription ?: ""} " + (0 until node.childCount).joinToString { visible(node.getChild(it)) }
        }
        fun waitFor(text: String) {
            val deadline = System.currentTimeMillis()+15000
            while(System.currentTimeMillis()<deadline) {
                if(visible(instrumentation.uiAutomation.rootInActiveWindow).contains(text)) return
                Thread.sleep(100)
            }
            val image = instrumentation.uiAutomation.takeScreenshot()
            java.io.File(context.getExternalFilesDir(null),"meal-detail-failure.png").outputStream().use {
                image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            }
            fail("Expected visible text: $text; visible: ${visible(instrumentation.uiAutomation.rootInActiveWindow)}")
        }
        val main = ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java))
        try {
            MealNotificationContent.build(context,meal,MealPush.scope(settings)).actions.single().actionIntent.send()
            val deadline = System.currentTimeMillis()+10000
            var opened = false
            while(!opened && System.currentTimeMillis()<deadline) {
                instrumentation.runOnMainSync {
                    opened = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).any {
                        it is MealNotificationActivity && it.intent.getLongExtra("meal_id",-1)==meal.serverId
                    }
                }
                if(!opened) Thread.sleep(100)
            }
            assertTrue("Notification action opens its exact meal",opened)
            waitFor(meal.title)
            waitFor(MealNotificationContent.nutrients(context,meal))
            instrumentation.waitForIdleSync()
            Thread.sleep(300)
            java.io.File(context.getExternalFilesDir(null),"meal-detail-061.png").outputStream().use {
                instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            }
            instrumentation.runOnMainSync {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MealNotificationActivity>().forEach { it.finish() }
            }
        } finally { main.close() }
        ActivityScenario.launch<MealNotificationActivity>(Intent(context,MealNotificationActivity::class.java)
            .putExtra("meal_id",meal.serverId).putExtra("account_scope","a-different-account")).use {
            waitFor(context.getString(R.string.meal_notification_account_changed))
        }
    }
}
