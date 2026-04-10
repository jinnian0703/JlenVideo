package top.jlen.vod.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import top.jlen.vod.data.Episode
import top.jlen.vod.data.VodItem


@Composable
fun DetailScreen(
    state: DetailUiState,
    isLoggedIn: Boolean,
    onBack: () -> Unit,
    onSelectSource: (Int) -> Unit,
    onFavorite: () -> Unit,
    onDismissActionMessage: () -> Unit,
    onPlay: (String, Int, Int) -> Unit
) {
    val isDarkTheme = isSystemInDarkTheme()
    val errorMessage = state.error
    val detailItem = state.item
    when {
        state.isLoading -> LoadingPane("正在加载详情...")
        !errorMessage.isNullOrBlank() -> ErrorBanner(message = errorMessage, onRetry = onBack, actionLabel = "返回")
        detailItem == null -> EmptyPane(
            message = "没有找到影片详情",
            description = "这条资源可能已下架，或者当前站点未返回详情数据"
        )
        else -> {
            val item = detailItem
            val source = state.selectedSource
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
                item { DetailHero(item = item, onBack = onBack, darkMode = isDarkTheme) }
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            text = item.displayTitle,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = UiPalette.Ink
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = item.subtitle.ifBlank { "站内资源" },
                            style = MaterialTheme.typography.bodyLarge,
                            color = UiPalette.TextSecondary
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        val pendingResume = state.pendingResumePlayback
                        val primaryEpisodeIndex = pendingResume
                            ?.episodeIndex
                            ?.takeIf { it >= 0 }
                            ?.coerceAtMost((source?.episodes?.lastIndex ?: 0).coerceAtLeast(0))
                            ?: 0
                        val primaryActionLabel = if (pendingResume != null) "继续观看" else "立即播放"
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = {
                                    if (source != null) {
                                        onPlay(item.displayTitle, state.selectedSourceIndex, primaryEpisodeIndex)
                                    }
                                },
                                enabled = source != null,
                                modifier = Modifier.height(52.dp),
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = UiPalette.Accent,
                                    contentColor = UiPalette.AccentText
                                )
                            ) {
                                Text(primaryActionLabel, fontWeight = FontWeight.ExtraBold)
                            }
                            OutlinedButton(
                                onClick = onBack,
                                modifier = Modifier.height(52.dp),
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, UiPalette.BorderSoft),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = UiPalette.Ink)
                            ) {
                                Text("返回")
                            }
                            OutlinedButton(
                                onClick = onFavorite,
                                enabled = !state.isActionLoading,
                                modifier = Modifier.height(52.dp),
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (state.isFavorited) UiPalette.Accent.copy(alpha = 0.28f) else UiPalette.BorderSoft
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (state.isFavorited) UiPalette.AccentSoft.copy(alpha = 0.16f) else Color.Transparent,
                                    contentColor = UiPalette.Accent
                                )
                            ) {
                                Text(
                                    if (!isLoggedIn) "登录后追剧"
                                    else if (state.isActionLoading) "处理中..."
                                    else if (state.isFavorited) "已追剧"
                                    else "追剧"
                                )
                            }
                        }
                    }
                }
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        state.actionMessage?.takeIf { it.isNotBlank() }?.let { message ->
                            DetailActionNotice(
                                message = message,
                                isError = state.isActionError
                            )
                        }
                    }
                }
                item { DetailInfoCard(item = item) }
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
                        item { SectionTitle(title = "选集播放", action = selected.name, onAction = {}) }
                        item {
                            EpisodePanel(
                                episodes = selected.episodes,
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
