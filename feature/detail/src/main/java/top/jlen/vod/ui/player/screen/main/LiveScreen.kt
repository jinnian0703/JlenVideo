package top.jlen.vod.ui

import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
    val browser: @Composable () -> Unit = {
        Column {
            if (categoryError != null) TextButton(onClick = { categoryRetry++ }) { Text("分类加载失败 · 重试") }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                items(listOf(LiveCategory(0, "全部")) + categories, key = { it.id }) { cat ->
                    FilterChip(selected = category == cat.id, onClick = { category = cat.id }, label = { Text(cat.name) })
                }
            }
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                items(channels, key = { it.id }) { entry ->
                    ListItem(headlineContent = { Text(entry.name) }, supportingContent = { Text(entry.category) },
                        trailingContent = { if (selected == entry.id) Text("当前频道", color = UiPalette.Accent) },
                        modifier = Modifier.clickable { selected = entry.id; picker = false })
                }
                item {
                    when {
                        loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                        listError != null -> TextButton(onClick = { listRetry++ }) { Text("频道加载失败 · 重试") }
                        next != null -> TextButton(onClick = { next?.let { offset = it } }) { Text("更多频道") }
                        channels.isEmpty() -> Text("暂无频道", Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
    val lines: @Composable () -> Unit = {
        val sources = channel?.sources.orEmpty()
        if (sources.size > 1) LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sources.size) { index ->
                FilterChip(selected = line == index, onClick = { line = index; picker = false },
                    label = { Text("线路 ${index + 1} · ${sources[index].name}") })
            }
        }
    }
    Column(Modifier.fillMaxSize().background(if (full) Color.Black else UiPalette.BackgroundTop)
        .then(if (full) Modifier else Modifier.appTopInsetsPadding())) {
        if (!full) Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("返回") }
            Text(channel?.name ?: "电视直播", style = MaterialTheme.typography.titleLarge)
        }
        val current = channel
        val stream = current?.sources?.getOrNull(line)
        if (stream != null && !playError) key(selected, line, attempt) {
            NativeVideoPlayer(url = stream.url, title = current.name, sourceName = stream.name, episodeName = "直播",
                hasNextEpisode = false, onNextEpisode = null, liveMode = true, fullscreenMode = full,
                onToggleFullscreen = { _, _ -> full = !full }, onClose = if (full) ({ full = false }) else null,
                onOpenEpisodePicker = if (full) ({ picker = true }) else null,
                onPlaybackError = { playError = true })
        } else if (selected > 0) {
            Surface(color = UiPalette.SurfaceStrong, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(when { detailError != null -> detailError.orEmpty(); playError -> "该直播线路播放失败";
                        current != null -> "频道暂无可播放的线路"; else -> "正在打开频道…" })
                    if (detailError != null || current != null) Row {
                        TextButton(onClick = { if (playError) { playError = false; attempt++ } else detailRetry++ }) { Text("重试") }
                        if (full) TextButton(onClick = { picker = true }) { Text("频道与线路") }
                    }
                }
            }
        }
        if (!full) { lines(); browser() }
    }
    if (full && picker) Dialog(onDismissRequest = { picker = false }) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(vertical = 12.dp)) {
                Text("频道与线路", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleMedium)
                lines(); browser()
            }
        }
    }
}
