package top.jlen.vod.offline

import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import top.jlen.vod.data.PlaybackResumeRecord
import top.jlen.vod.data.PlaybackResumeStore
import top.jlen.vod.ui.FullscreenPlayerContent
import top.jlen.vod.ui.PlaybackSnapshot
import java.io.File

internal fun offlineQueue(entries: List<Entry>, selected: Entry): List<Entry> = entries.filter {
    it.stage == Stage.DONE && it.vod == selected.vod && it.si == selected.si && it.source == selected.source
}.sortedBy { it.ei }

internal fun PlaybackSnapshot.offlineResumePosition(): Long? = when {
    durationMs <= 0L -> null
    positionMs >= durationMs || (durationMs > 60_000 && positionMs >= durationMs - 10_000) -> 0L
    else -> positionMs.coerceAtLeast(0L)
}

@Composable
internal fun OfflinePlaybackScreen(downloadId: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val library = remember(context) { OfflineLibrary.get(context) }
    val resumes = remember(context) { PlaybackResumeStore(context) }
    var selectedId by rememberSaveable(downloadId) { mutableStateOf(downloadId) }
    val entries = remember(selectedId) { library.entries.value }
    val row = entries.find { it.id == selectedId && it.stage == Stage.DONE }
    val uri = remember(row) {
        row?.let { File(library.episodeDir(it), it.file) }?.takeIf { it.isFile }?.let(Uri::fromFile)
    }
    fun fail(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        onClose()
    }
    if (row == null || uri == null) {
        LaunchedEffect(selectedId) {
            fail(if (row == null) "该下载已被删除" else "离线文件不存在，请删除后重新下载")
        }
        return
    }

    key(row.id) {
        val next = remember(entries, row) { offlineQueue(entries, row).firstOrNull { it.ei > row.ei } }
        val initial = remember {
            val record = resumes.loadForSource(row.vod, row.source, row.si)
            PlaybackSnapshot(positionMs = record?.takeIf { it.episodeIndex == row.ei }?.positionMs ?: 0L,
                speed = record?.speed ?: 1f)
        }
        val checkpoint = remember {
            OfflineCheckpoint { position, speed ->
                resumes.save(PlaybackResumeRecord(vodId = row.vod, sourceName = row.source, sourceIndex = row.si,
                    episodeIndex = row.ei, positionMs = position, speed = speed, updatedAt = System.currentTimeMillis()))
            }
        }
        DisposableEffect(checkpoint) { onDispose { checkpoint.flush() } }
        FullscreenPlayerContent(
            url = uri.toString(), title = row.title, sourceName = row.source, episodeName = row.episode,
            hasNextEpisode = next != null, initialSnapshot = initial,
            onPlaybackSnapshotChanged = checkpoint::update,
            onNextEpisode = { next?.let { checkpoint.flush(); selectedId = it.id } },
            onClose = onClose, localFilesOnly = true,
            onPlaybackError = { fail("离线文件无法播放，请删除后重新下载") }
        )
    }
}

private class OfflineCheckpoint(val persist: (Long, Float) -> Unit) {
    private var latest: PlaybackSnapshot? = null
    private var saved: PlaybackSnapshot? = null
    private var savedAt = 0L

    fun update(snapshot: PlaybackSnapshot) {
        if (snapshot.offlineResumePosition() == null) return
        latest = snapshot
        if (saved == null || !snapshot.playWhenReady || saved?.speed != snapshot.speed ||
            snapshot.positionMs >= snapshot.durationMs || SystemClock.elapsedRealtime() - savedAt >= 5_000) flush()
    }

    fun flush() {
        val snapshot = latest ?: return
        if (snapshot == saved) return
        persist(snapshot.offlineResumePosition() ?: return, snapshot.speed)
        saved = snapshot
        savedAt = SystemClock.elapsedRealtime()
    }
}
