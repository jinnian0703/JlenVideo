package top.jlen.vod.ui

import androidx.compose.runtime.snapshots.Snapshot
import org.junit.Assert.*
import org.junit.Test

class SavedScrollPositionTest {
    @Test fun offsetsAreSavedWithoutComposeWrites() {
        val position = SavedScrollPosition()
        var writes = 0
        Snapshot.observe(writeObserver = { writes++ }) { repeat(100) { position.update(3, it) } }
        assertEquals(0, writes)
        assertEquals(3, position.index)
        assertEquals(99, position.offset)
    }
    @Test fun restoreRetainsExactOffset() {
        val restored = SavedScrollPosition.Saver.restore(listOf(18, 47))!!
        assertEquals(18, restored.index)
        assertEquals(47, restored.offset)
    }
}
