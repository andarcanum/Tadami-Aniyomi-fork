package eu.kanade.presentation.easteregg.crystal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import com.tadami.aurora.R
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.LocalAuroraColors

internal enum class CrystalPalette(val key: String, val gold: Int, val rose: Int, val mint: Int) {
    Moon("moon", 0xFFF6E0BA.toInt(), 0xFFE6B2CA.toInt(), 0xFFA8DED2.toInt()),
    Dusk("dusk", 0xFFD2C3F5.toInt(), 0xFFE1B9DC.toInt(), 0xFFAFCEEE.toInt()),
    Pearl("pearl", 0xFFF2ECE2.toInt(), 0xFFD7C4CE.toInt(), 0xFFB8DED2.toInt()),
    ;

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key } ?: Moon
    }
}

internal enum class CrystalFrameVariant(val key: String) {
    Fold("crystal_fold"),
    Point("crystal_point"),
    Thread("crystal_thread"),
    ;

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key } ?: Fold
    }
}

internal enum class CrystalPortrait(val drawable: Int) {
    Warm(R.drawable.crystal_portrait_v1),
    Soft(R.drawable.crystal_portrait_v2),
    Photo(R.drawable.crystal_portrait_photo),
}
internal enum class CrystalDissolveStyle { Layers, Heart, Aurora }

internal object CrystalFonts {
    val Script = FontFamily(Font(R.font.crystal_great_vibes))
    val Serif =
        FontFamily(Font(R.font.crystal_cormorant), Font(R.font.crystal_cormorant_italic, style = FontStyle.Italic))
    val Ui = FontFamily(Font(R.font.crystal_manrope))
}

/** An isolated authored art surface, never a mutation of the user's selected app theme. */
@Composable
internal fun CrystalArtTheme(palette: CrystalPalette = CrystalPalette.Moon, content: @Composable () -> Unit) {
    val current = AuroraTheme.colors
    val colors = if (current.isEInk) {
        current
    } else {
        current.copy(
            accent = Color(palette.gold), accentVariant = Color(palette.rose),
            background = Color(0xFF070A11), surface = Color(0xFF0E1421),
            gradientStart = Color(0xFF151D2E), gradientEnd = Color(0xFF070B14),
            glass = Color(0xCC111827), cardBackground = Color(0xEE101623),
            textPrimary = Color(0xFFF5EFE6), textSecondary = Color(0xFFB3B8C8),
            textOnAccent = Color(0xFF201A23), divider = Color(0x33EAD5B3), isDark = true,
        )
    }
    CompositionLocalProvider(LocalAuroraColors provides colors, content = content)
}
