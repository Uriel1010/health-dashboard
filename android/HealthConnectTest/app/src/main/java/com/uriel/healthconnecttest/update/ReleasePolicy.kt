package com.uriel.healthconnecttest.update

import java.net.URI

data class AndroidRelease(
    val tag: String,
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val minSdk: Int,
    val notes: String
)

object ReleasePolicy {
    const val REPOSITORY = "Uriel1010/health-dashboard"
    const val API = "https://api.github.com/repos/$REPOSITORY/releases?per_page=100"

    fun validAssetUrl(url: String, tag: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null &&
            uri.rawQuery == null && uri.fragment == null &&
            uri.path.startsWith("/$REPOSITORY/releases/download/$tag/") &&
            !uri.path.contains("/../")
    }.getOrDefault(false)

    fun eligible(release: AndroidRelease, installed: Long, sdk: Int): Boolean =
        release.tag.matches(Regex("android-v[0-9]+\\.[0-9]+\\.[0-9]+")) &&
            release.versionCode > installed && release.minSdk in 28..sdk &&
            release.sha256.matches(Regex("[a-fA-F0-9]{64}")) &&
            validAssetUrl(release.apkUrl, release.tag) && release.apkUrl.endsWith(".apk")
}
