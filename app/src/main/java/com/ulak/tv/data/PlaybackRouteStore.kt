package com.ulak.tv.data

import android.content.Context
import com.ulak.tv.data.model.Channel

class PlaybackRouteStore(context: Context) {
    private val prefs = context.getSharedPreferences("ulak_working_routes", Context.MODE_PRIVATE)

    fun preferred(channel: Channel): String? = prefs.getString(channel.favoriteKey(), null)

    fun markWorking(channel: Channel, url: String) {
        if (url.isNotBlank()) prefs.edit().putString(channel.favoriteKey(), url).apply()
    }
}
