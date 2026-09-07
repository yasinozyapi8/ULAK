package com.ulak.tv.data

import android.content.Context
import com.ulak.tv.data.model.Channel

class FavoritesStore(context: Context) {
    private val prefs = context.getSharedPreferences("ulak_favorites", Context.MODE_PRIVATE)

    fun ids(): Set<String> = prefs.getStringSet("favorite_ids", emptySet())?.toSet().orEmpty()

    fun isFavorite(channel: Channel): Boolean = channel.favoriteKey() in ids()

    fun toggle(channel: Channel): Boolean {
        val current = ids().toMutableSet()
        val key = channel.favoriteKey()
        val nowFavorite = if (key in current) {
            current.remove(key)
            false
        } else {
            current.add(key)
            true
        }
        prefs.edit().putStringSet("favorite_ids", current).apply()
        return nowFavorite
    }

    fun removeMissing(channels: List<Channel>) {
        val valid = channels.map { it.favoriteKey() }.toSet()
        val cleaned = ids().filterTo(mutableSetOf()) { it in valid }
        prefs.edit().putStringSet("favorite_ids", cleaned).apply()
    }
}

fun Channel.favoriteKey(): String = streamId?.let { "xtream:$it" } ?: "url:$streamUrl"
