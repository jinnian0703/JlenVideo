package top.jlen.vod.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import top.jlen.vod.offline.DownloadEpisodeDialog
import top.jlen.vod.offline.resolveDownloadSources

private enum class DownloadPrompt { Closed, Password, Episodes }

@Composable
fun DetailScreen(
    state: DetailUiState,
    isLoggedIn: Boolean,
    onBack: () -> Unit,
    onSelectSource: (Int) -> Unit,
    onFavorite: () -> Unit,
    onDismissActionMessage: () -> Unit,
    onRetry: () -> Unit,
    onUnlock: suspend (Int, String) -> Unit,
    checkDownloadSources: suspend (String) -> List<top.jlen.vod.data.PlaySource>,
    onPlay: (String, Int, Int) -> Unit
) {
    val isDarkTheme = isSystemInDarkTheme()
    val errorMessage = state.error
    val detailItem = state.item

    when {
        state.detailLocked -> Column(Modifier.fillMaxSize().background(UiPalette.BackgroundBottom)) {
            DetailTopBar(title = "影片详情", onBack = onBack)
            Column(Modifier.padding(UiDimens.PagePadding), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("这部影片的详情需要密码才能查看。", color = UiPalette.TextSecondary)
                ContentUnlock("详情") { onUnlock(1, it) }
            }
        }
        state.isLoading && detailItem == null -> LoadingPane("正在加载详情...")
        !errorMessage.isNullOrBlank() -> Box(modifier = Modifier.appTopInsetsPadding()) {
            DetailLoadErrorPane(message = errorMessage, onRetry = onRetry, onBack = onBack)
        }
        detailItem == null -> EmptyPane(
            message = "没有找到影片详情",
            description = "这条资源可能已下架，或当前站点暂未返回详情数据"
        )

        else -> {
            val item = detailItem
            val source = state.selectedSource
            val downloadVod = item.vodId.ifBlank { item.siteVodId }.ifBlank { state.requestedVodId.orEmpty() }
            var downloadPrompt by rememberSaveable(downloadVod) { mutableStateOf(DownloadPrompt.Closed) }
            val needsDownloadPassword = item.downloadLocked
            when (downloadPrompt) {
                DownloadPrompt.Password -> ContentUnlockPrompt("下载", filmTitle = item.displayTitle, onDismiss = { downloadPrompt = DownloadPrompt.Closed },
                    onVerified = { downloadPrompt = DownloadPrompt.Episodes }) { onUnlock(5, it) }
                DownloadPrompt.Episodes -> {
                    val sources = remember(item.downloadGroups, needsDownloadPassword, state.sources) {
                        resolveDownloadSources(item.downloadGroups.orEmpty(), needsDownloadPassword, state.sources)
                    }
                    DownloadEpisodeDialog(downloadVod, item.displayTitle, item.vodPic.orEmpty(), sources,
                        initialSource = if (item.downloadGroups.isNullOrEmpty()) state.selectedSourceIndex else 0,
                        loading = state.isLoading, checkSources = checkDownloadSources, onDismiss = { downloadPrompt = DownloadPrompt.Closed })
                }
                DownloadPrompt.Closed -> Unit
            }
            val downloadLabels = top.jlen.vod.offline.episodeDownloadLabels(downloadVod, state.selectedSourceIndex, source?.name.orEmpty())
            val detailSubtitle = remember(item, state.sources) {
                resolvedDetailSubtitle(item).ifBlank { "站内资源" }
            }
            val detailListState = rememberLazyListState()

            LaunchedEffect(state.actionMessage, state.isActionError) {
                if (!state.actionMessage.isNullOrBlank() && !state.isActionError) {
                    delay(2200)
                    onDismissActionMessage()
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(UiPalette.BackgroundBottom),
                state = detailListState,
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item {
                    DetailHero(
                        item = item,
                        onBack = onBack,
                        darkMode = isDarkTheme
                    )
                }
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = UiDimens.PagePadding),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = item.displayTitle,
                            style = androidx.compose.material3.MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = UiPalette.Ink
                        )
                        Text(
                            text = detailSubtitle,
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            color = UiPalette.TextSecondary
                        )

                        val pendingResume = state.pendingResumePlayback
                        // 有续播记录时优先按记录中的线路续播，便于携带播放进度直接进入播放页
                        val primarySourceIndex = pendingResume
                            ?.sourceIndex
                            ?.takeIf { it in state.sources.indices }
                            ?: state.selectedSourceIndex
                        val primarySource = state.sources.getOrNull(primarySourceIndex) ?: source
                        val primaryEpisodeIndex = pendingResume
                            ?.episodeIndex
                            ?.takeIf { it >= 0 }
                            ?.coerceAtMost((primarySource?.episodes?.lastIndex ?: 0).coerceAtLeast(0))
                            ?: 0
                        val primaryActionLabel = if (pendingResume != null) {
                            formatResumeActionLabel(primaryEpisodeIndex, pendingResume.positionMs)
                        } else {
                            "立即播放"
                        }
                        val followActionLabel = when {
                            !isLoggedIn -> "登录后追剧"
                            state.isActionLoading -> "处理中..."
                            state.isFavorited -> "已追剧"
                            else -> "追剧"
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (item.playLocked) {
                                ContentUnlock("播放", filmTitle = item.displayTitle) { onUnlock(4, it) }
                            } else Button(
                                onClick = {
                                    if (primarySource != null) {
                                        onPlay(item.displayTitle, primarySourceIndex, primaryEpisodeIndex)
                                    }
                                },
                                enabled = primarySource != null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(UiDimens.PrimaryButtonHeight),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(UiDimens.ControlRadius),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = UiPalette.Accent,
                                    contentColor = UiPalette.OnAccentButton
                                )
                            ) {
                                Text(primaryActionLabel, fontWeight = FontWeight.ExtraBold)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onFavorite,
                                    enabled = !state.isActionLoading,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(UiDimens.SecondaryButtonHeight),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(UiDimens.ControlRadius),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (state.isFavorited) UiPalette.Accent.copy(alpha = 0.24f) else UiPalette.BorderSoft
                                    ),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (state.isFavorited) {
                                            UiPalette.AccentSoft.copy(alpha = 0.14f)
                                        } else {
                                            UiPalette.SurfaceSoft.copy(alpha = 0.58f)
                                        },
                                        contentColor = UiPalette.Accent
                                    )
                                ) {
                                    Text(followActionLabel, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = { downloadPrompt = if (needsDownloadPassword) DownloadPrompt.Password else DownloadPrompt.Episodes },
                                    enabled = downloadVod.isNotBlank(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(UiDimens.SecondaryButtonHeight),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(UiDimens.ControlRadius),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, UiPalette.BorderSoft),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = UiPalette.SurfaceSoft.copy(alpha = 0.38f),
                                        contentColor = UiPalette.TextPrimary
                                    )
                                ) {
                                    Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("下载", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                item {
                    Column(modifier = Modifier.padding(horizontal = UiDimens.PagePadding)) {
                        state.actionMessage?.takeIf { it.isNotBlank() }?.let { message ->
                            DetailActionNotice(
                                message = message,
                                isError = state.isActionError
                            )
                        }
                    }
                }
                item { DetailInfoCard(item = item, sources = state.sources) }
                if (state.sources.isNotEmpty()) {
                    item { SectionTitle(title = "播放线路", action = null, onAction = {}) }
                    item {
                        SourceRow(
                            sourceNames = state.sources.map { it.name },
                            selectedIndex = state.selectedSourceIndex,
                            onSelectSource = onSelectSource
                        )
                    }
                    source?.let { selected ->
                        item { SectionTitle(title = "选集播放", caption = "共 ${selected.episodes.size} 集") }
                        item {
                            EpisodePanel(
                                episodes = selected.episodes,
                                downloadLabels = downloadLabels,
                                selectedIndex = -1,
                                pauseMarquee = detailListState.isScrollInProgress,
                                onEpisodeClick = { episodeIndex ->
                                    onPlay(item.displayTitle, state.selectedSourceIndex, episodeIndex)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// 续播按钮文案：继续观看 第 N 集 mm:ss，没有进度时只显示集数
private fun formatResumeActionLabel(episodeIndex: Int, positionMs: Long): String {
    val episodeLabel = "继续观看 第 ${episodeIndex + 1} 集"
    if (positionMs <= 0L) return episodeLabel
    val totalSeconds = positionMs / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    val time = if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
    return "$episodeLabel $time"
}

@Composable
private fun DetailLoadErrorPane(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = UiDimens.PagePadding, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 重试会重新请求详情，返回按钮保留退出途径
        ErrorBanner(message = message, onRetry = onRetry)
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(UiDimens.SecondaryButtonHeight),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(UiDimens.ControlRadius),
            border = androidx.compose.foundation.BorderStroke(1.dp, UiPalette.BorderSoft),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = UiPalette.SurfaceSoft.copy(alpha = 0.38f),
                contentColor = UiPalette.TextPrimary
            )
        ) {
            Text("返回", fontWeight = FontWeight.Bold)
        }
    }
}
