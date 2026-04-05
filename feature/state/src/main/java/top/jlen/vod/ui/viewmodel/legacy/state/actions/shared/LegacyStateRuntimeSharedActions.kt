package top.jlen.vod.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.jlen.vod.data.AppleCmsRepository

internal fun LegacyStateRuntimeViewModelCore.legacyReportHeartbeat(route: String) {
    val normalizedRoute = route.trim().ifBlank { "home" }
    val userId = currentAccountState().session.userId
    val vodId = if (normalizedRoute == "player") resolveHeartbeatVodId(currentPlayerState()) else ""
    val sid = if (normalizedRoute == "player") currentPlayerState().selectedSourceIndex + 1 else null
    val nid = if (normalizedRoute == "player") currentPlayerState().selectedEpisodeIndex + 1 else null
    viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            legacyRepository().reportHeartbeat(
                route = normalizedRoute,
                userId = userId,
                vodId = vodId,
                sid = sid,
                nid = nid
            )
        }
    }
}

internal fun LegacyStateRuntimeViewModelCore.legacyRunAccountAction(
    block: suspend AppleCmsRepository.() -> String,
    onSuccess: () -> Unit
) {
    if (currentAccountState().isActionLoading) return
    viewModelScope.launch {
        updateAccountState(beginAccountAction(currentAccountState()))
        runCatching {
            withContext(Dispatchers.IO) { legacyRepository().block() }
        }.onSuccess { message ->
            updateAccountState(accountStateWithActionSuccess(currentAccountState(), message))
            onSuccess()
        }.onFailure { error ->
            if (legacyHandleAccountSessionExpired(error)) return@onFailure
            updateAccountState(
                accountStateWithActionError(
                    currentAccountState(),
                    toUserFacingMessage(error, "操作失败")
                )
            )
        }
    }
}

internal fun LegacyStateRuntimeViewModelCore.legacyHandleAccountSessionExpired(error: Throwable): Boolean {
    val message = error.message.orEmpty()
    val isExpired = message.contains("请先登录") || message.contains("登录已失效")
    if (!isExpired) return false

    legacyRepository().clearSession()
    updateAccountState(expiredAccountState(currentAccountState()))
    return true
}
