package top.jlen.vod.ui

import androidx.compose.runtime.saveable.listSaver

// 仅作返回/进程重建恢复用，不应把每一像素滚动向上传播为 UI 状态。
internal class SavedScrollPosition(var index: Int = 0, var offset: Int = 0) {
    fun update(index: Int, offset: Int) {
        this.index = index
        this.offset = offset
    }

    companion object {
        val Saver = listSaver<SavedScrollPosition, Int>(
            save = { listOf(it.index, it.offset) },
            restore = { SavedScrollPosition(it[0], it[1]) }
        )
    }
}
