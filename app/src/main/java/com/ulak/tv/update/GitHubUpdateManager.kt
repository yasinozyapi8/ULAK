package com.ulak.tv.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object GitHubUpdateManager {
    private const val OWNER = "yasinozyapi8"
    private const val REPO = "ULAK"
    private const val API = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    private const val USER_AGENT = "ULAK-AndroidTV-Updater"

    data class ReleaseInfo(
        val version: String,
        val title: String,
        val notes: String,
        val apkUrl: String,
        val apkName: String,
        val htmlUrl: String
    )

    suspend fun checkLatest(currentVersion: String): Result<ReleaseInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", USER_AGENT)
                instanceFollowRedirects = true
            }
            try {
                val code = connection.responseCode
                val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

                if (code == 404) {
                    error("GitHub'da henüz bir Release bulunamadı.")
                }
                if (code !in 200..299) error("GitHub HTTP $code yanıtı verdi.")

                val json = JSONObject(body)
                val tag = json.optString("tag_name").trim().removePrefix("v").removePrefix("V")
                if (tag.isBlank()) error("Release sürüm etiketi okunamadı.")

                val assets = json.optJSONArray("assets")
                var apkUrl: String? = null
                var apkName: String? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val item = assets.optJSONObject(i) ?: continue
                        val name = item.optString("name")
                        val url = item.optString("browser_download_url")
                        if (name.endsWith(".apk", ignoreCase = true) && url.startsWith("https://")) {
                            apkName = name
                            apkUrl = url
                            break
                        }
                    }
                }
                if (!isNewer(tag, currentVersion)) return@runCatching null
                if (apkUrl == null || apkName == null) {
                    error("Yeni Release bulundu ancak içinde APK dosyası yok.")
                }

                ReleaseInfo(
                    version = tag,
                    title = json.optString("name").ifBlank { "ULAK v$tag" },
                    notes = json.optString("body"),
                    apkUrl = apkUrl,
                    apkName = apkName,
                    htmlUrl = json.optString("html_url")
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    suspend fun downloadApk(
        context: Context,
        release: ReleaseInfo,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.externalCacheDir ?: context.cacheDir, "updates").apply { mkdirs() }
            val target = File(dir, release.apkName.replace(Regex("[^A-Za-z0-9._-]"), "_"))
            val connection = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/octet-stream")
                setRequestProperty("User-Agent", USER_AGENT)
                instanceFollowRedirects = true
            }
            try {
                val code = connection.responseCode
                if (code !in 200..299) error("APK indirilemedi: HTTP $code")
                val total = connection.contentLengthLong
                connection.inputStream.use { input ->
                    target.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var copied = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            copied += count
                            if (total > 0) onProgress(((copied * 100L) / total).toInt().coerceIn(0, 100))
                        }
                    }
                }
                if (!target.exists() || target.length() == 0L) error("İndirilen APK boş.")
                onProgress(100)
                target
            } finally {
                connection.disconnect()
            }
        }
    }

    fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun launchInstaller(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    private fun isNewer(remote: String, current: String): Boolean {
        fun parts(value: String): List<Int> = value.trim().removePrefix("v").split('.', '-', '_')
            .map { token -> token.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
        val a = parts(remote)
        val b = parts(current)
        val size = maxOf(a.size, b.size)
        for (i in 0 until size) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
