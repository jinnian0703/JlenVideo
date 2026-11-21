package top.jlen.vod.ui

import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.TvOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import top.jlen.vod.data.*

@Composable
fun LiveScreen(api: CmsAppApi, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("live_channels", Context.MODE_PRIVATE) }
    var selected by rememberSaveable { mutableIntStateOf(prefs.getInt("last_channel", 0)) }
    var category by rememberSaveable { mutableIntStateOf(0) }
    var categories by remember { mutableStateOf(emptyList<LiveCategory>()) }
    var categoryError by remember { mutableStateOf<String?>(null) }
    var categoryRetry by remember { mutableIntStateOf(0) }
    var channels by remember(category) { mutableStateOf(emptyList<LiveChannel>()) }
    var offset by remember(category) { mutableIntStateOf(0) }
    var next by remember(category) { mutableStateOf<Int?>(null) }
    var loading by remember(category) { mutableStateOf(true) }
    var listError by remember(category) { mutableStateOf<String?>(null) }
    var listRetry by remember { mutableIntStateOf(0) }
    var channel by remember(selected) { mutableStateOf<LiveChannel?>(null) }
    var detailError by remember(selected) { mutableStateOf<String?>(null) }
    var detailRetry by remember { mutableIntStateOf(0) }
    var line by rememberSaveable(selected) { mutableIntStateOf(0) }
    var full by rememberSaveable { mutableStateOf(false) }
    var picker by rememberSaveable { mutableStateOf(false) }
    var playError by remember(selected, line) { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(categoryRetry) {
        viewingRequest { api.liveCategories() }.onSuccess { categories = it; categoryError = null }
            .onFailure { categoryError = it.message ?: "分类加载失败" }
    }
    LaunchedEffect(category, offset, listRetry) {
        loading = true
        listError = null
        viewingRequest { api.liveChannels(category, offset) }.onSuccess { page ->
            channels = (if (offset == 0) page.channels else channels + page.channels).distinctBy { it.id }
            next = page.nextOffset
            if (selected == 0) selected = channels.firstOrNull()?.id ?: 0
        }.onFailure { listError = it.message ?: "频道列表加载失败" }
        loading = false
    }
    LaunchedEffect(selected, detailRetry) {
        if (selected <= 0) return@LaunchedEffect
        detailError = null
        viewingRequest { api.liveChannel(selected) }.onSuccess {
            channel = it
            line = line.coerceIn(0, it.sources.lastIndex.coerceAtLeast(0))
            prefs.edit().putInt("last_channel", it.id).apply()
        }.onFailure { detailError = it.message ?: "频道加载失败" }
    }
    BackHandler(enabled = full && !picker) { full = false }
    val activity = context.findActivity()
    DisposableEffect(activity, full) {
        if (activity == null || !full) return@DisposableEffect onDispose { }
        val previous = activity.requestedOrientation
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val previousBehavior = controller.systemBarsBehavior
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = previousBehavior
            activity.requestedOrientation = previous
        }
    }
    val current = channel
    val stream = current?.sources?.getOrNull(line)
    val browser: @Composable () -> Unit = {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (current != null) item(key = "tuned") {
                Column(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("当前频道", style = MaterialTheme.typography.labelMedium, color = UiPalette.TextSecondary)
                    Text(current.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = UiPalette.Ink)
                    if (current.sources.size > 1) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(current.sources.size) { index ->
                            LiveFilter("线路 ${index + 1} · ${current.sources[index].name}", line == index) {
                                line = index
                                picker = false
                            }
                        }
                    }
                }
            }
            item(key = "categories") {
                Column {
                    Text("频道列表", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = UiPalette.Ink)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf(LiveCategory(0, "全部")) + categories.filter { it.id != 0 }, key = { it.id }) { cat ->
                            LiveFilter(cat.name, category == cat.id) { category = cat.id }
                        }
                    }
                    if (categoryError != null) TextButton(onClick = { categoryRetry++ }) { Text("分类加载失败 · 重试") }
                }
            }
            items(channels, key = { it.id }) { entry ->
                LiveChannelRow(entry, selected == entry.id) { selected = entry.id; picker = false }
            }
            item(key = "pager") {
                Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    when {
                        loading -> CircularProgressIndicator(Modifier.size(22.dp), color = UiPalette.Accent, strokeWidth = 2.dp)
                        listError != null -> TextButton(onClick = { listRetry++ }) { Text("频道加载失败 · 重试") }
                        next != null -> TextButton(onClick = { next?.let { offset = it } }) { Text("更多频道") }
                        channels.isEmpty() -> Text("这个分类下暂无频道", color = UiPalette.TextSecondary)
                    }
                }
            }
        }
    }
    Column(Modifier.fillMaxSize().background(if (full) Color.Black else UiPalette.BackgroundBottom)
        .then(if (full) Modifier else Modifier.appTopInsetsPadding())) {
        if (!full) DetailTopBar("电视直播", onBack, isSystemInDarkTheme())
        if (stream != null && !playError) key(selected, line, attempt) {
            NativeVideoPlayer(url = stream.url, title = current.name, sourceName = stream.name, episodeName = "直播",
                hasNextEpisode = false, onNextEpisode = null, liveMode = true, fullscreenMode = full,
                onToggleFullscreen = { _, _ -> full = !full }, onClose = if (full) ({ full = false }) else null,
                onOpenEpisodePicker = if (full) ({ picker = true }) else null,
                onPlaybackError = { playError = true })
        } else if (selected > 0) {
            val unavailable = detailError != null || current != null
            Box(modifier = if (full) Modifier.fillMaxSize() else Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                .height(228.dp).background(UiPalette.PlayerBackground, RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (unavailable) Icon(Icons.Rounded.TvOff, null, tint = Color.LightGray)
                    else CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    Text(when { detailError != null -> "频道加载失败"; playError -> "该直播线路播放失败";
                        current != null -> "频道暂无可播放的线路"; else -> "正在打开频道…" }, color = Color.White)
                    if (unavailable) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { if (playError) { playError = false; attempt++ } else detailRetry++ }) { Text("重试") }
                        if (full) FilledTonalButton(onClick = { picker = true }) { Text("切换频道") }
                    }
                }
            }
        }
        if (!full) browser()
    }
    if (full && picker) Dialog(onDismissRequest = { picker = false }) {
        Surface(shape = RoundedCornerShape(24.dp), color = UiPalette.BackgroundBottom) {
            Box(Modifier.padding(vertical = 12.dp)) { browser() }
        }
    }
}

@Composable
private fun LiveFilter(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected, onClick, label = { Text(label) }, shape = RoundedCornerShape(16.dp),
        colors = FilterChipDefaults.filterChipColors(containerColor = UiPalette.Surface, labelColor = UiPalette.TextSecondary,
            selectedContainerColor = UiPalette.Ink, selectedLabelColor = UiPalette.Surface))
}

@Composable
private fun LiveChannelRow(channel: LiveChannel, selected: Boolean, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = UiPalette.Surface,
        border = BorderStroke(1.dp, if (selected) UiPalette.Accent.copy(alpha = .65f) else UiPalette.Border)) {
        Row(Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).background(if (selected) UiPalette.AccentGlow else UiPalette.SurfaceSoft, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center) { Icon(Icons.Rounded.LiveTv, null, tint = UiPalette.Ink) }
            Column(Modifier.weight(1f)) {
                Text(channel.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    color = UiPalette.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (channel.category.isNotBlank()) Text(channel.category, style = MaterialTheme.typography.bodySmall, color = UiPalette.TextSecondary)
            }
            if (selected) Icon(Icons.Rounded.Check, "当前频道", tint = UiPalette.Accent)
        }
    }
}
