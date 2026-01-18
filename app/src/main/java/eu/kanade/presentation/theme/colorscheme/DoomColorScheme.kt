package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Colors for Doom theme
 * A dark red theme inspired by the classic game aesthetic
 *
 * Key colors:
 * Primary: Red (#FF0000)
 * Secondary: Dark Red (#6C0303)
 * Background: Near Black (#1B1B1B)
 */
internal object DoomColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        // Primary colors
        primary = Color(0xFFFF6B6B),                    // Muted red for readability
        onPrimary = Color(0xFF1B1B1B),
        primaryContainer = Color(0xFFB71C1C),
        onPrimaryContainer = Color(0xFFFFFFFF),
        inversePrimary = Color(0xFF6D0D0B),

        // Secondary colors
        secondary = Color(0xFFFF0000),                  // Unread badge
        onSecondary = Color(0xFFFFFFFF),                // Unread badge text
        secondaryContainer = Color(0xFF6C0303),         // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFFFFFFFF),       // Navigation bar selector icon

        // Tertiary colors
        tertiary = Color(0xFFBFBFBF),                   // Downloaded badge
        onTertiary = Color(0xFF1B1B1B),                 // Downloaded badge text
        tertiaryContainer = Color(0xFF424242),
        onTertiaryContainer = Color(0xFFFFFFFF),

        // Background & Surface
        background = Color(0xFF1B1B1B),
        onBackground = Color(0xFFFFFFFF),
        surface = Color(0xFF1B1B1B),
        onSurface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFF2D2D2D),             // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFFD65A5A),
        surfaceTint = Color(0xFFFF0000),
        inverseSurface = Color(0xFFFAFAFA),
        inverseOnSurface = Color(0xFF313131),

        // Outline
        outline = Color(0xFFFF0000),
        outlineVariant = Color(0xFF8B0000),

        // Error
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),

        // Scrim
        scrim = Color(0xFF000000),

        // Surface containers
        surfaceDim = Color(0xFF141414),
        surfaceBright = Color(0xFF3D3D3D),
        surfaceContainerLowest = Color(0xFF0F0F0F),
        surfaceContainerLow = Color(0xFF1B1B1B),
        surfaceContainer = Color(0xFF1F1F1F),           // Navigation bar background
        surfaceContainerHigh = Color(0xFF2A2A2A),
        surfaceContainerHighest = Color(0xFF353535),
    )

    override val lightScheme = lightColorScheme(
        // Primary colors
        primary = Color(0xFFB71C1C),                    // Dark red for light theme
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD32F2F),
        onPrimaryContainer = Color(0xFFFFFFFF),
        inversePrimary = Color(0xFFFF6B6B),

        // Secondary colors
        secondary = Color(0xFFB71C1C),                  // Unread badge
        onSecondary = Color(0xFFFFFFFF),                // Unread badge text
        secondaryContainer = Color(0xFFFFCDD2),         // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFF1B1B1B),       // Navigation bar selector icon

        // Tertiary colors
        tertiary = Color(0xFF757575),                   // Downloaded badge
        onTertiary = Color(0xFFFFFFFF),                 // Downloaded badge text
        tertiaryContainer = Color(0xFFBDBDBD),
        onTertiaryContainer = Color(0xFF1B1B1B),

        // Background & Surface
        background = Color(0xFFFAFAFA),
        onBackground = Color(0xFF1B1B1B),
        surface = Color(0xFFFAFAFA),
        onSurface = Color(0xFF1B1B1B),
        surfaceVariant = Color(0xFFFFEBEE),             // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFF5D4040),
        surfaceTint = Color(0xFFB71C1C),
        inverseSurface = Color(0xFF313131),
        inverseOnSurface = Color(0xFFFAFAFA),

        // Outline
        outline = Color(0xFFB71C1C),
        outlineVariant = Color(0xFFEF9A9A),

        // Error
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),

        // Scrim
        scrim = Color(0xFF000000),

        // Surface containers
        surfaceDim = Color(0xFFE0E0E0),
        surfaceBright = Color(0xFFFFFFFF),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFAFAFA),
        surfaceContainer = Color(0xFFF5F5F5),           // Navigation bar background
        surfaceContainerHigh = Color(0xFFEEEEEE),
        surfaceContainerHighest = Color(0xFFE0E0E0),
    )
}
