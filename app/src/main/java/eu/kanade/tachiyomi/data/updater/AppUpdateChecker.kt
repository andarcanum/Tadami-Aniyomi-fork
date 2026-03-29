package eu.kanade.tachiyomi.data.updater

import android.content.Context
import com.tadami.aurora.BuildConfig
import eu.kanade.tachiyomi.util.system.isPreviewBuildType
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.release.interactor.GetApplicationRelease
import tachiyomi.domain.release.service.AppUpdatePreferences
import uy.kohesive.injekt.injectLazy

class AppUpdateChecker {

    private val getApplicationRelease: GetApplicationRelease by injectLazy()
    private val appUpdatePreferences: AppUpdatePreferences by injectLazy()

    suspend fun checkForUpdate(context: Context, forceCheck: Boolean = false): GetApplicationRelease.Result {
        // Disabling app update checks for older Android versions that we're going to drop support for
        // if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        //    return GetApplicationRelease.Result.OsTooOld
        // }

        return withIOContext {
            val result = getApplicationRelease.await(
                GetApplicationRelease.Arguments(
                    isPreviewBuildType,
                    BuildConfig.COMMIT_COUNT.toInt(),
                    BuildConfig.VERSION_NAME,
                    GITHUB_REPO,
                    forceCheck,
                ),
            )

            when (result) {
                is GetApplicationRelease.Result.NewUpdate -> {
                    val ignoredVersionPreference = appUpdatePreferences.ignoredAppUpdateVersion()
                    val decision = resolveAppUpdatePrompt(
                        availableVersion = result.release.version,
                        ignoredVersion = ignoredVersionPreference.get(),
                    )

                    if (decision.nextIgnoredVersion != ignoredVersionPreference.get()) {
                        ignoredVersionPreference.set(decision.nextIgnoredVersion)
                    }

                    if (decision.shouldPrompt) {
                        AppUpdateNotifier(context).promptUpdate(result.release)
                    }
                }
                else -> {}
            }

            result
        }
    }
}

data class AppUpdatePromptDecision(
    val shouldPrompt: Boolean,
    val nextIgnoredVersion: String,
)

internal fun resolveAppUpdatePrompt(
    availableVersion: String,
    ignoredVersion: String,
): AppUpdatePromptDecision {
    return when {
        ignoredVersion.isBlank() -> AppUpdatePromptDecision(
            shouldPrompt = true,
            nextIgnoredVersion = "",
        )
        ignoredVersion == availableVersion -> AppUpdatePromptDecision(
            shouldPrompt = false,
            nextIgnoredVersion = ignoredVersion,
        )
        else -> AppUpdatePromptDecision(
            shouldPrompt = true,
            nextIgnoredVersion = "",
        )
    }
}

val GITHUB_REPO = "andarcanum/Tadami-Aniyomi-fork"

val RELEASE_TAG: String by lazy {
    if (isPreviewBuildType) {
        "r${BuildConfig.COMMIT_COUNT}"
    } else {
        "v${BuildConfig.VERSION_NAME}"
    }
}

val RELEASE_URL = "https://github.com/$GITHUB_REPO/releases"
