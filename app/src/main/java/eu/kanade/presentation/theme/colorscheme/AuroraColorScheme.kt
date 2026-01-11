package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal object AuroraColorScheme : BaseColorScheme() {

    val auroraAccent = Color(0xFF279df1)
    val auroraAccentLight = Color(0xFF0077CC)
    
    val auroraDarkBackground = Color(0xFF101b22)
    val auroraDarkSurface = Color(0xFF1a2730)
    val auroraDarkGradientStart = Color(0xFF1e1b4b)
    val auroraDarkGradientEnd = Color(0xFF101b22)
    
    val auroraLightBackground = Color(0xFFf8fafc)
    val auroraLightSurface = Color(0xFFffffff)
    val auroraLightGradientStart = Color(0xFFe0e7ff)
    val auroraLightGradientEnd = Color(0xFFf8fafc)
    
    val auroraGlass = Color(0x33FFFFFF)
    val auroraGlassLight = Color(0x1A000000)

    override val darkScheme = darkColorScheme(
        primary = auroraAccent,
        onPrimary = Color.White,
        primaryContainer = auroraAccent.copy(alpha = 0.2f),
        onPrimaryContainer = auroraAccent,
        
        secondary = auroraAccent,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFF1e3a5f),
        onSecondaryContainer = Color(0xFF90caf9),
        
        tertiary = Color(0xFF7c4dff),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFF311b92),
        onTertiaryContainer = Color(0xFFb388ff),
        
        background = auroraDarkBackground,
        onBackground = Color.White,
        
        surface = auroraDarkSurface,
        onSurface = Color.White,
        surfaceVariant = Color(0xFF1e293b),
        onSurfaceVariant = Color(0xFF94a3b8),
        
        surfaceContainerLowest = Color(0xFF0d1318),
        surfaceContainerLow = Color(0xFF121d24),
        surfaceContainer = Color(0xFF1a2730),
        surfaceContainerHigh = Color(0xFF22323d),
        surfaceContainerHighest = Color(0xFF2a3d4a),
        
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
