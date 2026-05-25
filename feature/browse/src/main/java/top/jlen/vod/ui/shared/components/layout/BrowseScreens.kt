package top.jlen.vod.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import top.jlen.vod.AppConfig
import top.jlen.vod.AppRuntimeInfo
import top.jlen.vod.PLAYER_DESKTOP_UA
import top.jlen.vod.data.AppNotice
import top.jlen.vod.data.AppleCmsCategory
import top.jlen.vod.data.CategoryFilterGroup
import top.jlen.vod.data.FindPasswordEditor
import top.jlen.vod.data.HotSearchGroup
import top.jlen.vod.data.MembershipPlan
import top.jlen.vod.data.PersistentCookieJar
import top.jlen.vod.data.RegisterEditor
import top.jlen.vod.data.UserProfileEditor
import top.jlen.vod.data.VodItem
import top.jlen.vod.data.sanitizeUserFacingComposite


@Composable
internal fun HomeTopBlock(
    onRefresh: () -> Unit,
    noticeState: NoticeUiState,
    onRefreshAnnouncements: () -> Unit,
    onOpenAnnouncementList: () -> Unit,
    onOpenAnnouncementDetail: (String) -> Unit,
    onOpenSearch: () -> Unit,
    pauseMotion: Boolean = false
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "精选片库",
                    style = MaterialTheme.typography.labelLarge,
                    color = UiPalette.TextSecondary
                )
                Text(
                    text = "Jlen 影视",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = UiPalette.Ink
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircleActionButton(icon = Icons.Rounded.NewReleases, onClick = onOpenAnnouncementList)
                CircleActionButton(icon = Icons.Rounded.Refresh, onClick = onRefresh)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        SearchDock(onClick = onOpenSearch)
        Spacer(modifier = Modifier.height(14.dp))
        AnnouncementTickerStrip(
            state = noticeState,
            onRefresh = onRefreshAnnouncements,
            onOpenList = onOpenAnnouncementList,
            onOpenDetail = onOpenAnnouncementDetail,
            pauseMotion = pauseMotion
        )
    }
}

@Composable
private fun SearchDock(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface.copy(alpha = 0.96f)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, UiPalette.BorderSoft.copy(alpha = 0.78f))
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            UiPalette.Surface,
                            UiPalette.AccentGlow.copy(alpha = 0.14f)
                        )
                    )
                )
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = UiPalette.Accent
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "搜索片库",
                style = MaterialTheme.typography.bodyMedium,
                color = UiPalette.TextSecondary
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AnnouncementTickerStrip(
    state: NoticeUiState,
    onRefresh: () -> Unit,
    onOpenList: () -> Unit,
    onOpenDetail: (String) -> Unit,
    pauseMotion: Boolean
) {
    val activeNotices = state.activeNotices

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface.copy(alpha = 0.96f)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, UiPalette.BorderSoft.copy(alpha = 0.78f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NewReleases,
                        contentDescription = null,
                        tint = UiPalette.Accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "公告",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = UiPalette.Ink
                    )
                }
                TextButton(
                    onClick = onOpenList,
                    colors = ButtonDefaults.textButtonColors(contentColor = UiPalette.Accent)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("全部公告", fontWeight = FontWeight.Bold)
                        if (state.hasUnreadActiveNotices) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(UiPalette.Accent, CircleShape)
                            )
                        }
                    }
                }
            }

            when {
                state.isLoading && activeNotices.isEmpty() -> {
                    Text(
                        text = "公告加载中...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = UiPalette.TextSecondary
                    )
                }

                !state.error.isNullOrBlank() && activeNotices.isEmpty() -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onRefresh() }
                            .background(UiPalette.DangerSurface)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = state.error.orEmpty(),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = UiPalette.DangerText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "重试",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = UiPalette.DangerText
                        )
                    }
                }

                activeNotices.isEmpty() -> {
                    Text(
                        text = "当前暂无有效公告",
                        style = MaterialTheme.typography.bodyMedium,
                        color = UiPalette.TextSecondary
                    )
                }

                else -> {
                    val pagerState = rememberPagerState(pageCount = { activeNotices.size })
                    LaunchedEffect(activeNotices.size, pauseMotion) {
                        if (activeNotices.size <= 1) return@LaunchedEffect
                        while (true) {
                            delay(3200)
                            if (pauseMotion) continue
                            val nextPage = (pagerState.currentPage + 1) % activeNotices.size
                            pagerState.animateScrollToPage(nextPage, animationSpec = tween(durationMillis = 600))
                        }
                    }
                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = false,
                        modifier = Modifier.fillMaxWidth()
                    ) { page ->
                        val notice = activeNotices[page]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onOpenDetail(notice.id) }
                                .background(UiPalette.SurfaceSoft)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (notice.isPinned) {
                                Box(
                                    modifier = Modifier
                                        .background(UiPalette.Accent.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
                                        .padding(horizontal = 9.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "置顶",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = UiPalette.Accent
                                    )
                                }
                            }
                            AnnouncementRichInlineText(
                                content = notice.title,
                                modifier = Modifier
                                    .weight(1f)
                                    .then(
                                        if (!pauseMotion && notice.title.length > 14) {
                                            Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                                        } else {
                                            Modifier
                                        }
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = UiPalette.Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Clip
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = UiPalette.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CircleActionButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(UiPalette.Surface)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = UiPalette.Ink)
    }
}

@Composable
private fun PosterImage(
    data: String?,
    title: String,
    width: Int,
    height: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    showFallbackTitle: Boolean = true,
    fallbackStyle: PosterFallbackStyle = PosterFallbackStyle.Default,
    fallbackBottomInset: Dp = 0.dp,
    lightweightPlaceholder: Boolean = false
) {
    RetryablePosterImage(
        data = data,
        title = title,
        width = width,
        height = height,
        modifier = modifier,
        contentScale = contentScale,
        showFallbackTitle = showFallbackTitle,
        fallbackStyle = fallbackStyle,
        fallbackBottomInset = fallbackBottomInset,
        lightweightPlaceholder = lightweightPlaceholder
    )
}


@Composable
fun FeaturedCard(
    item: VodItem,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    lightweightImage: Boolean = false,
    decorativeImage: Boolean = false
) {
    val badgeText = rememberPosterBadgeText(item.resolvedBadgeText, compact = false)
    val subtitle = item.resolvedSubtitle.ifBlank { "精选推荐" }
    val imageWidth = if (lightweightImage) 540 else 720
    val imageHeight = if (lightweightImage) 324 else 432

    Card(
        modifier = modifier
            .clickable { onClick(item.vodId) },
        shape = RoundedCornerShape(UiDimens.LargeContainerRadius),
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
        border = BorderStroke(1.dp, UiPalette.BorderSoft.copy(alpha = 0.62f))
    ) {
        Box {
            PosterImage(
                data = item.vodPic,
                title = if (decorativeImage) "" else item.displayTitle,
                width = imageWidth,
                height = imageHeight,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(196.dp)
                    .then(if (decorativeImage) Modifier.clearAndSetSemantics { } else Modifier),
                contentScale = ContentScale.Crop,
                lightweightPlaceholder = lightweightImage
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0x24000000),
                                Color(0x92000000),
                                Color(0xEE000000)
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.14f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.10f)
                            )
                        )
                    )
            )
            if (badgeText.isNotBlank()) {
                Text(
                    text = badgeText,
                    color = UiPalette.Surface,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(16.dp)
                        .clip(RoundedCornerShape(UiDimens.PillRadius))
                        .background(UiPalette.Accent.copy(alpha = 0.86f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                        .padding(start = UiDimens.CardPadding, end = UiDimens.PagePadding, bottom = UiDimens.PagePadding),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.76f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.displayTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = UiPalette.Surface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(UiDimens.PillRadius))
                        .background(UiPalette.Surface.copy(alpha = 0.14f))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "查看详情",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = UiPalette.Surface
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = UiPalette.Surface,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun FeaturedCarouselSection(
    items: List<VodItem>,
    onOpenDetail: (String) -> Unit,
    pauseMotion: Boolean = false
) {
    if (items.isEmpty()) return
    val rowState = rememberLazyListState()

    LaunchedEffect(items.map { it.stableKey() }, pauseMotion) {
        if (items.size <= 1) return@LaunchedEffect
        while (true) {
            delay(3500)
            if (pauseMotion || rowState.isScrollInProgress) continue
            val currentIndex = rowState.firstVisibleItemIndex.coerceIn(0, items.lastIndex)
            if (currentIndex >= items.lastIndex) {
                rowState.scrollToItem(0)
            } else {
                rowState.animateScrollToItem(currentIndex + 1)
            }
        }
    }

    LazyRow(
        state = rowState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(
            items = items,
            key = { it.stableKey() },
            contentType = { "featured_card" }
        ) { item ->
            FeaturedCard(
                item = item,
                onClick = onOpenDetail,
                modifier = Modifier.width(318.dp),
                lightweightImage = true,
                decorativeImage = true
            )
        }
    }
}

internal enum class AccountNoticeTone {
    Info,
    Error
}

@Composable
internal fun AccountStatusNotice(
    message: String,
    tone: AccountNoticeTone,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val isError = tone == AccountNoticeTone.Error
    val containerColor = if (isError) UiPalette.DangerSurface.copy(alpha = 0.42f) else UiPalette.AccentSoft.copy(alpha = 0.1f)
    val borderColor = if (isError) UiPalette.DangerBorder.copy(alpha = 0.45f) else UiPalette.BorderSoft.copy(alpha = 0.72f)
    val iconTint = if (isError) UiPalette.DangerText else UiPalette.Accent
    val textColor = if (isError) UiPalette.DangerText else UiPalette.Ink
    val icon = if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle
    val badgeText = if (isError) "提示" else "状态"

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = iconTint
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = textColor
                    )
                }
                if (!actionLabel.isNullOrBlank() && onAction != null) {
                    OutlinedButton(
                        onClick = onAction,
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, borderColor),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = textColor
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(actionLabel, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
internal fun InlineEmptyStateCard(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    EmptyPane(
        message = message,
        actionLabel = actionLabel,
        onAction = onAction,
        style = FeedbackPaneStyle.Card
    )
}

@Composable
internal fun PosterGridRow(
    rowItems: List<VodItem>,
    onOpenDetail: (String) -> Unit
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        rowItems.forEach { item ->
            CompactPosterCard(
                item = item,
                onClick = onOpenDetail,
                modifier = Modifier.weight(1f)
            )
        }
        repeat(POSTER_GRID_COLUMNS - rowItems.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
internal fun LoadMoreFooter(
    hasMore: Boolean,
    isLoading: Boolean,
    errorMessage: String? = null,
    onLoadMore: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
                color = UiPalette.Accent
            )
            Text(
                text = "加载中...",
                style = MaterialTheme.typography.bodySmall,
                color = UiPalette.TextMuted
            )
        } else if (!errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = UiPalette.DangerText
            )
            TextButton(
                onClick = onLoadMore,
                colors = ButtonDefaults.textButtonColors(contentColor = UiPalette.Accent)
            ) {
                Text("重试", fontWeight = FontWeight.Bold)
            }
        } else if (hasMore) {
            TextButton(
                onClick = onLoadMore,
                colors = ButtonDefaults.textButtonColors(contentColor = UiPalette.Accent)
            ) {
                Text("继续加载", fontWeight = FontWeight.Bold)
            }
        } else {
            Text(
                text = "没有更多了",
                style = MaterialTheme.typography.bodySmall,
                color = UiPalette.TextMuted
            )
        }
    }
}

@Composable
private fun CompactPosterCard(
    item: VodItem,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val badgeText = rememberPosterBadgeText(item.resolvedBadgeText, compact = true)

    Column(modifier = modifier.clickable { onClick(item.vodId) }) {
        Box {
            PosterImage(
                data = item.vodPic,
                title = item.displayTitle,
                width = 360,
                height = 540,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(154.dp)
                    .clip(RoundedCornerShape(18.dp)),
                contentScale = ContentScale.Crop,
                showFallbackTitle = true,
                fallbackStyle = PosterFallbackStyle.CompactTitle,
                fallbackBottomInset = 30.dp,
                lightweightPlaceholder = true
            )
            if (badgeText.isNotBlank()) {
                PosterBadgeText(
                    text = badgeText,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xDE111419))
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = item.displayTitle,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = UiPalette.Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PosterBadgeText(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.ExtraBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

private val noticeDisplayFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

internal val AppNotice.formattedActiveTime: String
    get() {
        val start = startAt.formatNoticeTime()
        val end = if (endAt.isNeverExpireNoticeTime()) "永不过期" else endAt.formatNoticeTime()
        return when {
            start.isNotBlank() && end.isNotBlank() -> "$start - $end"
            start.isNotBlank() -> start
            end.isNotBlank() -> end
            else -> ""
        }
    }

internal val AppNotice.formattedPublishTime: String
    get() = createdAt.formatNoticeTime().ifBlank { updatedAt.formatNoticeTime() }

private fun String.formatNoticeTime(): String {
    val raw = trim()
    if (raw.isBlank()) return ""
    val timeMillis = raw.toLongOrNull()?.let { numeric ->
        if (raw.length <= 10) numeric * 1000 else numeric
    } ?: return raw
    return runCatching {
        noticeDisplayFormatter.format(Date(timeMillis))
    }.getOrDefault(raw)
}

private fun String.isNeverExpireNoticeTime(): Boolean {
    val raw = trim()
    if (raw.isBlank()) return true
    val normalized = raw.lowercase(Locale.ROOT)
    if (
        normalized in setOf(
            "never",
            "forever",
            "permanent",
            "no_expire",
            "no_expiry",
            "unlimited",
            "0",
            "0000-00-00",
            "0000-00-00 00:00",
            "0000-00-00 00:00:00",
            "永不失效",
            "永不过期",
            "长期有效",
            "永久"
        )
    ) {
        return true
    }
    if (normalized.startsWith("1970-01-01")) return true
    val millis = raw.toLongOrNull()?.let { numeric ->
        if (raw.length <= 10) numeric * 1000 else numeric
    } ?: runCatching {
        noticeDisplayFormatter.parse(raw)?.time
    }.getOrNull()
    val year = millis?.let {
        java.util.Calendar.getInstance().apply {
            timeInMillis = it
        }.get(java.util.Calendar.YEAR)
    }
    return year != null && (year <= 1970 || year >= 2035)
}

@Composable
private fun rememberPosterBadgeText(raw: String, compact: Boolean): String =
    remember(raw, compact) {
        formatPosterBadge(raw = raw, compact = compact)
    }

internal fun LazyListState.maxVisiblePosterRowIndex(rowKeyPrefix: String): Int =
    layoutInfo.visibleItemsInfo
        .mapNotNull { itemInfo ->
            val key = itemInfo.key.toString()
            if (!key.startsWith(rowKeyPrefix)) return@mapNotNull null
            key.removePrefix(rowKeyPrefix)
                .substringBefore('-')
                .toIntOrNull()
        }
        .maxOrNull() ?: -1

internal fun shouldAutoPreloadRows(
    lastVisibleRowIndex: Int,
    totalRows: Int,
    hasMore: Boolean,
    isLoading: Boolean
): Boolean {
    if (!hasMore || isLoading || totalRows <= 0 || lastVisibleRowIndex < 0) return false
    val triggerRowIndex = (totalRows - GRID_AUTO_PRELOAD_REMAINING_ROWS - 1).coerceAtLeast(0)
    return lastVisibleRowIndex >= triggerRowIndex
}

private const val GRID_AUTO_PRELOAD_REMAINING_ROWS = 6

@Composable
internal fun ListCard(item: VodItem, onClick: (String) -> Unit) {
    val detailDescription = item.description
        .takeIf {
            it.isNotBlank() &&
                it != "暂无简介" &&
                it != item.resolvedSubtitle &&
                it != item.resolvedBadgeText
        }
    val compactMeta = item.resolvedSubtitle.ifBlank { "站内资源" }
    val badgeText = rememberPosterBadgeText(item.resolvedBadgeText, compact = false)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(item.vodId) },
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface.copy(alpha = 0.96f)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, UiPalette.BorderSoft.copy(alpha = 0.78f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PosterImage(
                data = item.vodPic,
                title = item.displayTitle,
                width = 312,
                height = 414,
                modifier = Modifier
                    .size(width = 94.dp, height = 128.dp)
                    .clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop,
                fallbackStyle = PosterFallbackStyle.CompactTitle,
                lightweightPlaceholder = true
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(
                    text = item.displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = UiPalette.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = compactMeta,
                    style = MaterialTheme.typography.labelMedium,
                    color = UiPalette.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                detailDescription?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = UiPalette.TextMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    badgeText
                        .takeIf { it.isNotBlank() }
                        ?.let { badge ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(UiDimens.PillRadius))
                                .background(UiPalette.SurfaceSoft)
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = UiPalette.TextSecondary,
                                maxLines = 1
                            )
                        }
                    } ?: Spacer(modifier = Modifier.width(1.dp))
                    TextButton(
                        onClick = { onClick(item.vodId) },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = UiPalette.Accent)
                    ) {
                        Text(
                            text = "查看详情",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AuthenticatedAvatar(
    imageUrl: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val cookieJar = remember(context) { PersistentCookieJar(context.applicationContext) }
    val imageClient = remember(context, cookieJar) {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .build()
    }
    val imageBytes by produceState<ByteArray?>(initialValue = null, context, imageUrl) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(imageUrl)
                    .header("Referer", AppConfig.appleCmsBaseUrl)
                    .header("Origin", AppConfig.appleCmsBaseUrl.trimEnd('/'))
                    .header("User-Agent", PLAYER_DESKTOP_UA)
                    .build()
                imageClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.bytes()
                }
            }.getOrNull()
        }
    }
    val bitmap = remember(imageBytes) {
        imageBytes?.let { bytes ->
            runCatching {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    }
}

internal fun VodItem.stableKey(): String = vodId.ifBlank { "$displayTitle|${vodPic.orEmpty()}" }

