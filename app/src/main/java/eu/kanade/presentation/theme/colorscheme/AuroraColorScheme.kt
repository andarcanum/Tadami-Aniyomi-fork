package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal object AuroraColorScheme : BaseColorScheme() {

    // Aniview Premium color palette
    val aniviewElectricBlue = Color(0xFF0095FF)
    val aniviewCyan = Color(0xFF00E5FF)
    val aniviewPurple = Color(0xFF7C4DFF)
    val aniviewDarkBg = Color(0xFF0F1116)
    val aniviewSurface = Color(0xFF1A1D23)
    val aniviewGlow = Color(0xFF0095FF)

    // Legacy Aurora colors (for backwards compatibility)
    val auroraAccent = aniviewElectricBlue
    val auroraAccentLight = Color(0xFF0077CC)

    val auroraDarkBackground = aniviewDarkBg
    val auroraDarkSurface = aniviewSurface
    val auroraDarkGradientStart = Color(0xFF1e1b4b)
    val auroraDarkGradientEnd = aniviewDarkBg

    val auroraLightBackground = Color(0xFFf8fafc)
    val auroraLightSurface = Color(0xFFffffff)
    val auroraLightGradientStart = Color(0xFFe0e7ff)
    val auroraLightGradientEnd = Color(0xFFf8fafc)

    val auroraGlass = Color(0x33FFFFFF)
    val auroraGlassLight = Color(0x1A000000)

    override val darkScheme = darkColorScheme(
        primary = aniviewElectricBlue,
        onPrimary = Color.White,
        primaryContainer = aniviewElectricBlue.copy(alpha = 0.2f),
        onPrimaryContainer = aniviewElectricBlue,

        secondary = aniviewCyan,
        onSecondary = Color.Black,
        secondaryContainer = Color(0xFF1e3a5f),
        onSecondaryContainer = aniviewCyan,

        tertiary = aniviewPurple,
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFF311b92),
        onTertiaryContainer = Color(0xFFb388ff),

        background = aniviewDarkBg,
        onBackground = Color.White,

        surface = aniviewSurface,
        onSurface = Color.White,
        surfaceVariant = Color(0xFF252931),
        onSurfaceVariant = Color(0xFF94a3b8),

        surfaceContainerLowest = Color(0xFF0A0C0F),
        surfaceContainerLow = Color(0xFF13151A),
        surfaceContainer = aniviewSurface,
        surfaceContainerHigh = Color(0xFF24272E),
        surfaceContainerHighest = Color(0xFF2F3239),

        outline = Color(0xFF334155),
        outlineVariant = Color(0xFF1e293b),

        error = Color(0xFFf87171),
        onError = Color.White,
        errorContainer = Color(0xFF7f1d1d),
        onErrorContainer = Color(0xFFfecaca),

        inverseSurface = Color(0xFFe2e8f0),
        inverseOnSurface = Color(0xFF1e293b),
        inversePrimary = Color(0xFF0369a1),

        scrim = Color.Black,
    )

    override val lightScheme = lightColorScheme(
        primary = auroraAccentLight,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFe0f2fe),
        onPrimaryContainer = Color(0xFF0c4a6e),

        secondary = auroraAccentLight,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFe0f2fe),
        onSecondaryContainer = Color(0xFF0c4a6e),

        tertiary = Color(0xFF6366f1),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFe0e7ff),
        onTertiaryContainer = Color(0xFF3730a3),

        background = auroraLightBackground,
        onBackground = Color(0xFF0f172a),

        surface = auroraLightSurface,
        onSurface = Color(0xFF0f172a),
        surfaceVariant = Color(0xFFf1f5f9),
        onSurfaceVariant = Color(0xFF475569),

        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFf8fafc),
        surfaceContainer = Color(0xFFf1f5f9),
        surfaceContainerHigh = Color(0xFFe2e8f0),
        surfaceContainerHighest = Color(0xFFcbd5e1),

        outline = Color(0xFFcbd5e1),
        outlineVariant = Color(0xFFe2e8f0),

        error = Color(0xFFdc2626),
        onError = Color.White,
        errorContainer = Color(0xFFfee2e2),
        onErrorContainer = Color(0xFF991b1b),

        inverseSurface = Color(0xFF1e293b),
        inverseOnSurface = Color(0xFFf1f5f9),
        inversePrimary = Color(0xFF7dd3fc),

        scrim = Color.Black,
    )
}
