package com.uriel.healthconnecttest.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class UpdateState(
    val checking: Boolean = false,
    val release: AndroidRelease? = null,
    val downloading: Boolean = false,
    val progress: Float = 0f,
    val ready: File? = null,
    val message: String = "Check for new Android releases"
)

class AppUpdates(private val context: Context) {
    private val client = OkHttpClient.Builder().followSslRedirects(false).callTimeout(3, TimeUnit.MINUTES).build()
    private val mutable = MutableStateFlow(UpdateState())
    val state = mutable.asStateFlow()
    private val mutex = Mutex()
    private val installed get() = context.packageManager.getPackageInfo(context.packageName, 0)
    val versionName get() = installed.versionName.orEmpty()

    suspend fun check() = mutex.withLock {
        mutable.value = mutable.value.copy(checking = true, message = "Checking GitHub releases...")
        try {
            val latest = withContext(Dispatchers.IO) {
                val releases = JSONArray(get(ReleasePolicy.API))
                val candidates = mutableListOf<AndroidRelease>()
                for (index in 0 until releases.length()) {
                    val item = releases.getJSONObject(index)
                    val tag = item.optString("tag_name")
                    if (item.optBoolean("draft") || !tag.matches(Regex("android-v[0-9]+\\.[0-9]+\\.[0-9]+"))) continue
                    val assets = item.getJSONArray("assets")
                    val manifestAsset = (0 until assets.length()).map { assets.getJSONObject(it) }
                        .firstOrNull { it.optString("name") == "update.json" } ?: continue
                    val manifestUrl = manifestAsset.getString("browser_download_url")
                    if (!ReleasePolicy.validAssetUrl(manifestUrl, tag)) continue
                    val manifest = JSONObject(get(manifestUrl))
                    val release = AndroidRelease(tag, manifest.getLong("versionCode"),
                        manifest.getString("versionName"), manifest.getString("apkUrl"),
                        manifest.getString("sha256"), manifest.getInt("minSdk"), item.optString("body"))
                    if (ReleasePolicy.eligible(release, installed.longVersionCode, Build.VERSION.SDK_INT)) candidates += release
                }
                candidates.maxByOrNull { it.versionCode }
            }
            val same = latest?.sha256 == mutable.value.release?.sha256
            mutable.value = UpdateState(release = latest, ready = if (same) mutable.value.ready else null,
                message = if (latest == null) "You're up to date" else "Version ${latest.versionName} is available")
        } catch (e: CancellationException) {
            mutable.value = mutable.value.copy(checking = false)
            throw e
        } catch (_: Exception) {
            mutable.value = mutable.value.copy(checking = false, message = "Could not check updates. Check your connection and try again.")
        }
    }

    suspend fun download() = mutex.withLock {
        val release = mutable.value.release ?: return@withLock
        mutable.value = mutable.value.copy(downloading = true, ready = null, progress = 0f, message = "Downloading update...")
        try {
            val file = withContext(Dispatchers.IO) {
                val folder = File(context.cacheDir, "updates").apply { mkdirs() }
                val target = File(folder, "update.apk")
                val temp = File(folder, "update.part")
                target.delete()
                try {
                    client.newCall(Request.Builder().url(release.apkUrl).build()).execute().use { response ->
                        check(response.isSuccessful) { "Download failed" }
                        val body = checkNotNull(response.body)
                        val length = body.contentLength()
                        require(length <= 200L * 1024 * 1024)
                        body.byteStream().use { input ->
                            temp.outputStream().use { output ->
                                val buffer = ByteArray(32 * 1024)
                                var total = 0L
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count == -1) break
                                    total += count
                                    require(total <= 200L * 1024 * 1024)
                                    output.write(buffer, 0, count)
                                    mutable.value = mutable.value.copy(progress = if (length > 0) total.toFloat() / length else 0f)
                                }
                            }
                        }
                    }
                    val hash = MessageDigest.getInstance("SHA-256")
                    temp.inputStream().use { input ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) { val n = input.read(buffer); if (n < 0) break; hash.update(buffer, 0, n) }
                    }
                    check(hash.digest().joinToString("") { "%02x".format(it) }.equals(release.sha256, true)) { "Checksum mismatch" }
                    verifyPackage(temp, release)
                    check(temp.renameTo(target))
                    target
                } finally { temp.delete() }
            }
            mutable.value = mutable.value.copy(downloading = false, ready = file, progress = 1f, message = "Verified and ready to install")
        } catch (e: CancellationException) {
            mutable.value = mutable.value.copy(downloading = false)
            throw e
        } catch (_: Exception) {
            mutable.value = mutable.value.copy(downloading = false, message = "Download or verification failed. Only matching, trusted APKs can be installed. Try again.")
        }
    }

    @Suppress("DEPRECATION")
    private fun verifyPackage(file: File, release: AndroidRelease) {
        val manager = context.packageManager
        val candidate = checkNotNull(manager.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES))
        val current = manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        check(candidate.packageName == context.packageName && candidate.longVersionCode == release.versionCode)
        fun signatures(info: PackageInfo) = info.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
        check(signatures(candidate).isNotEmpty() && signatures(candidate) == signatures(current)) { "Signing identity mismatch" }
    }

    fun install() {
        val file = mutable.value.ready ?: return
        try {
            val release = mutable.value.release ?: return
            verifyPackage(file, release)
            if (!context.packageManager.canRequestPackageInstalls()) {
                mutable.value = mutable.value.copy(message = "Allow updates from this app, then return and tap Install again")
                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")))
                return
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (_: Exception) {
            mutable.value = mutable.value.copy(message = "Unable to open the installer. Download the update again or use the GitHub release page.")
        }
    }

    private fun get(url: String): String = client.newCall(Request.Builder().url(url)
        .header("Accept", "application/vnd.github+json").header("User-Agent", "HealthCompanion-Android").build())
        .execute().use { response ->
            check(response.isSuccessful)
            val body = checkNotNull(response.body)
            require(body.contentLength() <= 2 * 1024 * 1024)
            body.string().also { require(it.length <= 2 * 1024 * 1024) }
        }
}
