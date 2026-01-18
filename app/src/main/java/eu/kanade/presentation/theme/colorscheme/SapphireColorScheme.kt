package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Colors for Sapphire theme
 * A blue theme inspired by the sapphire gemstone
 *
 * Key colors:
 * Primary: Blue (#1E88E5)
 * Secondary: Dark Blue/Indigo (#0D47A1)
 * Background: Dark Grey (#212121)
 */
internal object SapphireColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        // Primary colors
        primary = Color(0xFF64B5F6),                    // Muted blue for readability
        onPrimary = Color(0xFF0D47A1),
        primaryContainer = Color(0xFF1E88E5),
        onPrimaryContainer = Color(0xFFFFFFFF),
        inversePrimary = Color(0xFF2979FF),

        // Secondary colors
        secondary = Color(0xFF1E88E5),                  // Unread badge
        onSecondary = Color(0xFFFFFFFF),                // Unread badge text
        secondaryContainer = Color(0xFF0D47A1),         // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFFFFFFFF),       // Navigation bar selector icon

        // Tertiary colors
        tertiary = Color(0xFFB0BEC5),                   // Downloaded badge
        onTertiary = Color(0xFF212121),                 // Downloaded badge text
        tertiaryContainer = Color(0xFF546E7A),
        onTertiaryContainer = Color(0xFFFFFFFF),

        // Background & Surface
        background = Color(0xFF212121),
        onBackground = Color(0xFFFFFFFF),
        surface = Color(0xFF212121),
        onSurface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFF303030),             // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFFD6E3F3),
        surfaceTint = Color(0xFF1E88E5),
        inverseSurface = Color(0xFFFAFAFA),
        inverseOnSurface = Color(0xFF313131),

        // Outline
        outline = Color(0xFF1E88E5),
        outlineVariant = Color(0xFF0D47A1),

        // Error
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),

        // Scrim
        scrim = Color(0xFF000000),

        // Surface containers
        surfaceDim = Color(0xFF1A1A1A),
        surfaceBright = Color(0xFF3D3D3D),
        surfaceContainerLowest = Color(0xFF141414),
        surfaceContainerLow = Color(0xFF212121),
        surfaceContainer = Color(0xFF262626),           // Navigation bar background
        surfaceContainerHigh = Color(0xFF303030),
        surfaceContainerHighest = Color(0xFF3B3B3B),
    )

    override val lightScheme = lightColorScheme(
        // Primary colors
        primary = Color(0xFF1565C0),                    // Dark blue for light theme
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF1E88E5),
        onPrimaryContainer = Color(0xFFFFFFFF),
        inversePrimary = Color(0xFF64B5F6),

        // Secondary colors
        secondary = Color(0xFF1565C0),                  // Unread badge
        onSecondary = Color(0xFFFFFFFF),                // Unread badge text
        secondaryContainer = Color(0xFFBBDEFB),         // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFF0D47A1),       // Navigation bar selector icon

        // Tertiary colors
        tertiary = Color(0xFF607D8B),                   // Downloaded badge
        onTertiary = Color(0xFFFFFFFF),                 // Downloaded badge text
        tertiaryContainer = Color(0xFFB0BEC5),
        onTertiaryContainer = Color(0xFF212121),

        // Background & Surface
        background = Color(0xFFFFFFFF),
        onBackground = Color(0xFF212121),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF212121),
        surfaceVariant = Color(0xFFE3F2FD),             // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFF455A64),
        surfaceTint = Color(0xFF1565C0),
        inverseSurface = Color(0xFF424242),
        inverseOnSurface = Color(0xFFFAFAFA),

        // Outline
        outline = Color(0xFF1565C0),
        outlineVariant = Color(0xFF90CAF9),

        // Error
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),

        // Scrim
        scrim = Color(0xFF000000),

        // Surface containers
        surfaceDim = Color(0xFFDCE4EC),
        surfaceBright = Color(0xFFFFFFFF),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF5F9FC),
        surfaceContainer = Color(0xFFECF3F9),           // Navigation bar background
        surfaceContainerHigh = Color(0xFFE3ECF4),
        surfaceContainerHighest = Color(0xFFDAE5EE),
    )
}
