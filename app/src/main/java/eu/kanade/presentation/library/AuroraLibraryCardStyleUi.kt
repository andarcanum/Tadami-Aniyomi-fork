package eu.kanade.presentation.library

import dev.icerock.moko.resources.StringResource
import tachiyomi.domain.library.model.AuroraLibraryCardStyle
import tachiyomi.i18n.MR

internal fun auroraLibraryCardStyleOptions(): List<Pair<StringResource, AuroraLibraryCardStyle>> {
    return listOf(
        MR.strings.pref_aurora_library_card_style_standard to AuroraLibraryCardStyle.Standard,
        MR.strings.pref_aurora_library_card_style_glow_contour to AuroraLibraryCardStyle.GlowContour,
    )
}
