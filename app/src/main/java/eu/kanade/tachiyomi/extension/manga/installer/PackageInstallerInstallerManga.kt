package eu.kanade.tachiyomi.extension.manga.installer

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.installer.ApkExtensionKind
import eu.kanade.tachiyomi.extension.installer.ApkInstallBackend
import eu.kanade.tachiyomi.extension.installer.ApkInstallFailure
import eu.kanade.tachiyomi.extension.installer.ApkInstallFallbackNotifier
import eu.kanade.tachiyomi.extension.installer.ApkInstallFallbackSuggestion
import eu.kanade.tachiyomi.extension.installer.classifyInstallError
import eu.kanade.tachiyomi.util.lang.use
import eu.kanade.tachiyomi.util.system.getParcelableExtraCompat
import eu.kanade.tachiyomi.util.system.getUriSize
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

class PackageInstallerInstallerManga(private val service: Service) : InstallerManga(service) {

    private val packageInstaller = service.packageManager.packageInstaller
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val fallbackNotifier = ApkInstallFallbackNotifier(service)

    private val packageActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
            if (sessionId != activeSession?.second) {
                logcat(LogPriority.WARN) { "Ignoring PackageInstaller callback for unexpected session=$sessionId" }
                return
            }

            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            val statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
            val legacyStatus = intent.getIntExtra(EXTRA_LEGACY_STATUS, 0)
            when (status) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val userAction = intent.getParcelableExtraCompat<Intent>(Intent.EXTRA_INTENT)
                    if (userAction == null) {
                        logcat(LogPriority.ERROR) { "PackageInstaller requested user action without intent: $intent" }
                        completeSession(InstallStep.Error)
                        return
                    }
                    userAction.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    service.startActivity(userAction)
                }
                PackageInstaller.STATUS_FAILURE_ABORTED -> {
                    logcat(LogPriority.INFO) { "Package install aborted: $statusMessage legacy=$legacyStatus" }
                    completeSession(InstallStep.Idle)
                }
                PackageInstaller.STATUS_SUCCESS -> completeSession(InstallStep.Installed)
                else -> {
                    logcat(LogPriority.ERROR) {
                        "Package install failed: status=$status legacy=$legacyStatus message=$statusMessage"
                    }
                    val failure = classifyInstallError(statusMessage)
                    if (failure == ApkInstallFailure.SignatureMismatch) {
                        // The APK is signed with a different key — a fallback backend cannot fix
                        // this; the caller will offer reinstall-with-uninstall instead.
                        logcat(LogPriority.WARN) {
                            "Signature mismatch for extension install; reinstall-with-uninstall required"
                        }
                        activeSession?.first?.pkgName?.let { extensionManager.reportSignatureMismatch(it) }
                    } else {
                        activeSession?.first?.let { entry ->
                            notifyFallbackSuggestion(
                                entry,
                                "status=$status legacy=$legacyStatus message=$statusMessage",
                            )
                        }
                    }
                    completeSession(InstallStep.Error)
                }
            }
        }
    }

    private var activeSession: Pair<Entry, Int>? = null
    private var timeoutRunnable: Runnable? = null

    // Always ready
    override var ready = true

    @SuppressLint("RequestInstallPackagesPolicy")
    override fun processEntry(entry: Entry) {
        super.processEntry(entry)
        activeSession = null
        try {
            val installParams = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                installParams.setRequireUserAction(
                    PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED,
                )
            }
            val fileSize = service.getUriSize(entry.uri) ?: throw IllegalStateException()
            installParams.setSize(fileSize)
            activeSession = entry to packageInstaller.createSession(installParams)
            scheduleSessionTimeout(entry, activeSession!!.second)

            val inputStream = service.contentResolver.openInputStream(entry.uri) ?: throw IllegalStateException()
            val session = packageInstaller.openSession(activeSession!!.second)
            val outputStream = session.openWrite(entry.downloadId.toString(), 0, fileSize)
            session.use {
                arrayOf(inputStream, outputStream).use {
                    inputStream.copyTo(outputStream)
                    session.fsync(outputStream)
                }

                val intentSender = PendingIntent.getBroadcast(
                    service,
                    activeSession!!.second,
                    Intent(INSTALL_ACTION).setPackage(service.packageName),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0,
                ).intentSender
                session.commit(intentSender)
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to install extension ${entry.downloadId} ${entry.uri}" }
            notifyFallbackSuggestion(entry, e.message ?: e::class.simpleName.orEmpty())
            activeSession?.let { (_, sessionId) ->
                packageInstaller.abandonSession(sessionId)
            }
            completeSession(InstallStep.Error)
        }
    }

    override fun cancelEntry(entry: Entry): Boolean {
        activeSession?.let { (activeEntry, sessionId) ->
            if (activeEntry == entry) {
                clearSessionTimeout()
                activeSession = null
                // Abandoning a committed session may throw, and the failure callback is dropped
                // anyway (activeSession is already null). Return true so the caller unsticks the
                // queue immediately instead of waiting for a callback that never arrives.
                runCatching { packageInstaller.abandonSession(sessionId) }
                    .onFailure { e -> logcat(LogPriority.ERROR, e) { "Failed to abandon install session $sessionId" } }
                return true
            }
        }
        return true
    }

    override fun onDestroy() {
        clearSessionTimeout()
        service.unregisterReceiver(packageActionReceiver)
        super.onDestroy()
    }

    private fun scheduleSessionTimeout(entry: Entry, sessionId: Int) {
        clearSessionTimeout()
        timeoutRunnable = Runnable {
            if (activeSession?.second != sessionId) return@Runnable
            logcat(LogPriority.ERROR) {
                "PackageInstaller callback timed out for " +
                    "downloadId=${entry.downloadId} session=$sessionId uri=${entry.uri}. " +
                    "User can retry with Legacy, Private, or Shizuku on MIUI/HyperOS."
            }
            runCatching { packageInstaller.abandonSession(sessionId) }
            notifyFallbackSuggestion(entry, "PackageInstaller timeout")
            completeSession(InstallStep.Error)
        }.also { timeoutHandler.postDelayed(it, PACKAGE_INSTALLER_TIMEOUT_MS) }
    }

    private fun clearSessionTimeout() {
        timeoutRunnable?.let(timeoutHandler::removeCallbacks)
        timeoutRunnable = null
    }

    private fun completeSession(step: InstallStep) {
        clearSessionTimeout()
        activeSession = null
        continueQueue(step)
    }

    private fun notifyFallbackSuggestion(entry: Entry, reason: String) {
        fallbackNotifier.show(
            ApkInstallFallbackSuggestion(
                packageName = entry.pkgName ?: "download-${entry.downloadId}",
                displayName = entry.pkgName ?: "download-${entry.downloadId}",
                kind = ApkExtensionKind.MANGA,
                failedBackend = ApkInstallBackend.PACKAGE_INSTALLER,
                reason = reason,
                suggestedBackends = listOf(
                    ApkInstallBackend.PRIVATE,
                    ApkInstallBackend.SHIZUKU,
                    ApkInstallBackend.LEGACY,
                ),
            ),
        )
    }

    init {
        ContextCompat.registerReceiver(
            service,
            packageActionReceiver,
            IntentFilter(INSTALL_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }
}

private const val INSTALL_ACTION = "eu.kanade.tachiyomi.extension.manga.PackageInstallerInstaller.INSTALL_ACTION"
private const val EXTRA_LEGACY_STATUS = "android.content.pm.extra.LEGACY_STATUS"
private const val PACKAGE_INSTALLER_TIMEOUT_MS = 5 * 60 * 1000L
