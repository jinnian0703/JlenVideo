package top.jlen.vod.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

@Composable
fun CaptchaImageBox(
    bytes: ByteArray?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "点击刷新验证码",
    loadingText: String = "加载中..."
) {
    val captchaBitmap = remember(bytes) {
        runCatching {
            bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
        }.getOrNull()
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .clip(RoundedCornerShape(UiDimens.ControlRadius))
                .background(UiPalette.SurfaceSoft)
                .clickable(onClick = onRefresh),
            contentAlignment = Alignment.Center
        ) {
            if (captchaBitmap != null) {
                Image(
                    bitmap = captchaBitmap,
                    contentDescription = "图片验证码",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    text = if (isLoading) loadingText else placeholder,
                    color = UiPalette.TextSecondary
                )
            }
        }
        OutlinedButton(
            onClick = onRefresh,
            enabled = !isLoading,
            shape = RoundedCornerShape(UiDimens.ControlRadius),
            border = BorderStroke(1.dp, UiPalette.BorderSoft)
        ) {
            Text("刷新")
        }
    }
}
