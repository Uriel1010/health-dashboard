package com.uriel.healthconnecttest.sync

import com.uriel.healthconnecttest.data.ServerSettings
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class HealthSyncApiClient(
    private val client: OkHttpClient = OkHttpClient()
) {
    fun validateSettings(settings: ServerSettings) {
        validateServerUrl(settings)
        require(settings.deviceId.isNotBlank()) { "Device ID is required" }
        require(settings.apiToken.isNotBlank()) { "Ingest API token is required" }
    }

    private fun validateReadSettings(settings: ServerSettings) {
        validateServerUrl(settings)
        require(settings.readToken.isNotBlank()) { "Dashboard session cookie is required" }
    }

    private fun validateServerUrl(settings: ServerSettings) {
        require(settings.serverBaseUrl.isNotBlank()) { "Server URL is required" }
        require(settings.serverBaseUrl.startsWith("https://") || settings.allowInsecureHttpForLan) {
            "HTTPS is required unless LAN HTTP is explicitly enabled"
        }
        require(settings.serverBaseUrl.startsWith("http://") || settings.serverBaseUrl.startsWith("https://")) {
            "Server URL must start with http:// or https://"
        }
    }

    suspend fun sendSnapshot(settings: ServerSettings, snapshot: HealthSnapshot): SyncResult {
        return executePost(settings, snapshot.toJson())
    }

    suspend fun sendMeal(settings: ServerSettings, meal: MealLogRequest): SyncResult {
        validateSettings(settings)
        val request = Request.Builder()
            .url("${settings.serverBaseUrl.trimEnd('/')}/api/v1/nutrition/meals")
            .header("Authorization", "Bearer ${settings.apiToken}")
            .post(meal.toJson().toRequestBody(JSON))
            .build()
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    response.toSyncResult(
                        successMessage = "Meal uploaded",
                        validationMessage = "Meal validation failed HTTP ${response.code}: check calories, macros, and required fields",
                        duplicateMessage = "Meal already exists HTTP 409: this entry may have been uploaded already",
                        notFoundMessage = "Meal endpoint not found HTTP 404: check the server URL and server version",
                        serviceUnavailableMessage = "Server configuration error HTTP 503: nutrition API is unavailable or not configured"
                    )
                }
            } catch (exception: Exception) {
                SyncResult(false, retryable = exception is IOException, message = safeNetworkMessage(exception))
            }
        }
    }

    suspend fun testConnection(settings: ServerSettings): SyncResult {
        validateServerUrl(settings)
        val request = Request.Builder()
            .url("${settings.serverBaseUrl.trimEnd('/')}/health")
            .get()
            .build()
        return withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(request).execute().use { response ->
                    response.toSyncResult(
                        successMessage = "Connection test succeeded",
                        validationMessage = "Connection test failed HTTP ${response.code}",
                        duplicateMessage = "Connection test failed HTTP ${response.code}",
                        notFoundMessage = "Health endpoint not found HTTP 404: check the server URL",
                        serviceUnavailableMessage = "Server configuration error HTTP 503: health check is unavailable"
                    )
                }
            }.getOrElse { exception ->
                SyncResult(false, retryable = exception is IOException, message = safeNetworkMessage(exception))
            }
        }
    }

    suspend fun loginForReadSession(
        settings: ServerSettings,
        username: String,
        password: String
    ): LoginResult {
        validateServerUrl(settings)
        require(username.isNotBlank()) { "Username is required" }
        require(password.isNotBlank()) { "Password is required" }

        val payload = JSONObject()
            .put("username", username.trim())
            .put("password", password)
            .toString()
        val request = Request.Builder()
            .url("${settings.serverBaseUrl.trimEnd('/')}/api/v1/auth/login")
            .post(payload.toRequestBody(JSON))
            .build()

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    when {
                        response.isSuccessful -> {
                            val cookie = response.headers("Set-Cookie")
                                .firstNotNullOfOrNull(::sessionCookieFromHeader)
                                .orEmpty()
                            if (cookie.isBlank()) {
                                LoginResult(
                                    success = false,
                                    retryable = false,
                                    message = "Login succeeded but no session cookie was returned"
                                )
                            } else {
                                LoginResult(
                                    success = true,
                                    retryable = false,
                                    message = "Dashboard login succeeded",
                                    sessionCookie = cookie
                                )
                            }
                        }
                        response.code == 401 -> LoginResult(
                            success = false,
                            retryable = false,
                            message = "Dashboard login failed HTTP 401: username or password is invalid"
                        )
                        response.code == 400 || response.code == 422 -> LoginResult(
                            success = false,
                            retryable = false,
                            message = "Dashboard login validation failed HTTP ${response.code}"
                        )
                        response.code >= 500 -> LoginResult(
                            success = false,
                            retryable = true,
                            message = "Server error HTTP ${response.code}: try again later"
                        )
                        else -> LoginResult(
                            success = false,
                            retryable = false,
                            message = "Dashboard login failed HTTP ${response.code}"
                        )
                    }
                }
            } catch (exception: Exception) {
                LoginResult(false, retryable = exception is IOException, message = safeNetworkMessage(exception))
            }
        }
    }

    suspend fun fetchServerMeals(
        settings: ServerSettings,
        from: LocalDate,
        to: LocalDate
    ): ServerMealsFetchResult {
        validateReadSettings(settings)
        val request = Request.Builder()
            .url("${settings.serverBaseUrl.trimEnd('/')}/api/v1/nutrition/meals?from=$from&to=$to")
            .header("Cookie", settings.readToken)
            .get()
            .build()

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    when {
                        response.isSuccessful -> {
                            val body = response.body?.string().orEmpty()
                            val parsed = parseServerMeals(body)
                            ServerMealsFetchResult(
                                success = true,
                                retryable = false,
                                message = "Fetched server meals",
                                meals = parsed.meals,
                                failedParseCount = parsed.failedCount,
                                firstParseError = parsed.firstError
                            )
                        }
                        response.code == 401 -> ServerMealsFetchResult(
                            success = false,
                            retryable = false,
                            message = "Authentication failed HTTP 401: dashboard session is missing, expired, or invalid"
                        )
                        response.code == 403 -> ServerMealsFetchResult(
                            success = false,
                            retryable = false,
                            message = "Authorization failed HTTP 403: dashboard account cannot read nutrition meals"
                        )
                        response.code == 404 -> ServerMealsFetchResult(
                            success = false,
                            retryable = false,
                            message = "Nutrition meals read endpoint not found HTTP 404: check the server URL and server version"
                        )
                        response.code == 400 || response.code == 422 -> ServerMealsFetchResult(
                            success = false,
                            retryable = false,
                            message = "Nutrition meals read validation failed HTTP ${response.code}: check the date range"
                        )
                        response.code >= 500 -> ServerMealsFetchResult(
                            success = false,
                            retryable = true,
                            message = "Server error HTTP ${response.code}: try again later"
                        )
                        else -> ServerMealsFetchResult(
                            success = false,
                            retryable = false,
                            message = "Nutrition meals read failed HTTP ${response.code}"
                        )
                    }
                }
            } catch (exception: Exception) {
                ServerMealsFetchResult(
                    success = false,
                    retryable = exception is IOException,
                    message = safeNetworkMessage(exception)
                )
            }
        }
    }

    suspend fun fetchCurrentMeals(settings: ServerSettings): ServerMealsFetchResult {
        validateReadSettings(settings)
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("${settings.serverBaseUrl.trimEnd('/')}/api/v1/auth/me")
                    .header("Cookie", settings.readToken).build()
                val day = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext ServerMealsFetchResult(
                        false, response.code >= 500, "Unable to load nutrition preferences HTTP ${response.code}")
                    val account = JSONObject(response.body?.string().orEmpty())
                    val goals = account.getJSONObject("nutritionGoals")
                    ServerNutritionDay(LocalDate.parse(account.getString("currentNutritionDate")),
                        account.getString("serverTimezone"), account.getInt("nutritionResetHour"),
                        goals.getDouble("caloriesKcal"), goals.getDouble("proteinG"), goals.getDouble("fiberG"))
                }
                fetchServerMeals(settings, day.date, day.date).copy(nutritionDay = day)
            } catch (exception: Exception) {
                ServerMealsFetchResult(false, exception is IOException, safeNetworkMessage(exception))
            }
        }
    }

    suspend fun deleteMeal(settings: ServerSettings, mealId: Long): SyncResult {
        validateSettings(settings)
        require(mealId > 0) { "Meal ID must be positive" }
        val request = Request.Builder()
            .url("${settings.serverBaseUrl.trimEnd('/')}/api/v1/nutrition/meals/$mealId")
            .header("Authorization", "Bearer ${settings.apiToken}")
            .delete()
            .build()

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    response.toSyncResult(
                        successMessage = "Meal deleted",
                        validationMessage = "Meal deletion validation failed HTTP ${response.code}",
                        duplicateMessage = "Meal deletion conflict HTTP 409",
                        notFoundMessage = "Meal was not found or was already deleted",
                        serviceUnavailableMessage = "Nutrition API is unavailable HTTP 503"
                    )
                }
            } catch (exception: Exception) {
                SyncResult(false, retryable = exception is IOException, message = safeNetworkMessage(exception))
            }
        }
    }

    private fun sessionCookieFromHeader(header: String): String? {
        val pair = header.substringBefore(";").trim()
        return pair.takeIf { it.contains("=") }
    }

    private suspend fun executePost(settings: ServerSettings, json: String): SyncResult {
        validateSettings(settings)
        val request = Request.Builder()
            .url("${settings.serverBaseUrl.trimEnd('/')}/api/v1/ingest/health-snapshot")
            .header("Authorization", "Bearer ${settings.apiToken}")
            .post(json.toRequestBody(JSON))
            .build()
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    response.toSyncResult(
                        successMessage = "Sync succeeded",
                        validationMessage = "Snapshot validation failed HTTP ${response.code}: check server payload expectations",
                        duplicateMessage = "Snapshot conflict HTTP 409: this snapshot may have already been accepted",
                        notFoundMessage = "Ingest endpoint not found HTTP 404: check the server URL and server version",
                        serviceUnavailableMessage = "Server configuration error HTTP 503: ingest API is unavailable or not configured"
                    )
                }
            } catch (exception: Exception) {
                SyncResult(false, retryable = exception is IOException, message = safeNetworkMessage(exception))
            }
        }
    }

    private fun okhttp3.Response.toSyncResult(
        successMessage: String,
        validationMessage: String,
        duplicateMessage: String,
        notFoundMessage: String,
        serviceUnavailableMessage: String
    ): SyncResult {
        return when {
            isSuccessful -> SyncResult(true, retryable = false, message = successMessage)
            code == 401 -> SyncResult(
                false,
                retryable = false,
                message = "Authentication failed HTTP 401: ingest API token is missing, expired, or invalid"
            )
            code == 403 -> SyncResult(
                false,
                retryable = false,
                message = "Authorization failed HTTP 403: ingest API token is not allowed for this endpoint"
            )
            code == 400 || code == 422 -> SyncResult(false, retryable = false, message = validationMessage)
            code == 409 -> SyncResult(false, retryable = false, message = duplicateMessage)
            code == 404 -> SyncResult(false, retryable = false, message = notFoundMessage)
            code == 503 -> SyncResult(false, retryable = false, message = serviceUnavailableMessage)
            code >= 500 -> SyncResult(
                false,
                retryable = true,
                message = "Server error HTTP $code: retrying later"
            )
            else -> SyncResult(false, retryable = false, message = "Request failed HTTP $code")
        }
    }

    private fun safeNetworkMessage(exception: Throwable): String {
        return when (exception) {
            is SocketTimeoutException -> "Network timeout: server did not respond in time"
            is ConnectException -> "Server unreachable: connection failed"
            is UnknownHostException -> "Network error: server host could not be resolved"
            else -> "Network error: ${exception.message ?: exception::class.java.simpleName}"
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
