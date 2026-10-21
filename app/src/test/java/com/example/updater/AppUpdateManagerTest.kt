package com.example.updater

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
