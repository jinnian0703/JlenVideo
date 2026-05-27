package top.jlen.vod.ui

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson

internal class FollowCacheStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun load(ownerKey: String): List<FollowUpItem> {
        val key = storageKey(ownerKey)
        if (key.isBlank()) return emptyList()
        val raw = prefs.getString(key, null)
            ?.takeIf(String::isNotBlank)
            ?: return emptyList()
        return runCatching {
            gson.fromJson(raw, FollowCacheSnapshot::class.java)
        }.getOrNull()
            ?.items
            .orEmpty()
            .filter { item -> item.vodId.isNotBlank() && item.title.isNotBlank() }
    }

    fun save(ownerKey: String, items: List<FollowUpItem>) {
        val key = storageKey(ownerKey)
        if (key.isBlank()) return
        if (items.isEmpty()) {
            prefs.edit { remove(key) }
            return
        }
        prefs.edit {
            putString(
                key,
                gson.toJson(
                    FollowCacheSnapshot(
                        cachedAt = System.currentTimeMillis(),
                        items = items
                    )
                )
            )
        }
    }

    fun clear(ownerKey: String) {
        val key = storageKey(ownerKey)
        if (key.isNotBlank()) {
            prefs.edit { remove(key) }
        }
    }

    private fun storageKey(ownerKey: String): String =
        ownerKey.trim().takeIf(String::isNotBlank)?.let { "follow::$it" }.orEmpty()

    companion object {
        private const val PREFS_NAME = "follow_cache_store"
    }
}

private data class FollowCacheSnapshot(
    val cachedAt: Long = 0L,
    val items: List<FollowUpItem> = emptyList()
)
