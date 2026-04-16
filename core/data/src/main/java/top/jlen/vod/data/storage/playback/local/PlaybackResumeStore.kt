package top.jlen.vod.data

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.JsonParser

class PlaybackResumeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun load(vodId: String): PlaybackResumeRecord? =
        loadBucket(vodId)?.latestOrLastSourceRecord()

    fun loadBucket(vodId: String): PlaybackResumeBucket? {
        val normalizedVodId = vodId.trim()
        if (normalizedVodId.isBlank()) return null
        val raw = prefs.getString(storageKey(normalizedVodId), null)
            ?.takeIf(String::isNotBlank)
            ?: return null
        return parseBucket(raw, normalizedVodId)
    }

    fun loadForSource(
        vodId: String,
        sourceName: String,
        sourceIndex: Int = -1
    ): PlaybackResumeRecord? = loadBucket(vodId)?.recordForSource(sourceName, sourceIndex)

    fun save(record: PlaybackResumeRecord) {
        val normalizedVodId = record.vodId.trim()
        if (normalizedVodId.isBlank()) return
        val normalizedRecord = normalizeRecord(record.copy(vodId = normalizedVodId))
        val existingBucket = loadBucket(normalizedVodId)
        val updatedRecords = (
            existingBucket
                ?.records
                .orEmpty()
                .filterNot { existing -> existing.sourceKey.equals(normalizedRecord.sourceKey, ignoreCase = true) } +
                normalizedRecord
            )
            .sortedByDescending { it.updatedAt }
        prefs.edit {
            putString(
                storageKey(normalizedVodId),
                gson.toJson(
                    PlaybackResumeBucket(
                        vodId = normalizedVodId,
                        lastSourceKey = normalizedRecord.sourceKey,
                        records = updatedRecords
                    )
                )
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

    private fun parseBucket(raw: String, normalizedVodId: String): PlaybackResumeBucket? {
        val root = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()
        return if (root?.has("records") == true) {
            runCatching {
                gson.fromJson(raw, PlaybackResumeBucket::class.java)
            }.getOrNull()
                ?.let { bucket ->
                    val normalizedRecords = bucket.records
                        .map { record -> normalizeRecord(record.copy(vodId = normalizedVodId)) }
                        .distinctBy { it.sourceKey }
                    PlaybackResumeBucket(
                        vodId = normalizedVodId,
                        lastSourceKey = bucket.lastSourceKey
                            .trim()
                            .ifBlank { normalizedRecords.firstOrNull()?.sourceKey.orEmpty() },
                        records = normalizedRecords
                    )
                }
                ?.takeIf { bucket -> bucket.records.isNotEmpty() }
        } else {
            runCatching { gson.fromJson(raw, PlaybackResumeRecord::class.java) }.getOrNull()
                ?.let { legacyRecord ->
                    val normalizedRecord = normalizeRecord(legacyRecord.copy(vodId = normalizedVodId))
                    PlaybackResumeBucket(
                        vodId = normalizedVodId,
                        lastSourceKey = normalizedRecord.sourceKey,
                        records = listOf(normalizedRecord)
                    )
                }
        }
    }

    private fun normalizeRecord(record: PlaybackResumeRecord): PlaybackResumeRecord {
        val normalizedSourceName = record.sourceName.trim()
        return record.copy(
            vodId = record.vodId.trim(),
            sourceKey = normalizePlaybackSourceKey(normalizedSourceName, record.sourceIndex),
            sourceName = normalizedSourceName,
            sourceIndex = record.sourceIndex.coerceAtLeast(0),
            episodeIndex = record.episodeIndex.coerceAtLeast(0),
            positionMs = record.positionMs.coerceAtLeast(0L),
            speed = record.speed.coerceIn(0.5f, 3f)
        )
    }
}
