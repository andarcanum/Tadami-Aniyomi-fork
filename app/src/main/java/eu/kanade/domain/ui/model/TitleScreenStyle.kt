package eu.kanade.domain.ui.model

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.aniyomi.AYMR

enum class TitleScreenStyle(
    val titleRes: StringResource,
) {
    POSTER_IMMERSIVE(
        titleRes = AYMR.strings.pref_title_screen_style_poster_immersive,
    ),
    GLASS_STACK(
        titleRes = AYMR.strings.pref_title_screen_style_glass_stack,
    ),
}
