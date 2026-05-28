package top.jlen.vod.data

import android.content.Context
import java.io.File
import java.nio.charset.StandardCharsets

internal fun Context.runtimeSharedPrefsFileSize(
    prefsName: String,
    excludedKeys: Set<String> = emptySet()
): Long {
    val appContext = applicationContext
    val prefs = appContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    val estimatedValuesBytes = if (excludedKeys.isEmpty()) {
        0L
    } else {
        prefs.all
            .filterKeys { it in excludedKeys }
            .values
            .sumOf(::runtimeEstimatePreferenceValueSize)
    }
    val prefsDir = appContext.filesDir
        .resolve("../shared_prefs")
        .canonicalFile
    val base = prefsDir.resolve("$prefsName.xml")
    val backup = prefsDir.resolve("$prefsName.xml.bak")
    return (base.runtimeLengthIfFile() + backup.runtimeLengthIfFile() - estimatedValuesBytes)
        .coerceAtLeast(0L)
}

internal fun runtimeEstimatePreferenceValueSize(value: Any?): Long =
    when (value) {
        is String -> value.toByteArray(StandardCharsets.UTF_8).size.toLong()
        is Set<*> -> value.sumOf { runtimeEstimatePreferenceValueSize(it) }
        else -> value?.toString()?.toByteArray(StandardCharsets.UTF_8)?.size?.toLong() ?: 0L
    }

internal fun File.runtimeDirectorySize(): Long {
    if (!exists()) return 0L
    if (isFile) return length()
    return listFiles()
        ?.sumOf(File::runtimeDirectorySize)
        ?: 0L
}

internal fun File.runtimeDeleteFilesOlderThan(cutoffMs: Long) {
    if (!exists()) return
    runtimeWalkCacheFilesOldestFirst()
        .filter { file -> file.lastModified().takeIf { it > 0L }?.let { it < cutoffMs } == true }
        .forEach { it.delete() }
    runtimePruneEmptyDirectories()
}

internal fun File.runtimeWalkCacheFilesOldestFirst(): List<File> {
    if (!exists()) return emptyList()
    if (isFile) return listOf(this)
    return walkTopDown()
        .filter { it.isFile }
        .sortedBy { it.lastModified().takeIf { modified -> modified > 0L } ?: Long.MAX_VALUE }
        .toList()
}

internal fun File.runtimePruneEmptyDirectories() {
    if (!exists() || isFile) return
    walkBottomUp()
        .filter { it.isDirectory && it != this }
        .forEach { dir ->
            if (dir.listFiles()?.isEmpty() == true) {
                dir.delete()
            }
        }
}

private fun File.runtimeLengthIfFile(): Long =
    if (isFile) length() else 0L
