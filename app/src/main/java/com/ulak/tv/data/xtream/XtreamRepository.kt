package com.ulak.tv.data.xtream

import com.ulak.tv.data.model.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object XtreamRepository {

    data class StreamProbeResult(
        val label: String,
        val url: String,
        val code: Int?,
        val contentType: String?,
        val finalUrl: String?,
        val ok: Boolean,
        val note: String
    )

    suspend fun probeChannel(channel: Channel): Result<List<StreamProbeResult>> = withContext(Dispatchers.IO) {
        runCatching {
            val urls = (listOf(channel.streamUrl) + channel.alternateStreamUrls).distinct()
            urls.mapIndexed { index, url ->
                val label = when {
                    url.contains(".m3u8", true) && url.contains("/live/") -> "HLS /live"
                    url.contains(".m3u8", true) -> "HLS rewrite"
                    url.contains(".ts", true) && url.contains("/live/") -> "TS /live"
                    url.contains(".ts", true) -> "TS rewrite"
                    index == 0 -> "direct_source"
                    else -> "Kaynak ${index + 1}"
                }
                probeUrl(label, url)
            }
        }
    }

    private fun probeUrl(label: String, url: String): StreamProbeResult {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "ULAK/0.2.9 AndroidTV")
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Connection", "keep-alive")
                setRequestProperty("Range", "bytes=0-2047")
            }
            val code = connection.responseCode
            val type = connection.contentType
            val finalUrl = connection.url?.toString()
            val ok = code in 200..299
            if (ok) {
                runCatching {
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(512)
                        input.read(buffer)
                    }
                }
            }
            StreamProbeResult(
                label = label, url = url, code = code, contentType = type, finalUrl = finalUrl, ok = ok,
                note = when (code) {
                    200, 206 -> "Sunucu yayını kabul ediyor"
                    401 -> "Yetkilendirme reddedildi"
                    403 -> "Sunucu erişimi reddetti"
                    404 -> "Yayın adresi bulunamadı"
                    in 500..599 -> "Sunucu geçici hata verdi"
                    else -> "HTTP $code"
                }
            )
        } catch (t: Throwable) {
            StreamProbeResult(label, url, null, null, null, false, t.message ?: t.javaClass.simpleName)
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun loginAndLoadLive(
        serverUrl: String,
        username: String,
        password: String
    ): Result<List<Channel>> = withContext(Dispatchers.IO) {
        runCatching {
            val server = normalizeServer(serverUrl)
            require(username.isNotBlank()) { "Kullanıcı adı boş olamaz." }
            require(password.isNotBlank()) { "Parola boş olamaz." }

            val userJson = getJsonObject(
                apiUrl(server, username, password)
            )
            val userInfo = userJson.optJSONObject("user_info")
                ?: error("Sunucu geçerli bir Xtream kullanıcı yanıtı vermedi.")

            val authenticated = when (val auth = userInfo.opt("auth")) {
                is Number -> auth.toInt() == 1
                is String -> auth == "1"
                else -> false
            }
            if (!authenticated) error("Kullanıcı adı veya parola doğrulanamadı.")

            val status = userInfo.optString("status")
            if (status.isNotBlank() && !status.equals("Active", ignoreCase = true)) {
                error("Hesap durumu: $status")
            }

            val allowedFormats = userInfo.optJSONArray("allowed_output_formats")
                ?.toStringList().orEmpty()
            val extension = if (allowedFormats.any { it.equals("m3u8", true) }) "m3u8" else "ts"

            val categories = getJsonArray(
                apiUrl(server, username, password, "get_live_categories")
            )
            val categoryNames = buildMap<String, String> {
                for (i in 0 until categories.length()) {
                    val item = categories.optJSONObject(i) ?: continue
                    val id = item.optString("category_id")
                    if (id.isNotBlank()) put(id, item.optString("category_name", "Diğer"))
                }
            }
            val categoryOrders = buildMap<String, Int> {
                for (i in 0 until categories.length()) {
                    val item = categories.optJSONObject(i) ?: continue
                    val id = item.optString("category_id")
                    if (id.isNotBlank()) put(id, i + 1)
                }
            }

            val streams = getJsonArray(
                apiUrl(server, username, password, "get_live_streams")
            )

            val channels = ArrayList<Channel>(streams.length())
            for (i in 0 until streams.length()) {
                val item = streams.optJSONObject(i) ?: continue
                val streamId = item.optLong("stream_id", -1L)
                if (streamId <= 0) continue

                val categoryId = item.optString("category_id")
                val directSource = item.optString("direct_source").takeIf {
                    it.startsWith("http://") || it.startsWith("https://")
                }

                val tsUrl = buildLiveUrl(
                    server = server,
                    username = username,
                    password = password,
                    streamId = streamId,
                    extension = "ts",
                    livePrefix = true
                )
                val hlsUrl = buildLiveUrl(
                    server = server,
                    username = username,
                    password = password,
                    streamId = streamId,
                    extension = "m3u8",
                    livePrefix = true
                )
                // XUI/Xtream installations can also expose the rewrite form
                // /USERNAME/PASSWORD/STREAM_ID.ext instead of /live/... .
                // Keep both forms because a server may return 404 for one and accept the other.
                val tsRewriteUrl = buildLiveUrl(
                    server = server,
                    username = username,
                    password = password,
                    streamId = streamId,
                    extension = "ts",
                    livePrefix = false
                )
                val hlsRewriteUrl = buildLiveUrl(
                    server = server,
                    username = username,
                    password = password,
                    streamId = streamId,
                    extension = "m3u8",
                    livePrefix = false
                )

                // Prefer the account-advertised HLS form when available, because it
                // rendered correctly on channels that regressed when v0.1.5 forced TS.
                // Keep both /live and rewrite URL forms as fallbacks.
                val candidates = buildList {
                    if (directSource != null) add(directSource)
                    if (extension.equals("m3u8", true)) {
                        add(hlsUrl); add(hlsRewriteUrl); add(tsUrl); add(tsRewriteUrl)
                    } else {
                        add(tsUrl); add(tsRewriteUrl); add(hlsUrl); add(hlsRewriteUrl)
                    }
                }.distinct()

                channels += Channel(
                    name = item.optString("name", "İsimsiz Kanal"),
                    streamUrl = candidates.first(),
                    alternateStreamUrls = candidates.drop(1),
                    logoUrl = item.optString("stream_icon").takeIf { it.isNotBlank() },
                    group = categoryNames[categoryId] ?: "Diğer",
                    tvgId = item.optString("epg_channel_id").takeIf { it.isNotBlank() },
                    serverOrder = i + 1,
                    xtreamNum = item.optInt("num", -1).takeIf { it >= 0 },
                    streamId = streamId,
                    categoryId = categoryId.takeIf { it.isNotBlank() },
                    categoryOrder = categoryOrders[categoryId]
                )
            }

            if (channels.isEmpty()) error("Hesap doğrulandı ancak canlı kanal bulunamadı.")
            channels
        }
    }

    private fun normalizeServer(raw: String): String {
        var server = raw.trim().trimEnd('/')
        require(server.isNotBlank()) { "Sunucu URL boş olamaz." }
        if (!server.startsWith("http://") && !server.startsWith("https://")) {
            server = "http://$server"
        }
        return server
    }

    private fun apiUrl(
        server: String,
        username: String,
        password: String,
        action: String? = null
    ): String {
        val query = buildString {
            append("username=").append(enc(username))
            append("&password=").append(enc(password))
            if (action != null) append("&action=").append(enc(action))
        }
        return "$server/player_api.php?$query"
    }

    private fun buildLiveUrl(
        server: String,
        username: String,
        password: String,
        streamId: Long,
        extension: String,
        livePrefix: Boolean
    ): String {
        val prefix = if (livePrefix) "/live" else ""
        return "$server$prefix/${pathEnc(username)}/${pathEnc(password)}/$streamId.$extension"
    }

    private fun getJsonObject(url: String): JSONObject = JSONObject(getText(url))
    private fun getJsonArray(url: String): JSONArray = JSONArray(getText(url))

    private fun getText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "ULAK/0.2.9 AndroidTV")
            setRequestProperty("Accept", "application/json, */*")
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Sunucu HTTP $code yanıtı verdi.")
            if (text.isBlank()) error("Sunucu boş yanıt verdi.")
            text
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONArray.toStringList(): List<String> = buildList {
        for (i in 0 until length()) add(optString(i))
    }

    private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
    private fun pathEnc(value: String): String = enc(value).replace("+", "%20")
}
