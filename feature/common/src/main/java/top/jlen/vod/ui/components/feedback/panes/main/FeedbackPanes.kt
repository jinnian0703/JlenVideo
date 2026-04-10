package top.jlen.vod.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class FeedbackPaneStyle {
    Fullscreen,
    Card
}

@Composable
fun LoadingPane(
    message: String,
    style: FeedbackPaneStyle = FeedbackPaneStyle.Fullscreen
) {
    FeedbackPaneContainer(style = style) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(color = UiPalette.Accent)
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = UiPalette.TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun EmptyPane(
    message: String,
    description: String = "",
    icon: ImageVector = Icons.Rounded.Info,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    style: FeedbackPaneStyle = FeedbackPaneStyle.Fullscreen
) {
    FeedbackPaneContainer(style = style) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                UiPalette.AccentSoft.copy(alpha = 0.2f),
                                UiPalette.Accent.copy(alpha = 0.1f)
                            )
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = UiPalette.Accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = UiPalette.Ink,
                    textAlign = TextAlign.Center
                )
                description.takeIf { it.isNotBlank() }?.let { summary ->
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = UiPalette.TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
            if (!actionLabel.isNullOrBlank() && onAction != null) {
                OutlinedButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, UiPalette.BorderSoft),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = UiPalette.Accent
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = actionLabel,
                        modifier = Modifier.padding(start = 6.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedbackPaneContainer(
    style: FeedbackPaneStyle,
    content: @Composable ColumnScope.() -> Unit
) {
    when (style) {
        FeedbackPaneStyle.Fullscreen -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(UiPalette.BackgroundBottom)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = UiPalette.Surface.copy(alpha = 0.96f)
                    ),
                    shape = RoundedCornerShape(28.dp),
                    border = BorderStroke(1.dp, UiPalette.BorderSoft.copy(alpha = 0.78f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        content = content
                    )
                }
            }
        }

        FeedbackPaneStyle.Card -> {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = UiPalette.Surface.copy(alpha = 0.96f)
                ),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, UiPalette.BorderSoft.copy(alpha = 0.74f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 156.dp)
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    content = content
                )
            }
        }
    }
}
