package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Sakura Noir.
 *
 * A secret-hall palette: ink-black paper, neon sakura, plum shadows, and a
 * jade afterglow. It should feel like a hidden midnight reward, not a pastel
 * Strawberry variant.
 */
internal object SakuraNoirColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFFFF78B7),
        onPrimary = Color(0xFF300018),
        primaryContainer = Color(0xFF721143),
        onPrimaryContainer = Color(0xFFFFD8E8),
        inversePrimary = Color(0xFFAA2B66),

        secondary = Color(0xFFD9A8FF),
        onSecondary = Color(0xFF280045),
        secondaryContainer = Color(0xFF51305F),
        onSecondaryContainer = Color(0xFFF4DAFF),

        tertiary = Color(0xFF6EF2C2),
        onTertiary = Color(0xFF003826),
        tertiaryContainer = Color(0xFF0B5A44),
        onTertiaryContainer = Color(0xFFA8FDDC),

        background = Color(0xFF09050A),
        onBackground = Color(0xFFF8E8F0),
        surface = Color(0xFF110912),
        onSurface = Color(0xFFF8E8F0),
        surfaceVariant = Color(0xFF281728),
        onSurfaceVariant = Color(0xFFE2C1D6),
        surfaceTint = Color(0xFFFF78B7),
        inverseSurface = Color(0xFFF4E7EE),
        inverseOnSurface = Color(0xFF2D1A28),

        outline = Color(0xFF9D738B),
        outlineVariant = Color(0xFF4D2B3E),

        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),

        scrim = Color(0xFF000000),

        surfaceDim = Color(0xFF09050A),
        surfaceBright = Color(0xFF342035),
        surfaceContainerLowest = Color(0xFF050206),
        surfaceContainerLow = Color(0xFF130A14),
        surfaceContainer = Color(0xFF1A0F1C),
        surfaceContainerHigh = Color(0xFF241426),
        surfaceContainerHighest = Color(0xFF301B32),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF9B245C),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFD8E8),
        onPrimaryContainer = Color(0xFF3E0020),
        inversePrimary = Color(0xFFFF78B7),

        secondary = Color(0xFF6F4186),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFF4DAFF),
        onSecondaryContainer = Color(0xFF280045),

        tertiary = Color(0xFF00785A),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFA8FDDC),
        onTertiaryContainer = Color(0xFF002116),

        background = Color(0xFFFFF7FA),
        onBackground = Color(0xFF27161F),
        surface = Color(0xFFFFF7FA),
        onSurface = Color(0xFF27161F),
        surfaceVariant = Color(0xFFF2DCE8),
        onSurfaceVariant = Color(0xFF554252),
        surfaceTint = Color(0xFF9B245C),
        inverseSurface = Color(0xFF3C2A34),
        inverseOnSurface = Color(0xFFFFECF3),

        outline = Color(0xFF877080),
        outlineVariant = Color(0xFFD9C0CF),

        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),

        scrim = Color(0xFF000000),

        surfaceDim = Color(0xFFE7D6DE),
        surfaceBright = Color(0xFFFFF7FA),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFFEEF5),
        surfaceContainer = Color(0xFFFBE6F0),
        surfaceContainerHigh = Color(0xFFF3DDE8),
        surfaceContainerHighest = Color(0xFFEBD3DF),
    )
}
