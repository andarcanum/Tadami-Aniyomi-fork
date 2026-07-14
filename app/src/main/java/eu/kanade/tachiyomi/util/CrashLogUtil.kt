package eu.kanade.tachiyomi.util

import android.content.Context
import android.os.Build
import com.tadami.aurora.BuildConfig
import eu.kanade.tachiyomi.data.backup.BackupDiagnosticLog
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.WebViewUtil
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.lang.withUIContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.OffsetDateTime
import java.time.ZoneId

class CrashLogUtil(
    private val context: Context,
    private val mangaExtensionManager: MangaExtensionManager? = runCatching { Injekt.get<MangaExtensionManager>() }.getOrNull(),
    private val animeExtensionManager: AnimeExtensionManager? = runCatching { Injekt.get<AnimeExtensionManager>() }.getOrNull(),
    private val networkPreferences: NetworkPreferences? = runCatching { Injekt.get<NetworkPreferences>() }.getOrNull(),
) {

    suspend fun dumpLogs(exception: Throwable? = null) = withNonCancellableContext {
        try {
            val file = context.createFileInCacheDir("tadami_crash_logs.txt")

            file.writeText(getDebugInfo() + "\n\n")

            BackupDiagnosticLog.readLog(context)?.let { backupLog ->
                file.appendText("=== Backup diagnostics (last sessions) ===\n\n")
                file.appendText(backupLog)
                file.appendText("\n\n")
            }

            getMangaExtensionsInfo()?.let { file.appendText("$it\n\n") }
            getAnimeExtensionsInfo()?.let { file.appendText("$it\n\n") }
            exception?.let { file.appendText("$it\n\n") }

            val logcatFile = context.createFileInCacheDir("tadami_logcat_dump.tmp")
            dumpLogcat(logcatFile)

            if (logcatFile.exists() && logcatFile.length() > 0) {
                file.appendText("=== System logcat ===\n\n")
                file.appendText(logcatFile.readText())
            }
            logcatFile.delete()

            val uri = file.getUriCompat(context)
            context.startActivity(uri.toShareIntent(context, "text/plain"))
        } catch (e: Throwable) {
            withUIContext { context.toast("Failed to get logs") }
        }
    }

    private fun dumpLogcat(outputFile: java.io.File) {
        val verbose = networkPreferences?.verboseLogging()?.get() ?: false
        val backupFilter = if (verbose) {
            BackupDiagnosticLog.TAG + ":V"
        } else {
            BackupDiagnosticLog.TAG + ":E"
        }
        val filterSpec = arrayOf(
            "logcat",
            "-d",
            "-v", "year",
            "-v", "zone",
            "-f", outputFile.absolutePath,
            backupFilter,
            "*:E",
        )
        Runtime.getRuntime().exec(filterSpec).waitFor()
    }

    fun getDebugInfo(): String {
        return """
            App ID: ${BuildConfig.APPLICATION_ID}
            App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.COMMIT_SHA}, ${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TIME})
            Android version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}; build ${Build.DISPLAY})
            Android build ID: ${Build.DISPLAY}
            Device brand: ${Build.BRAND}
            Device manufacturer: ${Build.MANUFACTURER}
            Device name: ${Build.DEVICE} (${Build.PRODUCT})
            Device model: ${Build.MODEL}
            WebView: ${WebViewUtil.getVersion(context)}
            Verbose logging: ${networkPreferences?.verboseLogging()?.get() ?: false}
            Current time: ${OffsetDateTime.now(ZoneId.systemDefault())}
            MPV version: 6764488
            Libplacebo version: v7.349.0
            FFmpeg version: n7.1
        """.trimIndent()
        // TODO: Use this again (from aniyomi-mpv-lib 1.17.n onwards):

        //    MPV version: ${Utils.VERSIONS.mpv}
        //    Libplacebo version: ${Utils.VERSIONS.libPlacebo}
        //    FFmpeg version: ${Utils.VERSIONS.ffmpeg}
    }

    private fun getMangaExtensionsInfo(): String? {
        val mangaExtensionManager = mangaExtensionManager ?: return null
        val availableExtensions = mangaExtensionManager.availableExtensionsFlow.value.associateBy { it.pkgName }

        val extensionInfoList = mangaExtensionManager.installedExtensionsFlow.value
            .sortedBy { it.name }
            .mapNotNull {
                val availableExtension = availableExtensions[it.pkgName]
                val hasUpdate = (availableExtension?.versionCode ?: 0) > it.versionCode

                if (!hasUpdate && !it.isObsolete) return@mapNotNull null

                """
                    - ${it.name}
                      Installed: ${it.versionName} / Available: ${availableExtension?.versionName ?: "?"}
                      Orphaned: ${it.isObsolete}
                """.trimIndent()
            }

        return if (extensionInfoList.isNotEmpty()) {
            (listOf("Problematic extensions:") + extensionInfoList)
                .joinToString("\n")
        } else {
            null
        }
    }

    private fun getAnimeExtensionsInfo(): String? {
        val animeExtensionManager = animeExtensionManager ?: return null
        val availableExtensions = animeExtensionManager.availableExtensionsFlow.value.associateBy { it.pkgName }

        val extensionInfoList = animeExtensionManager.installedExtensionsFlow.value
            .sortedBy { it.name }
            .mapNotNull {
                val availableExtension = availableExtensions[it.pkgName]
                val hasUpdate = (availableExtension?.versionCode ?: 0) > it.versionCode

                if (!hasUpdate && !it.isObsolete) return@mapNotNull null

                """
                    - ${it.name}
                      Installed: ${it.versionName} / Available: ${availableExtension?.versionName ?: "?"}
                      Orphaned: ${it.isObsolete}
                """.trimIndent()
            }

        return if (extensionInfoList.isNotEmpty()) {
            (listOf("Problematic extensions:") + extensionInfoList)
                .joinToString("\n")
        } else {
            null
        }
    }
}
