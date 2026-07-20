package eu.kanade.domain.ui.model

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR

enum class AppTheme(
    val titleRes: StringResource?,
    val isAuroraStyle: Boolean = true,
    val isHidden: Boolean = false,
) {
    DEFAULT(MR.strings.label_default),
    MONET(MR.strings.theme_monet),
    CLOUDFLARE(AYMR.strings.theme_cloudflare),
    COTTONCANDY(AYMR.strings.theme_cottoncandy),
    DOOM(AYMR.strings.theme_doom),
    GREEN_APPLE(MR.strings.theme_greenapple),
    LAVENDER(MR.strings.theme_lavender),
    MATRIX(AYMR.strings.theme_matrix),
    MIDNIGHT_DUSK(MR.strings.theme_midnightdusk),
    MOCHA(AYMR.strings.theme_mocha),
    SAPPHIRE(AYMR.strings.theme_sapphire),
    NORD(MR.strings.theme_nord),
    STRAWBERRY_DAIQUIRI(MR.strings.theme_strawberrydaiquiri),
    TAKO(MR.strings.theme_tako),
    TEALTURQUOISE(MR.strings.theme_tealturquoise),
    TIDAL_WAVE(MR.strings.theme_tidalwave),
    YINYANG(MR.strings.theme_yinyang),
    YOTSUBA(MR.strings.theme_yotsuba),
    MONOCHROME(MR.strings.theme_monochrome),
    AURORA(AYMR.strings.theme_aurora),
    ONYX_GOLD(AYMR.strings.theme_onyx_gold, isHidden = true),
    SAKURA_NOIR(AYMR.strings.theme_sakura_noir, isHidden = true),
    NEBULA_TIDE(AYMR.strings.theme_nebula_tide, isHidden = true),
    EVENT_HORIZON(AYMR.strings.theme_event_horizon, isHidden = true),
    VOID_RED(AYMR.strings.theme_void_red, isHidden = true),
    AURORA_PRIME(AYMR.strings.theme_aurora_prime, isHidden = true),

    // Deprecated
    DARK_BLUE(null),
    HOT_PINK(null),
    BLUE(null),
}
