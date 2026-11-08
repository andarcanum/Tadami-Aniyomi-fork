package eu.kanade.domain.easteregg.crystal

import com.tadami.aurora.BuildConfig

/** Intentionally unarmed until the owner chooses the release version codes, including any hotfixes. */
internal object CrystalCampaignConfig {
    val targetReleaseCodes: Set<Int> = emptySet()
    val isProductionRelease: Boolean get() = !BuildConfig.DEBUG && BuildConfig.BUILD_TYPE == "release"
}
