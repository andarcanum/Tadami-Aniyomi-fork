package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Nebula Tide.
 *
 * Endgame cosmic palette: abyssal blue surfaces, plasma cyan actions,
 * violet gravity wells, and alien-lime highlights. Designed to be clearly
 * separate from Tidal Wave and Sapphire.
 */
internal object NebulaTideColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFF46F4FF),
        onPrimary = Color(0xFF001F26),
        primaryContainer = Color(0xFF005866),
        onPrimaryContainer = Color(0xFFB8F8FF),
        inversePrimary = Color(0xFF007586),

        secondary = Color(0xFFB39BFF),
        onSecondary = Color(0xFF21005D),
        secondaryContainer = Color(0xFF49328F),
        onSecondaryContainer = Color(0xFFE9DDFF),

        tertiary = Color(0xFFA7FF6E),
        onTertiary = Color(0xFF113800),
        tertiaryContainer = Color(0xFF2D5B12),
        onTertiaryContainer = Color(0xFFD8FFC1),

        background = Color(0xFF030812),
        onBackground = Color(0xFFEAF2FF),
        surface = Color(0xFF07101D),
        onSurface = Color(0xFFEAF2FF),
        surfaceVariant = Color(0xFF16233A),
        onSurfaceVariant = Color(0xFFC9D6EA),
        surfaceTint = Color(0xFF46F4FF),
        inverseSurface = Color(0xFFEAF2FF),
        inverseOnSurface = Color(0xFF102033),

        outline = Color(0xFF7B8DA8),
        outlineVariant = Color(0xFF314158),

        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),

        scrim = Color(0xFF000000),

        surfaceDim = Color(0xFF030812),
        surfaceBright = Color(0xFF243651),
        surfaceContainerLowest = Color(0xFF01040A),
        surfaceContainerLow = Color(0xFF07111F),
        surfaceContainer = Color(0xFF0D1828),
        surfaceContainerHigh = Color(0xFF14233A),
        surfaceContainerHighest = Color(0xFF1E2F4A),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF006B78),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFB8F8FF),
        onPrimaryContainer = Color(0xFF001F26),
        inversePrimary = Color(0xFF46F4FF),

        secondary = Color(0xFF6250B5),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE9DDFF),
        onSecondaryContainer = Color(0xFF1D0061),

        tertiary = Color(0xFF3D6F19),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFD8FFC1),
        onTertiaryContainer = Color(0xFF0B2100),

        background = Color(0xFFF6FAFF),
        onBackground = Color(0xFF182334),
        surface = Color(0xFFF6FAFF),
        onSurface = Color(0xFF182334),
        surfaceVariant = Color(0xFFE1EAF8),
        onSurfaceVariant = Color(0xFF44546B),
        surfaceTint = Color(0xFF006B78),
        inverseSurface = Color(0xFF2D3142),
        inverseOnSurface = Color(0xFFF4F0FF),

        outline = Color(0xFF73839B),
        outlineVariant = Color(0xFFC7D4E5),

        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),

        scrim = Color(0xFF000000),

        surfaceDim = Color(0xFFDDE5F0),
        surfaceBright = Color(0xFFF6FAFF),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF0F6FF),
        surfaceContainer = Color(0xFFE8F0FA),
        surfaceContainerHigh = Color(0xFFE0E8F4),
        surfaceContainerHighest = Color(0xFFD5DEEC),
    )
}
