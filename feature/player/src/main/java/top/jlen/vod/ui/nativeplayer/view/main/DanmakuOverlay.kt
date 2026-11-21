package top.jlen.vod.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.jlen.vod.data.TimedComment

private const val FLIGHT_MS = 6500L
internal data class CommentSlot(val message: TimedComment, val lane: Int)

internal fun scheduleComments(messages: List<TimedComment>, lanes: Int): List<CommentSlot> {
    val freeAt = LongArray(lanes)
    return messages.sortedBy { it.timeMs }.mapNotNull { message ->
        val lane = if (message.type == 2) freeAt.indexOfLast { it <= message.timeMs }
                   else freeAt.indexOfFirst { it <= message.timeMs }
        if (lane < 0) return@mapNotNull null
        freeAt[lane] = message.timeMs + FLIGHT_MS
        CommentSlot(message, lane)
    }
}

// Look up the visible window from media time, including when playback jumps backwards.
internal fun visibleSlots(slots: List<CommentSlot>, positionMs: Long): IntRange {
    fun firstAfter(time: Long): Int {
        var low = 0
        var high = slots.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (slots[mid].message.timeMs <= time) low = mid + 1 else high = mid
        }
        return low
    }
    return firstAfter(positionMs - FLIGHT_MS) until firstAfter(positionMs)
}

@Composable
internal fun DanmakuOverlay(messages: List<TimedComment>, playing: Boolean, position: Long, clock: () -> Long) {
    val density = LocalDensity.current
    val font = with(density) { 16.sp.toPx() }
    val paint = remember(font) { Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = font
        isFakeBoldText = true
        setShadowLayer(3f, 1f, 1f, android.graphics.Color.BLACK)
    } }
    var frame by remember { mutableLongStateOf(0L) }
    val latestClock by rememberUpdatedState(clock)
    LaunchedEffect(playing) {
        if (playing) while (true) withFrameMillis { frame = latestClock() }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().fillMaxHeight(.5f).padding(top = 6.dp).clipToBounds()) {
        val lanes = (constraints.maxHeight / (font * 1.5f)).toInt().coerceIn(1, 6)
        val slots = remember(messages, lanes) { scheduleComments(messages, lanes) }
        Canvas(Modifier.fillMaxSize()) {
            val now = if (playing) frame else position
            for (i in visibleSlots(slots, now)) {
                val (message, lane) = slots[i]
                val width = paint.measureText(message.text)
                val x = if (message.type in 1..2) (size.width - width) / 2 else
                    size.width - (size.width + width) * (now - message.timeMs).toFloat() / FLIGHT_MS
                paint.color = message.color.removePrefix("#").takeIf { it.length == 6 }?.toIntOrNull(16)?.or(0xff000000.toInt()) ?: -1
                drawContext.canvas.nativeCanvas.drawText(message.text, x, font + lane * font * 1.5f, paint)
            }
        }
    }
}
