package com.hydroforce.pro.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.hydroforce.pro.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Details of a newer release found on GitHub. */
data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val apkSizeBytes: Long
)

/**
 * In-app self-updater. Checks the GitHub Releases of [BuildConfig.UPDATE_REPO], and if a release
 * with a higher versionCode exists, downloads its signed APK and launches the system installer.
 * The install happens in place (keeping user data) as long as every release is signed with the
 * same key.
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private val API_LATEST = "https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest"

    /** The versionCode of the currently installed app. */
    fun currentVersionCode(context: Context): Long {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        return PackageInfoCompat.getLongVersionCode(pi)
    }

    fun currentVersionName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"

    /**
     * @return a newer [UpdateInfo] if one exists, or null when already up to date.
     * @throws Exception on network/parse errors (caller shows the message).
     */
    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val json = httpGetString(API_LATEST)
        val obj = JSONObject(json)

        val tag = obj.optString("tag_name", "")
        val body = obj.optString("body", "")
        val releaseName = obj.optString("name", tag)

        val assets = obj.optJSONArray("assets") ?: return@withContext null
        var apkUrl: String? = null
        var apkName = ""
        var apkSize = 0L
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.optString("name", "")
            if (name.endsWith(".apk", ignoreCase = true)) {
                apkUrl = a.optString("browser_download_url", "")
                apkName = name
                apkSize = a.optLong("size", 0L)
                break
            }
        }
        if (apkUrl.isNullOrEmpty()) return@withContext null

        // versionCode: prefer a "versionCode=NN" marker in the release body, else parse the APK name.
        val latestCode = parseVersionCode(body) ?: parseVersionCode(apkName) ?: return@withContext null
        if (latestCode <= currentVersionCode(context)) return@withContext null

        val versionName = tag.removePrefix("v").ifEmpty { releaseName }
        val cleanNotes = body
            .replace(Regex("(?im)^\\s*versionCode\\s*[=:].*$"), "")
            .trim()

        UpdateInfo(
            versionCode = latestCode,
            versionName = versionName,
            notes = cleanNotes.ifEmpty { "تحسينات وإصلاحات عامة." },
            apkUrl = apkUrl,
            apkSizeBytes = apkSize
        )
    }

    private fun parseVersionCode(text: String): Long? {
        Regex("(?i)versionCode\\s*[=:]\\s*(\\d+)").find(text)?.let { return it.groupValues[1].toLongOrNull() }
        Regex("-(\\d+)\\.apk", RegexOption.IGNORE_CASE).find(text)?.let { return it.groupValues[1].toLongOrNull() }
        return null
    }

    /**
     * Downloads the APK to the app's external files dir and reports progress 0f..1f.
     * @return the downloaded file, ready to install.
     */
    suspend fun downloadApk(
        context: Context,
        info: UpdateInfo,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
        // Clean up older downloads to save space.
        dir.listFiles()?.forEach { it.delete() }
        val outFile = File(dir, "HydroForcePro-${info.versionCode}.apk")

        var conn = URL(info.apkUrl).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 20000
        conn.readTimeout = 60000
        conn.connect()

        // Manually follow a cross-protocol redirect if the platform didn't.
        if (conn.responseCode in 300..399) {
            val loc = conn.getHeaderField("Location")
            conn.disconnect()
            conn = URL(loc).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 20000
            conn.readTimeout = 60000
            conn.connect()
        }

        val total = if (info.apkSizeBytes > 0) info.apkSizeBytes else conn.contentLength.toLong()
        conn.inputStream.use { input ->
            outFile.outputStream().use { output ->
                val buffer = ByteArray(8 * 1024)
                var downloaded = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    if (total > 0) onProgress((downloaded.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        conn.disconnect()
        onProgress(1f)
        outFile
    }

    /** On Android O+ the user must allow "install unknown apps" for this app once. */
    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** Opens the system screen to grant "install unknown apps" for this app. */
    fun openUnknownSourcesSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Launches the system package installer for the downloaded APK. */
    fun installApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    private fun httpGetString(urlStr: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "HydroForcePro-Updater")
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw RuntimeException("GitHub API HTTP $code")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
