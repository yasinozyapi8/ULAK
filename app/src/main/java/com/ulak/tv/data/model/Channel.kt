package com.ulak.tv.data.model

data class Channel(
    val name: String,
    val streamUrl: String,
    val alternateStreamUrls: List<String> = emptyList(),
    val logoUrl: String? = null,
    val group: String? = null,
    val tvgId: String? = null,
    // Xtream diagnostics. Defaults keep M3U profiles fully compatible.
    val serverOrder: Int? = null,
    val xtreamNum: Int? = null,
    val streamId: Long? = null,
    val categoryId: String? = null,
    val categoryOrder: Int? = null,
    // Raw Xtream source hints used by source diagnostics / smart playback.
    val directSource: String? = null,
    val xtreamStreamType: String? = null,
    val containerExtension: String? = null,
    val customSid: String? = null,
    val sourceMetadata: Map<String, String> = emptyMap()
)
