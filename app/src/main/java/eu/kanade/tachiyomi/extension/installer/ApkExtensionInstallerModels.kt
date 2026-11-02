package eu.kanade.tachiyomi.extension.installer

import android.net.Uri
import java.io.File

/**
 * Download backend selected for APK extension files.
 */
enum class ApkDownloadBackend {
    AUTO,
    DOWNLOAD_MANAGER,
    OKHTTP,
}

/**
 * Install backend selected for APK extension files.
 */
enum class ApkInstallBackend {
    PACKAGE_INSTALLER,
    LEGACY,
    SHIZUKU,
    DHIZUKU,
    PRIVATE,
}

/**
 * APK extension family. Novel JS plugins are intentionally not represented here.
 */
enum class ApkExtensionKind {
    MANGA,
    ANIME,
    NOVEL_KOTLIN,
    ;

    companion object {
        val NOVEL: ApkExtensionKind get() = NOVEL_KOTLIN
    }
}

data class ApkDownloadRequest(
    val id: String,
    val url: String,
    val packageName: String,
    val displayName: String,
    val kind: ApkExtensionKind,
    val preferredBackend: ApkDownloadBackend = ApkDownloadBackend.AUTO,
)

data class ApkDownloadResult(
    val id: String,
    val packageName: String,
    val file: File,
    val uri: Uri,
    val backend: ApkDownloadBackend,
)

data class ApkInstallRequest(
    val id: String,
    val packageName: String,
    val displayName: String,
    val uri: Uri,
    val file: File?,
    val backend: ApkInstallBackend,
    val kind: ApkExtensionKind,
)

data class ApkUninstallRequest(
    val packageName: String,
    val kind: ApkExtensionKind,
    /** The backend the extension was installed with; routes the uninstall to the same adapter. */
    val backend: ApkInstallBackend? = null,
)

sealed class ApkInstallResult {
    data object Installed : ApkInstallResult()
    data object Cancelled : ApkInstallResult()
    data class Error(val reason: String, val cause: Throwable? = null) : ApkInstallResult()
}
