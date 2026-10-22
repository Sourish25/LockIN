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
    val sha256Checksum: String? = null,
    val assetId: Long = 0L,
    val assetUpdatedAt: String = "",
    val assetUpdatedAtMs: Long = 0L,
    val isSameVersionRebuild: Boolean = false
)

sealed interface UpdateState {
    object Idle : UpdateState
    object Checking : UpdateState
    data class UpdateAvailable(val release: AppReleaseInfo) : UpdateState
    data class UpToDate(val latestRelease: AppReleaseInfo? = null) : UpdateState
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

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    companion object {
        const val DEFAULT_REPO = "Sourish25/LockIN"
        const val GITHUB_API_URL = "https://api.github.com/repos"
        const val PREFS_NAME = "lockin_updater_prefs"
        const val KEY_LAST_INSTALLED_ASSET_ID = "last_installed_asset_id"
        const val KEY_LAST_INSTALLED_ASSET_TIME = "last_installed_asset_time"

        fun isNewerVersion(currentVersion: String, remoteVersion: String): Boolean {
            val currClean = currentVersion.trimStart('v').trim()
            val remClean = remoteVersion.trimStart('v').trim()

            val currPre = currClean.contains("-")
            val remPre = remClean.contains("-")

            val currBase = currClean.split("-")[0]
            val remBase = remClean.split("-")[0]

            val currParts = currBase.split(".").mapNotNull { it.toIntOrNull() }
            val remParts = remBase.split(".").mapNotNull { it.toIntOrNull() }

            val maxLen = maxOf(currParts.size, remParts.size)
            for (i in 0 until maxLen) {
                val c = currParts.getOrElse(i) { 0 }
                val r = remParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }

            // If base numeric parts are equal:
            // e.g. current is pre-release "1.1.0-beta" and remote is stable "1.1.0" -> remote is newer!
            if (currPre && !remPre) return true
            // If current is stable "1.1.0" and remote is pre-release "1.1.0-beta" -> remote is not newer
            if (!currPre && remPre) return false

            return false
        }

        fun isSameOrEquivalentVersion(versionA: String, versionB: String): Boolean {
            val aClean = versionA.trimStart('v').trim().split("-")[0]
            val bClean = versionB.trimStart('v').trim().split("-")[0]
            val aParts = aClean.split(".").mapNotNull { it.toIntOrNull() }
            val bParts = bClean.split(".").mapNotNull { it.toIntOrNull() }
            val maxLen = maxOf(aParts.size, bParts.size)
            for (i in 0 until maxLen) {
                val a = aParts.getOrElse(i) { 0 }
                val b = bParts.getOrElse(i) { 0 }
                if (a != b) return false
            }
            return true
        }

        fun parseIso8601ToEpochMs(isoString: String): Long {
            if (isoString.isBlank()) return 0L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    return java.time.Instant.parse(isoString).toEpochMilli()
                } catch (_: Exception) {
                    try {
                        return java.time.OffsetDateTime.parse(isoString).toInstant().toEpochMilli()
                    } catch (_: Exception) {
                    }
                }
            }
            val patterns = arrayOf(
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ssXXX"
            )
            for (pat in patterns) {
                try {
                    val sdf = java.text.SimpleDateFormat(pat, java.util.Locale.US)
                    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    val date = sdf.parse(isoString)
                    if (date != null) return date.time
                } catch (_: Exception) {
                }
            }
            return 0L
        }

        fun getLocalAppInstallTime(context: Context): Long {
            return try {
                val pi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(
                        context.packageName,
                        android.content.pm.PackageManager.PackageInfoFlags.of(0)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }
                maxOf(pi.lastUpdateTime, pi.firstInstallTime)
            } catch (_: Exception) {
                0L
            }
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

        fun getInstalledApkSha256(context: Context): String? {
            return try {
                val path = context.applicationInfo.sourceDir
                if (path.isNullOrBlank()) return null
                val apkFile = File(path)
                if (apkFile.exists() && apkFile.canRead()) {
                    calculateSha256(apkFile)
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }

        fun getInstalledApkSize(context: Context): Long {
            return try {
                val path = context.applicationInfo.sourceDir
                if (path.isNullOrBlank()) return 0L
                File(path).length()
            } catch (_: Exception) {
                0L
            }
        }
    }

    suspend fun checkForUpdates(
        customRepo: String? = null,
        forceUpdate: Boolean = false,
        silent: Boolean = false
    ) {
        if (!silent) {
            _updateState.value = UpdateState.Checking
        }
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
                if (responseCode == 403 || responseCode == 429) {
                    if (!silent) {
                        val remaining = connection.getHeaderField("X-RateLimit-Remaining")
                        val msg = if (remaining == "0") {
                            "GitHub API rate limit reached. Please try again later."
                        } else {
                            "GitHub API request limit reached ($responseCode). Please try again later."
                        }
                        _updateState.value = UpdateState.Error(msg)
                    }
                    return@withContext
                }

                if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    fetchFirstAvailableRelease(repo, forceUpdate, silent = silent)
                    return@withContext
                }

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    if (!silent) {
                        _updateState.value = UpdateState.Error("Server returned code $responseCode")
                    }
                    return@withContext
                }

                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                parseReleaseJson(JSONObject(responseText), forceUpdate, silent = silent)
            } catch (e: Exception) {
                if (!silent) {
                    _updateState.value = UpdateState.Error(
                        e.localizedMessage ?: "Failed to check for updates. Check internet connection."
                    )
                }
            }
        }
    }

    private suspend fun fetchFirstAvailableRelease(
        repo: String,
        forceUpdate: Boolean = false,
        silent: Boolean = false
    ) {
        try {
            val url = URL("$GITHUB_API_URL/$repo/releases")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "LockIN-Updater/${BuildConfig.VERSION_NAME}")
            }
            if (connection.responseCode == 403 || connection.responseCode == 429) {
                if (!silent) {
                    _updateState.value = UpdateState.Error("GitHub API rate limit reached. Please try again later.")
                }
                return
            }
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val releases = JSONArray(responseText)
                if (releases.length() > 0) {
                    parseReleaseJson(releases.getJSONObject(0), forceUpdate, silent = silent)
                    return
                }
            }
            if (!silent) {
                _updateState.value = UpdateState.UpToDate()
            }
        } catch (e: Exception) {
            if (!silent) {
                _updateState.value = UpdateState.Error("No releases found on GitHub: ${e.message}")
            }
        }
    }

    fun parseReleaseJson(
        releaseObj: JSONObject,
        forceUpdate: Boolean = false,
        localInstallTimeOverrideMs: Long? = null,
        silent: Boolean = false
    ) {
        val tagName = releaseObj.optString("tag_name", "")
        val remoteVersion = tagName.trimStart('v')
        val currentVersion = BuildConfig.VERSION_NAME

        val assetsJson = releaseObj.optJSONArray("assets") ?: JSONArray()
        val assetsList = (0 until assetsJson.length()).map { assetsJson.getJSONObject(it) }

        val bestApk = resolveBestApkAsset(assetsList, Build.SUPPORTED_ABIS)
        if (bestApk == null) {
            if (isNewerVersion(currentVersion, remoteVersion)) {
                if (!silent) _updateState.value = UpdateState.Error("No compatible APK found in release $tagName")
            } else {
                if (!silent) _updateState.value = UpdateState.UpToDate()
            }
            return
        }

        val apkAsset = bestApk.first
        val targetAbi = bestApk.second
        val downloadUrl = apkAsset.optString("browser_download_url")
        val assetSize = apkAsset.optLong("size", 0L)
        val assetId = apkAsset.optLong("id", 0L)
        val assetUpdatedAt = apkAsset.optString(
            "updated_at",
            releaseObj.optString("published_at", releaseObj.optString("created_at", ""))
        )
        val assetUpdatedAtMs = parseIso8601ToEpochMs(assetUpdatedAt)
        val releaseTitle = releaseObj.optString("name", "Version $tagName")
        val changelog = releaseObj.optString("body", "Bug fixes and performance improvements.")
        val publishedAt = releaseObj.optString("published_at", "")

        val remoteDigest = apkAsset.optString("digest", "")
        val remoteSha256 = if (remoteDigest.startsWith("sha256:", ignoreCase = true)) {
            remoteDigest.substringAfter("sha256:").trim()
        } else {
            apkAsset.optString("sha256", "").ifBlank { null }
        }

        val localInstallTimeMs = localInstallTimeOverrideMs ?: getLocalAppInstallTime(context)
        val lastSavedAssetId = prefs.getLong(KEY_LAST_INSTALLED_ASSET_ID, -1L)

        val isSemverNewer = isNewerVersion(currentVersion, remoteVersion)
        val isSameVersion = isSameOrEquivalentVersion(currentVersion, remoteVersion)

        // 1. GitHub asset ID change compared to the last asset installed through the in-app updater
        val isAssetIdDifferent = assetId != 0L && lastSavedAssetId != -1L && assetId != lastSavedAssetId

        // 2. Asset was uploaded to GitHub after this app was compiled (BUILD_TIME_MILLIS)
        val isBuildTimeNewer = assetUpdatedAtMs > 0L &&
                try { BuildConfig.BUILD_TIME_MILLIS > 0L && assetUpdatedAtMs > (BuildConfig.BUILD_TIME_MILLIS + 10_000L) } catch (_: Throwable) { false }

        // 3. Asset was updated after installation time (+5s grace)
        val isInstallTimeNewer = assetUpdatedAtMs > 0L && localInstallTimeMs > 0L &&
                assetUpdatedAtMs > (localInstallTimeMs + 5_000L)

        val isAssetNewer = isAssetIdDifferent || isBuildTimeNewer || isInstallTimeNewer
        val isSameVersionRebuild = isSameVersion && (isAssetNewer || forceUpdate)

        val releaseInfo = AppReleaseInfo(
            tagName = tagName,
            versionName = remoteVersion,
            releaseTitle = releaseTitle,
            changelog = changelog,
            publishedAt = publishedAt,
            downloadUrl = downloadUrl,
            targetAbi = targetAbi,
            assetSize = assetSize,
            sha256Checksum = remoteSha256,
            assetId = assetId,
            assetUpdatedAt = assetUpdatedAt,
            assetUpdatedAtMs = assetUpdatedAtMs,
            isSameVersionRebuild = isSameVersionRebuild
        )

        if (isSemverNewer) {
            _updateState.value = UpdateState.UpdateAvailable(releaseInfo.copy(isSameVersionRebuild = false))
        } else if (isSameVersionRebuild || forceUpdate) {
            _updateState.value = UpdateState.UpdateAvailable(releaseInfo.copy(isSameVersionRebuild = true))
        } else {
            if (!silent) {
                _updateState.value = UpdateState.UpToDate(latestRelease = releaseInfo)
            } else {
                _updateState.value = UpdateState.Idle
            }
        }
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
                        val location = connection.getHeaderField("Location")
                            ?: throw IllegalStateException("Redirect missing Location header")
                        currentUrl = URL(url, location).toString()
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

        if (release.assetId != 0L) {
            prefs.edit()
                .putLong(KEY_LAST_INSTALLED_ASSET_ID, release.assetId)
                .putLong(KEY_LAST_INSTALLED_ASSET_TIME, release.assetUpdatedAtMs)
                .apply()
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
