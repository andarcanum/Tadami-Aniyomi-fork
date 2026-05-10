package eu.kanade.domain.ui.model

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.aniyomi.AYMR

enum class BottomNavAppearance(
    val titleRes: StringResource,
) {
    Classic(
        titleRes = AYMR.strings.pref_bottom_nav_classic,
    ),
    Aurora(
        titleRes = AYMR.strings.pref_bottom_nav_aurora,
    ),
}
