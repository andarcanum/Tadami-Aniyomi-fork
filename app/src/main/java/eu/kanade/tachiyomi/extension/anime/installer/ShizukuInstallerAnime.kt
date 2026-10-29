package eu.kanade.tachiyomi.extension.anime.installer

import android.app.Service
import android.content.pm.PackageManager
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.installer.ApkInstallFailure
import eu.kanade.tachiyomi.extension.installer.ShizukuShellRunner
import eu.kanade.tachiyomi.extension.installer.classifyInstallError
import eu.kanade.tachiyomi.util.system.getUriSize
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import logcat.LogPriority
import rikka.shizuku.Shizuku
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR

class ShizukuInstallerAnime(private val service: Service) : InstallerAnime(service) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val shizukuDeadListener = ShizukuDeadListener(this)

    private val shizukuPermissionListener = ShizukuPermissionListener(this)

    override var ready = false

    override fun processEntry(entry: Entry) {
        super.processEntry(entry)
        scope.launch {
            var sessionId: String? = null
            try {
                val size = service.getUriSize(entry.uri)
                    ?: throw IllegalStateException("Unable to determine APK size for ${entry.uri}")
                val inputStream =
                    service.contentResolver.openInputStream(entry.uri)
                        ?: throw IllegalStateException("Unable to open APK input stream")
                inputStream.use {
                    val createResult = ShizukuShellRunner.exec(
                        arrayOf("pm", "install-create", "-r", "-i", service.packageName, "-S", size.toString()),
                    )
                    sessionId = ShizukuShellRunner.SESSION_ID_REGEX.find(createResult.combinedOutput)?.value
                        ?: throw RuntimeException("Failed to create install session: ${createResult.combinedOutput}")

                    val writeResult = ShizukuShellRunner.exec(
                        arrayOf("pm", "install-write", "-S", size.toString(), sessionId, "base", "-"),
                        it,
                    )
                    if (writeResult.resultCode != 0) {
                        throw RuntimeException(
                            "Failed to write APK to session $sessionId: ${writeResult.combinedOutput}",
                        )
                    }

                    val commitResult = ShizukuShellRunner.exec(
                        arrayOf("pm", "install-commit", sessionId),
                    )
                    if (commitResult.resultCode != 0) {
                        throw RuntimeException(
                            "Failed to commit install session $sessionId: ${commitResult.combinedOutput}",
                        )
                    }

                    continueQueue(InstallStep.Installed)
                }
            } catch (e: Exception) {
                if (classifyInstallError(e.message) == ApkInstallFailure.SignatureMismatch) {
                    logcat(LogPriority.WARN, e) {
                        "Signature mismatch for extension ${entry.pkgName}; reinstall-with-uninstall required"
                    }
                    entry.pkgName?.let { extensionManager.reportSignatureMismatch(it) }
                } else {
                    logcat(LogPriority.ERROR, e) { "Failed to install extension ${entry.downloadId} ${entry.uri}" }
                }
                sessionId?.let {
                    ShizukuShellRunner.exec(arrayOf("pm", "install-abandon", it))
                }
                continueQueue(InstallStep.Error)
            }
        }
    }

    // Don't cancel if entry is already started installing
    override fun cancelEntry(entry: Entry): Boolean = getActiveEntry() != entry

    override fun onDestroy() {
        Shizuku.removeBinderDeadListener(shizukuDeadListener)
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        scope.cancel()
        super.onDestroy()
    }

    init {
        Shizuku.addBinderDeadListener(shizukuDeadListener)
        ready = if (Shizuku.pingBinder()) {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                true
            } else {
                Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
                false
            }
        } else {
            logcat(LogPriority.ERROR) { "Shizuku is not ready to use" }
            service.toast(MR.strings.ext_installer_shizuku_stopped)
            service.stopSelf()
            false
        }
    }

    private class ShizukuDeadListener(
        installer: ShizukuInstallerAnime,
    ) : Shizuku.OnBinderDeadListener {
        private val installerRef = java.lang.ref.WeakReference(installer)

        override fun onBinderDead() {
            logcat(LogPriority.ERROR) { "Shizuku was killed prematurely" }
            val installer = installerRef.get() ?: return
            installer.service.stopSelf()
        }
    }

    private class ShizukuPermissionListener(
        installer: ShizukuInstallerAnime,
    ) : Shizuku.OnRequestPermissionResultListener {
        private val installerRef = java.lang.ref.WeakReference(installer)

        override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
            val installer = installerRef.get() ?: return
            if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    installer.ready = true
                    installer.checkQueue()
                } else {
                    installer.service.stopSelf()
                }
                Shizuku.removeRequestPermissionResultListener(this)
            }
        }
    }
}

private const val SHIZUKU_PERMISSION_REQUEST_CODE = 14045
