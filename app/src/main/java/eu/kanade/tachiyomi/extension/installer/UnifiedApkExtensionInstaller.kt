package eu.kanade.tachiyomi.extension.installer

import eu.kanade.tachiyomi.extension.InstallStep
import kotlinx.coroutines.flow.Flow

/**
 * Shared contract for APK extension installers.
 *
 * The primary tracking key is [ApkInstallRequest.packageName]. Legacy Manga/Anime wrappers may
 * keep their DownloadManager Long ids internally, but those ids must not become the public key of
 * this unified API.
 */
interface UnifiedApkExtensionInstaller {
    fun install(request: ApkInstallRequest): Flow<InstallStep>

    /** Observes the install step tracked in the shared state store for [packageName]. */
    fun observe(packageName: String): Flow<InstallStep>

    fun cancel(packageName: String)

    suspend fun uninstall(request: ApkUninstallRequest): ApkInstallResult
}
