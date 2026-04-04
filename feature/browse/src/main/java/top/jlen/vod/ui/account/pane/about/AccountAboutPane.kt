package top.jlen.vod.ui

import androidx.compose.runtime.Composable

@Composable
internal fun AboutPane(
    currentVersion: String,
    latestVersion: String,
    notes: String,
    hasUpdate: Boolean,
    isUpdateLoading: Boolean,
    crashLogText: String,
    hasCrashLog: Boolean,
    onCheckUpdate: () -> Unit,
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
    crashLogText = crashLogText,
    hasCrashLog = hasCrashLog,
    onCheckUpdate = onCheckUpdate,
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
