package top.jlen.vod.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class ErrorBannerDensity {
    Regular,
    Compact
}

@Composable
fun ErrorBanner(
    message: String,
    onRetry: () -> Unit,
    actionLabel: String = "重试",
    density: ErrorBannerDensity = ErrorBannerDensity.Regular,
    modifier: Modifier = Modifier
) {
    val compact = density == ErrorBannerDensity.Compact

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = UiPalette.DangerSurface),
        shape = RoundedCornerShape(if (compact) 20.dp else 24.dp),
        border = BorderStroke(1.dp, UiPalette.DangerBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (compact) 14.dp else 18.dp,
                    vertical = if (compact) 12.dp else 16.dp
                ),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 32.dp else 38.dp)
                    .clip(RoundedCornerShape(if (compact) 12.dp else 14.dp))
                    .background(UiPalette.Surface.copy(alpha = 0.78f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = UiPalette.DangerText,
                    modifier = Modifier.size(if (compact) 16.dp else 18.dp)
                )
            }
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                color = UiPalette.DangerText,
                maxLines = if (compact) 3 else 4,
                overflow = TextOverflow.Ellipsis
            )
            OutlinedButton(
                onClick = onRetry,
                shape = RoundedCornerShape(if (compact) 16.dp else 18.dp),
                border = BorderStroke(1.dp, UiPalette.DangerBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = UiPalette.DangerText)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(if (compact) 14.dp else 16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = actionLabel,
                    fontWeight = FontWeight.Bold,
                    style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
