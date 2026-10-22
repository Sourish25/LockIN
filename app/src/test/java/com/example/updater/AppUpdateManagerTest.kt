package com.example.updater

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppUpdateManagerTest {

    @Test
    fun testIsNewerVersion_standardIncrements() {
        assertTrue(AppUpdateManager.isNewerVersion("1.0.0", "1.1.0"))
        assertTrue(AppUpdateManager.isNewerVersion("1.0", "1.0.1"))
        assertTrue(AppUpdateManager.isNewerVersion("v1.0.0", "v2.0.0"))
        assertTrue(AppUpdateManager.isNewerVersion("0.9.5", "1.0.0"))
        assertTrue(AppUpdateManager.isNewerVersion("1.1.0-alpha", "1.1.1"))
        assertTrue(AppUpdateManager.isNewerVersion("1.1.0-beta", "1.1.0"))
    }

    @Test
    fun testIsNewerVersion_equalOrOlderVersions() {
        assertFalse(AppUpdateManager.isNewerVersion("1.0.0", "1.0.0"))
        assertFalse(AppUpdateManager.isNewerVersion("1.1.0", "1.0.9"))
        assertFalse(AppUpdateManager.isNewerVersion("2.0.0", "1.9.9"))
        assertFalse(AppUpdateManager.isNewerVersion("v1.5", "1.5.0"))
        assertFalse(AppUpdateManager.isNewerVersion("1.2.0", "1.2.0-beta"))
    }

    @Test
    fun testResolveBestApkAsset_matchesExactDeviceAbi() {
        val assets = listOf(
            JSONObject("""{"name":"LockIN-v1.1.0-arm64-v8a.apk", "size": 15000000, "browser_download_url": "https://example.com/arm64.apk"}"""),
            JSONObject("""{"name":"LockIN-v1.1.0-armeabi-v7a.apk", "size": 14000000, "browser_download_url": "https://example.com/armv7.apk"}"""),
            JSONObject("""{"name":"LockIN-v1.1.0-universal.apk", "size": 25000000, "browser_download_url": "https://example.com/universal.apk"}""")
        )

        val supportedAbis = arrayOf("arm64-v8a", "armeabi-v7a")
        val match = AppUpdateManager.resolveBestApkAsset(assets, supportedAbis)

        assertNotNull(match)
        assertEquals("arm64-v8a", match!!.second)
        assertEquals("LockIN-v1.1.0-arm64-v8a.apk", match.first.getString("name"))
    }

    @Test
    fun testResolveBestApkAsset_fallsBackToUniversalWhenAbiNotFound() {
        val assets = listOf(
            JSONObject("""{"name":"LockIN-v1.1.0-x86_64.apk", "size": 16000000, "browser_download_url": "https://example.com/x86.apk"}"""),
            JSONObject("""{"name":"LockIN-v1.1.0-universal.apk", "size": 28000000, "browser_download_url": "https://example.com/universal.apk"}""")
        )

        val supportedAbis = arrayOf("arm64-v8a")
        val match = AppUpdateManager.resolveBestApkAsset(assets, supportedAbis)

        assertNotNull(match)
        assertEquals("universal", match!!.second)
        assertEquals("LockIN-v1.1.0-universal.apk", match.first.getString("name"))
    }

    @Test
    fun testResolveBestApkAsset_ignoresNonApkAssets() {
        val assets = listOf(
            JSONObject("""{"name":"SHA256SUMS.txt", "size": 200, "browser_download_url": "https://example.com/sums.txt"}"""),
            JSONObject("""{"name":"release-notes.md", "size": 500, "browser_download_url": "https://example.com/notes.md"}""")
        )

        val supportedAbis = arrayOf("arm64-v8a")
        val match = AppUpdateManager.resolveBestApkAsset(assets, supportedAbis)

        assertEquals(null, match)
    }

    @Test
    fun testIsSameOrEquivalentVersion() {
        assertTrue(AppUpdateManager.isSameOrEquivalentVersion("1.2.0", "v1.2.0"))
        assertTrue(AppUpdateManager.isSameOrEquivalentVersion("1.2", "1.2.0"))
        assertTrue(AppUpdateManager.isSameOrEquivalentVersion("v1.2.0", "1.2.0"))
        assertTrue(AppUpdateManager.isSameOrEquivalentVersion("1.2.0-beta", "1.2.0"))
        assertFalse(AppUpdateManager.isSameOrEquivalentVersion("1.2.0", "1.2.1"))
        assertFalse(AppUpdateManager.isSameOrEquivalentVersion("1.2.0", "2.0.0"))
    }

    @Test
    fun testParseIso8601ToEpochMs() {
        val epochMs = AppUpdateManager.parseIso8601ToEpochMs("2026-10-22T12:00:00Z")
        assertTrue("Epoch ms should be positive", epochMs > 0L)
        // Verify 2026-10-22 timestamp falls within year 2026 range
        val expectedMin = 1767225600000L // 2026-01-01
        val expectedMax = 1798761600000L // 2027-01-01
        assertTrue(epochMs in expectedMin..expectedMax)
    }

    @Test
    fun testParseReleaseJson_detectsSameVersionRebuildWhenAssetIsNewer() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val updateManager = AppUpdateManager(context)

        // Remote release has the same tag "v1.2.0" as BuildConfig.VERSION_NAME "1.2.0"
        // But the asset was uploaded in late 2026, which is newer than local package install time (1000L)
        val releaseJson = JSONObject("""
            {
                "tag_name": "v1.2.0",
                "name": "LockIN Maintenance Rebuild",
                "body": "Fixed in-app update detection on same version tags.",
                "published_at": "2026-10-22T21:00:00Z",
                "assets": [
                    {
                        "id": 998877,
                        "name": "LockIN-v1.2.0-arm64-v8a.apk",
                        "size": 16500000,
                        "updated_at": "2026-10-22T21:10:00Z",
                        "browser_download_url": "https://github.com/Sourish25/LockIN/releases/download/v1.2.0/LockIN-v1.2.0-arm64-v8a.apk"
                    }
                ]
            }
        """.trimIndent())

        updateManager.parseReleaseJson(releaseJson, localInstallTimeOverrideMs = 1000L)

        val state = updateManager.updateState.value
        assertTrue("State should be UpdateAvailable for rebuild", state is UpdateState.UpdateAvailable)
        val release = (state as UpdateState.UpdateAvailable).release
        assertTrue("isSameVersionRebuild should be true", release.isSameVersionRebuild)
        assertEquals("v1.2.0", release.tagName)
        assertEquals("1.2.0", release.versionName)
        assertEquals(998877L, release.assetId)
        assertTrue(release.downloadUrl.contains("LockIN-v1.2.0-arm64-v8a.apk"))
    }

    @Test
    fun testParseReleaseJson_forceUpdateTriggersUpdateAvailable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val updateManager = AppUpdateManager(context)

        val releaseJson = JSONObject("""
            {
                "tag_name": "v1.2.0",
                "name": "LockIN Current Release",
                "body": "Existing release payload.",
                "published_at": "2020-01-01T00:00:00Z",
                "assets": [
                    {
                        "id": 112233,
                        "name": "LockIN-v1.2.0-arm64-v8a.apk",
                        "size": 16000000,
                        "updated_at": "2020-01-01T00:00:00Z",
                        "browser_download_url": "https://github.com/Sourish25/LockIN/releases/download/v1.2.0/LockIN-v1.2.0-arm64-v8a.apk"
                    }
                ]
            }
        """.trimIndent())

        // Without forceUpdate, installed time is newer (e.g. 2026) -> UpToDate
        val nowMs = AppUpdateManager.parseIso8601ToEpochMs("2026-10-22T21:00:00Z")
        updateManager.parseReleaseJson(releaseJson, forceUpdate = false, localInstallTimeOverrideMs = nowMs)
        val upToDateState = updateManager.updateState.value
        assertTrue("State should be UpToDate", upToDateState is UpdateState.UpToDate)
        assertNotNull((upToDateState as UpdateState.UpToDate).latestRelease)

        // With forceUpdate = true -> UpdateAvailable
        updateManager.parseReleaseJson(releaseJson, forceUpdate = true, localInstallTimeOverrideMs = nowMs)
        val forcedState = updateManager.updateState.value
        assertTrue("State should be UpdateAvailable with forceUpdate", forcedState is UpdateState.UpdateAvailable)
    }

    @Test
    fun testCalculateSha256_computesExactDigest() {
        val tempFile = File.createTempFile("test_sha256", ".tmp")
        try {
            tempFile.writeText("LockIN Expressive Android Updater Test Payload")
            val hash = AppUpdateManager.calculateSha256(tempFile)
            assertNotNull(hash)
            assertEquals(64, hash.length)
            assertTrue(hash.matches(Regex("^[a-f0-9]{64}$")))
        } finally {
            tempFile.delete()
        }
    }
}
