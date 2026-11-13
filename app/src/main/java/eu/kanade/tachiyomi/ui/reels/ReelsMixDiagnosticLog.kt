package eu.kanade.tachiyomi.ui.reels

import android.app.Application
import android.content.Context
import logcat.LogPriority
import logcat.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Lightweight, privacy-safe ring-buffer diagnostics for Reels multi-source Mix mode.
 *
 * Events are kept in memory, mirrored via [logcat], and persisted best-effort asynchronously to
 * [FILE_NAME] so [eu.kanade.tachiyomi.util.CrashLogUtil] includes them in the standard log
 * export without ever blocking the caller thread. Safe in pure-JVM unit tests (disk writes are
 * skipped cleanly when no Android [Application] is registered in [Injekt]).
 */
object ReelsMixDiagnosticLog {

    const val TAG = "ReelsMixDiag"

    private const val FILE_NAME = "reels_mix_diag.log"
    private const val MAX_FILE_BYTES = 64 * 1024
    private const val MAX_MEMORY_LINES = 400

    private val lock = Any()
    private val fileLock = Any()
    private val memoryLines = ArrayDeque<String>(MAX_MEMORY_LINES)
    private val fileExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ReelsMixDiagWriter").apply { isDaemon = true }
    }
    private val timestampFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat =
            SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    }

    fun log(event: String, details: String? = null) {
        val line = formatLine(event, details)
        synchronized(lock) {
            if (memoryLines.size >= MAX_MEMORY_LINES) {
                memoryLines.removeFirst()
            }
            memoryLines.addLast(line)
        }
        val appContext = resolveAppContext()
        if (appContext != null) {
            runCatching {
                fileExecutor.execute {
                    synchronized(fileLock) {
                        appendToFileLocked(appContext, line)
                    }
                }
            }
        }
        runCatching { logcat(priority = LogPriority.INFO, tag = TAG) { line } }
    }

    fun readLog(context: Context): String? {
        val diskText = synchronized(fileLock) {
            val file = getFile(context)
            if (file.exists() && file.length() > 0L) {
                runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        }
        if (diskText != null) return diskText
        synchronized(lock) {
            if (memoryLines.isEmpty()) return null
            return memoryLines.joinToString("\n")
        }
    }

    private fun formatLine(event: String, details: String?): String {
        val stamp = timestampFormat.get()?.format(Date()) ?: ""
        return if (details.isNullOrBlank()) {
            "$stamp $event"
        } else {
            "$stamp $event | $details"
        }
    }

    private fun resolveAppContext(): Context? =
        runCatching { Injekt.get<Application>() }.getOrNull()

    private fun getFile(context: Context): File = File(context.filesDir, FILE_NAME)

    private fun appendToFileLocked(context: Context, line: String) {
        try {
            val file = getFile(context)
            file.appendText("$line\n")
            if (file.length() > MAX_FILE_BYTES) {
                val tail = file.readText().takeLast(MAX_FILE_BYTES / 2)
                file.writeText(tail)
            }
        } catch (_: Exception) {
            // Best-effort only
        }
    }
}
