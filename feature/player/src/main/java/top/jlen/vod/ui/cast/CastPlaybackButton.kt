package top.jlen.vod.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.view.Display
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.WifiFind
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.net.URL

@Composable
internal fun CastPlaybackButton(
    url: String,
    title: String,
    subtitle: String,
    positionMs: Long,
    visible: Boolean,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var dialogVisible by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var searchVersion by remember { mutableIntStateOf(0) }
    var devices by remember { mutableStateOf<List<DlnaDevice>>(emptyList()) }
    // 投屏状态来自进程级会话，切集、进出全屏都不会丢失
    val castState by DlnaCastSession.state.collectAsState()
    val connectingDevice = castState.connectingDevice
    val connectedDevice = castState.device
    val wirelessDisplayName = rememberWirelessDisplayName(context)
    val latestPosition by rememberUpdatedState(positionMs.coerceAtLeast(0L))
    val mediaTitle = listOf(title, subtitle).filter(String::isNotBlank).joinToString(" · ")

    LaunchedEffect(connectedDevice) {
        if (connectedDevice != null) dialogVisible = false
    }

    // 打开弹窗或点击刷新时搜索局域网设备
    LaunchedEffect(dialogVisible, searchVersion) {
        if (!dialogVisible || connectedDevice != null) return@LaunchedEffect
        searching = true
        try {
            devices = runCatchingCancellable { DlnaCastClient.discover(context) }.getOrDefault(emptyList())
        } finally {
            searching = false
        }
    }

    if (visible) Box(modifier = modifier) {
        IconButton(
            onClick = {
                onInteraction()
                dialogVisible = true
            },
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = if (connectedDevice != null || wirelessDisplayName != null) {
                    Icons.Rounded.CastConnected
                } else {
                    Icons.Rounded.Cast
                },
                contentDescription = "投屏",
                tint = Color.White
            )
        }
    }

    if (dialogVisible) {
        CastDialog(
            devices = devices,
            searching = searching,
            connectingDevice = connectingDevice,
            connectedDevice = connectedDevice,
            wirelessDisplayName = wirelessDisplayName,
            onDismiss = { dialogVisible = false },
            onRefresh = { searchVersion++ },
            onSelect = { device ->
                if (url.isBlank() || connectingDevice != null) return@CastDialog
                DlnaCastSession.castTo(context, device, url, mediaTitle, latestPosition)
            },
            onDisconnect = {
                dialogVisible = false
                DlnaCastSession.disconnect()
            },
            onOpenWirelessDisplay = {
                if (launchWirelessDisplaySettings(context)) {
                    dialogVisible = false
                } else {
                    Toast.makeText(context, "当前系统不支持无线显示器投屏", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
private fun CastDialog(
    devices: List<DlnaDevice>,
    searching: Boolean,
    connectingDevice: DlnaDevice?,
    connectedDevice: DlnaDevice?,
    wirelessDisplayName: String?,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onSelect: (DlnaDevice) -> Unit,
    onDisconnect: (DlnaDevice) -> Unit,
    onOpenWirelessDisplay: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val dialogMaxHeight = (configuration.screenHeightDp.dp * 0.88f).coerceAtLeast(360.dp)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = dialogMaxHeight),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = UiPalette.Surface)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CastDialogHeader(
                    connected = connectedDevice != null,
                    searching = searching,
                    deviceCount = devices.size
                )
                if (connectedDevice != null) {
                    ConnectedPanel(device = connectedDevice)
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CastSection(title = "电视 / 盒子（DLNA）") {
                            DlnaDeviceSection(
                                devices = devices,
                                searching = searching,
                                connectingDevice = connectingDevice,
                                onSelect = onSelect
                            )
                        }
                        CastSection(title = "电脑 / 屏幕镜像") {
                            CastOptionRow(
                                icon = Icons.Rounded.DesktopWindows,
                                title = "Windows 无线显示器",
                                // 系统无法可靠区分有线 HDMI 与无线显示器，使用中性文案
                                subtitle = wirelessDisplayName?.let { "外接显示器：$it" }
                                    ?: "Miracast 镜像，需在电脑上开启「投影到此电脑」",
                                highlighted = wirelessDisplayName != null,
                                onClick = onOpenWirelessDisplay
                            )
                        }
                    }
                }
                CastDialogActions(
                    connected = connectedDevice != null,
                    searching = searching,
                    onDismiss = onDismiss,
                    onRefresh = onRefresh,
                    onDisconnect = { connectedDevice?.let(onDisconnect) }
                )
            }
        }
    }
}

@Composable
private fun CastDialogHeader(
    connected: Boolean,
    searching: Boolean,
    deviceCount: Int
) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(
                    brush = Brush.linearGradient(colors = listOf(UiPalette.Accent, UiPalette.AccentSoft)),
                    shape = RoundedCornerShape(18.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (connected) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
                contentDescription = null,
                tint = UiPalette.AccentText
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (connected) "正在投屏" else "投屏到设备",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = UiPalette.Ink
            )
            Text(
                text = if (connected) "视频正在电视端播放，可在此断开投屏。" else "请确保设备与手机连接同一 Wi-Fi。",
                style = MaterialTheme.typography.bodyMedium,
                color = UiPalette.TextSecondary
            )
        }
        CastStatusPill(
            text = when {
                connected -> "已连接"
                searching -> "搜索中"
                else -> "$deviceCount 台"
            }
        )
    }
}

@Composable
private fun CastStatusPill(text: String) {
    Box(
        modifier = Modifier
            .offset(y = 2.dp)
            .background(UiPalette.AccentSoft.copy(alpha = 0.2f), RoundedCornerShape(999.dp))
            .border(1.dp, UiPalette.Accent.copy(alpha = 0.28f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = UiPalette.Accent
        )
    }
}

@Composable
private fun CastSection(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = UiPalette.SurfaceSoft),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = UiPalette.Ink
            )
            content()
        }
    }
}

@Composable
private fun DlnaDeviceSection(
    devices: List<DlnaDevice>,
    searching: Boolean,
    connectingDevice: DlnaDevice?,
    onSelect: (DlnaDevice) -> Unit
) {
    // 预留固定高度（约两台设备），避免搜索完成前后弹窗高度跳动
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = DLNA_SECTION_MIN_HEIGHT),
        contentAlignment = if (devices.isEmpty()) Alignment.Center else Alignment.TopStart
    ) {
        DlnaDeviceContent(
            devices = devices,
            searching = searching,
            connectingDevice = connectingDevice,
            onSelect = onSelect
        )
    }
}

@Composable
private fun DlnaDeviceContent(
    devices: List<DlnaDevice>,
    searching: Boolean,
    connectingDevice: DlnaDevice?,
    onSelect: (DlnaDevice) -> Unit
) {
    if (devices.isEmpty()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (searching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = UiPalette.Accent
                )
            } else {
                Icon(Icons.Rounded.WifiFind, contentDescription = null, tint = UiPalette.TextMuted)
            }
            Text(
                text = if (searching) "正在搜索附近的设备…" else "未发现设备，请在电视或盒子上开启 DLNA 投屏接收后重新搜索。",
                style = MaterialTheme.typography.bodyMedium,
                color = UiPalette.TextSecondary
            )
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        devices.forEach { device ->
            CastOptionRow(
                icon = Icons.Rounded.Tv,
                title = device.name,
                subtitle = runCatchingCancellable { URL(device.location).host }.getOrDefault("DLNA"),
                loading = connectingDevice == device,
                onClick = { onSelect(device) }
            )
        }
    }
}

private val DLNA_SECTION_MIN_HEIGHT = 130.dp

@Composable
private fun CastOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    loading: Boolean = false,
    highlighted: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(UiPalette.Surface)
            .border(
                1.dp,
                if (highlighted) UiPalette.Accent.copy(alpha = 0.5f) else UiPalette.BorderSoft,
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(UiPalette.AccentSoft.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = UiPalette.Accent, modifier = Modifier.size(20.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = UiPalette.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (highlighted) UiPalette.Accent else UiPalette.TextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = UiPalette.Accent
            )
        } else {
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = UiPalette.TextMuted)
        }
    }
}

@Composable
private fun ConnectedPanel(device: DlnaDevice) {
    Card(
        colors = CardDefaults.cardColors(containerColor = UiPalette.SurfaceSoft),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Rounded.Tv, contentDescription = null, tint = UiPalette.Accent)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "当前设备",
                    style = MaterialTheme.typography.labelMedium,
                    color = UiPalette.TextMuted
                )
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = UiPalette.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CastDialogActions(
    connected: Boolean,
    searching: Boolean,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onDismiss) {
            Text("关闭")
        }
        if (connected) {
            Button(
                onClick = onDisconnect,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = UiPalette.DangerText,
                    contentColor = UiPalette.Surface
                )
            ) {
                Text("断开投屏", fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = onRefresh,
                enabled = !searching,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = UiPalette.Accent,
                    contentColor = UiPalette.AccentText
                )
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Box(modifier = Modifier.width(4.dp))
                Text(if (searching) "搜索中" else "重新搜索", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// 监听系统无线显示器（Miracast）连接状态，返回已连接的显示器名称
@Composable
private fun rememberWirelessDisplayName(context: Context): String? {
    val displayManager = remember(context) {
        context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
    }
    fun query(): String? = runCatching {
        displayManager?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            ?.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
            ?.name
    }.getOrNull()

    var name by remember { mutableStateOf(query()) }
    DisposableEffect(displayManager) {
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) { name = query() }
            override fun onDisplayRemoved(displayId: Int) { name = query() }
            override fun onDisplayChanged(displayId: Int) { name = query() }
        }
        runCatching { displayManager?.registerDisplayListener(listener, null) }
        onDispose { runCatching { displayManager?.unregisterDisplayListener(listener) } }
    }
    return name
}

// 普通应用无法直接发起 Miracast 连接，只能打开系统的无线显示/屏幕镜像页面
private fun launchWirelessDisplaySettings(context: Context): Boolean {
    val intents = listOf(
        Intent(Settings.ACTION_CAST_SETTINGS),
        Intent("android.settings.WIFI_DISPLAY_SETTINGS"),
        Intent().setClassName("com.android.settings", "com.android.settings.Settings\$WifiDisplaySettingsActivity"),
        Intent().setClassName("com.oplus.cast", "com.oplus.cast.ui.DeviceListActivity")
    )
    return intents.any { intent ->
        runCatching {
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.isSuccess
    }
}
