package top.jlen.vod

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess

data class IssueLogEntry(
    val id: String,
    val title: String,
    val time: String,
    val summary: String
)

object CrashLogger {
    private const val CRASH_DIR = "crash_logs"
    private const val LATEST_CRASH_FILE = "latest_crash.txt"
    private const val SESSION_FILE = "session_state.properties"
    private const val TRACE_FILE = "recent_trace.txt"
    private const val EXIT_FILE_PREFIX = "exit_"
    private const val CRASH_FILE_PREFIX = "crash_"
    private const val MAX_HISTORY_FILES = 8
    private const val MAX_TRACE_LINES = 80
    private const val MAX_TRACE_CHARS = 12_000
    private const val MAX_EXIT_TRACE_CHARS = 8_000

    @Volatile
    private var installed = false

    @Volatile
    private var currentSessionId: String = ""

    private val traceLock = Any()
    private val pendingTraceLines = ArrayDeque<String>()
    private const val FLUSH_TRACE_LINES = 12

    fun install(context: Context) {
        val appContext = context.applicationContext
        if (installed) return
        synchronized(this) {
            if (installed) return
            val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            beginSession(appContext)
            writePreviousExitReasonIfNeeded(appContext)
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                runCatching { writeCrashLog(appContext, thread, throwable) }
                runCatching { markSession(appContext, "crashed") }
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

    fun registerApplicationCallbacks(application: android.app.Application) {
        application.registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                recordEvent(application, "activityCreated", activity.javaClass.simpleName)
            }

            override fun onActivityStarted(activity: Activity) {
                markForeground(application, true, activity.javaClass.simpleName)
                recordEvent(application, "activityStarted", activity.javaClass.simpleName)
            }

            override fun onActivityResumed(activity: Activity) {
                markForeground(application, true, activity.javaClass.simpleName)
                recordEvent(application, "activityResumed", activity.javaClass.simpleName)
            }

            override fun onActivityPaused(activity: Activity) {
                recordEvent(application, "activityPaused", activity.javaClass.simpleName)
            }

            override fun onActivityStopped(activity: Activity) {
                markForeground(application, false, activity.javaClass.simpleName)
                recordEvent(application, "activityStopped", activity.javaClass.simpleName)
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

            override fun onActivityDestroyed(activity: Activity) {
                recordEvent(application, "activityDestroyed", activity.javaClass.simpleName)
            }
        })
    }

    fun recordRoute(context: Context, route: String) {
        val normalized = sanitize(route).ifBlank { "unknown" }
        recordEvent(context, "route", normalized)
        updateSession(context) { state ->
            state["lastRoute"] = normalized
        }
    }

    fun recordEvent(context: Context, type: String, detail: String = "") {
        val safeType = sanitize(type).ifBlank { "event" }
        val safeDetail = sanitize(detail)
        appendTrace(context.applicationContext, "$safeType${if (safeDetail.isBlank()) "" else "=$safeDetail"}")
    }

    fun recordMemoryTrim(context: Context, level: Int) {
        recordEvent(context, "trimMemory", "level=$level")
        updateSession(context) { state ->
            state["lastTrimMemoryLevel"] = level.toString()
        }
    }

    fun recordLowMemory(context: Context) {
        recordEvent(context, "lowMemory")
        updateSession(context) { state ->
            state["lastLowMemory"] = displayNow()
        }
    }

    fun readLatest(context: Context): String = runCatching {
        readIssueLogEntries(context).joinToString("\n") { "${it.time} ${it.title}" }
            .ifBlank { latestCrashFile(context).takeIf(File::exists)?.readText(StandardCharsets.UTF_8).orEmpty() }
    }.getOrDefault("")

    fun readIssueLogEntries(context: Context): List<IssueLogEntry> = runCatching {
        flushTrace(context.applicationContext)
        issueLogFiles(context).map { file ->
            val text = file.readText(StandardCharsets.UTF_8)
            IssueLogEntry(
                id = file.name,
                title = firstLogValue(text, "type").ifBlank { file.nameWithoutExtension },
                time = firstLogValue(text, "time").ifBlank { formatFileTime(file) },
                summary = listOf(
                    firstLogValue(text, "reason"),
                    firstLogValue(text, "lastRoute"),
                    firstLogValue(text, "lastActivity")
                ).filter { it.isNotBlank() }.joinToString(" · ")
            )
        }
    }.getOrDefault(emptyList())

    fun readIssueLog(context: Context, id: String): String = runCatching {
        flushTrace(context.applicationContext)
        issueLogFile(context, id)?.readText(StandardCharsets.UTF_8).orEmpty()
    }.getOrDefault("")

    fun deleteIssueLog(context: Context, id: String) {
        runCatching { issueLogFile(context, id)?.delete() }
        val latest = issueLogFiles(context).firstOrNull()
        if (latest == null) {
            runCatching { latestCrashFile(context).delete() }
        } else {
            runCatching { latestCrashFile(context).writeText(latest.readText(StandardCharsets.UTF_8), StandardCharsets.UTF_8) }
        }
    }

    fun clear(context: Context) {
        crashDir(context).listFiles()?.forEach { file ->
            runCatching { file.delete() }
        }
        runCatching { beginSession(context.applicationContext) }
    }

    private fun beginSession(context: Context) {
        val now = LocalDateTime.now()
        currentSessionId = now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + "_${Process.myPid()}"
        val previous = readSession(context)
        if (previous["status"] == "foreground") {
            writeSuspiciousExitLog(context, previous)
        }
        val state = linkedMapOf(
            "sessionId" to currentSessionId,
            "status" to "started",
            "startedAt" to displayNow(),
            "pid" to Process.myPid().toString(),
            "versionName" to AppRuntimeInfo.versionName,
            "versionCode" to AppRuntimeInfo.versionCode.toString(),
            "package" to AppRuntimeInfo.applicationId,
            "device" to "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            "android" to "Android ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}",
            "lastRoute" to previous["lastRoute"].orEmpty()
        )
        writeSession(context, state)
        appendTrace(context, "sessionStart=${state["sessionId"]}")
    }

    private fun markForeground(context: Context, foreground: Boolean, activityName: String) {
        updateSession(context) { state ->
            state["status"] = if (foreground) "foreground" else "background"
            state["lastActivity"] = sanitize(activityName)
            state["updatedAt"] = displayNow()
        }
    }

    private fun markSession(context: Context, status: String) {
        updateSession(context) { state ->
            state["status"] = status
            state["updatedAt"] = displayNow()
        }
    }

    private fun writeCrashLog(context: Context, thread: Thread, throwable: Throwable) {
        val stackTrace = StringWriter().also { writer ->
            PrintWriter(writer).use { printWriter ->
                throwable.printStackTrace(printWriter)
            }
        }.toString()

        val content = buildIssueLog(
            title = "未捕获崩溃",
            extra = listOf("thread" to sanitize(thread.name)),
            body = stackTrace,
            context = context
        )
        writeIssueLog(context, CRASH_FILE_PREFIX, content)
    }

    private fun writeSuspiciousExitLog(context: Context, previous: Map<String, String>) {
        val content = buildIssueLog(
            title = "疑似异常退出",
            extra = listOf(
                "previousSession" to previous["sessionId"].orEmpty(),
                "previousStatus" to previous["status"].orEmpty(),
                "lastActivity" to previous["lastActivity"].orEmpty(),
                "lastRoute" to previous["lastRoute"].orEmpty(),
                "updatedAt" to previous["updatedAt"].orEmpty()
            ),
            body = "上次运行处于前台状态时未记录到正常后台/停止事件，可能是卡退、系统杀进程或低内存回收。",
            context = context
        )
        writeIssueLog(context, EXIT_FILE_PREFIX, content)
    }

    private fun writePreviousExitReasonIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        val exitInfo = runCatching {
            activityManager.getHistoricalProcessExitReasons(context.packageName, 0, 5)
        }.getOrNull().orEmpty().firstOrNull() ?: return
        val exitTimestamp = exitInfo.timestamp
        val state = readSession(context)
        val lastExitTimestamp = state["lastExitTimestamp"]?.toLongOrNull()
        if (lastExitTimestamp == exitTimestamp) return
        updateSession(context) { it["lastExitTimestamp"] = exitTimestamp.toString() }
        if (!exitInfo.isProblemExit()) return

        val trace = runCatching {
            exitInfo.traceInputStream?.bufferedReader()?.use { reader ->
                reader.readText().take(MAX_EXIT_TRACE_CHARS)
            }.orEmpty()
        }.getOrDefault("")
        val content = buildIssueLog(
            title = "上次进程退出原因",
            extra = listOf(
                "reason" to exitReasonName(exitInfo.reason),
                "importance" to exitInfo.importance.toString(),
                "description" to sanitize(exitInfo.description.orEmpty()),
                "pss" to exitInfo.pss.toString(),
                "rss" to exitInfo.rss.toString(),
                "timestamp" to exitTimestamp.toString()
            ),
            body = trace.ifBlank { "系统未提供附加 trace。" },
            context = context
        )
        writeIssueLog(context, EXIT_FILE_PREFIX, content)
    }

    private fun android.app.ApplicationExitInfo.isProblemExit(): Boolean = when (reason) {
        android.app.ApplicationExitInfo.REASON_ANR,
        android.app.ApplicationExitInfo.REASON_CRASH,
        android.app.ApplicationExitInfo.REASON_CRASH_NATIVE,
        android.app.ApplicationExitInfo.REASON_LOW_MEMORY,
        android.app.ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
        android.app.ApplicationExitInfo.REASON_SIGNALED -> true
        else -> false
    }

    private fun buildIssueLog(
        title: String,
        extra: List<Pair<String, String>>,
        body: String,
        context: Context
    ): String {
        flushTrace(context.applicationContext)
        val session = readSession(context)
        val trace = readTrace(context)
        return buildString {
            appendLine("type=$title")
            appendLine("time=${displayNow()}")
            appendLine("session=${currentSessionId.ifBlank { session["sessionId"].orEmpty() }}")
            appendLine("versionName=${AppRuntimeInfo.versionName}")
            appendLine("versionCode=${AppRuntimeInfo.versionCode}")
            appendLine("package=${AppRuntimeInfo.applicationId}")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}".trim())
            appendLine("android=Android ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}")
            appendLine("pid=${Process.myPid()}")
            appendLine("lastActivity=${session["lastActivity"].orEmpty()}")
            appendLine("lastRoute=${session["lastRoute"].orEmpty()}")
            extra.filter { it.second.isNotBlank() }.forEach { (key, value) ->
                appendLine("$key=$value")
            }
            appendLine()
            appendLine("recentTrace:")
            appendLine(trace.ifBlank { "暂无运行轨迹" })
            appendLine()
            appendLine("details:")
            appendLine(body)
        }
    }

    private fun writeIssueLog(context: Context, prefix: String, content: String) {
        val dir = crashDir(context).apply { mkdirs() }
        val fileTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        latestCrashFile(context).writeText(content, StandardCharsets.UTF_8)
        File(dir, "$prefix$fileTimestamp.txt").writeText(content, StandardCharsets.UTF_8)

        issueLogFiles(context)
            .drop(MAX_HISTORY_FILES)
            .forEach { oldFile -> runCatching { oldFile.delete() } }
    }

    private fun issueLogFiles(context: Context): List<File> =
        crashDir(context)
            .listFiles()
            ?.filter {
                (it.name.startsWith(CRASH_FILE_PREFIX) || it.name.startsWith(EXIT_FILE_PREFIX)) &&
                    it.name.endsWith(".txt")
            }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    private fun issueLogFile(context: Context, id: String): File? =
        issueLogFiles(context).firstOrNull { it.name == id }

    private fun firstLogValue(text: String, key: String): String =
        text.lineSequence()
            .firstOrNull { it.startsWith("$key=") }
            ?.substringAfter('=')
            .orEmpty()

    private fun formatFileTime(file: File): String =
        LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(file.lastModified()),
            java.time.ZoneId.systemDefault()
        ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

    private fun appendTrace(context: Context, message: String) {
        val line = "${displayNow()} ${sanitize(message)}"
        val shouldFlush = synchronized(traceLock) {
            pendingTraceLines.addLast(line)
            while (pendingTraceLines.size > MAX_TRACE_LINES) {
                pendingTraceLines.removeFirst()
            }
            pendingTraceLines.size >= FLUSH_TRACE_LINES
        }
        if (shouldFlush) {
            flushTrace(context)
        }
    }

    private fun flushTrace(context: Context) {
        runCatching {
            val pending = synchronized(traceLock) {
                if (pendingTraceLines.isEmpty()) return
                pendingTraceLines.toList().also { pendingTraceLines.clear() }
            }
            val file = traceFile(context.applicationContext)
            val existing = file.takeIf(File::exists)
                ?.readLines(StandardCharsets.UTF_8)
                .orEmpty()
            val next = (existing + pending)
                .takeLast(MAX_TRACE_LINES)
            file.parentFile?.mkdirs()
            file.writeText(next.joinToString("\n").takeLast(MAX_TRACE_CHARS), StandardCharsets.UTF_8)
        }
    }

    private fun readTrace(context: Context): String = runCatching {
        traceFile(context).takeIf(File::exists)?.readText(StandardCharsets.UTF_8).orEmpty()
    }.getOrDefault("")

    private fun updateSession(context: Context, mutate: (MutableMap<String, String>) -> Unit) {
        runCatching {
            val state = readSession(context).toMutableMap()
            mutate(state)
            state["updatedAt"] = displayNow()
            writeSession(context, state)
        }
    }

    private fun readSession(context: Context): Map<String, String> = runCatching {
        sessionFile(context)
            .takeIf(File::exists)
            ?.readLines(StandardCharsets.UTF_8)
            .orEmpty()
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) null else line.take(separator) to line.drop(separator + 1)
            }
            .toMap()
    }.getOrDefault(emptyMap())

    private fun writeSession(context: Context, state: Map<String, String>) {
        val file = sessionFile(context)
        file.parentFile?.mkdirs()
        file.writeText(
            state.entries.joinToString("\n") { (key, value) -> "$key=${sanitize(value)}" },
            StandardCharsets.UTF_8
        )
    }

    private fun exitReasonName(reason: Int): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return reason.toString()
        return when (reason) {
            android.app.ApplicationExitInfo.REASON_ANR -> "ANR"
            android.app.ApplicationExitInfo.REASON_CRASH -> "CRASH"
            android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
            android.app.ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
            android.app.ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
            android.app.ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
            android.app.ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
            android.app.ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
            android.app.ApplicationExitInfo.REASON_OTHER -> "OTHER"
            android.app.ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
            android.app.ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
            android.app.ApplicationExitInfo.REASON_UNKNOWN -> "UNKNOWN"
            android.app.ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
            android.app.ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
            else -> reason.toString()
        }
    }

    private fun sanitize(value: String): String =
        value.replace(Regex("(?i)(password|pwd|cookie|token|code|verify|url)=([^\\s&]+)"), "$1=<redacted>")
            .replace('\n', ' ')
            .replace('\r', ' ')
            .take(500)

    private fun displayNow(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

    private fun latestCrashFile(context: Context): File = File(crashDir(context), LATEST_CRASH_FILE)

    private fun sessionFile(context: Context): File = File(crashDir(context), SESSION_FILE)

    private fun traceFile(context: Context): File = File(crashDir(context), TRACE_FILE)

    private fun crashDir(context: Context): File = File(context.filesDir, CRASH_DIR)
}
