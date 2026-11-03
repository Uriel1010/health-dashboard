package com.uriel.healthconnecttest.sync

import android.content.Context
import androidx.work.*
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.uriel.healthconnecttest.data.ServerSettings
import com.uriel.healthconnecttest.data.ServerSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

object MealPush {
    private val client = OkHttpClient.Builder().callTimeout(20,TimeUnit.SECONDS).build()
    fun prefs(context:Context)=context.getSharedPreferences("meal_push",Context.MODE_PRIVATE)
    fun scope(settings:ServerSettings)=MessageDigest.getInstance("SHA-256").digest(
        "${settings.serverBaseUrl.trimEnd('/')}\n${settings.readToken}".toByteArray()).joinToString("") { "%02x".format(it) }
    fun scheduleRegistration(context:Context) {
        WorkManager.getInstance(context).enqueueUniqueWork("meal_push_registration",ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<MealPushRegistrationWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
    }
    suspend fun detach(context:Context,settings:ServerSettings) = withContext(Dispatchers.IO) {
        // Clear local acceptance first: queued pushes from a previous account must never display.
        prefs(context).edit().remove("binding").remove("scope").apply()
        androidx.core.app.NotificationManagerCompat.from(context).cancelAll()
        runCatching { register(context,settings,false) }
        prefs(context).edit().remove("binding").remove("scope").apply()
    }
    fun register(context:Context, settings:ServerSettings, enabled:Boolean):Boolean {
        if(settings.readToken.isBlank() || settings.serverBaseUrl.isBlank()) return true
        require(settings.serverBaseUrl.startsWith("https://") || settings.allowInsecureHttpForLan && settings.serverBaseUrl.startsWith("http://"))
        val p=prefs(context)
        val id=p.getString("installation",null) ?: UUID.randomUUID().toString().also { p.edit().putString("installation",it).apply() }
        val secret=p.getString("secret",null) ?: (UUID.randomUUID().toString()+UUID.randomUUID().toString()).also {p.edit().putString("secret",it).apply()}
        val token=p.getString("token",null) ?: return true
        val body=JSONObject().put("installationId",id).put("installationSecret",secret).put("token",token).put("enabled",enabled)
        val request=Request.Builder().url("${settings.serverBaseUrl.trimEnd('/')}/api/v1/push/installations")
            .header("Cookie",settings.readToken).post(body.toString().toRequestBody("application/json".toMediaType())).build()
        client.newCall(request).execute().use { response ->
            if(response.code in listOf(401,403)) {p.edit().remove("binding").remove("scope").apply();return true}
            if(!response.isSuccessful) return false
            val json=JSONObject(response.body!!.string())
            if(enabled) p.edit().putString("binding",json.getString("binding")).putString("scope",scope(settings))
                .putBoolean("available",json.optBoolean("available")).apply()
            return true
        }
    }
    fun enqueue(context:Context,data:Map<String,String>) {
        if(data["type"]!="meal.created") return
        val meal=data["mealId"]?.toLongOrNull()?.takeIf {it>0} ?: return
        val event=data["eventId"]?.toLongOrNull()?.takeIf {it>0} ?: return
        val binding=data["binding"]?.takeIf {it.length in 32..64} ?: return
        val request=OneTimeWorkRequestBuilder<MealPushDeliveryWorker>()
            .setInputData(workDataOf("mealId" to meal,"binding" to binding))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build()
        WorkManager.getInstance(context).enqueueUniqueWork("meal_push_$binding-$event",ExistingWorkPolicy.KEEP,request)
    }
    fun fetchMeal(settings:ServerSettings,id:Long):ServerMeal? {
        val request=Request.Builder().url("${settings.serverBaseUrl.trimEnd('/')}/api/v1/push/meals/$id").header("Cookie",settings.readToken).build()
        client.newCall(request).execute().use { response ->
            if(response.code in listOf(401,403,404)) return null
            check(response.isSuccessful)
            return parseServerMeals(JSONObject().put("meals",org.json.JSONArray().put(JSONObject(response.body!!.string()))).toString()).meals.single()
        }
    }
}

class MealMessagingService:FirebaseMessagingService() {
    override fun onNewToken(token:String) {
        MealPush.prefs(this).edit().putString("token",token).apply()
        MealPush.scheduleRegistration(this)
    }
    override fun onMessageReceived(message:RemoteMessage) = MealPush.enqueue(this,message.data)
}

class MealPushRegistrationWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result=withContext(Dispatchers.IO) {
        try {
            val context=applicationContext
            val settings=ServerSettingsRepository(context).settings.first()
            if(settings.readToken.isBlank()) return@withContext Result.success()
            val enabled=settings.mealNotificationsEnabled && MealNotifications.allowed(context)
            if(!enabled) {
                MealPush.prefs(context).edit().remove("binding").remove("scope").apply()
                return@withContext if(MealPush.register(context,settings,false)) Result.success() else Result.retry()
            }
            if(FirebaseApp.getApps(context).isEmpty() && FirebaseApp.initializeApp(context)==null) return@withContext Result.success()
            val token=Tasks.await(FirebaseMessaging.getInstance().token,20,TimeUnit.SECONDS)
            MealPush.prefs(context).edit().putString("token",token).apply()
            if(enabled && MealPush.prefs(context).getString("scope",null)!=MealPush.scope(settings)) {
                val baseline=HealthSyncApiClient().fetchCurrentMeals(settings)
                if(!baseline.success) return@withContext Result.retry()
                MealNotifications.observe(context,settings,baseline.meals,silent=true)
            }
            if(!enabled) MealPush.prefs(context).edit().remove("binding").remove("scope").apply()
            if(MealPush.register(context,settings,enabled)) Result.success() else Result.retry()
        } catch(e:kotlinx.coroutines.CancellationException) {throw e} catch(_:Exception) {Result.retry()}
    }
}

class MealPushDeliveryWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun getForegroundInfo():ForegroundInfo {
        val manager=applicationContext.getSystemService(android.app.NotificationManager::class.java)
        manager.createNotificationChannel(android.app.NotificationChannel("meal_push_refresh","Meal notification refresh",android.app.NotificationManager.IMPORTANCE_LOW))
        return ForegroundInfo(901,androidx.core.app.NotificationCompat.Builder(applicationContext,"meal_push_refresh")
            .setSmallIcon(com.uriel.healthconnecttest.R.drawable.ic_meal_notification).setContentTitle("Refreshing new meal")
            .setOngoing(true).build())
    }
    override suspend fun doWork():Result=withContext(Dispatchers.IO) {
        try {
            val context=applicationContext
            val settings=ServerSettingsRepository(context).settings.first()
            val prefs=MealPush.prefs(context)
            val binding=inputData.getString("binding")
            if(!settings.mealNotificationsEnabled || !MealNotifications.allowed(context) || binding==null ||
                prefs.getString("binding",null)!=binding || prefs.getString("scope",null)!=MealPush.scope(settings)) return@withContext Result.success()
            val meal=MealPush.fetchMeal(settings,inputData.getLong("mealId",-1)) ?: return@withContext Result.success()
            // Account or preference may have changed while the authenticated fetch was in flight.
            val current=ServerSettingsRepository(context).settings.first()
            if(current.mealNotificationsEnabled && MealPush.scope(current)==MealPush.scope(settings) && prefs.getString("binding",null)==binding) {
                MealNotifications.observe(context,current,listOf(meal),allowUninitialized=true)
                HealthSyncScheduler.enqueueManualWorkerSync(context)
            }
            Result.success()
        } catch(e:kotlinx.coroutines.CancellationException) {throw e} catch(_:Exception) {Result.retry()}
    }
}
