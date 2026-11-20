package top.jlen.vod.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import top.jlen.vod.ui.PlaybackSnapshot

class OfflinePlaybackTest {
    @Test fun queueOrdersDownloadedEpisodesFromTheSelectedFilmAndSource() {
        val first = Entry("1", "film", "标题", "线路", 0, 1, "第一集", "", 0L, Stage.DONE)
        val fourth = first.copy(id = "4", ei = 4)
        val entries = listOf(fourth, first.copy(ei = 2, stage = Stage.ACTIVE),
            first.copy(ei = 3, stage = Stage.FAILED), first.copy(vod = "other"),
            first.copy(id = "B", si = 1), first.copy(id = "C", source = "其他线路"), first)
        val groups = downloadedSources(entries, first.vod)
        assertEquals(listOf(listOf("C"), listOf("1", "4"), listOf("B")), groups.map { it.map(Entry::id) })
    }

    @Test fun resumeIgnoresUnknownDurationAndRestartsFinishedVideos() {
        fun position(at: Long, duration: Long) = PlaybackSnapshot(positionMs = at, durationMs = duration).offlineResumePosition()
        assertNull(position(15_000, 0))
        assertEquals(29_000L, position(29_000, 30_000))
        assertEquals(0L, position(30_000, 30_000))
        assertEquals(64_000L, position(64_000, 75_000))
        assertEquals(0L, position(65_000, 75_000))
    }
}
