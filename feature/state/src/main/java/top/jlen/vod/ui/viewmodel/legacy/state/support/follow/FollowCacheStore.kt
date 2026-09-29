package top.jlen.vod.ui

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import java.util.concurrent.ConcurrentHashMap
import top.jlen.vod.performance.CoalescingTaskQueue

internal class FollowCacheStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val memory = ConcurrentHashMap<String, List<FollowUpItem>>()
    private val writes = CoalescingTaskQueue()

    fun load(ownerKey: String): List<FollowUpItem> {
        val key = storageKey(ownerKey)
        if (key.isBlank()) return emptyList()
        memory[key]?.let { return it }
        val raw = prefs.getString(key, null)
            ?.takeIf(String::isNotBlank)
            ?: return emptyList()
        val items = runCatching {
            gson.fromJson(raw, FollowCacheSnapshot::class.java)
        }.getOrNull()
            ?.items
            .orEmpty()
            .filter { item -> item.vodId.isNotBlank() && item.title.isNotBlank() }
        return memory.putIfAbsent(key, items) ?: items
    }

    fun save(ownerKey: String, items: List<FollowUpItem>) {
        val key = storageKey(ownerKey)
        if (key.isBlank()) return
        val snapshot = items.toList()
        memory[key] = snapshot
        writes.submit(key) {
            prefs.edit {
                if (snapshot.isEmpty()) remove(key)
                else putString(
                    key,
                    gson.toJson(
                        FollowCacheSnapshot(
                            cachedAt = System.currentTimeMillis(),
                            items = snapshot
                        )
                    )
                )
            }
        }
    }

    fun clear(ownerKey: String) {
        save(ownerKey, emptyList())
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
