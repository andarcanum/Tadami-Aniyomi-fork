package eu.kanade.tachiyomi.extension.anime.installer

import android.app.Service
import android.content.pm.PackageManager
import eu.kanade.tachiyomi.extension.InstallStep
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
import java.io.BufferedReader
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class ShizukuInstallerAnime(private val service: Service) : InstallerAnime(service) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val shizukuDeadListener = Shizuku.OnBinderDeadListener {
        logcat(LogPriority.ERROR) { "Shizuku was killed prematurely" }
        service.stopSelf()
    }

    private val shizukuPermissionListener = object : Shizuku.OnRequestPermissionResultListener {
        override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
            if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    ready = true
                    checkQueue()
                } else {
                    service.stopSelf()
                }
                Shizuku.removeRequestPermissionResultListener(this)
            }
        }
    }

    override var ready = false

    override fun processEntry(entry: Entry) {
        super.processEntry(entry)
        scope.launch {
            var sessionId: String? = null
            try {
                val size = service.getUriSize(entry.uri) ?: throw IllegalStateException()
                val inputStream =
                    service.contentResolver.openInputStream(entry.uri)
                        ?: throw IllegalStateException("Unable to open APK input stream")
                inputStream.use {
                    val createCommand = "pm install-create -r -i ${service.packageName} -S $size"
                    val createResult = exec(createCommand)
                    sessionId = SESSION_ID_REGEX.find(createResult.combinedOutput)?.value
                        ?: throw RuntimeException("Failed to create install session")

                    val writeResult = exec("pm install-write -S $size $sessionId base -", it)
                    if (writeResult.resultCode != 0) {
                        throw RuntimeException(
                            "Failed to write APK to session $sessionId: ${writeResult.combinedOutput}",
                        )
                    }

                    val commitResult = exec("pm install-commit $sessionId")
                    if (commitResult.resultCode != 0) {
                        throw RuntimeException(
                            "Failed to commit install session $sessionId: ${commitResult.combinedOutput}",
                        )
                    }

                    continueQueue(InstallStep.Installed)
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to install extension ${entry.downloadId} ${entry.uri}" }
                if (sessionId != null) {
                    exec("pm install-abandon $sessionId")
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

    private fun exec(command: String, stdin: InputStream? = null): ShellResult {
        @Suppress("DEPRECATION")
        val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
        var stdout = ""
        var stderr = ""
        val stdoutThread = thread(start = true) {
            stdout = process.inputStream.bufferedReader().use(BufferedReader::readText)
        }
        val stderrThread = thread(start = true) {
            stderr = process.errorStream.bufferedReader().use(BufferedReader::readText)
        }
        if (stdin != null) {
            process.outputStream.use { stdin.copyTo(it) }
        } else {
            process.outputStream.close()
        }
        val finished = process.waitFor(SHELL_COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            stdoutThread.join(1_000)
            stderrThread.join(1_000)
            return ShellResult(-1, stdout, stderr.ifBlank { "Command timed out: $command" })
        }
        stdoutThread.join()
        stderrThread.join()
        return ShellResult(process.exitValue(), stdout, stderr)
    }

    private data class ShellResult(val resultCode: Int, val out: String, val err: String) {
        val combinedOutput: String get() = listOf(out, err).filter { it.isNotBlank() }.joinToString("\n")
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
}

private const val SHIZUKU_PERMISSION_REQUEST_CODE = 14045
private const val SHELL_COMMAND_TIMEOUT_SECONDS = 120L
private val SESSION_ID_REGEX = Regex("(?<=\\[).+?(?=])")
