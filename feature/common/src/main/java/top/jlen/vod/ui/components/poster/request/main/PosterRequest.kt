package top.jlen.vod.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale

@Composable
fun rememberPosterRequest(
    data: String?,
    width: Int,
    height: Int,
    retryToken: Int = 0
): ImageRequest {
    val context = LocalContext.current
    return remember(context, data, width, height, retryToken) {
        buildPosterRequest(context, data, width, height, retryToken)
    }
}

fun buildPosterRequest(
    context: Context,
    data: String?,
    width: Int,
    height: Int,
    retryToken: Int = 0
): ImageRequest = ImageRequest.Builder(context)
    .data(data.orEmpty())
    .size(width, height)
    .bitmapConfig(Bitmap.Config.RGB_565)
    .precision(Precision.INEXACT)
    .scale(Scale.FILL)
    .memoryCachePolicy(CachePolicy.ENABLED)
    .diskCachePolicy(CachePolicy.ENABLED)
    .networkCachePolicy(CachePolicy.ENABLED)
    .allowHardware(true)
    .crossfade(false)
    .diskCacheKey(data.orEmpty())
    .memoryCacheKey("${data.orEmpty()}@$width@$height#$retryToken")
    .build()
