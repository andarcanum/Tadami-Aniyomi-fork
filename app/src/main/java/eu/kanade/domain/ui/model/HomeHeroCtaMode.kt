package eu.kanade.domain.ui.model

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.aniyomi.AYMR

enum class HomeHeroCtaMode(
    val key: String,
    val titleRes: StringResource,
) {
    Aurora(
        key = "aurora",
        titleRes = AYMR.strings.pref_home_hero_cta_mode_aurora,
    ),
    Classic(
        key = "classic",
        titleRes = AYMR.strings.pref_home_hero_cta_mode_classic,
    ),
    ;

    companion object {
        fun fromKey(key: String): HomeHeroCtaMode {
            return entries.firstOrNull { it.key == key } ?: Aurora
        }
    }
}
