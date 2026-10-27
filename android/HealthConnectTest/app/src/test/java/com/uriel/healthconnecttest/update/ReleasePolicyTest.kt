package com.uriel.healthconnecttest.update

import org.junit.Assert.*
import org.junit.Test

class ReleasePolicyTest {
    private val release = AndroidRelease("android-v0.3.0", 3, "0.3.0",
        "https://github.com/Uriel1010/health-dashboard/releases/download/android-v0.3.0/app.apk",
        "a".repeat(64), 28, "Update")

    @Test fun acceptsNewCompatibleRelease() { assertTrue(ReleasePolicy.eligible(release, 2, 36)) }
    @Test fun separatesProductionAndDevelopmentUpdates() {
        assertTrue(ReleasePolicy.channelTag("android-production-v0.2.1", true))
        assertFalse(ReleasePolicy.channelTag("android-production-v0.2.1", false))
        assertTrue(ReleasePolicy.channelTag("android-v0.2.0", false))
        assertFalse(ReleasePolicy.channelTag("android-v0.2.0", true))
    }
    @Test fun refusesDowngradesAndSameVersion() {
        assertFalse(ReleasePolicy.eligible(release, 3, 36))
        assertFalse(ReleasePolicy.eligible(release, 4, 36))
    }
    @Test fun refusesDashboardTagsAndUnsupportedSdk() {
        assertFalse(ReleasePolicy.eligible(release.copy(tag = "dashboard-v0.3.0"), 2, 36))
        assertFalse(ReleasePolicy.eligible(release.copy(minSdk = 37), 2, 36))
    }
    @Test fun refusesUntrustedDownloadsAndInvalidChecksums() {
        for (url in listOf("http://github.com/Uriel1010/health-dashboard/releases/download/android-v0.3.0/app.apk",
            "https://github.com.attacker.test/Uriel1010/health-dashboard/releases/download/android-v0.3.0/app.apk",
            "https://github.com/other/repo/releases/download/android-v0.3.0/app.apk",
            "https://github.com/Uriel1010/health-dashboard/releases/download/android-v0.3.0/../app.apk")) {
            assertFalse(ReleasePolicy.eligible(release.copy(apkUrl = url), 2, 36))
        }
        assertFalse(ReleasePolicy.eligible(release.copy(sha256 = "invalid"), 2, 36))
    }
}
