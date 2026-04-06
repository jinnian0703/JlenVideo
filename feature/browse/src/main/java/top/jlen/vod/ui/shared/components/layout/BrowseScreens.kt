package top.jlen.vod.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
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
    onOpenSearch: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Featured Library",
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
            onOpenDetail = onOpenAnnouncementDetail
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
    onOpenDetail: (String) -> Unit
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
                    LaunchedEffect(activeNotices.size) {
                        if (activeNotices.size <= 1) return@LaunchedEffect
                        while (true) {
                            delay(3200)
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
                            Text(
                                text = notice.title,
                                modifier = Modifier
                                    .weight(1f)
                                    .then(
                                        if (notice.title.length > 14) {
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
fun FeaturedCard(item: VodItem, onClick: (String) -> Unit) {
    val badgeText = compactPosterBadgeText(item.badgeText)

    Card(
        modifier = Modifier
            .width(312.dp)
            .clickable { onClick(item.vodId) },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface)
    ) {
        Box {
            AsyncImage(
                model = rememberPosterRequest(
                    data = item.vodPic,
                    width = 720,
                    height = 432
                ),
                contentDescription = item.displayTitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(188.dp),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xA0000000), Color(0xCC000000))
                        )
                    )
            )
            if (badgeText.isNotBlank()) {
                Text(
                    text = badgeText,
                    color = UiPalette.Surface,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .padding(14.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(UiPalette.Accent.copy(alpha = 0.92f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = item.displayTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = UiPalette.Surface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FeaturedCarouselSection(items: List<VodItem>, onOpenDetail: (String) -> Unit) {
    val actualCount = items.size
    val loopItems = remember(items) {
        when {
            items.isEmpty() -> emptyList()
            items.size == 1 -> items
            else -> listOf(items.last()) + items + listOf(items.first())
        }
    }
    val pagerState = rememberPagerState(
        initialPage = if (actualCount > 1) 1 else 0,
        pageCount = { loopItems.size }
    )
    val currentIndex = when {
        actualCount <= 1 -> 0
        pagerState.currentPage <= 0 -> actualCount - 1
        pagerState.currentPage >= loopItems.lastIndex -> 0
        else -> pagerState.currentPage - 1
    }
    val settledIndex = when {
        actualCount <= 1 -> 0
        pagerState.settledPage <= 0 -> actualCount - 1
        pagerState.settledPage >= loopItems.lastIndex -> 0
        else -> pagerState.settledPage - 1
    }

    LaunchedEffect(actualCount, pagerState.settledPage) {
        if (actualCount <= 1) return@LaunchedEffect
        when (pagerState.settledPage) {
            0 -> pagerState.scrollToPage(actualCount)
            loopItems.lastIndex -> pagerState.scrollToPage(1)
        }
    }

    LaunchedEffect(actualCount) {
        if (actualCount <= 1) return@LaunchedEffect
        while (true) {
            delay(UiMotion.CarouselAutoScrollMillis)
            if (!pagerState.isScrollInProgress) {
                pagerState.animateScrollToPage(
                    page = pagerState.currentPage + 1,
                    animationSpec = tween(UiMotion.CarouselSlideMillis)
                )
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
            userScrollEnabled = actualCount > 1,
            key = { page -> loopItems[page].vodId.ifBlank { "featured_$page" } + "_$page" }
        ) { page ->
            FeaturedCard(
                item = loopItems[page],
                onClick = onOpenDetail
            )
        }

        if (actualCount > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(actualCount) { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (index == currentIndex) 18.dp else 8.dp, 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == settledIndex || index == currentIndex) UiPalette.Accent
                                else UiPalette.BorderSoft
                            )
                    )
                }
            }
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
    val containerColor = if (isError) UiPalette.DangerSurface.copy(alpha = 0.58f) else UiPalette.AccentSoft.copy(alpha = 0.14f)
    val borderColor = if (isError) UiPalette.DangerBorder.copy(alpha = 0.65f) else UiPalette.BorderSoft
    val iconTint = if (isError) UiPalette.DangerText else UiPalette.Accent
    val textColor = if (isError) UiPalette.DangerText else UiPalette.Ink
    val icon = if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle
    val badgeText = if (isError) "提示" else "状态"

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(26.dp),
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
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = if (isError) 0.86f else 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
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
                        fontWeight = FontWeight.SemiBold,
                        color = textColor
                    )
                }
                if (!actionLabel.isNullOrBlank() && onAction != null) {
                    OutlinedButton(
                        onClick = onAction,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, borderColor),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White.copy(alpha = 0.52f),
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
    Card(
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface.copy(alpha = 0.96f)),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, UiPalette.BorderSoft.copy(alpha = 0.78f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(UiPalette.AccentSoft.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = UiPalette.Accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = UiPalette.Ink
                )
            }
            if (!actionLabel.isNullOrBlank() && onAction != null) {
                TextButton(
                    onClick = onAction,
                    colors = ButtonDefaults.textButtonColors(contentColor = UiPalette.Accent)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(actionLabel, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
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
    val badgeText = compactPosterBadgeText(item.badgeText)

    Column(modifier = modifier.clickable { onClick(item.vodId) }) {
        Box {
            AsyncImage(
                model = rememberPosterRequest(
                    data = item.vodPic,
                    width = 360,
                    height = 540
                ),
                contentDescription = item.displayTitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(154.dp)
                    .clip(RoundedCornerShape(18.dp)),
                contentScale = ContentScale.Crop
            )
            if (badgeText.isNotBlank()) {
                PosterBadgeText(
                    text = badgeText,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xB825252B))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = item.displayTitle,
            style = MaterialTheme.typography.titleSmall,
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
    val view = LocalView.current
    var isVisibleInWindow by remember(text) { mutableStateOf(false) }

    Text(
        text = text,
        color = UiPalette.Surface,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        overflow = if (isVisibleInWindow) TextOverflow.Clip else TextOverflow.Ellipsis,
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                val visibleFrame = Rect()
                view.getWindowVisibleDisplayFrame(visibleFrame)
                val bounds = coordinates.boundsInWindow()
                isVisibleInWindow =
                    bounds.right > visibleFrame.left &&
                        bounds.left < visibleFrame.right &&
                        bounds.bottom > visibleFrame.top &&
                        bounds.top < visibleFrame.bottom
            }
            .then(
                if (isVisibleInWindow && text.length > 8) {
                    Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                } else {
                    Modifier
                }
            )
    )
}

private val noticeDisplayFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

internal val AppNotice.formattedActiveTime: String
    get() {
        val start = startAt.formatNoticeTime()
        val end = endAt.formatNoticeTime()
        return when {
            start.isNotBlank() && end.isNotBlank() -> "$start - $end"
            start.isNotBlank() -> start
            end.isNotBlank() -> end
            else -> ""
        }
    }

internal val AppNotice.formattedPublishTime: String
    get() = createdAt.formatNoticeTime().ifBlank { updatedAt.formatNoticeTime() }

internal enum class AnnouncementBlockKind {
    Title,
    Heading,
    Paragraph
}

internal data class AnnouncementRichBlock(
    val text: AnnotatedString,
    val alignment: TextAlign = TextAlign.Start,
    val kind: AnnouncementBlockKind = AnnouncementBlockKind.Paragraph,
    val textColor: Color? = null
)

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

internal fun isAnnouncementListLine(text: String): Boolean =
    text.startsWith("- ") ||
        text.startsWith("* ") ||
        text.startsWith("•") ||
        Regex("^\\d+[.、]\\s*.+").matches(text)

internal fun String.removeAnnouncementListPrefix(): String =
    replaceFirst(Regex("^(-|\\*|•)\\s*"), "")
        .replaceFirst(Regex("^\\d+[.、]\\s*"), "")
        .trim()

internal fun parseAnnouncementHtmlBlocks(html: String): List<AnnouncementRichBlock> {
    val normalized = Parser.unescapeEntities(html.trim(), false).trim()
    if (normalized.isBlank() || !normalized.contains('<')) return emptyList()

    val body = Jsoup.parseBodyFragment(normalized).body()
    val blocks = mutableListOf<AnnouncementRichBlock>()
    body.childNodes().forEach { node ->
        blocks += node.toAnnouncementBlocks()
    }
    return blocks.filter { it.text.text.isNotBlank() }
}

private fun Node.toAnnouncementBlocks(): List<AnnouncementRichBlock> {
    return when (this) {
        is TextNode -> text()
            .trim()
            .takeIf(String::isNotBlank)
            ?.let {
                listOf(
                    AnnouncementRichBlock(
                        text = AnnotatedString(it),
                        kind = AnnouncementBlockKind.Paragraph
                    )
                )
            }
            .orEmpty()

        is Element -> when (tagName().lowercase(Locale.ROOT)) {
            in announcementContainerTags ->
                if (children().any { it.isAnnouncementBlockElement() }) {
                    childNodes().flatMap { it.toAnnouncementBlocks() }
                } else {
                    val text = buildAnnouncementAnnotatedString(this)
                    if (text.text.isBlank()) emptyList() else {
                        listOf(
                            AnnouncementRichBlock(
                                text = text,
                                alignment = resolveAnnouncementAlignment(),
                                kind = resolveAnnouncementBlockKind(),
                                textColor = resolveAnnouncementTextColor()
                            )
                        )
                    }
                }

            "ul", "ol" -> children().flatMapIndexed { index, child ->
                val childText = buildAnnouncementAnnotatedString(child).text.trim()
                if (childText.isBlank()) {
                    emptyList()
                } else {
                    val prefix = if (tagName().equals("ol", ignoreCase = true)) "${index + 1}. " else "? "
                    listOf(
                        AnnouncementRichBlock(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = UiPalette.Accent, fontWeight = FontWeight.Bold)) {
                                    append(prefix)
                                }
                                append(buildAnnouncementAnnotatedString(child))
                            },
                            alignment = child.resolveAnnouncementAlignment(),
                            kind = AnnouncementBlockKind.Paragraph,
                            textColor = child.resolveAnnouncementTextColor()
                        )
                    )
                }
            }

            "br" -> emptyList()
            else -> {
                val text = buildAnnouncementAnnotatedString(this)
                if (text.text.isBlank()) {
                    children().flatMap { it.toAnnouncementBlocks() }
                } else {
                    listOf(
                        AnnouncementRichBlock(
                            text = text,
                            alignment = resolveAnnouncementAlignment(),
                            kind = resolveAnnouncementBlockKind(),
                            textColor = resolveAnnouncementTextColor()
                        )
                    )
                }
            }
        }

        else -> emptyList()
    }
}

private fun buildAnnouncementAnnotatedString(node: Node): AnnotatedString {
    val builder = AnnotatedString.Builder()
    appendAnnouncementNode(builder, node)
    return builder.toAnnotatedString()
}

private fun appendAnnouncementNode(builder: AnnotatedString.Builder, node: Node) {
    when (node) {
        is TextNode -> {
            val normalized = node.text()
                .replace(Regex("\\s+"), " ")
            builder.append(normalized)
        }
        is Element -> {
            if (node.tagName().equals("br", ignoreCase = true)) {
                builder.append('\n')
                return
            }

            val style = node.resolveAnnouncementSpanStyle()
            val handledBlock = node.tagName().lowercase(Locale.ROOT) in setOf("p", "div", "section", "article")
            if (style != null) {
                builder.pushStyle(style)
            }
            node.childNodes().forEach { child ->
                appendAnnouncementNode(builder, child)
            }
            if (style != null) {
                builder.pop()
            }
            if (handledBlock && !builder.endsWithLineBreak()) {
                builder.append('\n')
            }
        }
    }
}

private fun Element.resolveAnnouncementBlockKind(): AnnouncementBlockKind {
    val tag = tagName().lowercase(Locale.ROOT)
    return when {
        tag in setOf("h1", "h2") -> AnnouncementBlockKind.Title
        tag in setOf("h3", "h4", "h5", "h6") -> AnnouncementBlockKind.Heading
        tag == "p" && text().trim().length <= 20 && containsBoldContent() -> AnnouncementBlockKind.Heading
        else -> AnnouncementBlockKind.Paragraph
    }
}

private fun Element.resolveAnnouncementAlignment(): TextAlign {
    val style = attr("style").lowercase(Locale.ROOT)
    return when {
        style.contains("text-align:center") || tagName().equals("center", ignoreCase = true) -> TextAlign.Center
        style.contains("text-align:right") -> TextAlign.End
        else -> TextAlign.Start
    }
}

private fun Element.resolveAnnouncementTextColor(): Color? {
    val style = attr("style")
    val colorValue = Regex("""color\s*:\s*([^;]+)""", RegexOption.IGNORE_CASE)
        .find(style)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        .orEmpty()
    if (colorValue.isBlank()) return null
    return runCatching { Color(android.graphics.Color.parseColor(colorValue)) }.getOrNull()
}

private fun Element.resolveAnnouncementSpanStyle(): SpanStyle? {
    var hasStyle = false
    var color: Color? = null
    var fontWeight: FontWeight? = null

    when (tagName().lowercase(Locale.ROOT)) {
        "b", "strong" -> {
            fontWeight = FontWeight.Bold
            hasStyle = true
        }
    }

    resolveAnnouncementTextColor()?.let {
        color = it
        hasStyle = true
    }

    if (!hasStyle) return null
    return SpanStyle(
        color = color ?: Color.Unspecified,
        fontWeight = fontWeight
    )
}

private fun Element.containsBoldContent(): Boolean =
    select("strong, b").isNotEmpty()

private fun AnnotatedString.Builder.endsWithLineBreak(): Boolean =
    length > 0 && toAnnotatedString().text.last() == '\n'

private fun Element.isAnnouncementBlockElement(): Boolean =
    tagName().lowercase(Locale.ROOT) in announcementBlockTags

private val announcementContainerTags = setOf("div", "section", "article", "main")

private val announcementBlockTags = setOf(
    "div",
    "section",
    "article",
    "p",
    "ul",
    "ol",
    "li",
    "h1",
    "h2",
    "h3",
    "h4",
    "h5",
    "h6",
    "center"
)

private fun compactPosterBadgeText(raw: String): String {
    val normalized = raw
        .replace(Regex("\\s+"), " ")
        .trim()
    if (normalized.isBlank()) return ""

    val trimmedRankPrefix = normalized
        .replace(Regex("^NO\\s*\\d+[\\d\\s]*"), "")
        .trim()

    val compactEpisodeBadge = when {
        trimmedRankPrefix.matches(Regex("^更新至第\\d{1,4}集?$")) ->
            trimmedRankPrefix.replace(Regex("^更新至第(\\d{1,4})集?$"), "第$1集")
        trimmedRankPrefix.matches(Regex("^更新至\\d{1,4}集?$")) ->
            trimmedRankPrefix.replace(Regex("^更新至(\\d{1,4})集?$"), "第$1集")
        trimmedRankPrefix.matches(Regex("^第\\d{1,4}$")) -> "${trimmedRankPrefix}集"
        else -> trimmedRankPrefix
    }

    return when {
        compactEpisodeBadge.matches(Regex("^[.、·•-]+$")) -> ""
        compactEpisodeBadge.isBlank() -> ""
        else -> compactEpisodeBadge
    }
}

private fun normalizePosterBadgeText(raw: String): String {
    val normalized = raw
        .replace(Regex("\\s+"), " ")
        .trim()
    if (normalized.isBlank()) return ""

    val trimmedRankPrefix = normalized
        .replace(Regex("^NO\\s*\\d+[\\d\\s]*"), "")
        .trim()

    val normalizedEpisodeBadge = when {
        trimmedRankPrefix.matches(Regex("^更新至第\\d{1,4}$")) -> "${trimmedRankPrefix}集"
        trimmedRankPrefix.matches(Regex("^第\\d{1,4}$")) -> "${trimmedRankPrefix}集"
        else -> trimmedRankPrefix
    }

    return when {
        normalizedEpisodeBadge.matches(Regex("^[.、·•-]+$")) -> ""
        normalizedEpisodeBadge.isBlank() -> ""
        else -> normalizedEpisodeBadge
    }
}

private fun sanitizePosterBadge(raw: String): String {
    val normalized = raw
        .replace(Regex("\\s+"), " ")
        .trim()
    if (normalized.isBlank()) return ""

    val trimmedRankPrefix = normalized
        .replace(Regex("^NO\\s*\\d+[\\d\\s]*"), "")
        .trim()

    return when {
        trimmedRankPrefix.matches(Regex("^[.、·•-]+$")) -> ""
        trimmedRankPrefix.isBlank() -> ""
        else -> trimmedRankPrefix
    }
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
                it != item.subtitle &&
                it != item.badgeText
        }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(item.vodId) },
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface.copy(alpha = 0.96f)),
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, UiPalette.BorderSoft.copy(alpha = 0.78f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AsyncImage(
                model = rememberPosterRequest(
                    data = item.vodPic,
                    width = 312,
                    height = 414
                ),
                contentDescription = item.displayTitle,
                modifier = Modifier
                    .size(width = 104.dp, height = 138.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = UiPalette.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(UiPalette.SurfaceSoft)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = item.subtitle.ifBlank { "站内资源" },
                        style = MaterialTheme.typography.labelMedium,
                        color = UiPalette.TextSecondary
                    )
                }
                detailDescription?.let {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = UiPalette.TextMuted,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
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

