package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Colors for Onyx Gold theme.
 *
 * A trophy-tier palette: black diamond surfaces, antique-gold hierarchy,
 * warm ivory text, and a restrained ruby accent so it feels earned instead
 * of just “dark + yellow”.
 */
internal object OnyxGoldColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFFFFD36E),
        onPrimary = Color(0xFF151006),
        primaryContainer = Color(0xFF5A4212),
        onPrimaryContainer = Color(0xFFFFE8B3),
        inversePrimary = Color(0xFF8E6500),

        secondary = Color(0xFFC49A3A),
        onSecondary = Color(0xFF211700),
        secondaryContainer = Color(0xFF3C2D0C),
        onSecondaryContainer = Color(0xFFFFE6A8),

        tertiary = Color(0xFFFFE0A3),
        onTertiary = Color(0xFF362100),
        tertiaryContainer = Color(0xFF523D14),
        onTertiaryContainer = Color(0xFFFFE8C8),

        background = Color(0xFF060607),
        onBackground = Color(0xFFF4E9D0),
        surface = Color(0xFF0B0A0C),
        onSurface = Color(0xFFF4E9D0),
        surfaceVariant = Color(0xFF1E1A13),
        onSurfaceVariant = Color(0xFFD9C9A6),
        surfaceTint = Color(0xFFFFD36E),
        inverseSurface = Color(0xFFEDE3CC),
        inverseOnSurface = Color(0xFF211A10),

        outline = Color(0xFFA88743),
        outlineVariant = Color(0xFF4B3A17),

        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),

        scrim = Color(0xFF000000),

        surfaceDim = Color(0xFF060607),
        surfaceBright = Color(0xFF2A251A),
        surfaceContainerLowest = Color(0xFF020203),
        surfaceContainerLow = Color(0xFF0E0D0F),
        surfaceContainer = Color(0xFF141210),
        surfaceContainerHigh = Color(0xFF1B1711),
        surfaceContainerHighest = Color(0xFF241F16),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF7A5600),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFD978),
        onPrimaryContainer = Color(0xFF271900),
        inversePrimary = Color(0xFFFFD36E),

        secondary = Color(0xFF7C5824),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFF7DEA0),
        onSecondaryContainer = Color(0xFF241A03),

        tertiary = Color(0xFF7A6533),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFE8C8),
        onTertiaryContainer = Color(0xFF261900),

        background = Color(0xFFFFFBF3),
        onBackground = Color(0xFF211A10),
        surface = Color(0xFFFFFBF3),
        onSurface = Color(0xFF211A10),
        surfaceVariant = Color(0xFFF1E2C4),
        onSurfaceVariant = Color(0xFF50452F),
        surfaceTint = Color(0xFF7A5600),
        inverseSurface = Color(0xFF382F20),
        inverseOnSurface = Color(0xFFFFF4DB),

        outline = Color(0xFF82734F),
        outlineVariant = Color(0xFFD6C6A6),

        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),

        scrim = Color(0xFF000000),

        surfaceDim = Color(0xFFE7DCC7),
        surfaceBright = Color(0xFFFFFBF3),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFCF2DE),
        surfaceContainer = Color(0xFFF6E9CF),
        surfaceContainerHigh = Color(0xFFEFE0C1),
        surfaceContainerHighest = Color(0xFFE7D6B4),
    )
}
