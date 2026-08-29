package eu.kanade.tachiyomi.extension.installer

import android.content.Context
import com.rosan.dhizuku.api.Dhizuku
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.installer.DhizukuShellRunner.SESSION_ID_REGEX
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
import tachiyomi.core.common.util.system.logcat

class DhizukuApkInstallBackendAdapter(
    private val context: Context,
) : ApkInstallBackendAdapter {

    override val backend = ApkInstallBackend.DHIZUKU

    override fun supports(kind: ApkExtensionKind): Boolean = kind == ApkExtensionKind.NOVEL_KOTLIN

    override fun install(request: ApkInstallRequest): Flow<InstallStep> = flow {
        emit(InstallStep.Installing)

        if (!Dhizuku.init(context)) {
            logcat(LogPriority.ERROR) { "Dhizuku is not ready for APK install" }
            emit(InstallStep.Error)
            return@flow
        }
        if (!awaitDhizukuPermission()) {
            logcat(LogPriority.ERROR) { "Dhizuku permission is required for APK install" }
            emit(InstallStep.Error)
            return@flow
        }

        var sessionId: String? = null
        try {
            val size = request.file?.length()?.takeIf { it > 0L }
                ?: context.getUriSize(request.uri)
                ?: throw IllegalStateException("Unable to determine APK size for ${request.packageName}")
            val inputStream = request.file?.inputStream()
                ?: context.contentResolver.openInputStream(request.uri)
                ?: throw IllegalStateException("Unable to open APK input stream for ${request.packageName}")
            inputStream.use {
                val createResult = DhizukuShellRunner.exec(
                    arrayOf("pm", "install-create", "-r", "-i", context.packageName, "-S", size.toString()),
                )
                sessionId = SESSION_ID_REGEX.find(createResult.combinedOutput)?.value
                    ?: throw RuntimeException(
                        "Failed to create Dhizuku install session: ${createResult.combinedOutput}",
                    )

                val writeResult = DhizukuShellRunner.exec(
                    arrayOf("pm", "install-write", "-S", size.toString(), sessionId, "base", "-"),
                    it,
                )
                if (writeResult.resultCode != 0) {
                    throw RuntimeException("Failed to write APK to session $sessionId: ${writeResult.combinedOutput}")
                }

                val commitResult = DhizukuShellRunner.exec(
                    arrayOf("pm", "install-commit", sessionId),
                )
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
                    "Signature mismatch for Dhizuku APK install ${request.packageName}; " +
                        "reinstall-with-uninstall required"
                }
            } else {
                logcat(LogPriority.ERROR, e) { "Failed Dhizuku APK install for ${request.packageName}" }
            }
            sessionId?.let { runCatching { DhizukuShellRunner.exec(arrayOf("pm", "install-abandon", it)) } }
            emit(InstallStep.Error)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun uninstall(request: ApkUninstallRequest): ApkInstallResult = withContext(Dispatchers.IO) {
        if (!Dhizuku.init(context)) return@withContext ApkInstallResult.Error("Dhizuku is not ready")
        if (!awaitDhizukuPermission()) {
            return@withContext ApkInstallResult.Error("Dhizuku permission is required")
        }
        if (!request.packageName.matches(Regex("""^[a-zA-Z_][a-zA-Z0-9_]*(\.[a-zA-Z_][a-zA-Z0-9_]*)+$"""))) {
            return@withContext ApkInstallResult.Error("Invalid package name: ${request.packageName}")
        }
        return@withContext runCatching {
            val result = DhizukuShellRunner.exec(arrayOf("pm", "uninstall", request.packageName))
            if (result.resultCode == 0) {
                ApkInstallResult.Installed
            } else {
                ApkInstallResult.Error(result.combinedOutput)
            }
        }.getOrElse { ApkInstallResult.Error(it.message ?: it::class.simpleName.orEmpty(), it) }
    }

    // cancel() is best-effort: the running exec() will time out after 120 s if the process hangs.
    override fun cancel(packageName: String) = Unit

    /**
     * Requests the Dhizuku permission and suspends until the user answers, so a first-time
     * install continues instead of failing instantly.
     */
    private suspend fun awaitDhizukuPermission(): Boolean {
        if (!Dhizuku.init(context)) return false
        if (Dhizuku.isPermissionGranted()) return true
        val result = CompletableDeferred<Int>()
        Dhizuku.requestPermission(
            object : com.rosan.dhizuku.api.DhizukuRequestPermissionListener() {
                override fun onRequestPermission(grantResult: Int) {
                    result.complete(grantResult)
                }
            },
        )
        try {
            withTimeout(PERMISSION_TIMEOUT_MS) { result.await() }
        } catch (e: TimeoutCancellationException) {
            logcat(LogPriority.WARN) { "Timed out waiting for the Dhizuku permission" }
            return false
        }
        // The callback only reports the dialog outcome; trust the authoritative check.
        return Dhizuku.isPermissionGranted()
    }

    private companion object {
        const val PERMISSION_TIMEOUT_MS = 60_000L
    }
}
