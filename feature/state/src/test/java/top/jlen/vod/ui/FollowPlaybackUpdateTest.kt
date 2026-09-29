package top.jlen.vod.ui

import org.junit.Assert.*
import org.junit.Test
import top.jlen.vod.data.PlaybackResumeRecord

class FollowPlaybackUpdateTest {
    private val card = FollowUpItem(vodId = "1", title = "测试", latestEpisodeIndex = 9,
        watchedEpisodeIndex = 2, sourceName = "线路一", lastWatchedAtMillis = 120_000L)
    private fun record(id: String = "1", episode: Int = 2, time: Long = 125_000L, source: String = "线路一") =
        PlaybackResumeRecord(vodId = id, episodeIndex = episode, updatedAt = time, sourceName = source)

    @Test fun progressWithinMinuteDoesNotRebuildList() {
        val items = listOf(card)
        assertSame(items, followItemsWithPlayback(items, record()))
    }
    @Test fun unrelatedVideoDoesNotRebuildList() {
        val items = listOf(card)
        assertSame(items, followItemsWithPlayback(items, record(id = "other")))
    }
    @Test fun episodeChangeUpdatesImmediately() {
        val updated = followItemsWithPlayback(listOf(card), record(episode = 9)).single()
        assertEquals(9, updated.watchedEpisodeIndex)
        assertFalse(updated.hasUpdate)
        assertEquals("", updated.updateLabel)
    }
    @Test fun sourceChangeUpdatesImmediately() {
        assertEquals("线路二", followItemsWithPlayback(listOf(card), record(source = "线路二")).single().sourceName)
    }
    @Test fun minuteBoundaryUpdatesOnlyWatchedCard() {
        val other = card.copy(vodId = "2")
        val result = followItemsWithPlayback(listOf(card, other), record(time = 180_000L))
        assertSame(other, result.first { it.vodId == "2" })
        assertEquals(180_000L, result.first { it.vodId == "1" }.lastWatchedAtMillis)
        assertTrue(result.first { it.vodId == "1" }.hasUpdate)
    }
    @Test fun staleSnapshotIsIgnored() {
        val items = listOf(card)
        assertSame(items, followItemsWithPlayback(items, record(episode = 1, time = 119_000L)))
    }
    @Test fun blankSourcePreservesKnownName() {
        assertEquals("线路一", followItemsWithPlayback(listOf(card), record(episode = 3, source = "")).single().sourceName)
    }
    @Test fun refreshKeepsNewerProgressAndFreshMetadata() {
        val loaded = card.copy(title = "新标题", latestEpisodeIndex = 10)
        val current = card.copy(watchedEpisodeIndex = 10, lastWatchedAtMillis = 180_000L)
        val result = mergeNewerFollowPlayback(listOf(loaded), listOf(current)).single()
        assertEquals("新标题", result.title)
        assertEquals(10, result.watchedEpisodeIndex)
        assertFalse(result.hasUpdate)
    }
    @Test fun refreshDoesNotResurrectRemovedFavorite() {
        assertTrue(mergeNewerFollowPlayback(emptyList(), listOf(card)).isEmpty())
    }
    @Test fun newerLoadedRecordWins() {
        val loaded = card.copy(lastWatchedAtMillis = 240_000L)
        assertSame(loaded, mergeNewerFollowPlayback(listOf(loaded), listOf(card)).single())
    }
}
