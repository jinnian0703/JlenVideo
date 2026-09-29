package top.jlen.vod.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// 滚动距离不是可见 UI 状态；只有展开值变化时才通知 Compose。
internal class CategoryHeaderController(initial: CategoryHeaderBehavior = CategoryHeaderBehavior()) {
    var behavior = initial
        private set
    var expanded by mutableStateOf(initial.expanded)
        private set

    fun onScroll(deltaY: Float, atTop: Boolean, collapseThreshold: Float) {
        update(behavior.onScroll(deltaY, atTop, collapseThreshold))
    }

    fun toggle(atTop: Boolean) = update(behavior.toggle(atTop))
    fun reset() = update(CategoryHeaderBehavior())

    private fun update(next: CategoryHeaderBehavior) {
        behavior = next
        expanded = next.expanded
    }
}
