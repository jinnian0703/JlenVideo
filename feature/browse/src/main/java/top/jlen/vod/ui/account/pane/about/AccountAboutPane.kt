package top.jlen.vod.ui

import androidx.compose.runtime.Composable
import top.jlen.vod.data.CacheRetentionOption
import top.jlen.vod.data.CacheSizeSummary

@Composable
internal fun AboutPane(
    currentVersion: String,
    latestVersion: String,
    notes: String,
    hasUpdate: Boolean,
    isUpdateLoading: Boolean,
    cacheRetention: CacheRetentionOption,
    cacheSizeSummary: CacheSizeSummary,
    isCacheSizeLoading: Boolean,
    isCacheClearing: Boolean,
    crashLogText: String,
    hasCrashLog: Boolean,
    onCheckUpdate: () -> Unit,
    onRefreshCacheSize: () -> Unit,
    onSetCacheRetention: (CacheRetentionOption) -> Unit,
    onClearAppCache: () -> Unit,
    onRefreshCrashLog: () -> Unit,
    onClearCrashLog: () -> Unit,
    onOpenRelease: () -> Unit,
    onDownloadUpdate: () -> Unit
) = LegacyAboutPane(
    currentVersion = currentVersion,
    latestVersion = latestVersion,
    notes = notes,
    hasUpdate = hasUpdate,
    isUpdateLoading = isUpdateLoading,
    cacheRetention = cacheRetention,
    cacheSizeSummary = cacheSizeSummary,
    isCacheSizeLoading = isCacheSizeLoading,
    isCacheClearing = isCacheClearing,
    crashLogText = crashLogText,
    hasCrashLog = hasCrashLog,
    onCheckUpdate = onCheckUpdate,
    onRefreshCacheSize = onRefreshCacheSize,
    onSetCacheRetention = onSetCacheRetention,
    onClearAppCache = onClearAppCache,
    onRefreshCrashLog = onRefreshCrashLog,
    onClearCrashLog = onClearCrashLog,
    onOpenRelease = onOpenRelease,
    onDownloadUpdate = onDownloadUpdate
)

@Composable
internal fun CrashLogCard(
    logText: String,
    onRefresh: () -> Unit,
    onClear: () -> Unit
) = LegacyCrashLogCard(
    logText = logText,
    onRefresh = onRefresh,
    onClear = onClear
)
