package top.jlen.vod.ui

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import top.jlen.vod.data.UserCenterItem

internal class HistoryCacheStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun load(ownerKey: String): List<UserCenterItem> {
        val key = storageKey(ownerKey)
        if (key.isBlank()) return emptyList()
        val raw = prefs.getString(key, null)
            ?.takeIf(String::isNotBlank)
            ?: return emptyList()
        return runCatching {
            gson.fromJson(raw, HistoryCacheSnapshot::class.java)
        }.getOrNull()
            ?.items
            .orEmpty()
            .filter { item ->
                item.title.isNotBlank() &&
                    (item.vodId.isNotBlank() || item.playUrl.isNotBlank() || item.actionUrl.isNotBlank())
            }
    }

    fun save(ownerKey: String, items: List<UserCenterItem>) {
        val key = storageKey(ownerKey)
        if (key.isBlank()) return
        val safeItems = items.filter { item ->
            item.title.isNotBlank() &&
                (item.vodId.isNotBlank() || item.playUrl.isNotBlank() || item.actionUrl.isNotBlank())
        }
        if (safeItems.isEmpty()) {
            prefs.edit { remove(key) }
            return
        }
        prefs.edit {
            putString(
                key,
                gson.toJson(
                    HistoryCacheSnapshot(
                        cachedAt = System.currentTimeMillis(),
                        items = safeItems.take(MAX_ITEMS)
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
        ownerKey.trim().takeIf(String::isNotBlank)?.let { "history::$it" }.orEmpty()

    companion object {
        private const val PREFS_NAME = "history_cache_store"
        private const val MAX_ITEMS = 120
    }
}

private data class HistoryCacheSnapshot(
    val cachedAt: Long = 0L,
    val items: List<UserCenterItem> = emptyList()
)
