package top.jlen.vod.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.SubtitlesOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun PortraitDanmakuBar(enabled: Boolean, toggle: () -> Unit, editor: @Composable (Modifier) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.clip(CircleShape).background(if (enabled) UiPalette.AccentGlow else UiPalette.Surface)
            .toggleable(value = enabled, role = Role.Switch, onValueChange = { toggle() })
            .heightIn(min = 48.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(if (enabled) Icons.Rounded.Subtitles else Icons.Rounded.SubtitlesOff, null, Modifier.size(20.dp), tint = UiPalette.Ink)
            Text(if (enabled) "弹幕开" else "弹幕关", style = MaterialTheme.typography.labelLarge, color = UiPalette.Ink, fontWeight = FontWeight.Bold)
        }
        editor(Modifier.weight(1f))
    }
}
