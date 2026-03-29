package eu.kanade.tachiyomi.data.updater

import android.content.Context
import tachiyomi.domain.release.interactor.AppUpdateVersionComparator
import tachiyomi.domain.release.service.AppUpdatePreferences
import java.io.File
import java.nio.file.Files

class AppUpdateFileManager(
    private val context: Context,
    private val preferences: AppUpdatePreferences,
) {

    fun apkFile(): File {
        return File(context.externalCacheDir ?: context.cacheDir, APK_FILE_NAME)
    }

    fun recordDownloadedVersion(version: String) {
        preferences.downloadedAppUpdateVersion().set(version)
    }

    fun cleanupIfInstalledVersionReached(
        isPreview: Boolean,
        installedCommitCount: Int,
        installedVersionName: String,
    ) {
        val downloadedVersion = preferences.downloadedAppUpdateVersion().get()
        if (downloadedVersion.isBlank()) {
            return
        }

        if (
            AppUpdateVersionComparator.hasInstalledOrNewer(
                isPreview = isPreview,
                installedCommitCount = installedCommitCount,
                installedVersionName = installedVersionName,
                targetVersionTag = downloadedVersion,
            )
        ) {
            Files.deleteIfExists(apkFile().toPath())
            preferences.downloadedAppUpdateVersion().set("")
        }
    }

    companion object {
        const val APK_FILE_NAME = "update.apk"
    }
}
