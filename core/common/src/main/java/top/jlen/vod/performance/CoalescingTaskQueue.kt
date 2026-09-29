package top.jlen.vod.performance

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Serial, latest-per-key writes. A burst never creates one disk task per UI event. */
class CoalescingTaskQueue(private val delayMs: Long = 500L) {
    private val executor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "jlen-cache-writer").apply { isDaemon = true }
    }
    private val pending = linkedMapOf<String, () -> Unit>()
    private var scheduled = false

    @Synchronized
    fun submit(key: String, task: () -> Unit) {
        pending[key] = task
        if (scheduled) return
        scheduled = true
        executor.schedule(::drain, delayMs, TimeUnit.MILLISECONDS)
    }

    private fun drain() {
        val tasks = synchronized(this) {
            val batch = pending.values.toList()
            pending.clear()
            scheduled = false
            batch
        }
        tasks.forEach { task -> runCatching { task() } }
    }
}
