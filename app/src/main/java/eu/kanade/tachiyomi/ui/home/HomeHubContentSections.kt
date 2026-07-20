package eu.kanade.tachiyomi.ui.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import eu.kanade.presentation.components.CoverReloadSignal
import eu.kanade.presentation.components.auroraMenuRimLightBrush
import eu.kanade.presentation.components.buildAuroraCoverImageRequest
import eu.kanade.presentation.components.rememberThemeAwareCoverErrorPainter
import eu.kanade.presentation.components.resolveAuroraCtaLabelShadowSpec
import eu.kanade.presentation.components.resolveAuroraHomeIconShadowSpec
import eu.kanade.presentation.components.resolveAuroraTabContainerColor
import eu.kanade.presentation.components.toComposeShadow
import eu.kanade.presentation.entries.components.aurora.AuroraGlassCtaSurface
import eu.kanade.presentation.entries.components.aurora.AuroraHeroCtaMode
import eu.kanade.presentation.entries.components.aurora.rememberAuroraPosterColorFilter
import eu.kanade.presentation.entries.components.aurora.resolveAuroraHeroOverlayBrush
import eu.kanade.presentation.more.settings.auroraCardStyle
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

@androidx.compose.runtime.Immutable
internal class HomeHeroVisuals(
    val overlayGradient: Brush,
    val textBackdropBrush: Brush,
    val readabilityScrim: Brush,
    val rimLightBrush: Brush,
    val textShadow: Shadow,
)

@Composable
internal fun rememberHomeHeroVisuals(
    colors: eu.kanade.presentation.theme.AuroraColors,
    isEInkMode: Boolean,
): HomeHeroVisuals {
    return remember(colors, isEInkMode) {
        val overlayGradient = if (isEInkMode) {
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

        val eInkTextBackdropBrush = if (isEInkMode) {
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

        val readabilityScrim = if (isEInkMode) {
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

        val heroTextShadow = if (isEInkMode) {
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

        val rimLightBrush = homeHubRimLightBrush(colors)

        HomeHeroVisuals(
            overlayGradient = overlayGradient,
            textBackdropBrush = eInkTextBackdropBrush,
            readabilityScrim = readabilityScrim,
            rimLightBrush = rimLightBrush,
            textShadow = heroTextShadow,
        )
    }
}

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
    val auroraAdaptiveSpec = rememberAuroraAdaptiveSpec()
    val contentMaxWidthDp = auroraAdaptiveSpec.updatesMaxWidthDp ?: auroraAdaptiveSpec.entryMaxWidthDp
    val heroCardShape = RoundedCornerShape(20.dp)

    val visuals = rememberHomeHeroVisuals(colors = colors, isEInkMode = isEInkMode)

    val heroInteractionSource = remember { MutableInteractionSource() }
    val isHeroPressed by heroInteractionSource.collectIsPressedAsState()
    val heroElevation by animateDpAsState(
        targetValue = if (isHeroPressed) 8.dp else 4.dp,
        label = "heroElevation",
    )

    Box(
        modifier = Modifier
            .auroraCenteredMaxWidth(contentMaxWidthDp)
            .height(440.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .clip(heroCardShape)
            .then(
                if (colors.isDark || colors.isEInk) {
                    Modifier.border(width = 1.dp, brush = visuals.rimLightBrush, shape = heroCardShape)
                } else {
                    Modifier.shadow(
                        elevation = heroElevation,
                        shape = heroCardShape,
                        ambientColor = Color.Black.copy(alpha = 0.04f),
                        spotColor = Color.Black.copy(alpha = 0.10f),
                    )
                },
            )
            .clickable(
                interactionSource = heroInteractionSource,
                indication = null,
            ) {
                appHaptics.tap()
                onEntryClick()
            },
    ) {
        val fallbackPainter = rememberThemeAwareCoverErrorPainter(variant = AuroraCoverPlaceholderVariant.Wide)
        val heroContext = LocalContext.current
        val heroCoverReloadTick = CoverReloadSignal.tick.value
        val heroCoverRequest = remember(heroContext, hero.coverData, heroCoverReloadTick) {
            buildAuroraCoverImageRequest(heroContext, hero.coverData)
        }
        AsyncImage(
            model = heroCoverRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = rememberAuroraPosterColorFilter(),
            modifier = Modifier.fillMaxSize(),
            error = fallbackPainter,
            fallback = fallbackPainter,
        )
        Box(Modifier.fillMaxSize().background(visuals.overlayGradient))
        Box(Modifier.fillMaxSize().background(visuals.readabilityScrim))

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .then(
                    if (isEInkMode) {
                        Modifier
                            .fillMaxWidth()
                            .background(visuals.textBackdropBrush)
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    baseStyle = TextStyle(
                        fontSize = 28.sp,
                        fontFamily = FontFamily(Font(R.font.montserrat_bold)),
                        lineHeight = 34.sp,
                        lineBreak = LineBreak.Heading,
                        shadow = visuals.textShadow,
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
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = Color.White,
                    fontSize = 28.sp,
                    fontFamily = FontFamily(Font(R.font.montserrat_bold)),
                    lineHeight = 34.sp,
                    style = TextStyle(lineBreak = LineBreak.Heading, shadow = visuals.textShadow),
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
                        text = stringResource(actionSpec.progressLabelRes, hero.progressNumber.toInt()),
                        baseStyle = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            shadow = visuals.textShadow,
                        ),
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        outlineWidth = 2.dp,
                        fillColor = Color.White,
                        outlineColor = Color.Black,
                    )
                } else {
                    Text(
                        stringResource(actionSpec.progressLabelRes, hero.progressNumber.toInt()),
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        style = TextStyle(shadow = visuals.textShadow),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            val buttonInteractionSource = remember { MutableInteractionSource() }
            val ctaPresentationMode = remember(ctaMode) {
                when (ctaMode) {
                    HomeHeroCtaMode.Aurora -> AuroraHeroCtaMode.Aurora
                    HomeHeroCtaMode.Classic -> AuroraHeroCtaMode.Classic
                }
            }

            AuroraGlassCtaSurface(
                mode = ctaPresentationMode,
                onClick = {
                    appHaptics.tap()
                    onPlayClick()
                },
                modifier = Modifier.height(52.dp),
                isHome = true,
                shape = CircleShape,
                contentPadding = if (ctaMode == HomeHeroCtaMode.Classic) {
                    PaddingValues(start = 22.dp, end = 24.dp, top = 8.dp, bottom = 8.dp)
                } else {
                    PaddingValues(start = 20.dp, end = 22.dp, top = 8.dp, bottom = 8.dp)
                },
                interactionSource = buttonInteractionSource,
            ) { contentColor ->
                val actionIcon = when (actionSpec.icon) {
                    HomeHubHeroActionIcon.Play -> Icons.Filled.PlayArrow
                }
                Row(
                    modifier = Modifier.offset(x = (-2).dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val actionButtonHasReadabilityEffects =
                        ctaPresentationMode == AuroraHeroCtaMode.Aurora && !isEInkMode
                    val actionButtonIconShadowSpec = remember(actionButtonHasReadabilityEffects) {
                        resolveAuroraHomeIconShadowSpec(enabled = actionButtonHasReadabilityEffects)
                    }
                    val actionButtonLabelShadow = remember(actionButtonHasReadabilityEffects) {
                        resolveAuroraCtaLabelShadowSpec(
                            enabled = actionButtonHasReadabilityEffects,
                        ).toComposeShadow()
                    }

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
                            tint = contentColor,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(actionSpec.labelRes),
                        color = contentColor,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(shadow = actionButtonLabelShadow),
                    )
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
    val sourceButtonShape = CircleShape
    val isLightTheme = !colors.isDark && !colors.isEInk
    val tabContainerColor = resolveAuroraTabContainerColor(colors)
    val quickSourceBorderBrush = remember(colors) {
        if (colors.isDark && !colors.isEInk) {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.08f),
                    Color.Transparent,
                ),
            )
        } else {
            auroraMenuRimLightBrush(colors)
        }
    }
    val sourceBorderBrush = remember(colors) { auroraMenuRimLightBrush(colors) }
    val sourceShowBorder = colors.isDark || colors.isEInk

    Card(
        modifier = Modifier
            .auroraCenteredMaxWidth(contentMaxWidthDp)
            .padding(horizontal = 16.dp, vertical = if (isLightTheme) 12.dp else 10.dp)
            .then(
                if (isLightTheme) {
                    Modifier
                        .drawBehind {
                            val radius = size.height / 2f
                            val cornerRadius = CornerRadius(radius, radius)

                            val neutralOffsetY = 3.dp.toPx()
                            val warmOffsetY = 5.dp.toPx()

                            val neutralInset = 1.dp.toPx()
                            val warmInset = 3.dp.toPx()

                            // 1. Neutral shadow
                            drawRoundRect(
                                color = Color.Black.copy(alpha = 0.035f),
                                topLeft = Offset(x = neutralInset, y = neutralOffsetY),
                                size = Size(width = size.width - neutralInset * 2, height = size.height),
                                cornerRadius = cornerRadius,
                            )

                            // 2. Accent glow
                            drawRoundRect(
                                color = colors.accent.copy(alpha = 0.025f),
                                topLeft = Offset(x = warmInset, y = warmOffsetY),
                                size = Size(width = size.width - warmInset * 2, height = size.height),
                                cornerRadius = cornerRadius,
                            )
                        }
                        .background(
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.78f),
                                    Color.White.copy(alpha = 0.68f),
                                    Color.White.copy(alpha = 0.60f),
                                ),
                            ),
                            shape = sourceButtonShape,
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.75f),
                                    Color.White.copy(alpha = 0.28f),
                                    Color.White.copy(alpha = 0.12f),
                                ),
                            ),
                            shape = sourceButtonShape,
                        )
                } else if (colors.isDark && !colors.isEInk) {
                    Modifier
                        .auroraCardStyle(
                            colors = colors,
                            shape = sourceButtonShape,
                            applyDarkRimLight = false,
                            applyDarkShadow = false,
                        )
                        .border(
                            BorderStroke(1.dp, quickSourceBorderBrush),
                            shape = sourceButtonShape,
                        )
                } else {
                    Modifier
                },
            )
            .clickable {
                appHaptics.tap()
                onClick()
            },
        shape = sourceButtonShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isLightTheme) Color.Transparent else tabContainerColor,
        ),
        border = if (sourceShowBorder && !colors.isDark) {
            BorderStroke(0.75.dp, sourceBorderBrush)
        } else {
            null
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, null, tint = colors.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = sourceName ?: stringResource(AYMR.strings.aurora_open_source),
                color = colors.textPrimary,
                fontSize = 13.sp,
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
    val isLightTheme = !colors.isDark && !colors.isEInk
    val outerSurface = if (colors.isDark) {
        colors.glass.copy(alpha = surfaceSpec.containerAlpha)
    } else if (colors.isEInk) {
        resolveAuroraSurfaceColor(colors, AuroraSurfaceLevel.Glass)
    } else {
        Color.Transparent
    }
    val posterSurface = if (colors.isDark) {
        colors.cardBackground.copy(alpha = surfaceSpec.posterAlpha)
    } else {
        resolveAuroraSurfaceColor(colors, AuroraSurfaceLevel.Subtle)
    }

    val cardContent: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(6.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(posterSpec.posterAspectRatio)
                    .clip(posterShape)
                    .background(posterSurface)
                    .then(
                        if (colors.isDark || colors.isEInk) {
                            Modifier.border(
                                width = 1.dp,
                                color = if (colors.isDark) {
                                    Color.White.copy(alpha = 0.06f)
                                } else {
                                    Color.Black.copy(alpha = 0.04f)
                                },
                                shape = posterShape,
                            )
                        } else {
                            Modifier
                        },
                    ),
            ) {
                val posterContext = LocalContext.current
                val posterCoverReloadTick = CoverReloadSignal.tick.value
                val posterCoverRequest = remember(posterContext, coverData, posterCoverReloadTick) {
                    buildAuroraCoverImageRequest(posterContext, coverData)
                }
                AsyncImage(
                    model = posterCoverRequest,
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

    if (isLightTheme) {
        Box(
            modifier = modifier
                .drawBehind {
                    val radius = 18.dp.toPx()
                    val cornerRadius = CornerRadius(radius, radius)
                    val neutralOffsetY = 3.dp.toPx()
                    val warmOffsetY = 5.dp.toPx()
                    val neutralInset = 1.dp.toPx()
                    val warmInset = 3.dp.toPx()

                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.035f),
                        topLeft = Offset(x = neutralInset, y = neutralOffsetY),
                        size = Size(width = size.width - neutralInset * 2, height = size.height),
                        cornerRadius = cornerRadius,
                    )
                    drawRoundRect(
                        color = colors.accent.copy(alpha = 0.025f),
                        topLeft = Offset(x = warmInset, y = warmOffsetY),
                        size = Size(width = size.width - warmInset * 2, height = size.height),
                        cornerRadius = cornerRadius,
                    )
                }
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.78f),
                            Color.White.copy(alpha = 0.68f),
                            Color.White.copy(alpha = 0.60f),
                        ),
                    ),
                    shape = cardShape,
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.75f),
                            Color.White.copy(alpha = 0.28f),
                            Color.White.copy(alpha = 0.12f),
                        ),
                    ),
                    shape = cardShape,
                )
                .clip(cardShape)
                .clickable {
                    appHaptics.tap()
                    onClick()
                },
        ) {
            cardContent()
        }
    } else {
        Card(
            onClick = {
                appHaptics.tap()
                onClick()
            },
            modifier = modifier,
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = outerSurface),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
            ),
        ) {
            cardContent()
        }
    }
}
