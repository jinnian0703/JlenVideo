package top.jlen.vod.data

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson

class HotSearchCacheStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun load(): HotSearchCacheSnapshot? =
        prefs.getString(KEY_SNAPSHOT, null)
            ?.takeIf(String::isNotBlank)
            ?.let { raw ->
                runCatching { gson.fromJson(raw, HotSearchCacheSnapshot::class.java) }.getOrNull()
            }
            ?.takeIf { snapshot ->
                snapshot.cachedAt > 0L && snapshot.groups.isNotEmpty()
            }

    fun save(snapshot: HotSearchCacheSnapshot) {
        if (snapshot.groups.isEmpty()) return
        prefs.edit {
            putString(KEY_SNAPSHOT, gson.toJson(snapshot))
        }
    }

    fun clear() {
        prefs.edit { remove(KEY_SNAPSHOT) }
    }

    companion object {
        private const val PREFS_NAME = "hot_search_cache_store"
        private const val KEY_SNAPSHOT = "snapshot"
    }
}
