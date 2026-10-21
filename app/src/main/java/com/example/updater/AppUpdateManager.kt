package com.example.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class AppReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseTitle: String,
    val changelog: String,
    val publishedAt: String,
    val downloadUrl: String,
    val targetAbi: String,
    val assetSize: Long,
    val sha256Checksum: String? = null
)

sealed interface UpdateState {
    object Idle : UpdateState
    object Checking : UpdateState
    data class UpdateAvailable(val release: AppReleaseInfo) : UpdateState
    object UpToDate : UpdateState
    data class Downloading(
        val release: AppReleaseInfo,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val progress: Float
    ) : UpdateState
    data class Verifying(val release: AppReleaseInfo) : UpdateState
    data class ReadyToInstall(val release: AppReleaseInfo, val apkFile: File) : UpdateState
    data class PermissionRequired(val release: AppReleaseInfo, val apkFile: File) : UpdateState
    data class ConflictGuidance(val release: AppReleaseInfo, val message: String) : UpdateState
    data class Error(val message: String, val canRetry: Boolean = true) : UpdateState
}

class AppUpdateManager(private val context: Context) {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    companion object {
        const val DEFAULT_REPO = "joymaity25/lockin"
        const val GITHUB_API_URL = "https://api.github.com/repos"

        fun isNewerVersion(currentVersion: String, remoteVersion: String): Boolean {
            val currClean = currentVersion.trimStart('v').split("-")[0]
            val remClean = remoteVersion.trimStart('v').split("-")[0]

            val currParts = currClean.split(".").mapNotNull { it.toIntOrNull() }
            val remParts = remClean.split(".").mapNotNull { it.toIntOrNull() }

            val maxLen = maxOf(currParts.size, remParts.size)
            for (i in 0 until maxLen) {
                val c = currParts.getOrElse(i) { 0 }
                val r = remParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
            return false
        }

        fun resolveBestApkAsset(
            assets: List<JSONObject>,
            supportedAbis: Array<String>
        ): Pair<JSONObject, String>? {
            val apkAssets = assets.filter {
                val name = it.optString("name", "").lowercase()
                name.endsWith(".apk")
            }
            if (apkAssets.isEmpty()) return null

            // Prioritize primary device ABI (e.g. arm64-v8a)
            for (abi in supportedAbis) {
                val normalizedAbi = abi.lowercase()
                val match = apkAssets.firstOrNull {
                    val name = it.optString("name", "").lowercase()
                    name.contains(normalizedAbi)
                }
                if (match != null) {
                    return Pair(match, abi)
                }
            }

            // Fallback to universal APK
            val universal = apkAssets.firstOrNull {
                it.optString("name", "").lowercase().contains("universal")
            }
            if (universal != null) {
                return Pair(universal, "universal")
            }

            // Fallback to first available APK
            return Pair(apkAssets.first(), "generic")
        }

        fun calculateSha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { inputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

    suspend fun checkForUpdates(customRepo: String? = null) {
        _updateState.value = UpdateState.Checking
        withContext(Dispatchers.IO) {
            try {
                val repo = customRepo ?: DEFAULT_REPO
                val url = URL("$GITHUB_API_URL/$repo/releases/latest")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("User-Agent", "LockIN-Updater/${BuildConfig.VERSION_NAME}")
                }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    // Try fetching all releases if latest is not tagged as latest
                    fetchFirstAvailableRelease(repo)
                    return@withContext
                }

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    _updateState.value = UpdateState.Error("Server returned code $responseCode")
                    return@withContext
                }

                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                parseReleaseJson(JSONObject(responseText))
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error(
                    e.localizedMessage ?: "Failed to check for updates. Check internet connection."
                )
            }
        }
    }

    private suspend fun fetchFirstAvailableRelease(repo: String) {
        try {
            val url = URL("$GITHUB_API_URL/$repo/releases")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "LockIN-Updater/${BuildConfig.VERSION_NAME}")
            }
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val releases = JSONArray(responseText)
                if (releases.length() > 0) {
                    parseReleaseJson(releases.getJSONObject(0))
                    return
                }
            }
            _updateState.value = UpdateState.UpToDate
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error("No releases found on GitHub: ${e.message}")
        }
    }

    private fun parseReleaseJson(releaseObj: JSONObject) {
        val tagName = releaseObj.optString("tag_name", "")
        val remoteVersion = tagName.trimStart('v')
        val currentVersion = BuildConfig.VERSION_NAME

        val isNewer = isNewerVersion(currentVersion, remoteVersion)
        if (!isNewer && tagName.isNotBlank()) {
            _updateState.value = UpdateState.UpToDate
            return
        }

        val assetsJson = releaseObj.optJSONArray("assets") ?: JSONArray()
        val assetsList = (0 until assetsJson.length()).map { assetsJson.getJSONObject(it) }

        val bestApk = resolveBestApkAsset(assetsList, Build.SUPPORTED_ABIS)
        if (bestApk == null) {
            _updateState.value = UpdateState.Error("No compatible APK found in release $tagName")
            return
        }

        val apkAsset = bestApk.first
        val targetAbi = bestApk.second
        val downloadUrl = apkAsset.optString("browser_download_url")
        val assetSize = apkAsset.optLong("size", 0L)
        val releaseTitle = releaseObj.optString("name", "Version $tagName")
        val changelog = releaseObj.optString("body", "Bug fixes and performance improvements.")
        val publishedAt = releaseObj.optString("published_at", "")

        val releaseInfo = AppReleaseInfo(
            tagName = tagName,
            versionName = remoteVersion,
            releaseTitle = releaseTitle,
            changelog = changelog,
            publishedAt = publishedAt,
            downloadUrl = downloadUrl,
            targetAbi = targetAbi,
            assetSize = assetSize
        )

        _updateState.value = UpdateState.UpdateAvailable(releaseInfo)
    }

    suspend fun startDownload(release: AppReleaseInfo) {
        _updateState.value = UpdateState.Downloading(release, 0L, release.assetSize, 0f)

        withContext(Dispatchers.IO) {
            val updateDir = File(context.cacheDir, "updates")
            if (!updateDir.exists()) updateDir.mkdirs()

            val targetFile = File(updateDir, "LockIN-${release.tagName}-${release.targetAbi}.apk")
            if (targetFile.exists()) targetFile.delete()

            try {
                var currentUrl = release.downloadUrl
                var redirects = 0
                var connection: HttpURLConnection? = null

                // Follow GitHub -> AWS S3 redirect safely
                while (redirects < 5) {
                    val url = URL(currentUrl)
                    connection = (url.openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = false
                        connectTimeout = 15000
                        readTimeout = 20000
                        setRequestProperty("User-Agent", "LockIN-Updater/${BuildConfig.VERSION_NAME}")
                    }
                    val code = connection.responseCode
                    if (code in listOf(HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP, 307, 308)) {
                        currentUrl = connection.getHeaderField("Location")
                        redirects++
                    } else if (code == HttpURLConnection.HTTP_OK) {
                        break
                    } else {
                        throw IllegalStateException("Download server returned HTTP $code")
                    }
                }

                val conn = connection ?: throw IllegalStateException("Failed to open connection")
                val totalLength = if (conn.contentLengthLong > 0) conn.contentLengthLong else release.assetSize

                conn.inputStream.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(16384)
                        var bytesRead: Int
                        var totalDownloaded = 0L
                        var lastProgressUpdate = System.currentTimeMillis()

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalDownloaded += bytesRead

                            val now = System.currentTimeMillis()
                            if (now - lastProgressUpdate > 100 || totalDownloaded == totalLength) {
                                val progress = if (totalLength > 0) {
                                    (totalDownloaded.toFloat() / totalLength.toFloat()).coerceIn(0f, 1f)
                                } else 0f
                                _updateState.value = UpdateState.Downloading(
                                    release,
                                    totalDownloaded,
                                    totalLength,
                                    progress
                                )
                                lastProgressUpdate = now
                            }
                        }
                    }
                }

                _updateState.value = UpdateState.Verifying(release)

                // Guard 1: Verify minimum file size and ZIP/APK magic header (PK\x03\x04)
                if (targetFile.length() < 4096 || !isValidApkArchive(targetFile)) {
                    targetFile.delete()
                    throw IllegalStateException("Downloaded APK file is incomplete or corrupted")
                }

                // Guard 2: Verify SHA-256 checksum if provided
                val computedSha = calculateSha256(targetFile)
                if (!release.sha256Checksum.isNullOrBlank() &&
                    !computedSha.equals(release.sha256Checksum.trim(), ignoreCase = true)
                ) {
                    targetFile.delete()
                    throw IllegalStateException("SHA-256 checksum mismatch for downloaded APK")
                }

                _updateState.value = UpdateState.ReadyToInstall(
                    release.copy(sha256Checksum = computedSha),
                    targetFile
                )
            } catch (e: Exception) {
                if (targetFile.exists()) targetFile.delete()
                _updateState.value = UpdateState.Error("Download failed: ${e.message}")
            }
        }
    }

    fun isValidApkArchive(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        return try {
            val header = ByteArray(4)
            file.inputStream().use { it.read(header) }
            // Standard APK / ZIP magic number: 0x50 ('P'), 0x4B ('K'), 0x03, 0x04
            header[0] == 0x50.toByte() &&
                header[1] == 0x4B.toByte() &&
                header[2] == 0x03.toByte() &&
                header[3] == 0x04.toByte()
        } catch (_: Exception) {
            false
        }
    }

    fun promptInstall(apkFile: File, release: AppReleaseInfo) {
        if (!apkFile.exists() || !isValidApkArchive(apkFile)) {
            _updateState.value = UpdateState.Error("APK file missing or corrupted, please download again")
            return
        }

        // On Android 8.0+ verify package install permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                _updateState.value = UpdateState.PermissionRequired(release, apkFile)
                return
            }
        }

        executeInstallIntent(apkFile)
    }

    fun onAppResumed() {
        val current = _updateState.value
        if (current is UpdateState.PermissionRequired) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                context.packageManager.canRequestPackageInstalls()
            ) {
                _updateState.value = UpdateState.ReadyToInstall(current.release, current.apkFile)
                executeInstallIntent(current.apkFile)
            }
        }
    }

    fun executeInstallIntent(apkFile: File) {
        try {
            com.example.service.ChapterLockEnforcer.allowSystemSettingsTemporarily()
            com.example.service.LockWindowOverlayManager.dismiss()

            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error("Installation trigger failed: ${e.message}")
        }
    }

    fun buildUnknownAppSourcesSettingsIntent(): Intent {
        com.example.service.ChapterLockEnforcer.allowSystemSettingsTemporarily()
        com.example.service.LockWindowOverlayManager.dismiss()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
    }

    fun dismiss() {
        _updateState.value = UpdateState.Idle
    }
}
