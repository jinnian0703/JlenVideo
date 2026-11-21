package top.jlen.vod.ui

import org.junit.Assert.*
import org.junit.Test
import top.jlen.vod.data.PlaybackResumeRecord
import top.jlen.vod.data.CmsSourceGroup
import top.jlen.vod.data.Episode
import top.jlen.vod.data.VodItem

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

    @Test fun favoriteKeepsItsReleaseAcrossAListRefreshAndAnOlderRebuild() {
        val detail = VodItem(vodRemarks = "更新至第24集")
        val release = requireNotNull(resolveFollowRelease(detail, "", null))
        val saved = card.copy(latestEpisodeIndex = release.index, latestEpisodeLabel = release.label, watchedEpisodeIndex = 0)
        assertEquals(FollowRelease(23, "更新至第24集"), resolveFollowRelease(null, "线路一 | 第1集", saved))
        assertNull(resolveFollowRelease(null, "线路一 | 第1集", null))

        val old = saved.copy(latestEpisodeIndex = 0, latestEpisodeLabel = "更新至第1集", hasUpdate = false)
        val rebuilt = mergeNewerFollowPlayback(listOf(old), listOf(saved), mapOf(old.vodId to old)).single()
        assertEquals("更新至第24集", rebuilt.latestEpisodeLabel)
        assertEquals(0, rebuilt.watchedEpisodeIndex)
        assertTrue(rebuilt.hasUpdate)
    }

    @Test fun refreshUsesAllPlaylistLinesAndAcceptsExplicitCatalogCorrections() {
        val detail = VodItem(vodPlayUrl = "第12集\$url#第1集\$url", playGroups = listOf(
            CmsSourceGroup("线路二", listOf(Episode("第16集", "url"), Episode("第1集", "url")))
        ))
        assertEquals(FollowRelease(15, "更新至第16集"), resolveFollowRelease(detail, "", null))
        val partial = detail.copy(playGroups = null, vodPlayUrl = "第1集\$url")
        assertEquals(9, resolveFollowRelease(partial, "", card)?.index)
        assertEquals(FollowRelease(10, "更新至第11集"), resolveFollowRelease(null, "更新至11集", card))
        assertEquals(FollowRelease(0, "更新至第1集"), resolveFollowRelease(detail.copy(vodRemarks = "更新至第1集"), "", card))
    }
}
