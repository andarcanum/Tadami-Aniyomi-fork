package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Colors for Matrix theme
 * A green theme inspired by The Matrix movie aesthetic
 *
 * Key colors:
 * Primary: Neon Green (#00FF00)
 * Secondary: Dark Green (#006400)
 * Background: Near Black (#111111)
 */
internal object MatrixColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        // Primary colors
        primary = Color(0xFF69F0AE),                    // Muted green for readability
        onPrimary = Color(0xFF003314),
        primaryContainer = Color(0xFF00C853),
        onPrimaryContainer = Color(0xFF003314),
        inversePrimary = Color(0xFF007700),

        // Secondary colors
        secondary = Color(0xFF00FF00),                  // Unread badge
        onSecondary = Color(0xFF003314),                // Unread badge text
        secondaryContainer = Color(0xFF006400),         // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFFFFFFFF),       // Navigation bar selector icon

        // Tertiary colors
        tertiary = Color(0xFFE0E0E0),                   // Downloaded badge
        onTertiary = Color(0xFF111111),                 // Downloaded badge text
        tertiaryContainer = Color(0xFF757575),
        onTertiaryContainer = Color(0xFFFFFFFF),

        // Background & Surface
        background = Color(0xFF111111),
        onBackground = Color(0xFFFFFFFF),
        surface = Color(0xFF111111),
        onSurface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFF1A1A1A),             // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFFD6E8D6),
        surfaceTint = Color(0xFF00FF00),
        inverseSurface = Color(0xFFFAFAFA),
        inverseOnSurface = Color(0xFF313131),

        // Outline
        outline = Color(0xFF00FF00),
        outlineVariant = Color(0xFF004D00),

        // Error
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),

        // Scrim
        scrim = Color(0xFF000000),

        // Surface containers
        surfaceDim = Color(0xFF0A0A0A),
        surfaceBright = Color(0xFF2A2A2A),
        surfaceContainerLowest = Color(0xFF050505),
        surfaceContainerLow = Color(0xFF111111),
        surfaceContainer = Color(0xFF161616),           // Navigation bar background
        surfaceContainerHigh = Color(0xFF1C1C1C),
        surfaceContainerHighest = Color(0xFF222222),
    )

    override val lightScheme = lightColorScheme(
        // Primary colors
        primary = Color(0xFF1B5E20),                    // Dark green for light theme
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF2E7D32),
        onPrimaryContainer = Color(0xFFFFFFFF),
        inversePrimary = Color(0xFF69F0AE),

        // Secondary colors
        secondary = Color(0xFF1B5E20),                  // Unread badge
        onSecondary = Color(0xFFFFFFFF),                // Unread badge text
        secondaryContainer = Color(0xFFC8E6C9),         // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFF111111),       // Navigation bar selector icon

        // Tertiary colors
        tertiary = Color(0xFF616161),                   // Downloaded badge
        onTertiary = Color(0xFFFFFFFF),                 // Downloaded badge text
        tertiaryContainer = Color(0xFFBDBDBD),
        onTertiaryContainer = Color(0xFF111111),

        // Background & Surface
        background = Color(0xFFF1F8E9),
        onBackground = Color(0xFF111111),
        surface = Color(0xFFF1F8E9),
        onSurface = Color(0xFF111111),
        surfaceVariant = Color(0xFFDCEDC8),             // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFF33691E),
        surfaceTint = Color(0xFF1B5E20),
        inverseSurface = Color(0xFF222222),
        inverseOnSurface = Color(0xFFFAFAFA),

        // Outline
        outline = Color(0xFF1B5E20),
        outlineVariant = Color(0xFFA5D6A7),

        // Error
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),

        // Scrim
        scrim = Color(0xFF000000),

        // Surface containers
        surfaceDim = Color(0xFFD7E8D0),
        surfaceBright = Color(0xFFFFFFFF),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF5FAF0),
        surfaceContainer = Color(0xFFEDF5E8),           // Navigation bar background
        surfaceContainerHigh = Color(0xFFE5EFE0),
        surfaceContainerHighest = Color(0xFFDCE8D7),
    )
}
