package com.ulak.tv.data.model

enum class ProfileType { M3U, XTREAM }

data class IptvProfile(
    val name: String,
    val type: ProfileType,
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val m3uUrl: String = ""
)
