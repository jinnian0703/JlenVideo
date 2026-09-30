package top.jlen.vod.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.JsonParser

class PlaybackResumeStore internal constructor(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    private val gson = Gson()

    fun load(vodId: String): PlaybackResumeRecord? =
        loadBucket(vodId)?.latestOrLastSourceRecord()

    fun loadBucket(vodId: String): PlaybackResumeBucket? = synchronized(lock) {
        val normalizedVodId = vodId.trim()
        if (normalizedVodId.isBlank()) return@synchronized null
        val raw = runCatching { prefs.getString(storageKey(normalizedVodId), null) }.getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: return@synchronized null
        parseBucket(raw, normalizedVodId)
    }

    fun loadForSource(
        vodId: String,
        sourceName: String,
        sourceIndex: Int = -1
    ): PlaybackResumeRecord? = loadBucket(vodId)?.recordForSource(sourceName, sourceIndex)

    fun save(record: PlaybackResumeRecord) = synchronized(lock) {
        // 读、合并、淘汰和写入整体加锁，多个实例同时保存时也不会互相覆盖。
        val normalizedVodId = record.vodId.orEmpty().trim()
        if (normalizedVodId.isBlank()) return@synchronized
        val normalizedRecord = normalizeRecord(record, normalizedVodId)
        val updatedRecords = (
            loadBucket(normalizedVodId)?.records.orEmpty()
                .filterNot { it.sourceKey.equals(normalizedRecord.sourceKey, ignoreCase = true) } +
                normalizedRecord
            ).sortedByDescending { it.updatedAt }
        val key = storageKey(normalizedVodId)
        val json = gson.toJson(
            PlaybackResumeBucket(
                vodId = normalizedVodId,
                lastSourceKey = normalizedRecord.sourceKey,
                records = updatedRecords
            )
        )
        val evictedKeys = findEvictedKeys(key, json)
        prefs.edit {
            putString(key, json)
            evictedKeys.forEach { remove(it) }
        }
    }

    fun remove(vodId: String) = synchronized(lock) {
        val normalizedVodId = vodId.trim()
        if (normalizedVodId.isBlank()) return@synchronized
        prefs.edit { remove(storageKey(normalizedVodId)) }
    }

    private fun findEvictedKeys(updatedKey: String, updatedJson: String): List<String> {
        val entries = prefs.all.filterKeys { it.startsWith(KEY_PREFIX) }.toMutableMap()
        entries[updatedKey] = updatedJson
        if (entries.size <= MAX_ENTRIES) return emptyList()
        // 只在超出上限时解析时间戳，按最近更新保留 500 部影片；损坏的数据优先淘汰。
        return entries.entries
            .sortedBy { (key, value) ->
                (value as? String)?.let { parseBucket(it, key.removePrefix(KEY_PREFIX)) }
                    ?.records?.maxOfOrNull { it.updatedAt } ?: Long.MIN_VALUE
            }
            .take(entries.size - MAX_ENTRIES)
            .map { it.key }
    }

    // 包含标准化的整个解析都兜底，Gson 注入 null 或 JSON 损坏不会导致崩溃。
    private fun parseBucket(raw: String, normalizedVodId: String): PlaybackResumeBucket? = runCatching {
        val root = JsonParser.parseString(raw).safeObject() ?: return@runCatching null
        if (root.has("records")) {
            val normalizedRecords = root.get("records").safeArray()
                ?.mapNotNull { element ->
                    runCatching {
                        element.safeObject()
                            ?.let { gson.fromJson(it, PlaybackResumeRecord::class.java) }
                            ?.let { normalizeRecord(it, normalizedVodId) }
                    }.getOrNull()
                }
                .orEmpty()
                .distinctBy { it.sourceKey }
                .sortedByDescending { it.updatedAt }
            if (normalizedRecords.isEmpty()) return@runCatching null
            PlaybackResumeBucket(
                vodId = normalizedVodId,
                lastSourceKey = root.get("lastSourceKey").safeString().trim()
                    .ifBlank { normalizedRecords.first().sourceKey },
                records = normalizedRecords
            )
        } else {
            gson.fromJson(root, PlaybackResumeRecord::class.java)?.let { legacyRecord ->
                val normalizedRecord = normalizeRecord(legacyRecord, normalizedVodId)
                PlaybackResumeBucket(
                    vodId = normalizedVodId,
                    lastSourceKey = normalizedRecord.sourceKey,
                    records = listOf(normalizedRecord)
                )
            }
        }
    }.getOrNull()

    private fun normalizeRecord(record: PlaybackResumeRecord, normalizedVodId: String): PlaybackResumeRecord {
        val normalizedSourceName = record.sourceName.orEmpty().trim()
        val normalizedSourceIndex = record.sourceIndex.coerceAtLeast(0)
        // 所有字符串显式赋值，避免 copy 的默认参数再次传入 Gson 产生的 null。
        return record.copy(
            vodId = normalizedVodId,
            sourceKey = normalizePlaybackSourceKey(normalizedSourceName, normalizedSourceIndex),
            sourceName = normalizedSourceName,
            sourceIndex = normalizedSourceIndex,
            episodeIndex = record.episodeIndex.coerceAtLeast(0),
            positionMs = record.positionMs.coerceAtLeast(0L),
            speed = record.speed.takeIf { it.isFinite() }?.coerceIn(0.5f, 3f) ?: 1f,
            updatedAt = record.updatedAt.coerceAtLeast(0L)
        )
    }

    companion object {
        private const val PREFS_NAME = "playback_resume_store"
        private const val KEY_PREFIX = "resume::"
        private const val MAX_ENTRIES = 500
        private val lock = Any()

        private fun storageKey(vodId: String): String = "$KEY_PREFIX$vodId"
    }
}
