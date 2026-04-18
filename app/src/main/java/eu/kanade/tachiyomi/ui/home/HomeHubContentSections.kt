package eu.kanade.tachiyomi.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.tadami.aurora.R
import eu.kanade.domain.ui.model.HomeHeroCtaMode
import eu.kanade.presentation.components.AuroraCard
import eu.kanade.presentation.components.AuroraCoverPlaceholderVariant
import eu.kanade.presentation.components.auroraMenuRimLightBrush
import eu.kanade.presentation.components.rememberThemeAwareCoverErrorPainter
import eu.kanade.presentation.components.resolveAuroraCoverModel
import eu.kanade.presentation.components.resolveAuroraCtaLabelShadowSpec
import eu.kanade.presentation.components.resolveAuroraHomeIconShadowSpec
import eu.kanade.presentation.components.toComposeShadow
import eu.kanade.presentation.entries.components.aurora.rememberAuroraPosterColorFilter
import eu.kanade.presentation.entries.components.aurora.resolveAuroraHeroOverlayBrush
import eu.kanade.presentation.theme.AuroraSurfaceLevel
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.aurora.adaptive.AuroraDeviceClass
import eu.kanade.presentation.theme.aurora.adaptive.auroraCenteredMaxWidth
import eu.kanade.presentation.theme.aurora.adaptive.rememberAuroraAdaptiveSpec
import eu.kanade.presentation.theme.resolveAuroraBorderColor
import eu.kanade.presentation.theme.resolveAuroraSurfaceColor
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics

@Composable
internal fun HeroSection(
    hero: HomeHubHero,
    section: HomeHubSection,
    ctaMode: HomeHeroCtaMode,
    onPlayClick: () -> Unit,
    onEntryClick: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val isEInkMode = colors.isEInk
    val actionSpec = remember(section, hero.progressNumber, ctaMode) {
        resolveHomeHubHeroActionSpec(
            section = section,
            progressNumber = hero.progressNumber,
            mode = ctaMode,
        )
    }
    val buttonVisualMode = remember(ctaMode) {
        resolveHomeHubHeroButtonVisualMode(ctaMode)
    }
    val auroraAdaptiveSpec = rememberAuroraAdaptiveSpec()
    val contentMaxWidthDp = auroraAdaptiveSpec.updatesMaxWidthDp ?: auroraAdaptiveSpec.entryMaxWidthDp
    val heroCardShape = RoundedCornerShape(24.dp)
    val overlayGradient = remember(colors, isEInkMode) {
        if (isEInkMode) {
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0.00f to Color.Transparent,
                    0.72f to Color.White.copy(alpha = 0.02f),
                    1.00f to Color.White.copy(alpha = 0.08f),
                ),
            )
        } else {
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0.00f to Color.Transparent,
                    0.40f to Color.Transparent,
                    0.72f to Color.Black.copy(alpha = 0.12f),
                    0.88f to Color.Black.copy(alpha = 0.38f),
                    1.00f to Color.Black.copy(alpha = 0.58f),
                ),
            )
        }
    }
    val eInkTextBackdropBrush = remember(colors, isEInkMode) {
        if (isEInkMode) {
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0.00f to Color.Transparent,
                    0.30f to Color.Transparent,
                    0.56f to Color.White.copy(alpha = 0.14f),
                    0.82f to Color.White.copy(alpha = 0.74f),
                    1.00f to Color.White.copy(alpha = 0.96f),
                ),
            )
        } else {
            resolveAuroraHeroOverlayBrush(colors)
        }
    }
    val readabilityScrim = remember(isEInkMode) {
        if (isEInkMode) {
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0.00f to Color.Transparent,
                    0.68f to Color.Transparent,
                    1.00f to Color.White.copy(alpha = 0.16f),
                ),
            )
        } else {
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0.00f to Color.Transparent,
                    0.58f to Color.Transparent,
                    0.78f to Color.Black.copy(alpha = 0.46f),
                    1.00f to Color.Black.copy(alpha = 0.88f),
                ),
            )
        }
    }
    val heroTextShadow = remember(isEInkMode) {
        if (isEInkMode) {
            Shadow(
                color = Color.Transparent,
                offset = Offset.Zero,
                blurRadius = 0f,
            )
        } else {
            Shadow(
                color = Color.Black.copy(alpha = 0.86f),
                offset = Offset(0f, 2.5f),
                blurRadius = 10f,
            )
        }
    }
    val rimLightBrush = remember(colors) { homeHubRimLightBrush(colors) }
    val actionButtonShape = RoundedCornerShape(16.dp)
    val actionButtonSurfaceSpec = remember(ctaMode, colors.isDark) {
        resolveHomeHubHeroButtonSurfaceSpec(
            mode = ctaMode,
            isDark = colors.isDark,
        )
    }
    val actionButtonHasReadabilityEffects = buttonVisualMode == HomeHubHeroButtonVisualMode.AuroraGlass && !isEInkMode
    val actionButtonLabelShadow = remember(actionButtonHasReadabilityEffects) {
        resolveAuroraCtaLabelShadowSpec(
            enabled = actionButtonHasReadabilityEffects,
        ).toComposeShadow()
    }
    val actionButtonIconShadowSpec = remember(actionButtonHasReadabilityEffects) {
        resolveAuroraHomeIconShadowSpec(enabled = actionButtonHasReadabilityEffects)
    }
    val actionButtonBrush = remember(colors, buttonVisualMode, actionButtonSurfaceSpec, isEInkMode) {
        when (buttonVisualMode) {
            HomeHubHeroButtonVisualMode.ClassicSolid -> SolidColor(colors.accent)
            HomeHubHeroButtonVisualMode.AuroraGlass -> if (isEInkMode) {
                SolidColor(colors.accent)
            } else {
                SolidColor(colors.accent.copy(alpha = actionButtonSurfaceSpec.containerAlpha))
            }
        }
    }
    val actionButtonInnerGlowBrush = remember(colors.accent, actionButtonSurfaceSpec, isEInkMode) {
        if (isEInkMode) {
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0.00f to Color.Transparent,
                    1.00f to Color.Transparent,
                ),
            )
        } else {
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0.00f to Color.Transparent,
                    0.46f to colors.accent.copy(alpha = actionButtonSurfaceSpec.innerGlowAlpha * 0.18f),
                    0.78f to colors.accent.copy(alpha = actionButtonSurfaceSpec.innerGlowAlpha * 0.58f),
                    1.00f to colors.accent.copy(alpha = actionButtonSurfaceSpec.innerGlowAlpha),
                ),
            )
        }
    }
    val actionButtonHighlightBrush = remember(actionButtonSurfaceSpec, isEInkMode) {
        if (isEInkMode) {
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0.00f to Color.Transparent,
                    1.00f to Color.Transparent,
                ),
            )
        } else {
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0.00f to Color.White.copy(alpha = actionButtonSurfaceSpec.highlightAlpha),
                    0.34f to Color.White.copy(alpha = actionButtonSurfaceSpec.highlightAlpha * 0.48f),
                    0.68f to Color.Transparent,
                    1.00f to Color.Transparent,
                ),
            )
        }
    }
    val actionButtonBorderBrush = remember(colors, buttonVisualMode, actionButtonSurfaceSpec, isEInkMode) {
        when (buttonVisualMode) {
            HomeHubHeroButtonVisualMode.ClassicSolid -> if (isEInkMode) {
                SolidColor(Color.White.copy(alpha = actionButtonSurfaceSpec.borderAlpha))
            } else {
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = actionButtonSurfaceSpec.borderAlpha),
                        Color.White.copy(alpha = actionButtonSurfaceSpec.borderAlpha),
                    ),
                )
            }
            HomeHubHeroButtonVisualMode.AuroraGlass -> SolidColor(
                Color.White.copy(alpha = actionButtonSurfaceSpec.borderAlpha),
            )
        }
    }
    val actionButtonElevation = when (buttonVisualMode) {
        HomeHubHeroButtonVisualMode.ClassicSolid -> ButtonDefaults.buttonElevation()
        HomeHubHeroButtonVisualMode.AuroraGlass -> ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            disabledElevation = 0.dp,
        )
    }
    val actionButtonModifier = remember(
        actionButtonBrush,
        actionButtonInnerGlowBrush,
        actionButtonHighlightBrush,
        actionButtonBorderBrush,
        actionButtonShape,
        buttonVisualMode,
        actionButtonSurfaceSpec,
    ) {
        Modifier
            .height(52.dp)
            .clip(actionButtonShape)
            .background(actionButtonBrush)
            .let { baseModifier ->
                if (buttonVisualMode == HomeHubHeroButtonVisualMode.AuroraGlass) {
                    baseModifier
                        .background(actionButtonInnerGlowBrush)
                        .background(actionButtonHighlightBrush)
                } else {
                    baseModifier
                }
            }
            .let { baseModifier ->
                if (actionButtonSurfaceSpec.borderAlpha > 0f) {
                    baseModifier.border(1.dp, actionButtonBorderBrush, actionButtonShape)
                } else {
                    baseModifier
                }
            }
    }
    val actionButtonContentColor = when (buttonVisualMode) {
        HomeHubHeroButtonVisualMode.ClassicSolid -> colors.textOnAccent
        HomeHubHeroButtonVisualMode.AuroraGlass -> if (isEInkMode) colors.textOnAccent else Color.White
    }
    val actionButtonPadding = when (buttonVisualMode) {
        HomeHubHeroButtonVisualMode.ClassicSolid -> {
            androidx.compose.foundation.layout.PaddingValues(start = 22.dp, end = 24.dp, top = 8.dp, bottom = 8.dp)
        }
        HomeHubHeroButtonVisualMode.AuroraGlass -> {
            androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 22.dp, top = 8.dp, bottom = 8.dp)
        }
    }

    Box(
        modifier = Modifier.auroraCenteredMaxWidth(contentMaxWidthDp).height(
            440.dp,
        ).padding(16.dp)
            .clip(heroCardShape)
            .border(width = 1.dp, brush = rimLightBrush, shape = heroCardShape)
            .clickable {
                appHaptics.tap()
                onEntryClick()
            },
    ) {
        val fallbackPainter = rememberThemeAwareCoverErrorPainter(variant = AuroraCoverPlaceholderVariant.Wide)
        AsyncImage(
            model = hero.coverData,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = rememberAuroraPosterColorFilter(),
            modifier = Modifier.fillMaxSize(),
            error = fallbackPainter,
            fallback = fallbackPainter,
        )
        Box(Modifier.fillMaxSize().background(overlayGradient))
        Box(Modifier.fillMaxSize().background(readabilityScrim))

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .then(
                    if (isEInkMode) {
                        Modifier
                            .fillMaxWidth()
                            .background(eInkTextBackdropBrush)
                            .padding(horizontal = 18.dp, vertical = 16.dp)
                    } else {
                        Modifier.padding(24.dp)
                    },
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isEInkMode) {
                OutlinedHeroText(
                    text = hero.title,
                    modifier = Modifier.fillMaxWidth(),
                    baseStyle = TextStyle(
                        fontSize = 28.sp,
                        fontFamily = FontFamily(Font(R.font.montserrat_bold)),
                        lineHeight = 34.sp,
                        lineBreak = LineBreak.Heading,
                        shadow = heroTextShadow,
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    outlineWidth = 4.dp,
                    fillColor = Color.White,
                    outlineColor = Color.Black,
                )
            } else {
                Text(
                    hero.title,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontFamily = FontFamily(Font(R.font.montserrat_bold)),
                    lineHeight = 34.sp,
                    style = TextStyle(lineBreak = LineBreak.Heading, shadow = heroTextShadow),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Box(Modifier.size(6.dp).background(if (isEInkMode) Color.Black else colors.accent, CircleShape))
                Spacer(Modifier.width(8.dp))
                if (isEInkMode) {
                    OutlinedHeroText(
                        text = stringResource(actionSpec.progressLabelRes, (hero.progressNumber % 1000).toInt()),
                        baseStyle = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            shadow = heroTextShadow,
                        ),
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        outlineWidth = 2.dp,
                        fillColor = Color.White,
                        outlineColor = Color.Black,
                    )
                } else {
                    Text(
                        stringResource(actionSpec.progressLabelRes, (hero.progressNumber % 1000).toInt()),
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        style = TextStyle(shadow = heroTextShadow),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Box(
                modifier = Modifier.height(52.dp),
                contentAlignment = Alignment.Center,
            ) {
                Button(
                    onClick = {
                        appHaptics.tap()
                        onPlayClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    elevation = actionButtonElevation,
                    shape = actionButtonShape,
                    contentPadding = actionButtonPadding,
                    modifier = actionButtonModifier,
                ) {
                    val actionIcon = when (actionSpec.icon) {
                        HomeHubHeroActionIcon.Play -> Icons.Filled.PlayArrow
                    }
                    Row(
                        modifier = Modifier.offset(x = (-2).dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(21.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (actionButtonIconShadowSpec.alpha > 0f) {
                                Icon(
                                    imageVector = actionIcon,
                                    contentDescription = null,
                                    tint = Color.Black.copy(alpha = actionButtonIconShadowSpec.alpha),
                                    modifier = Modifier
                                        .size(21.dp)
                                        .offset(
                                            x = actionButtonIconShadowSpec.offsetXDp,
                                            y = actionButtonIconShadowSpec.offsetYDp,
                                        ),
                                )
                            }
                            Icon(
                                imageVector = actionIcon,
                                contentDescription = null,
                                tint = actionButtonContentColor,
                                modifier = Modifier.size(21.dp),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(actionSpec.labelRes),
                            color = actionButtonContentColor,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            style = TextStyle(shadow = actionButtonLabelShadow),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun HeroSectionPlaceholder() {
    val colors = AuroraTheme.colors
    val auroraAdaptiveSpec = rememberAuroraAdaptiveSpec()
    val contentMaxWidthDp = auroraAdaptiveSpec.updatesMaxWidthDp ?: auroraAdaptiveSpec.entryMaxWidthDp
    val placeholderShape = RoundedCornerShape(24.dp)
    val placeholderSurface = if (colors.isEInk) {
        resolveAuroraSurfaceColor(colors, AuroraSurfaceLevel.Strong)
    } else {
        colors.cardBackground
    }

    Box(
        modifier = Modifier
            .auroraCenteredMaxWidth(contentMaxWidthDp)
            .height(440.dp)
            .padding(16.dp)
            .clip(placeholderShape)
            .background(placeholderSurface)
            .border(1.dp, resolveAuroraBorderColor(colors, emphasized = colors.isEInk), placeholderShape),
    )
}

@Composable
private fun OutlinedHeroText(
    text: String,
    baseStyle: TextStyle,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    outlineWidth: Dp,
    fillColor: Color,
    outlineColor: Color,
) {
    val strokeWidthPx = with(LocalDensity.current) { outlineWidth.toPx() }
    val strokeStyle = baseStyle.copy(
        color = outlineColor,
        drawStyle = Stroke(width = strokeWidthPx),
    )
    val fillStyle = baseStyle.copy(color = fillColor)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = strokeStyle,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow,
        )
        Text(
            text = text,
            style = fillStyle,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow,
        )
    }
}

@Composable
internal fun QuickSourceButton(sourceName: String?, onClick: () -> Unit) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val auroraAdaptiveSpec = rememberAuroraAdaptiveSpec()
    val contentMaxWidthDp = auroraAdaptiveSpec.updatesMaxWidthDp ?: auroraAdaptiveSpec.entryMaxWidthDp
    val sourceButtonShape = RoundedCornerShape(16.dp)
    val sourceSurface = when {
        colors.isEInk -> resolveAuroraSurfaceColor(colors, AuroraSurfaceLevel.Strong)
        colors.background.luminance() < 0.5f -> {
            Color.White.copy(alpha = 0.05f)
        }
        else -> resolveAuroraSurfaceColor(colors, AuroraSurfaceLevel.Strong)
    }
    val sourceBorderBrush = remember(colors) { auroraMenuRimLightBrush(colors) }
    val sourceShadowElevation = when {
        colors.isEInk -> 4.dp
        colors.isDark -> 8.dp
        else -> 2.dp
    }
    val sourceAmbientShadow = when {
        colors.isEInk -> Color.Black.copy(alpha = 0.06f)
        colors.isDark -> Color.Black.copy(alpha = 0.08f)
        else -> Color.Black.copy(alpha = 0.03f)
    }
    val sourceSpotShadow = when {
        colors.isEInk -> Color.Black.copy(alpha = 0.08f)
        colors.isDark -> Color.Black.copy(alpha = 0.12f)
        else -> Color.Black.copy(alpha = 0.05f)
    }
    val sourceBorderWidth = if (colors.isDark) 0.75.dp else 1.dp

    Box(
        modifier = Modifier
            .auroraCenteredMaxWidth(contentMaxWidthDp)
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Button(
            onClick = {
                appHaptics.tap()
                onClick()
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            shape = sourceButtonShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .shadow(
                    elevation = sourceShadowElevation,
                    shape = sourceButtonShape,
                    ambientColor = sourceAmbientShadow,
                    spotColor = sourceSpotShadow,
                )
                .clip(sourceButtonShape)
                .background(sourceSurface)
                .border(sourceBorderWidth, sourceBorderBrush, sourceButtonShape),
        ) {
            Icon(Icons.Filled.Search, null, tint = colors.accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = sourceName ?: stringResource(AYMR.strings.aurora_open_source),
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun HomeHubRecentCard(
    mode: HomeHubRecentCardRenderMode,
    title: String,
    coverData: Any? = null,
    subtitle: String? = null,
    deviceClass: AuroraDeviceClass,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (mode) {
        HomeHubRecentCardRenderMode.ClassicAuroraCard -> {
            AuroraCard(
                modifier = modifier.aspectRatio(0.68f),
                title = title,
                coverData = coverData,
                subtitle = subtitle,
                onClick = onClick,
                imagePadding = 6.dp,
            )
        }
        HomeHubRecentCardRenderMode.AuroraPoster -> {
            HomeHubRecentPosterCard(
                modifier = modifier,
                title = title,
                coverData = coverData,
                subtitle = subtitle,
                deviceClass = deviceClass,
                onClick = onClick,
            )
        }
    }
}

@Composable
internal fun HomeHubRecentPosterCard(
    title: String,
    coverData: Any?,
    subtitle: String? = null,
    deviceClass: AuroraDeviceClass,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val posterSpec = remember(deviceClass) {
        resolveHomeHubRecentPosterCardSpec(deviceClass)
    }
    val surfaceSpec = remember(colors.isDark) {
        resolveHomeHubRecentPosterSurfaceSpec(colors.isDark)
    }
    val cardShape = RoundedCornerShape(18.dp)
    val posterShape = RoundedCornerShape(16.dp)
    val fallbackPainter = rememberThemeAwareCoverErrorPainter(
        variant = AuroraCoverPlaceholderVariant.Portrait,
    )
    val outerSurface = if (colors.isDark) {
        colors.glass.copy(alpha = surfaceSpec.containerAlpha)
    } else {
        resolveAuroraSurfaceColor(colors, AuroraSurfaceLevel.Glass)
    }
    val posterSurface = if (colors.isDark) {
        colors.cardBackground.copy(alpha = surfaceSpec.posterAlpha)
    } else {
        resolveAuroraSurfaceColor(colors, AuroraSurfaceLevel.Subtle)
    }

    Column(
        modifier = modifier
            .clip(cardShape)
            .clickable {
                appHaptics.tap()
                onClick()
            }
            .background(outerSurface)
            .padding(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(posterSpec.posterAspectRatio)
                .clip(posterShape)
                .background(posterSurface)
                .border(
                    width = 1.dp,
                    color = if (colors.isDark) {
                        Color.White.copy(alpha = 0.06f)
                    } else {
                        Color.Black.copy(alpha = 0.04f)
                    },
                    shape = posterShape,
                ),
        ) {
            AsyncImage(
                model = resolveAuroraCoverModel(coverData),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = rememberAuroraPosterColorFilter(),
                modifier = Modifier.fillMaxSize(),
                error = fallbackPainter,
                fallback = fallbackPainter,
            )
        }

        Spacer(Modifier.height(posterSpec.textTopSpacingDp.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = posterSpec.textBlockMinHeightDp.dp)
                .padding(horizontal = posterSpec.textHorizontalPaddingDp.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = posterSpec.titleMaxLines,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp,
            )

            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
