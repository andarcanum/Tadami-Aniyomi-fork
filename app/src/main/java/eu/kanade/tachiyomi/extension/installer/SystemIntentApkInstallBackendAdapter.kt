package eu.kanade.tachiyomi.extension.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.util.storage.getUriCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import java.io.File

class SystemIntentApkInstallBackendAdapter(
    private val context: Context,
    private val pendingInstallStore: PendingApkInstallStore,
    override val backend: ApkInstallBackend,
) : ApkInstallBackendAdapter {

    override fun supports(kind: ApkExtensionKind): Boolean = kind == ApkExtensionKind.NOVEL_KOTLIN

    /**
     * The system installer dialog has no result callback here (the intent is launched from a
     * plain context), so completion is inferred from package broadcasts. Missing signals within
     * [INSTALL_TIMEOUT_MS] are reported as Idle — mirroring the STATUS_FAILURE_ABORTED handling
     * of the session-based backends — while the receiver stays registered so a late install
     * still lands on Installed instead of being reported as an error.
     */
    override fun install(request: ApkInstallRequest): Flow<InstallStep> = flow {
        emit(InstallStep.Installing)
        val file = request.file ?: return@flow emit(InstallStep.Error)

        val outcome = CompletableDeferred<InstallStep>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                val action = intent?.action ?: return
                val broadcastPkgName = intent.data?.encodedSchemeSpecificPart ?: return
                if (broadcastPkgName != request.packageName) return
                when (action) {
                    Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REPLACED -> {
                        outcome.complete(InstallStep.Installed)
                    }
                    // A replacement fires REMOVED with EXTRA_REPLACING set; only a plain
                    // removal while we are waiting is a definitive failure.
                    Intent.ACTION_PACKAGE_REMOVED -> {
                        if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                            outcome.complete(InstallStep.Error)
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            try {
                installApk(request, file)
            } catch (error: Throwable) {
                logcat(LogPriority.WARN, error) {
                    "System intent APK install failed for ${request.packageName}"
                }
                emit(InstallStep.Error)
                return@flow
            }

            val firstOutcome = withTimeoutOrNull(INSTALL_TIMEOUT_MS) { outcome.await() }
            if (firstOutcome != null) {
                if (firstOutcome == InstallStep.Installed) {
                    removePendingEntry(request.packageName)
                }
                emit(firstOutcome)
                return@flow
            }

            // Timed out without any signal: report Idle now, but keep listening — a user who
            // confirms the OEM dialog late must still land on Installed.
            emit(InstallStep.Idle)
            val lateOutcome = outcome.await()
            if (lateOutcome == InstallStep.Installed) {
                removePendingEntry(request.packageName)
            }
            emit(lateOutcome)
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    override suspend fun uninstall(request: ApkUninstallRequest): ApkInstallResult {
        return runCatching {
            withUIContext {
                Intent(Intent.ACTION_DELETE, Uri.parse("package:${request.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .let(context::startActivity)
            }
            ApkInstallResult.Installed
        }.getOrElse { ApkInstallResult.Error(it.message ?: it::class.simpleName.orEmpty(), it) }
    }

    override fun cancel(packageName: String) = Unit

    /** Removes only this install's entry: clear() would wipe pending entries of the other
     *  media types waiting for their own user permission. */
    private fun removePendingEntry(packageName: String) {
        pendingInstallStore.remove(packageName)
    }

    @Suppress("DEPRECATION")
    private suspend fun installApk(request: ApkInstallRequest, apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            pendingInstallStore.save(
                PendingApkInstallStore.PendingInstall(
                    packageName = request.packageName,
                    displayName = request.displayName,
                    filePath = apkFile.absolutePath,
                    kind = request.kind,
                    backend = request.backend,
                ),
            )
            withUIContext {
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .let(context::startActivity)
            }
            error("Package install permission is required")
        }

        withUIContext {
            Intent(Intent.ACTION_INSTALL_PACKAGE)
                .setDataAndType(apkFile.getUriCompat(context), APK_MIME)
                .putExtra(Intent.EXTRA_RETURN_RESULT, false)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .let(context::startActivity)
        }
    }

    private companion object {
        const val APK_MIME = "application/vnd.android.package-archive"

        /**
         * Budget for the system installer dialog interaction. The old 5-minute window kept rows
         * effectively spinning when the dialog was dismissed without any package broadcast; 90 s
         * still tolerates slow OEM installers. Late confirms keep landing on Installed via the
         * keep-listening path above — the terminal-aware screen mirror makes that safe.
         */
        const val INSTALL_TIMEOUT_MS = 90_000L
    }
}
