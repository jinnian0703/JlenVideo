package top.jlen.vod.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import java.io.IOException
import top.jlen.vod.data.CmsApiError
import top.jlen.vod.data.LoginCaptchaRejected

data class LoginChallengeState(
    val image: ByteArray? = null,
    val message: String? = null,
    val invalidAnswer: Boolean = false
)

internal class LoginChallengeController(private val fetchImage: suspend () -> ByteArray) {
    var state by mutableStateOf<LoginChallengeState?>(null, referentialEqualityPolicy())
        private set

    fun dismiss() { state = null }

    suspend fun loadImage(invalidAnswer: Boolean = false) {
        val pending = LoginChallengeState(invalidAnswer = invalidAnswer,
            message = if (invalidAnswer) "验证码错误，请重新输入" else null)
        state = pending
        val result = runStateCatching { fetchImage() }
        if (state !== pending) return // The dialog was dismissed while the request was in flight.
        state = result.fold(
            onSuccess = { pending.copy(image = it) },
            onFailure = { LoginChallengeState(message = "验证码加载失败，请点「换一张」重试") }
        )
    }

    suspend fun handleSubmitError(submitted: LoginChallengeState, error: Throwable): Boolean {
        if (state !== submitted) return true
        when {
            error is LoginCaptchaRejected -> loadImage(invalidAnswer = true)
            error is IOException && error !is CmsApiError -> state = submitted.copy(
                message = toUserFacingMessage(error, "提交失败"), invalidAnswer = false
            )
            else -> { dismiss(); return false }
        }
        return true
    }
}
