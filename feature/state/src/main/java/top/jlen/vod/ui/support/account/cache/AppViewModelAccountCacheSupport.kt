package top.jlen.vod.ui

import androidx.lifecycle.viewModelScope
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.jlen.vod.data.CacheRetentionOption
import top.jlen.vod.data.CacheSizeSummary

internal fun LegacyStateRuntimeViewModelCore.legacyRefreshCacheSettings() {
    val settings = legacyRepository().loadCacheSettings()
    updateAccountState(
        currentAccountState().copy(
            cacheRetention = settings.retention,
            error = null
        )
    )
    legacyRefreshCacheSize()
}

internal fun LegacyStateRuntimeViewModelCore.legacyRefreshCacheSize() {
    if (currentAccountState().isCacheSizeLoading) return
    viewModelScope.launch {
        updateAccountState(currentAccountState().copy(isCacheSizeLoading = true, error = null))
        runCatching {
            withContext(Dispatchers.IO) {
                legacyRepository().loadCacheSizeSummary()
            }
        }.onSuccess { summary ->
            updateAccountState(
                currentAccountState().copy(
                    cacheSizeSummary = summary,
                    isCacheSizeLoading = false,
                    error = null
                )
            )
        }.onFailure {
            updateAccountState(
                currentAccountState().copy(
                    cacheSizeSummary = CacheSizeSummary(isAvailable = false),
                    isCacheSizeLoading = false,
                    error = "缓存大小无法统计"
                )
            )
        }
    }
}

internal fun LegacyStateRuntimeViewModelCore.legacySetCacheRetention(option: CacheRetentionOption) {
    val settings = legacyRepository().saveCacheRetention(option)
    updateAccountState(
        currentAccountState().copy(
            cacheRetention = settings.retention,
            message = "缓存保存时间已设为${settings.retention.label}",
            error = null
        )
    )
}

@OptIn(ExperimentalCoilApi::class)
internal fun LegacyStateRuntimeViewModelCore.legacyClearAppCache() {
    if (currentAccountState().isCacheClearing) return
    viewModelScope.launch {
        updateAccountState(
            currentAccountState().copy(
                isCacheClearing = true,
                error = null,
                message = null
            )
        )
        runCatching {
            withContext(Dispatchers.IO) {
                legacyRepository().clearAppContentAndImageCaches()
                getApplication<android.app.Application>().imageLoader.memoryCache?.clear()
                getApplication<android.app.Application>().imageLoader.diskCache?.clear()
            }
        }.onSuccess {
            clearSearchResultScrollPositions()
            updateAccountState(
                currentAccountState().copy(
                    cacheSizeSummary = CacheSizeSummary(),
                    isCacheClearing = false,
                    isCacheSizeLoading = false,
                    message = "缓存已清除",
                    error = null
                )
            )
            legacyRefreshHome(forceRefresh = true)
            legacyRefreshCacheSize()
        }.onFailure { error ->
            updateAccountState(
                currentAccountState().copy(
                    isCacheClearing = false,
                    isCacheSizeLoading = false,
                    error = toUserFacingMessage(error, "清除缓存失败")
                )
            )
        }
    }
}
