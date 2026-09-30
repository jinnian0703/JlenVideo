package top.jlen.vod.data

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class PlaybackResumeStoreTest {
    @Test
    fun rejectsCorruptBucketsAndNormalizesNullRecordFields() {
        val prefs = InMemoryPreferences()
        val store = PlaybackResumeStore(prefs)
        listOf("null", "[]", "false", "{broken", "{\"records\":null}", "{\"records\":3}", "{\"records\":[null,false]}")
            .forEach { raw ->
                prefs.edit().putString("resume::video", raw).apply()
                assertNull(raw, store.loadBucket("video"))
            }
        prefs.edit().putString(
            "resume::video",
            """{"lastSourceKey":null,"records":[null,false,{"positionMs":"broken"},{"vodId":null,"sourceKey":null,"sourceName":null,"sourceIndex":-1,"episodeIndex":-2,"positionMs":-3,"speed":"NaN","updatedAt":7}]}"""
        ).apply()
        val bucket = store.loadBucket("video")!!
        assertEquals(1, bucket.records.size)
        val record = bucket.records.single()
        assertEquals("video", record.vodId)
        assertEquals("", record.sourceName)
        assertEquals(0, record.sourceIndex)
        assertEquals(0, record.episodeIndex)
        assertEquals(0L, record.positionMs)
        assertEquals(1f, record.speed, 0f)
        assertEquals(record.sourceKey, bucket.lastSourceKey)
    }

    @Test
    fun retainsLegacyRecordWhenAddingAnotherSource() {
        val prefs = InMemoryPreferences()
        prefs.edit().putString(
            "resume::video", """{"sourceName":"旧线路","sourceIndex":0,"positionMs":12000,"updatedAt":10}"""
        ).apply()
        val store = PlaybackResumeStore(prefs)
        assertEquals(12000L, store.load("video")!!.positionMs)
        store.save(PlaybackResumeRecord(vodId = "video", sourceName = "新线路", sourceIndex = 1, updatedAt = 20))
        assertEquals(2, store.loadBucket("video")!!.records.size)
        assertEquals(12000L, store.loadForSource("video", "旧线路", 0)!!.positionMs)
        assertEquals("新线路", store.load("video")!!.sourceName)
    }

    @Test
    fun retainsOnlyTheMostRecentlyUpdatedFiveHundredVideos() {
        val prefs = InMemoryPreferences()
        prefs.edit().apply {
            (1..500).forEach { id -> putString("resume::$id", """{"sourceName":"线路","updatedAt":$id}""") }
            putString("unrelated", "keep")
            putString("resume::corrupt", "not json")
        }.commit()
        val store = PlaybackResumeStore(prefs)
        store.save(PlaybackResumeRecord(vodId = "1", sourceName = "线路", updatedAt = 1000))
        store.save(PlaybackResumeRecord(vodId = "501", sourceName = "线路", updatedAt = 1001))
        assertEquals(500, prefs.all.keys.count { it.startsWith("resume::") })
        assertNotNull(store.load("1"))
        assertNotNull(store.load("501"))
        assertNull(store.load("2"))
        assertFalse(prefs.contains("resume::corrupt"))
        assertEquals("keep", prefs.getString("unrelated", null))
    }

    @Test(timeout = 10000)
    fun concurrentInstancesDoNotLoseSourceRecords() {
        val prefs = InMemoryPreferences()
        val stores = listOf(PlaybackResumeStore(prefs), PlaybackResumeStore(prefs))
        val executor = Executors.newFixedThreadPool(4)
        try {
            (0 until 32).map { index ->
                executor.submit {
                    stores[index % 2].save(
                        PlaybackResumeRecord(
                            vodId = "video", sourceName = "线路 $index", sourceIndex = index,
                            positionMs = index * 1000L, updatedAt = index.toLong()
                        )
                    )
                }
            }.forEach { it.get(5, TimeUnit.SECONDS) }
            assertEquals(32, stores.first().loadBucket("video")!!.records.size)
        } finally {
            executor.shutdownNow()
        }
    }
}
