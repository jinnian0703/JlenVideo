package top.jlen.vod.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage

enum class PosterFallbackStyle {
    Default,
    CompactTitle
}

@Composable
fun RetryablePosterImage(
    data: String?,
    title: String,
    width: Int,
    height: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    retryLabel: String = "重试",
    showFallbackTitle: Boolean = true,
    fallbackStyle: PosterFallbackStyle = PosterFallbackStyle.Default,
    fallbackBottomInset: Dp = 0.dp
) {
    var retryToken by remember(data, title, width, height) { mutableIntStateOf(0) }

    SubcomposeAsyncImage(
        model = rememberPosterRequest(
            data = data,
            width = width,
            height = height,
            retryToken = retryToken
        ),
        contentDescription = title,
        modifier = modifier,
        contentScale = contentScale
    ) {
        when (painter.state) {
            is AsyncImagePainter.State.Loading -> PosterSkeletonPlaceholder(title = title)
            is AsyncImagePainter.State.Error,
            is AsyncImagePainter.State.Empty -> PosterRetryFallback(
                title = title,
                retryLabel = retryLabel,
                onRetry = { retryToken += 1 },
                showTitle = showFallbackTitle,
                style = fallbackStyle,
                bottomInset = fallbackBottomInset
            )

            else -> Image(
                painter = painter,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        }
    }
}

@Composable
private fun PosterRetryFallback(
    title: String,
    retryLabel: String,
    onRetry: () -> Unit,
    showTitle: Boolean,
    style: PosterFallbackStyle,
    bottomInset: Dp
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        UiPalette.SurfaceStrong.copy(alpha = 0.96f),
                        UiPalette.SurfaceSoft.copy(alpha = 0.92f)
                    )
                )
            )
            .padding(horizontal = 12.dp, vertical = 12.dp + bottomInset / 2),
        contentAlignment = Alignment.Center
    ) {
        when (style) {
            PosterFallbackStyle.Default -> DefaultPosterRetryFallback(
                title = title,
                retryLabel = retryLabel,
                onRetry = onRetry,
                showTitle = showTitle
            )

            PosterFallbackStyle.CompactTitle -> CompactTitlePosterRetryFallback(
                title = title,
                retryLabel = retryLabel,
                onRetry = onRetry,
                showTitle = showTitle
            )
        }
    }
}

@Composable
private fun DefaultPosterRetryFallback(
    title: String,
    retryLabel: String,
    onRetry: () -> Unit,
    showTitle: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(
                    color = UiPalette.Accent.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                tint = UiPalette.Accent,
                modifier = Modifier.size(20.dp)
            )
        }
        if (showTitle) {
            Text(
                text = title.ifBlank { "暂无海报" },
                color = UiPalette.Ink,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
        OutlinedButton(
            onClick = onRetry,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, UiPalette.BorderSoft),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.Transparent,
                contentColor = UiPalette.Accent
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = retryLabel,
                modifier = Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CompactTitlePosterRetryFallback(
    title: String,
    retryLabel: String,
    onRetry: () -> Unit,
    showTitle: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = onRetry,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, UiPalette.Accent.copy(alpha = 0.24f)),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = UiPalette.Surface.copy(alpha = 0.76f),
                contentColor = UiPalette.Accent
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = retryLabel,
                modifier = Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (showTitle) {
            Text(
                text = title.ifBlank { "暂无海报" },
                color = UiPalette.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PosterSkeletonPlaceholder(title: String) {
    val shimmer = rememberInfiniteTransition(label = "posterSkeleton")
    val shift by shimmer.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1150, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "posterSkeletonShift"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        UiPalette.SurfaceStrong,
                        UiPalette.SurfaceSoft,
                        UiPalette.SurfaceStrong
                    ),
                    start = Offset.Zero,
                    end = Offset(620f * (shift + 1.2f), 620f * (shift + 1.2f))
                )
            )
            .padding(12.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Text(
            text = title.ifBlank { "加载中" },
            color = UiPalette.TextMuted,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
