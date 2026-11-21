package top.jlen.vod.offline

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class OfflineManagementTest {
    @Test fun albumStatusCombinesOnlyActiveSpeedsAndExplainsBlockedTransfers() {
        fun entry(stage: Stage, speed: Long = 0L) = Entry("id", "vod", "影片", "线路", 0, 0, "集", "url", 0L,
            stage = stage, bytesPerSecond = speed)
        val pending = listOf(entry(Stage.ACTIVE, 200), entry(Stage.ACTIVE, 300), entry(Stage.PAUSED, 100), entry(Stage.FAILED))
        fun status(rows: List<Entry>, allowed: Boolean, mobile: Boolean = false) =
            albumTransferStatus(rows, allowed, mobile) { "$it B" }

        assertEquals("下载中 · 500 B/s · 1 项失败", status(pending, true))
        assertEquals("等待 Wi-Fi · 1 项失败", status(pending, false))
        assertEquals("等待网络", status(listOf(entry(Stage.QUEUED)), false, true))
        assertEquals("已暂停", status(listOf(entry(Stage.PAUSED)), false))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun changingLimitFillsSlotsInOrderWithoutInterruptingExistingTransfers() = runTest {
        val waiting = (1..6).toMutableList()
        val started = mutableListOf<Int>()
        val gates = waiting.associateWith { CompletableDeferred<Unit>() }
        val wake = Channel<Unit>(Channel.CONFLATED)
        var limit = 2
        val queue = launch {
            runDownloadQueue(wake, { limit }, { waiting.toList() }, Int::toString) { episode ->
                waiting.remove(episode); started += episode; gates.getValue(episode).await()
            }
        }
        runCurrent(); assertEquals(listOf(1, 2), started)
        limit = 4; wake.trySend(Unit); runCurrent(); assertEquals(listOf(1, 2, 3, 4), started)
        limit = 1; wake.trySend(Unit)
        for (episode in 1..3) { gates.getValue(episode).complete(Unit); runCurrent() }
        assertEquals(listOf(1, 2, 3, 4), started)
        gates.getValue(4).complete(Unit); runCurrent(); assertEquals(5, started.last())
        gates.getValue(5).complete(Unit); runCurrent(); assertEquals(6, started.last())
        gates.getValue(6).complete(Unit); queue.join(); assertEquals((1..6).toList(), started)
    }

    @Test fun filmsWithIdenticalTitlesHaveSeparateFoldersAndEachFilmSharesOnlyItsCover() {
        val folder = albumFolder("a", "../电影:样片/续集")
        assertNotEquals(folder, albumFolder("b", "../电影:样片/续集"))
        assertFalse(folder.contains('/')); assertFalse(folder.contains(':'))
        val files = OfflineFiles(File("offline-video"))
        assertEquals(files.cover(folder).parentFile, files.episode(folder, "ep1").parentFile)
        assertNotEquals(files.episode(folder, "ep1"), files.episode(folder, "ep2"))
    }

    @Test fun storageSegmentsCoverWholeDeviceWithoutDoubleCounting() {
        assertEquals(listOf(10L, 5L, 45L, 40L), storageParts(100, 40, 10, 5))
        assertEquals(listOf(5L, 0L, 0L, 95L), storageParts(100, 95, 15, 10))
        assertEquals(listOf(0L, 80L, 20L, 0L), storageParts(100, -10, -1, 80))
        assertEquals(listOf(0L, 0L, 0L, 100L), storageParts(100, 110, 10, 1))
    }
}
