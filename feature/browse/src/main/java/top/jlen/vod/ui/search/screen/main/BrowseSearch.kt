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
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
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
import kotlinx.coroutines.flow.distinctUntilChanged
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
import top.jlen.vod.data.SearchSuggestionItem
import top.jlen.vod.data.UserProfileEditor
import top.jlen.vod.data.VodItem
import top.jlen.vod.data.sanitizeUserFacingComposite


@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onOpenSearchResults: (String) -> Unit,
    onSearchHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onLoadHotSearches: () -> Unit
) {
    LaunchedEffect(Unit) {
        onLoadHotSearches()
    }
    SearchLandingContent(
        state = state,
        onQueryChange = onQueryChange,
        onOpenSearchResults = onOpenSearchResults,
        onSearchHistory = onSearchHistory,
        onClearHistory = onClearHistory,
        onLoadHotSearches = onLoadHotSearches
    )
}

internal const val POSTER_GRID_COLUMNS = 3

internal fun LazyListScope.posterGridRows(
    rows: List<List<VodItem>>,
    rowKeyPrefix: String,
    onOpenDetail: (String) -> Unit
) {
    items(
        count = rows.size,
        key = { rowIndex ->
            val firstKey = rows[rowIndex].firstOrNull()?.stableKey().orEmpty()
            "$rowKeyPrefix-$rowIndex-$firstKey"
        },
        contentType = { "poster_row" }
    ) { rowIndex ->
        PosterGridRow(
            rowItems = rows[rowIndex],
            onOpenDetail = onOpenDetail
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchLandingContent(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onOpenSearchResults: (String) -> Unit,
    onSearchHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onLoadHotSearches: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(UiPalette.BackgroundBottom)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        item {
            Text(
                text = "搜索",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = UiPalette.Ink
            )
        }
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = UiPalette.SurfaceSoft.copy(alpha = 0.86f)
                ),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, UiPalette.BorderSoft.copy(alpha = 0.75f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SearchInputCard(
                        query = state.query,
                        onQueryChange = onQueryChange,
                        onSearch = { onOpenSearchResults(state.query.trim()) },
                        suggestions = if (state.suggestSubmittedQuery == state.query.trim()) state.suggestions else emptyList(),
                        onPickSuggestion = { keyword ->
                            onSearchHistory(keyword)
                            onOpenSearchResults(keyword)
                        }
                    )
                }
            }
        }
        if (state.history.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = UiPalette.Surface.copy(alpha = 0.92f)
                    ),
                    shape = RoundedCornerShape(26.dp),
                    border = BorderStroke(1.dp, UiPalette.BorderSoft.copy(alpha = 0.7f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
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
                                    imageVector = Icons.Rounded.History,
                                    contentDescription = null,
                                    tint = UiPalette.TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "搜索记录",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = UiPalette.Ink
                                )
                            }
                            TextButton(onClick = onClearHistory) {
                                Text("清空")
                            }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(
                                items = state.history,
                                key = { it },
                                contentType = { "history" }
                            ) { keyword ->
                                AssistChip(
                                    onClick = {
                                        onSearchHistory(keyword)
                                        onOpenSearchResults(keyword)
                                    },
                                    label = { Text(keyword) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = UiPalette.SurfaceSoft,
                                        labelColor = UiPalette.Ink
                                    ),
                                    border = AssistChipDefaults.assistChipBorder(
                                        borderColor = UiPalette.BorderSoft,
                                        enabled = true
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            SectionTitle(
                title = "热搜榜",
                action = null,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Whatshot,
                        contentDescription = null,
                        tint = UiPalette.Accent,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onAction = {}
            )
        }
        item {
            when {
                state.isHotSearchLoading && state.hotSearchGroups.isEmpty() ->
                    LoadingPane("热搜加载中...", style = FeedbackPaneStyle.Card)
                !state.hotSearchError.isNullOrBlank() && state.hotSearchGroups.isEmpty() ->
                    ErrorBanner(message = state.hotSearchError.orEmpty(), onRetry = onLoadHotSearches)
                state.hotSearchGroups.isEmpty() -> EmptyPane(
                    message = "暂无热搜",
                    description = "稍后刷新看看，或者直接搜索想看的影片",
                    actionLabel = "刷新",
                    onAction = onLoadHotSearches,
                    style = FeedbackPaneStyle.Card
                )
                else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.hotSearchGroups.forEach { group ->
                        HotSearchBoard(
                            group = group,
                            onPickKeyword = { keyword ->
                                onSearchHistory(keyword)
                                onOpenSearchResults(keyword)
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultsScreen(
    state: SearchUiState,
    resultKey: String,
    initialScrollIndex: Int,
    initialScrollOffset: Int,
    onScrollPositionChange: (Int, Int) -> Unit,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onPickSuggestion: (String) -> Unit,
    onLoadMore: () -> Unit,
    onOpenDetail: (String) -> Unit
) {
    val listState = rememberSaveable(resultKey, saver = LazyListState.Saver) {
        LazyListState(
            firstVisibleItemIndex = initialScrollIndex,
            firstVisibleItemScrollOffset = initialScrollOffset
        )
    }
    LaunchedEffect(resultKey, listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                onScrollPositionChange(index, offset)
            }
    }
    LaunchedEffect(listState, state.results.size, state.hasMore, state.isAppending, state.isLoading) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                val resultsStartIndex = 3
                val preloadThreshold = (resultsStartIndex + state.results.size - 3).coerceAtLeast(resultsStartIndex)
                if (
                    !state.isLoading &&
                        !state.isAppending &&
                        state.hasMore &&
                        state.results.isNotEmpty() &&
                        lastVisibleIndex >= preloadThreshold
                ) {
                    onLoadMore()
                }
            }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(UiPalette.BackgroundBottom)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircleActionButton(icon = Icons.AutoMirrored.Rounded.ArrowBack, onClick = onBack)
                Text(
                    text = "搜索结果",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = UiPalette.Ink
                )
            }
        }
        item {
            SearchInputCard(
                query = state.query,
                onQueryChange = onQueryChange,
                onSearch = onSearch,
                suggestions = if (state.suggestSubmittedQuery == state.query.trim()) state.suggestions else emptyList(),
                onPickSuggestion = onPickSuggestion
            )
        }
        item {
            Text(
                text = if (state.submittedQuery.isBlank()) "输入片名开始搜索" else "\"${state.submittedQuery}\" 的结果",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = UiPalette.TextPrimary
            )
        }
        when {
            state.isLoading -> item { LoadingPane("搜索中...", style = FeedbackPaneStyle.Card) }
            !state.error.isNullOrBlank() && state.submittedQuery.isBlank() && state.results.isEmpty() ->
                item { SearchEmptyState(query = "", message = state.error.orEmpty()) }
            !state.error.isNullOrBlank() && state.results.isEmpty() ->
                item { ErrorBanner(message = state.error.orEmpty(), onRetry = onSearch) }
            state.results.isEmpty() ->
                item { SearchEmptyState(query = state.submittedQuery) }
            else -> items(
                items = state.results,
                key = { it.stableKey() },
                contentType = { "search_result" }
            ) { item ->
                ListCard(item = item, onClick = onOpenDetail)
            }
        }
        if (state.results.isNotEmpty()) {
            item {
                LoadMoreFooter(
                    hasMore = state.hasMore,
                    isLoading = state.isAppending,
                    errorMessage = state.appendError,
                    onLoadMore = onLoadMore
                )
            }
        }
    }
}

@Composable
private fun SearchEmptyState(
    query: String,
    message: String? = null
) {
    val title = when {
        message != null && query.isBlank() -> message
        query.isBlank() -> "输入片名开始搜索"
        else -> "没有找到“$query”"
    }
    val description = when {
        query.isBlank() -> "也可以试试主角、导演或别名"
        else -> "换个关键词再试试，片名、主角和别名都可以"
    }
    EmptyPane(
        message = title,
        description = description,
        icon = Icons.Rounded.Search,
        style = FeedbackPaneStyle.Card
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchInputCard(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    suggestions: List<SearchSuggestionItem> = emptyList(),
    onPickSuggestion: (String) -> Unit = {}
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, UiPalette.BorderSoft.copy(alpha = 0.78f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = UiPalette.TextMuted,
                    modifier = Modifier.size(20.dp)
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = UiPalette.Ink,
                        unfocusedTextColor = UiPalette.Ink,
                        cursorColor = UiPalette.Accent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedPlaceholderColor = UiPalette.TextMuted,
                        unfocusedPlaceholderColor = UiPalette.TextMuted
                    ),
                    placeholder = { Text("搜片名") },
                    trailingIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            if (query.isNotBlank()) {
                                TextButton(
                                    onClick = { onQueryChange("") },
                                    colors = ButtonDefaults.textButtonColors(contentColor = UiPalette.TextSecondary),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "清空搜索关键词",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            TextButton(
                                onClick = onSearch,
                                colors = ButtonDefaults.textButtonColors(contentColor = UiPalette.Accent),
                                contentPadding = PaddingValues(start = 2.dp, top = 0.dp, end = 0.dp, bottom = 0.dp)
                            ) {
                                Text("搜索", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                )
            }
            if (suggestions.isNotEmpty()) {
                val visibleSuggestions = suggestions.take(4)
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(UiPalette.BorderSoft.copy(alpha = 0.55f))
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "猜你想搜",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = UiPalette.TextSecondary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                    visibleSuggestions.forEachIndexed { index, suggestion ->
                        SearchSuggestionRow(
                            item = suggestion,
                            onClick = { onPickSuggestion(suggestion.title.ifBlank { suggestion.highlight }) }
                        )
                        if (index != visibleSuggestions.lastIndex) {
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(UiPalette.BorderSoft.copy(alpha = 0.28f))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSuggestionRow(
    item: SearchSuggestionItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(UiPalette.SurfaceSoft.copy(alpha = 0.78f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = UiPalette.TextMuted,
                modifier = Modifier.size(12.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = buildSuggestionAnnotatedTitle(item),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = UiPalette.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            buildSuggestionMeta(item)
                .takeIf { it.isNotBlank() }
                ?.let { meta ->
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = UiPalette.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            tint = UiPalette.TextMuted,
            modifier = Modifier.size(12.dp)
        )
    }
}

private fun buildSuggestionMeta(item: SearchSuggestionItem): String =
    listOf(
        item.subTitle.takeIf { it.isNotBlank() },
        item.remarks.takeIf { it.isNotBlank() },
        listOf(item.typeParentName, item.typeName)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" / ")
            .takeIf { it.isNotBlank() }
    ).filterNotNull()
        .distinct()
        .joinToString(" · ")

private fun buildSuggestionAnnotatedTitle(item: SearchSuggestionItem): AnnotatedString {
    val raw = item.highlight.ifBlank { item.title }
    if (!raw.contains("<em>", ignoreCase = true)) {
        return AnnotatedString(raw.replace(Regex("<[^>]+>"), ""))
    }
    val regex = Regex("(?i)<em>(.*?)</em>")
    val plain = raw.replace(Regex("<[^>]+>"), "")
    val matches = regex.findAll(raw).toList()
    if (matches.isEmpty()) return AnnotatedString(plain)

    val highlightedSegments = matches.map { it.groupValues.getOrNull(1).orEmpty() }.filter { it.isNotBlank() }
    if (highlightedSegments.isEmpty()) return AnnotatedString(plain)

    return buildAnnotatedString {
        var cursor = 0
        highlightedSegments.forEach { segment ->
            val start = plain.indexOf(segment, startIndex = cursor)
            if (start < 0) return@forEach
            if (start > cursor) {
                append(plain.substring(cursor, start))
            }
            withStyle(SpanStyle(color = UiPalette.Accent, fontWeight = FontWeight.ExtraBold)) {
                append(segment)
            }
            cursor = start + segment.length
        }
        if (cursor < plain.length) {
            append(plain.substring(cursor))
        }
    }
}

@Composable
private fun HotSearchBoard(
    group: HotSearchGroup,
    onPickKeyword: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface.copy(alpha = 0.96f)),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, UiPalette.BorderSoft.copy(alpha = 0.75f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(UiPalette.AccentSoft.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = group.platform,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = UiPalette.Accent
                    )
                }
                Text(
                    text = "实时热度",
                    style = MaterialTheme.typography.labelMedium,
                    color = UiPalette.TextMuted
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            group.items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onPickKeyword(item.keyword) }
                        .padding(horizontal = 6.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (item.rank <= 3) UiPalette.AccentSoft.copy(alpha = 0.2f)
                                else UiPalette.SurfaceSoft.copy(alpha = 0.9f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item.rank.toString().padStart(2, '0'),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (item.rank <= 3) UiPalette.Accent else UiPalette.TextSecondary
                        )
                    }
                    Text(
                        text = item.keyword,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = UiPalette.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = UiPalette.TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (index != group.items.lastIndex) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(UiPalette.BorderSoft.copy(alpha = 0.5f))
                    )
                }
            }
        }
    }
}

