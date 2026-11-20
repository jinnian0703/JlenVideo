package top.jlen.vod.ui

import java.net.SocketTimeoutException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import top.jlen.vod.data.CmsApiError
import top.jlen.vod.data.LoginCaptchaRejected

class LoginChallengeControllerTest {
    @Test fun onlyRejectedAnswersReplaceTheImage() = runBlocking {
        val images = ArrayDeque(listOf(byteArrayOf(1), byteArrayOf(2)))
        val controller = LoginChallengeController { images.removeFirst() }
        controller.loadImage()
        val first = controller.state!!
        assertTrue(controller.handleSubmitError(first, SocketTimeoutException()))
        val timeout = controller.state!!
        assertSame(first.image, timeout.image)
        assertFalse(timeout.invalidAnswer)
        assertTrue(timeout.message!!.contains("超时"))

        assertTrue(controller.handleSubmitError(timeout, LoginCaptchaRejected("verify_err")))
        val replaced = controller.state!!
        assertArrayEquals(byteArrayOf(2), replaced.image)
        assertTrue(replaced.invalidAnswer)
        assertFalse(controller.handleSubmitError(replaced, CmsApiError(1003, "账号或密码错误")))
        assertNull(controller.state)
    }

    @Test fun failedReplacementCannotResubmitOldImageAndCanBeRetried() = runBlocking {
        var loads = 0
        val controller = LoginChallengeController { if (++loads == 2) throw SocketTimeoutException() else byteArrayOf(loads.toByte()) }
        controller.loadImage()
        controller.handleSubmitError(controller.state!!, LoginCaptchaRejected("verify_err"))
        assertNull(controller.state!!.image)
        assertTrue(controller.state!!.message!!.contains("换一张"))
        controller.loadImage()
        assertArrayEquals(byteArrayOf(3), controller.state!!.image)
        assertNull(controller.state!!.message)
    }

    @Test fun dismissWhileRefreshingDoesNotReopenDialog() = runBlocking {
        val result = CompletableDeferred<ByteArray>()
        val controller = LoginChallengeController { result.await() }
        val pending = launch(start = CoroutineStart.UNDISPATCHED) { controller.loadImage() }
        controller.dismiss()
        result.complete(byteArrayOf(1))
        pending.join()
        assertNull(controller.state)
    }
}
