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
import kotlinx.coroutines.withTimeout
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

    override fun install(request: ApkInstallRequest): Flow<InstallStep> = flow {
        emit(InstallStep.Installing)
        val file = request.file ?: return@flow emit(InstallStep.Error)
        runCatching {
            awaitSystemInstall(request, file)
        }.onSuccess {
            emit(InstallStep.Installed)
        }.onFailure { error ->
            logcat(LogPriority.WARN, error) { "System intent APK install failed for ${request.packageName}" }
            emit(InstallStep.Error)
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

    private suspend fun awaitSystemInstall(request: ApkInstallRequest, apkFile: File) {
        val installResult = CompletableDeferred<Unit>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                val action = intent?.action ?: return
                if (action != Intent.ACTION_PACKAGE_ADDED && action != Intent.ACTION_PACKAGE_REPLACED) return
                val installedPkgName = intent.data?.encodedSchemeSpecificPart ?: return
                if (installedPkgName == request.packageName) {
                    installResult.complete(Unit)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            installApk(request, apkFile)
            withTimeout(INSTALL_TIMEOUT_MS) { installResult.await() }
            // Remove only this install's entry: clear() would wipe pending entries of the
            // other media types waiting for their own user permission.
            pendingInstallStore.remove(request.packageName)
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
        }
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
        const val INSTALL_TIMEOUT_MS = 5 * 60 * 1000L
    }
}
