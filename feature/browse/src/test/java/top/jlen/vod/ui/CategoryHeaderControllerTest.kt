package top.jlen.vod.ui

import androidx.compose.runtime.snapshots.Snapshot
import org.junit.Assert.*
import org.junit.Test

class CategoryHeaderControllerTest {
    @Test fun pixelDistanceDoesNotInvalidateVisibleState() {
        val header = CategoryHeaderController()
        var writes = 0
        Snapshot.observe(writeObserver = { writes++ }) {
            repeat(31) { header.onScroll(-1f, false, 32f) }
        }
        assertTrue(header.expanded)
        assertEquals(0, writes)
        Snapshot.observe(writeObserver = { writes++ }) { header.onScroll(-1f, false, 32f) }
        assertFalse(header.expanded)
        assertEquals(1, writes)
    }
    @Test fun resetAndManualToggleStillWork() {
        val header = CategoryHeaderController()
        header.toggle(true)
        assertFalse(header.expanded)
        header.reset()
        assertTrue(header.expanded)
        assertEquals(0f, header.behavior.forwardDistance)
    }
    @Test fun returningToTopReopensCollapsedHeader() {
        val header = CategoryHeaderController(CategoryHeaderBehavior(false, true))
        header.onScroll(5f, true, 32f)
        assertTrue(header.expanded)
    }
}
