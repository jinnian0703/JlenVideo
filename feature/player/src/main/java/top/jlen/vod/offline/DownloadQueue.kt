package top.jlen.vod.offline

import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/** One dispatcher owns the running set. Lowering the limit lets existing transfers finish. */
internal suspend fun <T> runDownloadQueue(
    wake: Channel<Unit>, limit: () -> Int, queued: () -> List<T>, id: (T) -> String,
    transfer: suspend (T) -> Unit
) = supervisorScope {
    val running = mutableMapOf<String, Job>()
    while (true) {
        currentCoroutineContext().ensureActive()
        running.entries.removeAll { it.value.isCompleted }
        val slots = (limit() - running.size).coerceAtLeast(0)
        queued().asSequence().filterNot { id(it) in running }.take(slots).forEach { row ->
            val job = launch { transfer(row) }
            running[id(row)] = job
            job.invokeOnCompletion { wake.trySend(Unit) }
        }
        if (running.isEmpty()) break
        wake.receive()
    }
}
