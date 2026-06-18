package eu.kanade.tachiyomi.extension.manga.util

import android.app.DownloadManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import com.tadami.aurora.R
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.manga.installer.InstallerManga
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.isPackageInstalled
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.transformWhile
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import kotlin.time.Duration.Companion.seconds

/**
 * The installer which installs, updates and uninstalls the extensions.
 *
 * @param context The application context.
 */
internal class MangaExtensionInstaller(private val context: Context) {

    /**
     * The system's download manager
     */
    private val downloadManager = context.getSystemService<DownloadManager>()!!

    /**
     * The broadcast receiver which listens to download completion events.
     */
    private val downloadReceiver = DownloadCompletionReceiver()

    /**
     * The currently requested downloads, with the package name (unique id) as key, and the id
     * returned by the download manager.
     */
    private val activeDownloads = hashMapOf<String, Long>()

    /**
     * Reverse mapping from download id to package name. Used to register the extension directly
     * after install without relying on the broadcast receiver.
     */
    private val downloadIdToPkgName = hashMapOf<Long, String>()

    private val handledDownloadIds = hashSetOf<Long>()

    private val downloadsStateFlows = hashMapOf<Long, MutableStateFlow<InstallStep>>()

    private val extensionInstaller = Injekt.get<BasePreferences>().extensionInstaller()

    /**
     * Adds the given extension to the downloads queue and returns an observable containing its
     * step in the installation process.
     *
     * @param url The url of the apk.
     * @param extension The extension to install.
     */
    fun downloadAndInstall(url: String, extension: MangaExtension): Flow<InstallStep> {
        val pkgName = extension.pkgName

        val oldDownload = activeDownloads[pkgName]
        if (oldDownload != null) {
            deleteDownload(pkgName)
        }

        // Register the receiver after removing (and unregistering) the previous download
        downloadReceiver.register()

        val downloadUri = url.toUri()
        val request = DownloadManager.Request(downloadUri)
            .setTitle(extension.name)
            .setMimeType(APK_MIME)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                downloadUri.lastPathSegment,
            )
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        val id = downloadManager.enqueue(request)
        activeDownloads[pkgName] = id
        downloadIdToPkgName[id] = pkgName

        val downloadStateFlow = MutableStateFlow(InstallStep.Pending)
        downloadsStateFlows[id] = downloadStateFlow

        // Poll download status
        val pollStatusFlow = downloadStatusFlow(id).mapNotNull { downloadStatus ->
            // Map to our model and also handle terminal states in case the
            // DownloadManager completion broadcast is dropped by the OS/OEM ROM.
            when (downloadStatus) {
                DownloadManager.STATUS_PENDING -> InstallStep.Pending
                DownloadManager.STATUS_RUNNING -> InstallStep.Downloading
                DownloadManager.STATUS_SUCCESSFUL, DownloadManager.STATUS_FAILED -> {
                    handleDownloadCompletion(id)
                    null
                }
                else -> null
            }
        }

        return merge(downloadStateFlow, pollStatusFlow).transformWhile {
            emit(it)
            // Stop when the application is installed or errors
            !it.isCompleted()
        }.onCompletion { cause ->
            // Do not cancel the underlying DownloadManager request when the UI collection is
            // cancelled, for example when the screen model is destroyed by navigation or OEM
            // background restrictions. Explicit user cancellation still goes through
            // cancelInstall(), which removes the DownloadManager request.
            if (cause == null) {
                // Always notify on main thread
                withUIContext {
                    deleteDownload(pkgName)
                }
            }
        }
    }

    /**
     * Returns a flow that polls the given download id for its status every second, as the
     * manager doesn't have any notification system. It'll stop once the download finishes.
     *
     * @param id The id of the download to poll.
     */
    private fun downloadStatusFlow(id: Long): Flow<Int> = flow {
        val query = DownloadManager.Query().setFilterById(id)

        while (true) {
            // Get the current download status
            val downloadStatus = downloadManager.query(query).use { cursor ->
                if (!cursor.moveToFirst()) return@flow
                cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            }

            emit(downloadStatus)

            // Stop polling when the download fails or finishes
            if (downloadStatus == DownloadManager.STATUS_SUCCESSFUL ||
                downloadStatus == DownloadManager.STATUS_FAILED
            ) {
                return@flow
            }

            delay(1.seconds)
        }
    }
        // Ignore duplicate results
        .distinctUntilChanged()

    /**
     * Starts an intent to install the extension at the given uri.
     *
     * @param uri The uri of the extension to install.
     */
    fun installApk(downloadId: Long, uri: Uri) {
        val installer = extensionInstaller.get()
        if (requiresUnknownAppsPermission(installer)) {
            requestUnknownAppsPermission(downloadId)
            return
        }

        when (installer) {
            BasePreferences.ExtensionInstaller.LEGACY -> {
                val pkgName = downloadIdToPkgName[downloadId]
                val intent = Intent(context, MangaExtensionInstallActivity::class.java)
                    .setDataAndType(uri, APK_MIME)
                    .putExtra(EXTRA_DOWNLOAD_ID, downloadId)
                    .setFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                if (pkgName != null) {
                    intent.putExtra(EXTRA_PACKAGE_NAME, pkgName)
                }

                context.startActivity(intent)
            }
            BasePreferences.ExtensionInstaller.PRIVATE -> {
                val extensionManager = Injekt.get<MangaExtensionManager>()
                val tempFile = File(context.cacheDir, "temp_$downloadId")

                if (tempFile.exists() && !tempFile.delete()) {
                    // Unlikely but just in case
                    extensionManager.updateInstallStep(downloadId, InstallStep.Error)
                    return
                }

                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    if (MangaExtensionLoader.installPrivateExtensionFile(context, tempFile)) {
                        val pkgName = downloadIdToPkgName[downloadId]
                        if (pkgName != null) {
                            extensionManager.reloadAndRegisterExtension(pkgName)
                        }
                        extensionManager.updateInstallStep(downloadId, InstallStep.Installed)
                    } else {
                        extensionManager.updateInstallStep(downloadId, InstallStep.Error)
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to read downloaded extension file." }
                    extensionManager.updateInstallStep(downloadId, InstallStep.Error)
                }

                tempFile.delete()
            }
            else -> {
                val intent =
                    MangaExtensionInstallService.getIntent(context, downloadId, uri, installer)
                try {
                    ContextCompat.startForegroundService(context, intent)
                } catch (e: ForegroundServiceStartNotAllowedException) {
                    val pendingIntent = PendingIntent.getService(
                        context,
                        downloadId.toInt(),
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                    val notification = context.notificationBuilder(
                        Notifications.CHANNEL_EXTENSIONS_UPDATE,
                    ) {
                        setSmallIcon(R.drawable.ic_ani)
                        setContentTitle(
                            context.stringResource(MR.strings.ext_install_service_notif),
                        )
                        setContentIntent(pendingIntent)
                        setAutoCancel(true)
                    }.build()
                    context.notify(
                        Notifications.ID_EXTENSION_INSTALLER_PENDING,
                        notification,
                    )
                    updateInstallStep(downloadId, InstallStep.Idle)
                }
            }
        }
    }

    /**
     * Cancels extension install and remove from download manager and installer.
     */
    fun cancelInstall(pkgName: String) {
        val downloadId = activeDownloads.remove(pkgName) ?: return
        downloadManager.remove(downloadId)
        InstallerManga.cancelInstallQueue(context, downloadId)
    }

    /**
     * Starts an intent to uninstall the extension by the given package name.
     *
     * @param pkgName The package name of the extension to uninstall
     */
    fun uninstallApk(pkgName: String) {
        if (context.isPackageInstalled(pkgName)) {
            @Suppress("DEPRECATION")
            val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, "package:$pkgName".toUri())
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            MangaExtensionLoader.uninstallPrivateExtension(context, pkgName)
            MangaExtensionInstallReceiver.notifyRemoved(context, pkgName)
        }
    }

    /**
     * Sets the step of the installation of an extension.
     *
     * @param downloadId The id of the download.
     * @param step New install step.
     */
    fun updateInstallStep(downloadId: Long, step: InstallStep) {
        downloadsStateFlows[downloadId]?.let { it.value = step }
    }

    /**
     * Deletes the download for the given package name.
     *
     * @param pkgName The package name of the download to delete.
     */
    private fun deleteDownload(pkgName: String) {
        val downloadId = activeDownloads.remove(pkgName)
        if (downloadId != null) {
            downloadManager.remove(downloadId)
            downloadsStateFlows.remove(downloadId)
            downloadIdToPkgName.remove(downloadId)
            handledDownloadIds.remove(downloadId)
        }
        if (activeDownloads.isEmpty()) {
            downloadReceiver.unregister()
        }
    }

    private fun requiresUnknownAppsPermission(installer: BasePreferences.ExtensionInstaller): Boolean {
        return installer.requiresSystemPermission &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
    }

    private fun requestUnknownAppsPermission(downloadId: Long) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to open unknown-app-sources settings." }
            context.startActivity(
                Intent(Settings.ACTION_SECURITY_SETTINGS).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        updateInstallStep(downloadId, InstallStep.Idle)
    }

    private fun handleDownloadCompletion(id: Long) {
        if (!handledDownloadIds.add(id)) return

        val query = DownloadManager.Query().setFilterById(id)
        downloadManager.query(query).use { cursor ->
            if (!cursor.moveToFirst()) {
                logcat(LogPriority.ERROR) { "Download $id not found in DownloadManager" }
                updateInstallStep(id, InstallStep.Error)
                return
            }

            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val uri = getDownloadedApkUri(id, cursor)
                    if (uri == null) {
                        updateInstallStep(id, InstallStep.Error)
                    } else {
                        installApk(id, uri)
                    }
                }
                DownloadManager.STATUS_FAILED -> {
                    val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    logcat(LogPriority.ERROR) { "Download failed for id=$id, reason=$reason" }
                    updateInstallStep(id, InstallStep.Error)
                }
                else -> {
                    handledDownloadIds.remove(id)
                    logcat(LogPriority.WARN) { "Ignoring non-terminal download status=$status for id=$id" }
                }
            }
        }
    }

    private fun getDownloadedApkUri(id: Long, cursor: android.database.Cursor): Uri? {
        val localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
        if (localUri.isNullOrBlank()) {
            return downloadManager.getUriForDownloadedFile(id).also {
                if (it == null) logcat(LogPriority.ERROR) { "Downloaded APK URI is unavailable for id=$id" }
            }
        }

        val parsedUri = localUri.toUri()
        return when (parsedUri.scheme) {
            "content" -> parsedUri
            "file" -> parsedUri.path?.let { File(it).getUriCompat(context) }
            null -> File(localUri).getUriCompat(context)
            else -> File(localUri.removePrefix(FILE_SCHEME)).getUriCompat(context)
        }
    }

    /**
     * Receiver that listens to download status events.
     */
    private inner class DownloadCompletionReceiver : BroadcastReceiver() {

        /**
         * Whether this receiver is currently registered.
         */
        private var isRegistered = false

        /**
         * Registers this receiver if it's not already.
         */
        fun register() {
            if (isRegistered) return
            isRegistered = true

            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            ContextCompat.registerReceiver(context, this, filter, ContextCompat.RECEIVER_EXPORTED)
        }

        /**
         * Unregisters this receiver if it's not already.
         */
        fun unregister() {
            if (!isRegistered) return
            isRegistered = false

            context.unregisterReceiver(this)
        }

        /**
         * Called when a download event is received. It looks for the download in the current active
         * downloads and notifies its installation step.
         */
        override fun onReceive(context: Context, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0) ?: return

            // Avoid events for downloads we didn't request
            if (id !in activeDownloads.values) return

            handleDownloadCompletion(id)
        }
    }

    companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
        const val EXTRA_DOWNLOAD_ID = "ExtensionInstaller.extra.DOWNLOAD_ID"
        const val EXTRA_PACKAGE_NAME = "ExtensionInstaller.extra.PACKAGE_NAME"
        const val FILE_SCHEME = "file://"
    }
}
