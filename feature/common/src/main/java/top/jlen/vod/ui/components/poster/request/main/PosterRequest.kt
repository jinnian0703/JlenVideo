package top.jlen.vod.ui

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
    height: Int
): ImageRequest {
    val context = LocalContext.current
    return remember(context, data, width, height) {
        ImageRequest.Builder(context)
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
            .memoryCacheKey("${data.orEmpty()}@$width@$height")
            .build()
    }
}
