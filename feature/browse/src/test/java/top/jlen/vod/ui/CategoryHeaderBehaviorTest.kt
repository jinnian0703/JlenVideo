package top.jlen.vod.ui

import org.junit.Assert.*
import org.junit.Test

class CategoryHeaderBehaviorTest {
    @Test fun startsExpandedAndIgnoresTinyDrags() {
        val state = CategoryHeaderBehavior().onScroll(-10f, atTop = false, collapseThreshold = 32f)
        assertTrue(state.expanded)
        assertFalse(state.onScroll(-22f, atTop = false, collapseThreshold = 32f).expanded)
    }
    @Test fun returnsToTopAutomatically() {
        val collapsed = CategoryHeaderBehavior().onScroll(-40f, atTop = false, collapseThreshold = 32f)
        assertTrue(collapsed.onScroll(20f, atTop = true, collapseThreshold = 32f).expanded)
    }
    @Test fun doesNotExpandWhenScrollingUpInMiddle() {
        val collapsed = CategoryHeaderBehavior().onScroll(-40f, atTop = false, collapseThreshold = 32f)
        assertFalse(collapsed.onScroll(10f, atTop = false, collapseThreshold = 32f).expanded)
    }
    @Test fun restoredPositionCanExpandOnFirstScrollBackToTop() {
        val restored = CategoryHeaderBehavior(expanded = false, hasLeftTop = true)
        assertTrue(restored.onScroll(5f, atTop = true, collapseThreshold = 32f).expanded)
    }
    @Test fun manualCollapseAtTopPersistsUntilLeaveAndReturn() {
        val collapsed = CategoryHeaderBehavior().toggle(atTop = true)
        assertFalse(collapsed.onScroll(0f, atTop = true, collapseThreshold = 32f).expanded)
        assertFalse(collapsed.onScroll(20f, atTop = true, collapseThreshold = 32f).expanded)
        val away = collapsed.onScroll(-40f, atTop = false, collapseThreshold = 32f)
        assertTrue(away.onScroll(40f, atTop = true, collapseThreshold = 32f).expanded)
    }
    @Test fun manualExpandInMiddleAllowsNextBrowseToCollapse() {
        val expanded = CategoryHeaderBehavior(expanded = false).toggle(atTop = false)
        assertTrue(expanded.expanded)
        assertFalse(expanded.onScroll(-40f, atTop = false, collapseThreshold = 32f).expanded)
    }
    @Test fun reversingDirectionResetsCollapseDistance() {
        val state = CategoryHeaderBehavior().onScroll(-20f, false, 32f)
            .onScroll(5f, false, 32f).onScroll(-20f, false, 32f)
        assertTrue(state.expanded)
    }
    @Test fun selectingFiltersStartsExpandedAgain() {
        val collapsed = CategoryHeaderBehavior().onScroll(-40f, false, 32f)
        assertFalse(collapsed.expanded)
        assertTrue(CategoryHeaderBehavior().expanded)
    }
}
