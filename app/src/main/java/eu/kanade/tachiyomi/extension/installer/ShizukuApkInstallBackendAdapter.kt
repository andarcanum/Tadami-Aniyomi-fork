package eu.kanade.tachiyomi.extension.installer

import android.content.Context
import android.content.pm.PackageManager
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.util.system.getUriSize
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import logcat.LogPriority
import rikka.shizuku.Shizuku
import tachiyomi.core.common.util.system.logcat

class ShizukuApkInstallBackendAdapter(
    private val context: Context,
) : ApkInstallBackendAdapter {
    override val backend = ApkInstallBackend.SHIZUKU

    override fun supports(kind: ApkExtensionKind): Boolean = kind == ApkExtensionKind.NOVEL_KOTLIN

    override fun install(request: ApkInstallRequest): Flow<InstallStep> = flow {
        emit(InstallStep.Installing)
        if (!Shizuku.pingBinder()) {
            logcat(LogPriority.ERROR) { "Shizuku is not ready for APK install" }
            emit(InstallStep.Error)
            return@flow
        }
        if (!awaitShizukuPermission()) {
            logcat(LogPriority.ERROR) { "Shizuku permission is required for APK install" }
            emit(InstallStep.Error)
            return@flow
        }

        var sessionId: String? = null
        try {
            val size = request.file?.length()?.takeIf { it > 0L }
                ?: context.getUriSize(request.uri)
                ?: throw IllegalStateException("Unable to determine APK size")
            val inputStream = request.file?.inputStream()
                ?: context.contentResolver.openInputStream(request.uri)
                ?: throw IllegalStateException("Unable to open APK input stream")
            inputStream.use {
                val createResult = ShizukuShellRunner.exec(
                    arrayOf("pm", "install-create", "-r", "-i", context.packageName, "-S", size.toString()),
                )
                sessionId = ShizukuShellRunner.SESSION_ID_REGEX.find(createResult.combinedOutput)?.value
                    ?: throw RuntimeException(
                        "Failed to create Shizuku install session: ${createResult.combinedOutput}",
                    )

                val writeResult = ShizukuShellRunner.exec(
                    arrayOf("pm", "install-write", "-S", size.toString(), sessionId, "base", "-"),
                    it,
                )
                if (writeResult.resultCode != 0) {
                    throw RuntimeException("Failed to write APK to session $sessionId: ${writeResult.combinedOutput}")
                }

                val commitResult = ShizukuShellRunner.exec(arrayOf("pm", "install-commit", sessionId))
                if (commitResult.resultCode != 0) {
                    throw RuntimeException(
                        "Failed to commit install session $sessionId: ${commitResult.combinedOutput}",
                    )
                }
            }
            emit(InstallStep.Installed)
        } catch (e: Exception) {
            val failure = classifyInstallError(e.message)
            if (failure == ApkInstallFailure.SignatureMismatch) {
                logcat(LogPriority.WARN, e) {
                    "Signature mismatch for Shizuku APK install ${request.packageName}; " +
                        "reinstall-with-uninstall required"
                }
            } else {
                logcat(LogPriority.ERROR, e) { "Failed Shizuku APK install for ${request.packageName}" }
            }
            sessionId?.let { runCatching { ShizukuShellRunner.exec(arrayOf("pm", "install-abandon", it)) } }
            emit(InstallStep.Error)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun uninstall(request: ApkUninstallRequest): ApkInstallResult = withContext(Dispatchers.IO) {
        if (!Shizuku.pingBinder()) return@withContext ApkInstallResult.Error("Shizuku is not ready")
        if (!awaitShizukuPermission()) {
            return@withContext ApkInstallResult.Error("Shizuku permission is required")
        }
        val packageName = request.packageName
        require(ShizukuShellRunner.PACKAGE_NAME_REGEX.matches(packageName)) {
            "Invalid package name: $packageName"
        }
        return@withContext runCatching {
            val result = ShizukuShellRunner.exec(arrayOf("pm", "uninstall", packageName))
            if (result.resultCode == 0) ApkInstallResult.Installed else ApkInstallResult.Error(result.combinedOutput)
        }.getOrElse { ApkInstallResult.Error(it.message ?: it::class.simpleName.orEmpty(), it) }
    }

    override fun cancel(packageName: String) = Unit

    /**
     * Requests the Shizuku permission and suspends until the user answers, so a first-time
     * install continues instead of failing instantly. The result listener fires on a binder
     * thread; the deferred bridges it into this coroutine.
     */
    private suspend fun awaitShizukuPermission(): Boolean {
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) return true
        val result = CompletableDeferred<Int>()
        val listener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
                result.complete(grantResult)
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        try {
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
            withTimeout(PERMISSION_TIMEOUT_MS) { result.await() }
        } catch (e: TimeoutCancellationException) {
            logcat(LogPriority.WARN) { "Timed out waiting for the Shizuku permission" }
            return false
        } finally {
            Shizuku.removeRequestPermissionResultListener(listener)
        }
        return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val SHIZUKU_PERMISSION_REQUEST_CODE = 14045
        const val PERMISSION_TIMEOUT_MS = 60_000L
    }
}
