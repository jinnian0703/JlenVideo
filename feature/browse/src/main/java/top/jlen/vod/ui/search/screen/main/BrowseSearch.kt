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
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        item {
            Text(
                text = "搜索",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = UiPalette.Ink
            )
        }
        item {
            SearchInputCard(
                query = state.query,
                onQueryChange = onQueryChange,
                onSearch = { onOpenSearchResults(state.query.trim()) }
            )
        }
        if (state.history.isNotEmpty()) {
            item {
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
            }
            item {
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
                                containerColor = UiPalette.Surface,
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
                state.isHotSearchLoading && state.hotSearchGroups.isEmpty() -> LoadingPane("热搜加载中..")
                !state.hotSearchError.isNullOrBlank() && state.hotSearchGroups.isEmpty() ->
                    ErrorBanner(message = state.hotSearchError.orEmpty(), onRetry = onLoadHotSearches)
                state.hotSearchGroups.isEmpty() -> EmptyPane("暂无热搜")
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

/*
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultsScreen(
    state: SearchUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenDetail: (String) -> Unit
) {
    val listState = rememberSaveable(state.submittedQuery, saver = LazyListState.Saver) {
        LazyListState()
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
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
                onSearch = onSearch
            )
        }
        item {
            Text(
                text = if (state.submittedQuery.isBlank()) "输入片名开始搜索" else "“${state.submittedQuery}” 的结果",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = UiPalette.Ink
            )
        }
        when {
            state.isLoading -> item { LoadingPane("搜索中..") }
            !state.error.isNullOrBlank() && state.results.isEmpty() ->
                item { ErrorBanner(message = state.error, onRetry = onSearch) }
            state.results.isEmpty() ->
                item { EmptyPane(if (state.submittedQuery.isBlank()) "输入片名搜索" else "暂无结果") }
            else -> items(
                items = state.results,
                key = { it.stableKey() },
                contentType = { "search_result" }
            ) { item ->
                ListCard(item = item, onClick = onOpenDetail)
            }
        }
    }
}
*/

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
            .collect { (index, offset) ->
                onScrollPositionChange(index, offset)
            }
    }
    LaunchedEffect(listState, state.results.size, state.hasMore, state.isAppending, state.isLoading) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
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
                onSearch = onSearch
            )
        }
        item {
            Text(
                text = if (state.submittedQuery.isBlank()) "输入片名开始搜索" else "\"${state.submittedQuery}\" 的结果",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = UiPalette.Ink
            )
        }
        when {
            state.isLoading -> item { LoadingPane("搜索中..") }
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
        else -> "没找到“$query”"
    }
    val description = when {
        query.isBlank() -> "也可以试试主演、导演或别名"
        else -> "换个关键词再试试，片名、主演、别名都可以"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = UiPalette.SurfaceSoft.copy(alpha = 0.72f)),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, UiPalette.BorderSoft.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(UiPalette.AccentSoft.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = UiPalette.Accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = UiPalette.Ink
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = UiPalette.TextMuted
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchInputCard(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = UiPalette.Accent,
            unfocusedBorderColor = UiPalette.BorderSoft,
            focusedTextColor = UiPalette.Ink,
            unfocusedTextColor = UiPalette.Ink,
            cursorColor = UiPalette.Accent,
            focusedTrailingIconColor = UiPalette.Accent,
            unfocusedTrailingIconColor = UiPalette.TextSecondary,
            focusedContainerColor = UiPalette.Surface,
            unfocusedContainerColor = UiPalette.Surface,
            focusedPlaceholderColor = UiPalette.TextMuted,
            unfocusedPlaceholderColor = UiPalette.TextMuted
        ),
        placeholder = { Text("搜片名") },
        trailingIcon = {
            TextButton(
                onClick = onSearch,
                colors = ButtonDefaults.textButtonColors(contentColor = UiPalette.Accent)
            ) {
                Text("搜索", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun HotSearchBoard(
    group: HotSearchGroup,
    onPickKeyword: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, UiPalette.Border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = group.platform,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = UiPalette.Ink
            )
            group.items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onPickKeyword(item.keyword) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = item.rank.toString().padStart(2, '0'),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (item.rank <= 3) UiPalette.Accent else UiPalette.TextSecondary
                    )
                    Text(
                        text = item.keyword,
                        style = MaterialTheme.typography.bodyLarge,
                        color = UiPalette.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
