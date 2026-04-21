package eu.kanade.domain.ui.model

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR

enum class EInkProfile(val titleRes: StringResource) {
    OFF(MR.strings.off),
    MONOCHROME(AYMR.strings.pref_e_ink_profile_monochrome),
    COLOR(AYMR.strings.pref_e_ink_profile_color),
    ;

    val isEnabled: Boolean
        get() = this != OFF
}
