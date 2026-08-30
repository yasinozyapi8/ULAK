package com.ulak.tv.data.model

data class Channel(
    val name: String,
    val streamUrl: String,
    val alternateStreamUrls: List<String> = emptyList(),
    val logoUrl: String? = null,
    val group: String? = null,
    val tvgId: String? = null
)
