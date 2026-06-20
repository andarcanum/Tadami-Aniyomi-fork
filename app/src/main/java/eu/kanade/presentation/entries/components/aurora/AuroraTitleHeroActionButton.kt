package eu.kanade.presentation.entries.components.aurora

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

internal enum class AuroraHeroCtaMode {
    Aurora,
    Classic,
}

internal fun AuroraTitleHeroCtaMode.toPresentationMode(): AuroraHeroCtaMode = when (this) {
    AuroraTitleHeroCtaMode.Aurora -> AuroraHeroCtaMode.Aurora
    AuroraTitleHeroCtaMode.Classic -> AuroraHeroCtaMode.Classic
}

internal data class AuroraHeroCtaSurfaceSpec(
    val containerAlpha: Float,
    val usesGradient: Boolean,
    val innerGlowAlpha: Float,
    val highlightAlpha: Float,
    val borderAlpha: Float,
)

internal fun resolveAuroraHeroCtaSurfaceSpec(
    mode: AuroraHeroCtaMode,
    isDark: Boolean,
    isHome: Boolean,
): AuroraHeroCtaSurfaceSpec {
    return if (isHome) {
        when (mode) {
            AuroraHeroCtaMode.Aurora -> AuroraHeroCtaSurfaceSpec(
                containerAlpha = if (isDark) 0.50f else 0.78f,
                usesGradient = false,
                borderAlpha = if (isDark) 0.12f else 0.18f,
                innerGlowAlpha = if (isDark) 0.55f else 0.10f,
                highlightAlpha = if (isDark) 0f else 0.12f,
            )
            AuroraHeroCtaMode.Classic -> AuroraHeroCtaSurfaceSpec(
                containerAlpha = 1f,
                usesGradient = true,
                borderAlpha = 0.12f,
                innerGlowAlpha = 0f,
                highlightAlpha = 0f,
            )
        }
    } else {
        when (mode) {
            AuroraHeroCtaMode.Aurora -> if (isDark) {
                AuroraHeroCtaSurfaceSpec(
                    containerAlpha = 0.28f,
                    usesGradient = true,
                    innerGlowAlpha = 0.55f,
                    highlightAlpha = 0.42f,
                    borderAlpha = 0.12f,
                )
            } else {
                AuroraHeroCtaSurfaceSpec(
                    containerAlpha = 0.88f,
                    usesGradient = false,
                    innerGlowAlpha = 0.08f,
                    highlightAlpha = 0.12f,
                    borderAlpha = 0.10f,
                )
            }
            AuroraHeroCtaMode.Classic -> AuroraHeroCtaSurfaceSpec(
                containerAlpha = 1f,
                usesGradient = false,
                innerGlowAlpha = 0f,
                highlightAlpha = 0f,
                borderAlpha = 0f,
            )
        }
    }
}

@Composable
internal fun rememberAuroraTitleHeroCtaMode(): AuroraTitleHeroCtaMode {
    val userProfilePreferences = remember { Injekt.get<UserProfilePreferences>() }
    val titleHeroModeKey by userProfilePreferences.auroraTitleHeroCtaMode().collectAsState()
    return remember(titleHeroModeKey) {
        AuroraTitleHeroCtaMode.fromKey(titleHeroModeKey)
    }
}

@Composable
internal fun AuroraGlassCtaSurface(
    mode: AuroraHeroCtaMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isHome: Boolean = false,
    shape: Shape,
    contentPadding: PaddingValues,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable BoxScope.(contentColor: Color) -> Unit,
) {
    val colors = AuroraTheme.colors
    val isEInk = colors.isEInk
    val surfaceSpec = remember(mode, colors.isDark, isHome) {
        resolveAuroraHeroCtaSurfaceSpec(
            mode = mode,
            isDark = colors.isDark,
            isHome = isHome,
        )
    }

    val contentColor = when (mode) {
        AuroraHeroCtaMode.Aurora -> if (colors.isDark) colors.ctaContentOnGlassDark else Color.White
        AuroraHeroCtaMode.Classic -> colors.textOnAccent
    }

    val auroraInnerGlowBrush = remember(colors.accent, surfaceSpec, isEInk) {
        if (isEInk) {
            SolidColor(Color.Transparent)
        } else {
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0.00f to Color.Transparent,
                    0.46f to colors.accent.copy(alpha = surfaceSpec.innerGlowAlpha * 0.18f),
                    0.78f to colors.accent.copy(alpha = surfaceSpec.innerGlowAlpha * 0.58f),
                    1.00f to colors.accent.copy(alpha = surfaceSpec.innerGlowAlpha),
                ),
            )
        }
    }

    val auroraHighlightBrush = remember(surfaceSpec, isEInk) {
        if (isEInk) {
            SolidColor(Color.Transparent)
        } else {
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0.00f to Color.White.copy(alpha = surfaceSpec.highlightAlpha),
                    0.34f to Color.White.copy(alpha = surfaceSpec.highlightAlpha * 0.48f),
                    0.68f to Color.Transparent,
                    1.00f to Color.Transparent,
                ),
            )
        }
    }

    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "ctaSpringScale",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(
                brush = if (surfaceSpec.usesGradient) {
                    Brush.verticalGradient(
                        colors = listOf(
                            colors.accent.copy(alpha = surfaceSpec.containerAlpha),
                            colors.accentVariant.copy(alpha = surfaceSpec.containerAlpha),
                        ),
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            colors.accent.copy(alpha = surfaceSpec.containerAlpha),
                            colors.accent.copy(alpha = surfaceSpec.containerAlpha),
                        ),
                    )
                },
                shape = shape,
            )
            .background(
                brush = auroraInnerGlowBrush,
                alpha = if (mode == AuroraHeroCtaMode.Aurora) 1f else 0f,
            )
            .background(
                brush = auroraHighlightBrush,
                alpha = if (mode == AuroraHeroCtaMode.Aurora) 1f else 0f,
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
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
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
    val colors = AuroraTheme.colors
    val titleHeroMode = rememberAuroraTitleHeroCtaMode()
    val labelShadow = remember(titleHeroMode) {
        resolveAuroraCtaLabelShadowSpec(
            enabled = titleHeroMode == AuroraTitleHeroCtaMode.Aurora,
        ).toComposeShadow()
    }
    val interactionSource = remember { MutableInteractionSource() }

    val actualShape = remember(cornerRadius) {
        RoundedCornerShape(cornerRadius)
    }

    AuroraGlassCtaSurface(
        mode = titleHeroMode.toPresentationMode(),
        onClick = onClick,
        modifier = modifier,
        shape = actualShape,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
    ) { contentColor ->
        Row(
            modifier = Modifier.offset(x = (-2).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(iconSize),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(
                    if (hasProgress) MR.strings.action_resume else MR.strings.action_start,
                ),
                color = contentColor,
                fontSize = textSize,
                fontWeight = textWeight,
                style = TextStyle(shadow = labelShadow),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
    val interactionSource = remember { MutableInteractionSource() }

    AuroraGlassCtaSurface(
        mode = titleHeroMode.toPresentationMode(),
        onClick = onClick,
        modifier = modifier.size(containerSize),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(0.dp),
        interactionSource = interactionSource,
    ) { contentColor ->
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(iconSize),
        )
    }
}
