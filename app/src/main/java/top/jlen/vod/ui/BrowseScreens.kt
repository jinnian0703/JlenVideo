package top.jlen.vod.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import top.jlen.vod.BuildConfig
import top.jlen.vod.data.AppleCmsCategory
import top.jlen.vod.data.VodItem

@Composable
fun HomeScreen(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenCategory: () -> Unit,
    onOpenSearch: () -> Unit
) {
    if (state.isLoading) {
        LoadingPane("正在加载首页...")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            HomeTopBlock(
                source = BuildConfig.APPLE_CMS_BASE_URL.removePrefix("https://").removePrefix("http://").trimEnd('/'),
                onRefresh = onRefresh,
                onOpenCategory = onOpenCategory,
                onOpenSearch = onOpenSearch
            )
        }
        state.error?.let { message ->
            item { ErrorBanner(message = message, onRetry = onRefresh) }
        }
        if (state.featured.isNotEmpty()) {
            item {
                SectionTitle(
                    title = "热门推荐",
                    action = "刷新",
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Whatshot,
                            contentDescription = null,
                            tint = UiPalette.Accent,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onAction = onRefresh
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(state.featured) { item ->
                        FeaturedCard(item = item, onClick = onOpenDetail)
                    }
                }
            }
        }
        item {
            SectionTitle(
                title = "最新上架",
                action = "进入片库",
                onAction = onOpenCategory
            )
        }
        if (state.latest.isEmpty()) {
            item {
                ErrorBanner(
                    message = "当前没有加载到首页内容，点刷新再试一次。",
                    onRetry = onRefresh
                )
            }
        } else {
            item {
                PosterGridSection(
                    items = state.visibleLatest,
                    onOpenDetail = onOpenDetail
                )
            }
            item {
                LoadMoreFooter(
                    hasMore = state.hasMoreLatest,
                    isLoading = state.isHomeAppending,
                    onLoadMore = onLoadMore
                )
            }
        }
    }
}

@Composable
fun CategoryScreen(
    state: HomeUiState,
    onSelectCategory: (AppleCmsCategory) -> Unit,
    onLoadMore: () -> Unit,
    onOpenDetail: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = "片库",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = UiPalette.Ink
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "默认先显示全部分类，再按频道切换浏览",
                    style = MaterialTheme.typography.bodyMedium,
                    color = UiPalette.TextSecondary
                )
            }
        }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.categories) { category ->
                    val selected = category.typeId == state.selectedCategory?.typeId
                    AssistChip(
                        onClick = { onSelectCategory(category) },
                        label = { Text(category.typeName, fontWeight = FontWeight.SemiBold) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (selected) UiPalette.Ink else UiPalette.Surface,
                            labelColor = if (selected) UiPalette.Surface else UiPalette.Ink
                        ),
                        border = AssistChipDefaults.assistChipBorder(
                            borderColor = if (selected) UiPalette.Ink else UiPalette.BorderSoft,
                            enabled = true
                        )
                    )
                }
            }
        }
        item {
            SectionTitle(
                title = state.selectedCategory?.typeName ?: "分类",
                action = null,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.GridView,
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
                state.isCategoryLoading -> LoadingPane("正在切换分类...")
                state.categoryVideos.isEmpty() -> EmptyPane("这个分类暂时没有内容")
                else -> PosterGridSection(
                    items = state.visibleCategoryVideos,
                    onOpenDetail = onOpenDetail
                )
            }
        }
        if (!state.isCategoryLoading && state.categoryVideos.isNotEmpty()) {
            item {
                LoadMoreFooter(
                    hasMore = state.hasMoreCategoryVideos,
                    isLoading = state.isCategoryAppending,
                    onLoadMore = onLoadMore
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenDetail: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "搜索",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = UiPalette.Ink
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "直接搜索站内影片资源",
            style = MaterialTheme.typography.bodyMedium,
            color = UiPalette.TextSecondary
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = state.query,
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
            placeholder = { Text("输入电影、电视剧、综艺、动漫名称") },
            trailingIcon = {
                TextButton(
                    onClick = onSearch,
                    colors = ButtonDefaults.textButtonColors(contentColor = UiPalette.Accent)
                ) {
                    Text("搜索", fontWeight = FontWeight.Bold)
                }
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        when {
            state.isLoading -> LoadingPane("正在搜索...")
            !state.error.isNullOrBlank() && state.results.isEmpty() -> ErrorBanner(message = state.error, onRetry = onSearch)
            state.results.isEmpty() -> EmptyPane("搜索结果会显示在这里")
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(state.results) { item ->
                    ListCard(item = item, onClick = onOpenDetail)
                }
            }
        }
    }
}

@Composable
private fun HomeTopBlock(
    source: String,
    onRefresh: () -> Unit,
    onOpenCategory: () -> Unit,
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
                    text = source,
                    style = MaterialTheme.typography.labelLarge,
                    color = UiPalette.TextSecondary
                )
                Text(
                    text = "堇年影视",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = UiPalette.Ink
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircleActionButton(icon = Icons.Rounded.GridView, onClick = onOpenCategory)
                CircleActionButton(icon = Icons.Rounded.Refresh, onClick = onRefresh)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        SearchDock(onClick = onOpenSearch)
    }
}

@Composable
private fun SearchDock(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, UiPalette.Border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (false) {
                Text(
                text = "搜索电影、电视剧、综艺、动漫",
                color = UiPalette.TextMuted,
                modifier = Modifier.weight(1f)
                )
            }
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = UiPalette.Accent
            )
        }
    }
}

@Composable
private fun CircleActionButton(icon: ImageVector, onClick: () -> Unit) {
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
fun SectionTitle(
    title: String,
    action: String?,
    icon: @Composable (() -> Unit)? = null,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                icon()
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = UiPalette.Ink
            )
        }
        if (!action.isNullOrBlank()) {
            TextButton(
                onClick = onAction,
                colors = ButtonDefaults.textButtonColors(contentColor = UiPalette.Accent)
            ) {
                Text(action, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun FeaturedCard(item: VodItem, onClick: (String) -> Unit) {
    Card(
        modifier = Modifier
            .width(312.dp)
            .clickable { onClick(item.vodId) },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface)
    ) {
        Box {
            AsyncImage(
                model = item.vodPic,
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
            if (item.badgeText.isNotBlank()) {
                Text(
                    text = item.badgeText,
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
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = item.subtitle.ifBlank { "站内资源" },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.86f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PosterGridSection(items: List<VodItem>, onOpenDetail: (String) -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items.chunked(3).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowItems.forEach { item ->
                    CompactPosterCard(
                        item = item,
                        onClick = onOpenDetail,
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(3 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun LoadMoreFooter(
    hasMore: Boolean,
    isLoading: Boolean,
    onLoadMore: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val remainingCount = 0
        if (hasMore) {
            TextButton(
                onClick = onLoadMore,
                enabled = !isLoading,
                colors = ButtonDefaults.textButtonColors(contentColor = UiPalette.Accent)
            ) {
                Text("继续加载", fontWeight = FontWeight.Bold)
            }
            Text(
                text = "还有 $remainingCount 条内容",
                style = MaterialTheme.typography.bodySmall,
                color = UiPalette.TextMuted
            )
        } else {
            Text(
                text = "已经全部加载完了",
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
    Column(modifier = modifier.clickable { onClick(item.vodId) }) {
        Box {
            AsyncImage(
                model = item.vodPic,
                contentDescription = item.displayTitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(154.dp)
                    .clip(RoundedCornerShape(18.dp)),
                contentScale = ContentScale.Crop
            )
            if (item.badgeText.isNotBlank()) {
                Text(
                    text = item.badgeText,
                    color = UiPalette.Surface,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = item.subtitle.ifBlank { "站内资源" },
            style = MaterialTheme.typography.bodySmall,
            color = UiPalette.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ListCard(item: VodItem, onClick: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(item.vodId) },
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, UiPalette.Border)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AsyncImage(
                model = item.vodPic,
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
                Text(
                    text = item.subtitle.ifBlank { "站内资源" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = UiPalette.TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = UiPalette.TextMuted,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
