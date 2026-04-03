package top.jlen.vod.data

import android.content.Context
import androidx.core.content.edit

class SearchHistoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<String> =
        prefs.getStringSet(KEY_HISTORY, emptySet())
            .orEmpty()
            .sorted()
            .mapNotNull { entry ->
                entry.substringAfter(ENTRY_SEPARATOR, "")
                    .trim()
                    .takeIf { it.isNotBlank() }
            }

    fun save(query: String) {
        val normalized = query.trim()
        if (normalized.isBlank()) return

        val updated = buildList {
            add(normalized)
            addAll(load().filterNot { it.equals(normalized, ignoreCase = true) })
        }.take(MAX_SIZE)

        prefs.edit {
            putStringSet(
                KEY_HISTORY,
                updated.mapIndexed { index, item ->
                    index.toString().padStart(2, '0') + ENTRY_SEPARATOR + item
                }.toSet()
            )
        }
    }

    fun clear() {
        prefs.edit { remove(KEY_HISTORY) }
    }

    companion object {
        private const val PREFS_NAME = "search_history_store"
        private const val KEY_HISTORY = "history"
        private const val MAX_SIZE = 15
        private const val ENTRY_SEPARATOR = "::"
    }
}
