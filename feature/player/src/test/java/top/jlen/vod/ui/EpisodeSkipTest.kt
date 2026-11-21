package top.jlen.vod.ui

import org.junit.Assert.*
import org.junit.Test

class EpisodeSkipTest {
    private val rules = EpisodeSkipRules(introEndMs = 90_000, outroLengthMs = 120_000)

    @Test fun skipsIntroOnceAndPreservesResumeAndManualRewind() {
        val first = EpisodeSkipSession(rules)
        assertNull(first.seekTarget(0, 0, true))
        assertEquals(90_000L, first.seekTarget(1_000, 900_000, true))
        assertNull(first.seekTarget(5_000, 900_000, true))
        val resumed = EpisodeSkipSession(rules)
        assertNull(resumed.seekTarget(350_000, 900_000, true))
        assertNull(resumed.seekTarget(15_000, 900_000, true))
    }

    @Test fun outroUsesEachEpisodesDurationAndRequiresANextEpisode() {
        for (duration in listOf(900_000L, 1_080_000L)) {
            val session = EpisodeSkipSession(rules)
            val start = duration - 120_000
            assertNull(session.seekTarget(start - 1, duration, true))
            assertNull(session.seekTarget(start, duration, false))
            assertEquals(duration, session.seekTarget(start, duration, true))
            assertNull(session.seekTarget(start + 1, duration, true))
        }
    }

    @Test fun shortSpecialsAndClearedMarkersDoNotSkip() {
        assertNull(EpisodeSkipSession(rules).seekTarget(1, 210_000, true))
        assertNull(EpisodeSkipSession(EpisodeSkipRules()).seekTarget(0, 900_000, true))
        assertEquals(900_000L, EpisodeSkipSession(rules.copy(introEndMs = 0)).seekTarget(800_000, 900_000, true))
        assertNull(EpisodeSkipSession(rules.copy(outroLengthMs = 0)).seekTarget(800_000, 900_000, true))
    }

    @Test fun onlineAndOfflineUseTheSameFilmAndNamedLineEvenWhenIndexDiffers() {
        assertEquals(episodeSkipKey("42", "高清A", 0), episodeSkipKey("42", "高清a", 2))
        assertNotEquals(episodeSkipKey("42", "高清A", 0), episodeSkipKey("42", "高清B", 0))
        assertNotEquals(episodeSkipKey("42", "高清A", 0), episodeSkipKey("43", "高清A", 0))
        assertNull(episodeSkipKey("", "高清A", 0))
    }
}
