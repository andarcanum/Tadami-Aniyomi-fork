package eu.kanade.domain.easteregg.crystal

import com.tadami.aurora.BuildConfig

/** Armed for the 0.70-Crystal memorial release (versionCode 221). Add hotfix codes here if needed. */
internal object CrystalCampaignConfig {
    val targetReleaseCodes: Set<Int> = setOf(221)
    val isProductionRelease: Boolean get() = !BuildConfig.DEBUG && BuildConfig.BUILD_TYPE == "release"
}
