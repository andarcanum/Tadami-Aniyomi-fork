package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Colors for Cloudflare theme
 * An orange theme inspired by Cloudflare's brand colors
 *
 * Key colors:
 * Primary: Orange (#F38020)
 * Secondary: Dark Orange (#B25A06)
 * Background: Dark Blue-Grey (#1B1B22)
 */
internal object CloudflareColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        // Primary colors
        primary = Color(0xFFFFB74D),                    // Muted orange for readability
        onPrimary = Color(0xFF1B1B22),
        primaryContainer = Color(0xFFF38020),
        onPrimaryContainer = Color(0xFF1B1B22),
        inversePrimary = Color(0xFF8B5000),

        // Secondary colors
        secondary = Color(0xFFF38020),                  // Unread badge
        onSecondary = Color(0xFF1B1B22),                // Unread badge text
        secondaryContainer = Color(0xFFB25A06),         // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFFFFFFFF),       // Navigation bar selector icon

        // Tertiary colors
        tertiary = Color(0xFF90A4AE),                   // Downloaded badge
        onTertiary = Color(0xFF1B1B22),                 // Downloaded badge text
        tertiaryContainer = Color(0xFF546E7A),
        onTertiaryContainer = Color(0xFFFFFFFF),

        // Background & Surface
        background = Color(0xFF1B1B22),
        onBackground = Color(0xFFEFF2F5),
        surface = Color(0xFF1B1B22),
        onSurface = Color(0xFFEFF2F5),
        surfaceVariant = Color(0xFF2D2D36),             // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFFD69A6A),
        surfaceTint = Color(0xFFF38020),
        inverseSurface = Color(0xFFF3EFF4),
        inverseOnSurface = Color(0xFF313033),

        // Outline
        outline = Color(0xFFF38020),
        outlineVariant = Color(0xFF8B5000),

        // Error
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),

        // Scrim
        scrim = Color(0xFF000000),

        // Surface containers
        surfaceDim = Color(0xFF141418),
        surfaceBright = Color(0xFF3F3F46),
        surfaceContainerLowest = Color(0xFF0F0F14),
        surfaceContainerLow = Color(0xFF1B1B22),
        surfaceContainer = Color(0xFF1F1F26),           // Navigation bar background
        surfaceContainerHigh = Color(0xFF2A2A32),
        surfaceContainerHighest = Color(0xFF35353E),
    )

    override val lightScheme = lightColorScheme(
        // Primary colors
        primary = Color(0xFFE65100),                    // Dark orange for light theme
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFF38020),
        onPrimaryContainer = Color(0xFFFFFFFF),
        inversePrimary = Color(0xFFFFB74D),

        // Secondary colors
        secondary = Color(0xFFE65100),                  // Unread badge
        onSecondary = Color(0xFFFFFFFF),                // Unread badge text
        secondaryContainer = Color(0xFFFFE0B2),         // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFF1B1B22),       // Navigation bar selector icon

        // Tertiary colors
        tertiary = Color(0xFF607D8B),                   // Downloaded badge
        onTertiary = Color(0xFFFFFFFF),                 // Downloaded badge text
        tertiaryContainer = Color(0xFFB0BEC5),
        onTertiaryContainer = Color(0xFF1B1B22),

        // Background & Surface
        background = Color(0xFFEFF2F5),
        onBackground = Color(0xFF1B1B22),
        surface = Color(0xFFEFF2F5),
        onSurface = Color(0xFF1B1B22),
        surfaceVariant = Color(0xFFFFF3E0),             // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFF5D4E40),
        surfaceTint = Color(0xFFE65100),
        inverseSurface = Color(0xFF313033),
        inverseOnSurface = Color(0xFFF3EFF4),

        // Outline
        outline = Color(0xFFE65100),
        outlineVariant = Color(0xFFFFCC80),

        // Error
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),

        // Scrim
        scrim = Color(0xFF000000),

        // Surface containers
        surfaceDim = Color(0xFFDDE0E3),
        surfaceBright = Color(0xFFFFFFFF),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF5F8FB),
        surfaceContainer = Color(0xFFEFF2F5),           // Navigation bar background
        surfaceContainerHigh = Color(0xFFE9ECEF),
        surfaceContainerHighest = Color(0xFFE3E6E9),
    )
}
