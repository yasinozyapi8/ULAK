package com.ulak.tv.data.m3u

import com.ulak.tv.data.model.Channel

/** Tek geçişli M3U ayrıştırıcı. Büyük listelerde gereksiz liste kopyalamaz. */
object M3uParser {
    fun parse(content: String): List<Channel> {
        val result = ArrayList<Channel>()
        var pendingInfo: String? = null

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach

            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> pendingInfo = line
                line.startsWith("#") -> Unit
                pendingInfo != null -> {
                    val info = pendingInfo!!
                    val name = info.substringAfterLast(',').trim().ifEmpty { "İsimsiz Kanal" }
                    result += Channel(
                        name = name,
                        streamUrl = line,
                        logoUrl = attr(info, "tvg-logo"),
                        group = attr(info, "group-title")?.ifBlank { "Diğer" } ?: "Diğer",
                        tvgId = attr(info, "tvg-id")
                    )
                    pendingInfo = null
                }
            }
        }

        return result
    }

    private fun attr(text: String, key: String): String? =
        Regex("""$key\s*=\s*[\"']([^\"']*)[\"']""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.getOrNull(1)
}
