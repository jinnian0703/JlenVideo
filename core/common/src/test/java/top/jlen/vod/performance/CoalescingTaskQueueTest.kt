package top.jlen.vod.performance

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class CoalescingTaskQueueTest {
    @Test fun sameKeyKeepsOnlyLatestAndRunsOffCallerThread() {
        val queue = CoalescingTaskQueue(50)
        val count = AtomicInteger()
        val done = CountDownLatch(1)
        val caller = Thread.currentThread()
        var worker: Thread? = null
        // Hold the same queue monitor so the scheduled drain cannot see half the burst.
        synchronized(queue) {
            repeat(50) { queue.submit("owner") { count.incrementAndGet() } }
            queue.submit("owner") { worker = Thread.currentThread(); count.incrementAndGet(); done.countDown() }
        }
        assertTrue(done.await(3, TimeUnit.SECONDS))
        assertEquals(1, count.get())
        assertNotSame(caller, worker)
    }
    @Test fun differentOwnersAreNotDroppedAndFailureDoesNotStopQueue() {
        val queue = CoalescingTaskQueue(10)
        val done = CountDownLatch(2)
        synchronized(queue) {
            queue.submit("bad") { error("simulated write failure") }
            queue.submit("one") { done.countDown() }
            queue.submit("two") { done.countDown() }
        }
        assertTrue(done.await(3, TimeUnit.SECONDS))
    }
    @Test fun clearSupersedesPendingSave() {
        val queue = CoalescingTaskQueue(10)
        val saved = AtomicInteger(0)
        val done = CountDownLatch(1)
        synchronized(queue) {
            queue.submit("owner") { saved.set(1) }
            queue.submit("owner") { saved.set(0); done.countDown() }
        }
        assertTrue(done.await(3, TimeUnit.SECONDS))
        assertEquals(0, saved.get())
    }
}
