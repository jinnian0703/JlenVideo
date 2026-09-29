package top.jlen.vod.ui

// 只响应影片列表的实际滚动，展开面板内部滚动和尺寸变化不会反复触发折叠。
internal data class CategoryHeaderBehavior(
    val expanded: Boolean = true,
    val hasLeftTop: Boolean = false,
    val forwardDistance: Float = 0f
) {
    fun toggle(atTop: Boolean) = copy(
        expanded = !expanded,
        hasLeftTop = !atTop,
        forwardDistance = 0f
    )

    fun onScroll(deltaY: Float, atTop: Boolean, collapseThreshold: Float): CategoryHeaderBehavior {
        if (deltaY == 0f) return this
        if (deltaY > 0f && atTop && hasLeftTop) return CategoryHeaderBehavior()
        val distance = if (deltaY < 0f) forwardDistance - deltaY else 0f
        val collapse = distance >= collapseThreshold
        return copy(
            expanded = expanded && !collapse,
            hasLeftTop = hasLeftTop || !atTop,
            forwardDistance = if (collapse) 0f else distance
        )
    }
}
