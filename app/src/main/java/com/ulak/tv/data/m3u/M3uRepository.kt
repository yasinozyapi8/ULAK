package com.ulak.tv.data.m3u

import com.ulak.tv.data.model.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object M3uRepository {
    suspend fun load(url: String): Result<List<Channel>> = withContext(Dispatchers.IO) {
        runCatching {
            require(url.startsWith("http://") || url.startsWith("https://")) {
                "Bağlantı http:// veya https:// ile başlamalı."
            }

            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", "ULAK/0.1.1 AndroidTV")
                setRequestProperty("Accept", "*/*")
            }

            try {
                val code = connection.responseCode
                if (code !in 200..299) error("Sunucu HTTP $code yanıtı verdi.")

                val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val channels = M3uParser.parse(text)
                if (channels.isEmpty()) error("Bu bağlantıda okunabilir kanal bulunamadı.")
                channels
            } finally {
                connection.disconnect()
            }
        }
    }
}
