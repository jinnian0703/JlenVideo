package top.jlen.vod.data

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson

class PlaybackResumeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun load(vodId: String): PlaybackResumeRecord? =
        prefs.getString(storageKey(vodId), null)
            ?.takeIf(String::isNotBlank)
            ?.let { raw ->
                runCatching { gson.fromJson(raw, PlaybackResumeRecord::class.java) }.getOrNull()
            }
            ?.takeIf { record -> record.vodId.isNotBlank() }

    fun save(record: PlaybackResumeRecord) {
        val normalizedVodId = record.vodId.trim()
        if (normalizedVodId.isBlank()) return
        prefs.edit {
            putString(
                storageKey(normalizedVodId),
                gson.toJson(record.copy(vodId = normalizedVodId))
            )
        }
    }

    fun remove(vodId: String) {
        val normalizedVodId = vodId.trim()
        if (normalizedVodId.isBlank()) return
        prefs.edit { remove(storageKey(normalizedVodId)) }
    }

    companion object {
        private const val PREFS_NAME = "playback_resume_store"

        private fun storageKey(vodId: String): String = "resume::$vodId"
    }
}
