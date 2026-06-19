package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Event Horizon.
 *
 * Aurora-exclusive "singularity UI" palette.
 * Inspired by the cinematic aesthetic of a black hole's event horizon:
 * Absolute OLED black background contrasted with blazing hot plasma amber/orange,
 * glowing accretion disk red-orange, and white-hot gold highlights.
 */
internal object EventHorizonColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFFFF6F00), // Blazing Neon Amber/Orange (Accretion disk primary)
        onPrimary = Color(0xFF4C1F00),
        primaryContainer = Color(0xFF7F3500),
        onPrimaryContainer = Color(0xFFFFD8C2),
        inversePrimary = Color(0xFFB34A00),

        secondary = Color(0xFFFF3D00), // Plasma Red-Orange (Hawking radiation / inner disk)
        onSecondary = Color(0xFF4C0F00),
        secondaryContainer = Color(0xFF801A00),
        onSecondaryContainer = Color(0xFFFFD5CC),

        tertiary = Color(0xFFFFEA00), // White-Hot Gold (Photon sphere / hottest layer)
        onTertiary = Color(0xFF4C4500),
        tertiaryContainer = Color(0xFF807500),
        onTertiaryContainer = Color(0xFFFFF7C2),

        background = Color(0xFF000000), // Absolute black (Singularity void)
        onBackground = Color(0xFFF7F1ED),
        surface = Color(0xFF090707), // Extremely dark charcoal (Warm cosmic dust base)
        onSurface = Color(0xFFF7F1ED),
        surfaceVariant = Color(0xFF1D1716),
        onSurfaceVariant = Color(0xFFDDC9C5),
        surfaceTint = Color(0xFFFF6F00),
        inverseSurface = Color(0xFFF7F1ED),
        inverseOnSurface = Color(0xFF140F0E),

        outline = Color(0xFFA68D89),
        outlineVariant = Color(0xFF4B3D3B),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        scrim = Color(0xFF000000),

        surfaceDim = Color(0xFF090707),
        surfaceBright = Color(0xFF282120),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF0E0B0B),
        surfaceContainer = Color(0xFF140F0F),
        surfaceContainerHigh = Color(0xFF1C1615),
        surfaceContainerHighest = Color(0xFF251E1C),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFFB34A00), // Deep Solar Orange
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFE5D6),
        onPrimaryContainer = Color(0xFF3B1400),
        inversePrimary = Color(0xFFFF8A50),

        secondary = Color(0xFFB32700), // Solar Flare Red-Orange
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFFDAD0),
        onSecondaryContainer = Color(0xFF3B0700),

        tertiary = Color(0xFF8C7300), // Solar Corona Gold-Olive
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFF2B3),
        onTertiaryContainer = Color(0xFF2B2200),

        background = Color(0xFFFFFBF7), // Warm Solar Ivory
        onBackground = Color(0xFF251A15),
        surface = Color(0xFFFFFBF7),
        onSurface = Color(0xFF251A15),
        surfaceVariant = Color(0xFFF7DFD5),
        onSurfaceVariant = Color(0xFF55433C),
        surfaceTint = Color(0xFFB34A00),
        inverseSurface = Color(0xFF3C2F29),
        inverseOnSurface = Color(0xFFFFF1EB),

        outline = Color(0xFF88736B),
        outlineVariant = Color(0xFFDDC2B8),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        scrim = Color(0xFF000000),

        surfaceDim = Color(0xFFE6DDD8),
        surfaceBright = Color(0xFFFFFBF7),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFFF5EF),
        surfaceContainer = Color(0xFFFFF0E7),
        surfaceContainerHigh = Color(0xFFFFEADF),
        surfaceContainerHighest = Color(0xFFFFE4D6),
    )
}
