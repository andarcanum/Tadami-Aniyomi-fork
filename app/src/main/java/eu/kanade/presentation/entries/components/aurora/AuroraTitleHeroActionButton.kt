package eu.kanade.presentation.entries.components.aurora

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import eu.kanade.domain.ui.UserProfilePreferences
import eu.kanade.domain.ui.model.AuroraTitleHeroCtaMode
import eu.kanade.presentation.components.resolveAuroraCtaLabelShadowSpec
import eu.kanade.presentation.components.toComposeShadow
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

internal enum class AuroraTitleHeroCtaVisualMode {
    AuroraGlass,
    ClassicSolid,
}

internal data class AuroraTitleHeroCtaSurfaceSpec(
    val containerAlpha: Float,
    val usesGradient: Boolean,
    val innerGlowAlpha: Float,
    val highlightAlpha: Float,
    val borderAlpha: Float,
)

internal fun resolveAuroraTitleHeroCtaVisualMode(
    mode: AuroraTitleHeroCtaMode,
): AuroraTitleHeroCtaVisualMode {
    return when (mode) {
        AuroraTitleHeroCtaMode.Aurora -> AuroraTitleHeroCtaVisualMode.AuroraGlass
        AuroraTitleHeroCtaMode.Classic -> AuroraTitleHeroCtaVisualMode.ClassicSolid
    }
}

internal fun resolveAuroraTitleHeroCtaSurfaceSpec(
    mode: AuroraTitleHeroCtaMode,
    isDark: Boolean,
): AuroraTitleHeroCtaSurfaceSpec {
    return when (mode) {
        AuroraTitleHeroCtaMode.Aurora -> AuroraTitleHeroCtaSurfaceSpec(
            containerAlpha = if (isDark) 0.50f else 0.45f,
            usesGradient = false,
            innerGlowAlpha = if (isDark) 0.55f else 0.45f,
            highlightAlpha = 0f,
            borderAlpha = 0.12f,
        )
        AuroraTitleHeroCtaMode.Classic -> AuroraTitleHeroCtaSurfaceSpec(
            containerAlpha = 1f,
            usesGradient = false,
            innerGlowAlpha = 0f,
            highlightAlpha = 0f,
            borderAlpha = 0f,
        )
    }
}

@Composable
private fun rememberAuroraTitleHeroCtaMode(): AuroraTitleHeroCtaMode {
    val userProfilePreferences = remember { Injekt.get<UserProfilePreferences>() }
    val titleHeroModeKey by userProfilePreferences.auroraTitleHeroCtaMode().collectAsState()
    return remember(titleHeroModeKey) {
        AuroraTitleHeroCtaMode.fromKey(titleHeroModeKey)
    }
}

@Composable
private fun AuroraTitleHeroActionSurface(
    mode: AuroraTitleHeroCtaMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape,
    contentPadding: PaddingValues,
    content: @Composable BoxScope.(contentColor: Color) -> Unit,
) {
    val colors = AuroraTheme.colors
    val visualMode = remember(mode) {
        resolveAuroraTitleHeroCtaVisualMode(mode)
    }
    val surfaceSpec = remember(mode, colors.isDark) {
        resolveAuroraTitleHeroCtaSurfaceSpec(
            mode = mode,
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
    Box(
        modifier = modifier
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
            .clickable(onClick = onClick)
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        content(contentColor)
    }
}

@Composable
internal fun AuroraTitleHeroActionButton(
    hasProgress: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    iconSize: Dp = 28.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    textSize: TextUnit,
    textWeight: FontWeight,
) {
    val titleHeroMode = rememberAuroraTitleHeroCtaMode()
    val labelShadow = remember(titleHeroMode) {
        resolveAuroraCtaLabelShadowSpec(
            enabled = titleHeroMode == AuroraTitleHeroCtaMode.Aurora,
        ).toComposeShadow()
    }

    AuroraTitleHeroActionSurface(
        mode = titleHeroMode,
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        contentPadding = contentPadding,
    ) { contentColor ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(iconSize),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(
                    if (hasProgress) MR.strings.action_resume else MR.strings.action_start,
                ),
                color = contentColor,
                fontSize = textSize,
                fontWeight = textWeight,
                style = TextStyle(shadow = labelShadow),
            )
        }
    }
}

@Composable
internal fun AuroraTitleHeroActionFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerSize: Dp = 64.dp,
    iconSize: Dp = 32.dp,
) {
    val titleHeroMode = rememberAuroraTitleHeroCtaMode()

    AuroraTitleHeroActionSurface(
        mode = titleHeroMode,
        onClick = onClick,
        modifier = modifier.size(containerSize),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(0.dp),
    ) { contentColor ->
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(iconSize),
        )
    }
}
