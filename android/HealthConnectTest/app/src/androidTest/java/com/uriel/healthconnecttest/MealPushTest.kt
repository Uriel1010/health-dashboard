package com.uriel.healthconnecttest

import android.app.NotificationManager
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.uriel.healthconnecttest.data.ServerSettingsRepository
import com.uriel.healthconnecttest.sync.MealPush
import com.uriel.healthconnecttest.sync.MealNotifications
import com.uriel.healthconnecttest.sync.HealthSyncApiClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

class MealPushTest {
    @Test fun pushedMealsNotifySeparatelyDeduplicateAndRespectAccountAndToggle() = runBlocking {
        check(Build.HARDWARE in listOf("ranchu","goldfish"))
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val fixture=OkHttpClient().newCall(Request.Builder().url("http://127.0.0.1:18016/fixture/info").build()).execute().use {it.body!!.string()}
        check(fixture.contains("health-companion-reference-fixture"))
        val repository=ServerSettingsRepository(context)
        repository.saveConnectionSettings("http://127.0.0.1:18016","emulator-fixture","Synthetic","synthetic-ingest","health_session=synthetic-emulator-session",true)
        repository.setMealNotificationsEnabled(true)
        val baseline=HealthSyncApiClient().fetchCurrentMeals(repository.settings.first())
        assertTrue(baseline.success)
        MealNotifications.observe(context,repository.settings.first(),baseline.meals,silent=true)
        val binding="a".repeat(48)
        MealPush.prefs(context).edit().putString("binding",binding).putString("scope",MealPush.scope(repository.settings.first())).apply()
        val notifications=context.getSystemService(NotificationManager::class.java)
        notifications.cancelAll()
        fun mealAlerts()=notifications.activeNotifications.filter { it.tag?.startsWith("meal-")==true }
        fun newMeal():Int {
            val json=JSONObject().put("title","Synthetic push test").put("mealType","lunch").put("caloriesKcal",500)
                .put("proteinG",25).put("carbsG",50).put("fatG",10)
            return OkHttpClient().newCall(Request.Builder().url("http://127.0.0.1:18016/api/v1/nutrition/meals")
                .post(json.toString().toRequestBody("application/json".toMediaType())).build()).execute().use {JSONObject(it.body!!.string()).getInt("id")}
        }
        fun deliver(event:Int,meal:Int,tag:String=binding) {
            MealPush.enqueue(context,mapOf("type" to "meal.created","eventId" to "$event","mealId" to "$meal","binding" to tag))
            val name="meal_push_$tag-$event"
            val deadline=System.currentTimeMillis()+20000
            while(System.currentTimeMillis()<deadline) {
                val info=WorkManager.getInstance(context).getWorkInfosForUniqueWork(name).get(5,TimeUnit.SECONDS).firstOrNull()
                if(info?.state?.isFinished==true) {assertEquals(androidx.work.WorkInfo.State.SUCCEEDED,info.state);return}
                Thread.sleep(100)
            }
            fail("Push worker timed out")
        }
        val first=newMeal()
        deliver(1001,first);assertEquals(1,mealAlerts().size)
        val posted=mealAlerts().single().postTime
        deliver(1002,first);assertEquals(1,mealAlerts().size)
        assertEquals("Duplicate event must not re-alert",posted,mealAlerts().single().postTime)
        val second=newMeal()
        deliver(1003,second);assertEquals(2,mealAlerts().size)
        deliver(1004,3,"b".repeat(48));assertEquals(2,mealAlerts().size)
        repository.setMealNotificationsEnabled(false)
        deliver(1005,3);assertEquals(2,mealAlerts().size)
        notifications.cancelAll()
    }
}
