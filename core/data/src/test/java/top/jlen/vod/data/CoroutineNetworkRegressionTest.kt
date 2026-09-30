package top.jlen.vod.data

import java.io.IOException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Timeout
import okio.buffer
import org.junit.Assert.*
import org.junit.Test
import top.jlen.vod.common.coroutines.runSuspendCatching

class CoroutineNetworkRegressionTest {
    @Test
    fun clientHasBoundedTimeoutAndOnlyLogsInDebugBuilds() {
        val jar = PersistentCookieJar(InMemoryPreferences()) { null }
        val client = LegacyAppleCmsRuntimeRepositoryCore.createClient(jar)
        assertEquals(30000, client.callTimeoutMillis)
        assertEquals(
            top.jlen.vod.core.data.BuildConfig.DEBUG,
            client.interceptors.any { it is okhttp3.logging.HttpLoggingInterceptor }
        )
    }

    @Test(timeout = 5000)
    fun suspendCatchingPropagatesCancellationWithoutRunningFallback() = runBlocking {
        var usedFallback = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            runSuspendCatching { awaitCancellation() }.getOrElse { usedFallback = true }
        }
        job.cancelAndJoin()
        assertFalse(usedFallback)
        val expected = IOException("测试请求失败")
        assertSame(expected, runSuspendCatching { throw expected }.exceptionOrNull())
    }

    @Test(timeout = 5000)
    fun cancelingWaiterCancelsTheUnderlyingCall() = runBlocking {
        val call = FakeCall()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { call.await().use {} }
        job.cancelAndJoin()
        assertTrue(call.isCanceled())
        // 取消后才到达的响应也必须关闭。
        val body = TrackingBody()
        call.respond(body)
        assertTrue(body.closed)
    }

    @Test(timeout = 5000)
    fun cancellationBetweenResponseAndDispatchClosesTheResponse() = runBlocking {
        val call = FakeCall()
        var consumed = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            call.await().use { consumed = true }
        }
        val body = TrackingBody()
        call.respond(body)
        job.cancelAndJoin()
        assertFalse(consumed)
        assertTrue(body.closed)
    }

    @Test(timeout = 5000)
    fun successfulAndFailedCallsResumeTheirWaiters() = runBlocking {
        val call = FakeCall()
        val result = async(start = CoroutineStart.UNDISPATCHED) {
            call.await().use { it.body!!.string() }
        }
        val body = TrackingBody()
        call.respond(body)
        assertEquals("test", result.await())
        assertTrue(body.closed)

        val failed = FakeCall()
        val failure = IOException("测试连接失败")
        val failedResult = async(start = CoroutineStart.UNDISPATCHED) {
            runSuspendCatching { failed.await() }
        }
        failed.fail(failure)
        val reported = failedResult.await().exceptionOrNull()!!
        // 协程栈恢复可能复制异常，校验类型和消息而不是对象身份。
        assertEquals(IOException::class.java, reported.javaClass)
        assertEquals(failure.message, reported.message)
    }

    private class FakeCall : Call {
        private val request = Request.Builder().url("https://cms.jlen.top/").build()
        private lateinit var callback: Callback
        private var canceled = false
        private var executed = false
        override fun request(): Request = request
        override fun execute(): Response = error("测试只允许异步请求")
        override fun enqueue(responseCallback: Callback) {
            executed = true
            callback = responseCallback
        }
        override fun cancel() { canceled = true }
        override fun isExecuted(): Boolean = executed
        override fun isCanceled(): Boolean = canceled
        override fun timeout(): Timeout = Timeout()
        override fun clone(): Call = FakeCall()
        fun respond(body: ResponseBody) {
            callback.onResponse(
                this, Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body(body).build()
            )
        }
        fun fail(error: IOException) { callback.onFailure(this, error) }
    }

    private class TrackingBody : ResponseBody() {
        var closed = false
        private val content = object : ForwardingSource(Buffer().writeUtf8("test")) {
            override fun close() {
                closed = true
                super.close()
            }
        }.buffer()
        override fun contentType(): MediaType? = null
        override fun contentLength(): Long = 4
        override fun source(): BufferedSource = content
    }
}
