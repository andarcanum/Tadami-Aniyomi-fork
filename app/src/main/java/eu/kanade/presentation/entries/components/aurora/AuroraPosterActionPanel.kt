package eu.kanade.presentation.entries.components.aurora

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.kanade.domain.ui.UserProfilePreferences
import eu.kanade.domain.ui.model.AuroraTitleHeroCtaMode
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
private fun rememberAuroraPosterActionMode(): AuroraTitleHeroCtaMode {
    val userProfilePreferences = remember { Injekt.get<UserProfilePreferences>() }
    val titleHeroModeKey by userProfilePreferences.auroraTitleHeroCtaMode().collectAsState()
    return remember(titleHeroModeKey) {
        AuroraTitleHeroCtaMode.fromKey(titleHeroModeKey)
    }
}

@Composable
internal fun AuroraPosterActionPanel(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.(contentColor: Color) -> Unit,
) {
    val shape = RoundedCornerShape(999.dp)
    val colors = AuroraTheme.colors
    val titleHeroMode = rememberAuroraPosterActionMode()
    val visualMode = remember(titleHeroMode) {
        resolveAuroraTitleHeroCtaVisualMode(titleHeroMode)
    }
    val surfaceSpec = remember(titleHeroMode, colors.isDark) {
        resolveAuroraTitleHeroCtaSurfaceSpec(
            mode = titleHeroMode,
            isDark = colors.isDark,
        )
    }
    val contentColor = when (visualMode) {
        AuroraTitleHeroCtaVisualMode.AuroraGlass -> Color.White
        AuroraTitleHeroCtaVisualMode.ClassicSolid -> colors.textOnAccent
    }
    val auroraInnerGlowBrush = remember(colors.accent, surfaceSpec) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to Color.Transparent,
                0.46f to colors.accent.copy(alpha = surfaceSpec.innerGlowAlpha * 0.18f),
                0.78f to colors.accent.copy(alpha = surfaceSpec.innerGlowAlpha * 0.58f),
                1.00f to colors.accent.copy(alpha = surfaceSpec.innerGlowAlpha),
            ),
        )
    }
    val auroraHighlightBrush = remember(surfaceSpec) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to Color.White.copy(alpha = surfaceSpec.highlightAlpha),
                0.34f to Color.White.copy(alpha = surfaceSpec.highlightAlpha * 0.48f),
                0.68f to Color.Transparent,
                1.00f to Color.Transparent,
            ),
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .clip(shape)
            .background(colors.accent.copy(alpha = surfaceSpec.containerAlpha))
            .background(
                brush = auroraInnerGlowBrush,
                alpha = if (visualMode == AuroraTitleHeroCtaVisualMode.AuroraGlass) 1f else 0f,
            )
            .background(
                brush = auroraHighlightBrush,
                alpha = if (visualMode == AuroraTitleHeroCtaVisualMode.AuroraGlass) 1f else 0f,
            )
            .let { base ->
                if (surfaceSpec.borderAlpha > 0f) {
                    base.border(
                        BorderStroke(
                            width = 1.dp,
                            color = Color.White.copy(alpha = surfaceSpec.borderAlpha),
                        ),
                        shape,
                    )
                } else {
                    base
                }
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        content = { content(contentColor) },
    )
}
