package top.jlen.vod.ui

// 在主线程选择筛选条件时立即换代；忽略旧首屏及旧分页的成功/失败回调。
internal class LibraryRequestTracker {
    var version: Long = 0
        private set

    fun begin(): Long = ++version
    fun isCurrent(requestVersion: Long): Boolean = version == requestVersion
}
