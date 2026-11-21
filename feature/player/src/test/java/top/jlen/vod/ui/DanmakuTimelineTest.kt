package top.jlen.vod.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import top.jlen.vod.data.TimedComment

class DanmakuTimelineTest {
    @Test fun seekAndReplayUseTheWindowAtTheNewMediaPosition() {
        val slots = scheduleComments(listOf(1000L, 6500L, 60_000L).mapIndexed { id, time ->
            TimedComment(id, time, "测试", "", 0)
        }, 2)
        fun ids(time: Long) = visibleSlots(slots, time).map { slots[it].message.id }
        assertEquals(listOf(0, 1), ids(6500))
        assertEquals(listOf(2), ids(60_000))
        assertEquals(listOf(0), ids(1100))
        assertEquals(emptyList<Int>(), ids(0))
        assertEquals(emptyList<Int>(), ids(66_500))
    }

    @Test fun crowdedTimestampsDoNotOverlapAndFreedLanesCanBeUsedAfterSeek() {
        fun comment(id: Int, time: Long, type: Int = 0) = TimedComment(id, time, "测试", "#ffffff", type)
        val slots = scheduleComments(listOf(comment(4, 6500), comment(1, 0), comment(2, 0, 2), comment(3, 6000)), 2)
        assertEquals(listOf(1 to 0, 2 to 1, 4 to 0), slots.map { it.message.id to it.lane })
    }
}
