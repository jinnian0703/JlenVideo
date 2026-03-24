package top.jlen.vod

import android.content.Context
import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess

object CrashLogger {
    private const val CRASH_DIR = "crash_logs"
    private const val LATEST_CRASH_FILE = "latest_crash.txt"
    private const val MAX_HISTORY_FILES = 8

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            val appContext = context.applicationContext
            val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                runCatching { writeCrashLog(appContext, thread, throwable) }
                if (previousHandler != null) {
                    previousHandler.uncaughtException(thread, throwable)
                } else {
                    Process.killProcess(Process.myPid())
                    exitProcess(10)
                }
            }
            installed = true
        }
    }

    fun readLatest(context: Context): String = runCatching {
        latestCrashFile(context).takeIf(File::exists)?.readText(StandardCharsets.UTF_8).orEmpty()
    }.getOrDefault("")

    fun clear(context: Context) {
        crashDir(context).listFiles()?.forEach { file ->
            runCatching { file.delete() }
        }
    }

    private fun writeCrashLog(context: Context, thread: Thread, throwable: Throwable) {
        val now = LocalDateTime.now()
        val displayTimestamp = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val fileTimestamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val stackTrace = StringWriter().also { writer ->
            PrintWriter(writer).use { printWriter ->
                throwable.printStackTrace(printWriter)
            }
        }.toString()

        val content = buildString {
            appendLine("time=$displayTimestamp")
            appendLine("thread=${thread.name}")
            appendLine("versionName=${BuildConfig.VERSION_NAME}")
            appendLine("versionCode=${BuildConfig.VERSION_CODE}")
            appendLine("package=${BuildConfig.APPLICATION_ID}")
            appendLine()
            appendLine(stackTrace)
        }

        val dir = crashDir(context).apply { mkdirs() }
        latestCrashFile(context).writeText(content, StandardCharsets.UTF_8)
        File(dir, "crash_$fileTimestamp.txt").writeText(content, StandardCharsets.UTF_8)

        dir.listFiles()
            ?.filter { it.name.startsWith("crash_") && it.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_HISTORY_FILES)
            ?.forEach { oldFile -> runCatching { oldFile.delete() } }
    }

    private fun latestCrashFile(context: Context): File = File(crashDir(context), LATEST_CRASH_FILE)

    private fun crashDir(context: Context): File = File(context.filesDir, CRASH_DIR)
}
