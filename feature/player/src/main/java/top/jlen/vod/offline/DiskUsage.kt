package top.jlen.vod.offline

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import android.os.storage.StorageManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

internal data class DiskUsage(val capacity: Long, val free: Long, val folders: Map<String, Long>, val covers: Set<String>) {
    val downloads = folders.values.sum()
}

/** Scan local files on IO, including partial transfers and shared artwork. */
@Composable
internal fun rememberDiskUsage(lib: OfflineLibrary): DiskUsage? {
    val context = LocalContext.current.applicationContext
    val revision by lib.diskRevision.collectAsState()
    return produceState<DiskUsage?>(null, lib, revision) {
        while (true) {
            value = withContext(Dispatchers.IO) { runCatching { measureDisk(context, lib.root) }.getOrNull() }
            delay(if (lib.entries.value.any { it.stage == Stage.ACTIVE }) 5_000L else 60_000L)
        }
    }.value
}

private fun measureDisk(context: Context, root: File): DiskUsage {
    val fs = StatFs(root.path)
    val capacity = if (Build.VERSION.SDK_INT >= 26) runCatching {
        val uuid = context.getSystemService(StorageManager::class.java).getUuidForPath(root)
        context.getSystemService(StorageStatsManager::class.java).getTotalBytes(uuid)
    }.getOrNull()?.takeIf { it > 0 } ?: fs.totalBytes else fs.totalBytes
    val covers = mutableSetOf<String>()
    val folders = root.listFiles().orEmpty().associate { dir ->
        if (File(dir,"cover.image").isFile) covers += dir.name
        dir.name to dir.walkTopDown().filter(File::isFile).sumOf(File::length)
    }
    return DiskUsage(capacity, fs.availableBytes, folders, covers)
}
