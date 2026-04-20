package eu.kanade.domain.ui.model

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.aniyomi.AYMR

enum class EInkThemeMode(val titleRes: StringResource) {
    LIGHT(AYMR.strings.novel_reader_theme_light),
    DARK(AYMR.strings.novel_reader_theme_dark),
    SYSTEM(AYMR.strings.novel_reader_theme_system),
}
