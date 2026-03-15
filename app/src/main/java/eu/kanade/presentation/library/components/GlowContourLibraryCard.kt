package eu.kanade.presentation.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.PathParser
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.rememberAuroraCoverPlaceholderPainter
import eu.kanade.presentation.components.resolveAuroraCardOverlaySpec
import eu.kanade.presentation.components.resolveAuroraCoverModel
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.LocalCoverTitleFontFamily
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import android.graphics.Matrix as AndroidMatrix

private const val GLOW_CONTOUR_SVG_WIDTH = 256f
private const val GLOW_CONTOUR_SVG_HEIGHT = 269f
private val GLOW_CONTOUR_UNIFIED_OUTER_SHAPE = RoundedCornerShape(20.dp)
private val GLOW_CONTOUR_UNIFIED_TOP_CLIP_SHAPE = RoundedCornerShape(
    topStart = 20.dp,
    topEnd = 20.dp,
    bottomStart = 0.dp,
    bottomEnd = 0.dp,
)
private val GLOW_CONTOUR_UNIFIED_TEXT_BLOCK_SHAPE = RoundedCornerShape(
    topStart = 20.dp,
    topEnd = 20.dp,
    bottomStart = 20.dp,
    bottomEnd = 20.dp,
)

private const val GLOW_CONTOUR_SHELL_PATH_DATA =
    "m210 3.43h-166.8c-23.41 0-41.57 20.48-41.57 43.58v176.2c0 23.63 18.43 42.17 42.65 42.17h63.45l0.68-0.12h101.8c23.68 0 43.99-20.54 43.99-42.26v-176.9c0-23.46-18.94-42.71-44.26-42.71z"

private const val GLOW_CONTOUR_ACCENT_PATH_DATA =
    "m254.2 105.9c-1.79 11.11-3.11 27.04-12.48 34.11-4.76 3.73-10.65 4.2-15.45 10.32-4.1 5.21-7.31 10.75-14.18 15.32-9.3 6.12-19.27 7.49-35.5 7.39-18.43-0.12-31.7 4.61-39.89 16.87-9 13.43-8.53 31.31-21.41 46.79-7.6 9.24-17.1 12.01-28.39 9.4-7.76-1.89-11.49-4.66-20.83-3.1-7.18 1.24-11.55 6.85-23.03 8.69-14.68 2.55-29.98-3.98-38.92-16.43-1.13-1.6-1.63-4.02-2.07-5.46 3.94 20.96 20.69 35.62 42.54 35.62h63.6l0.57-0.12h101.4c23.68 0 43.99-20.54 43.99-42.26v-117.1z"

private const val GLOW_CONTOUR_PROGRESS_PATH_DATA =
    "m254.2 105.9c-1.7 9.94-2.11 27.19-11.32 36.03-6.78 6.62-14.13 5.79-18.99 17.11-5.99 14.03-15.9 24-38.26 32.1-18.46 7.18-24.93 13.52-30.64 35.98-6.05 24.08-17.94 35.4-44.73 38.18h99.95c23.68 0 43.99-20.54 43.99-42.26v-117.1z"

internal enum class GlowContourZoneDerivation {
    SHELL_MINUS_ACCENT,
    ACCENT_MINUS_PROGRESS,
    PROGRESS,
}

internal data class GlowContourZoneLayerSpec(
    val svgWidth: Float,
    val svgHeight: Float,
    val shellPathData: String,
    val accentPathData: String,
    val progressPathData: String,
    val posterDerivation: GlowContourZoneDerivation,
    val accentDerivation: GlowContourZoneDerivation,
    val progressDerivation: GlowContourZoneDerivation,
)

internal fun resolveGlowContourZoneLayerSpec(): GlowContourZoneLayerSpec {
    return GlowContourZoneLayerSpec(
        svgWidth = GLOW_CONTOUR_SVG_WIDTH,
        svgHeight = GLOW_CONTOUR_SVG_HEIGHT,
        shellPathData = GLOW_CONTOUR_SHELL_PATH_DATA,
        accentPathData = GLOW_CONTOUR_ACCENT_PATH_DATA,
        progressPathData = GLOW_CONTOUR_PROGRESS_PATH_DATA,
        posterDerivation = GlowContourZoneDerivation.SHELL_MINUS_ACCENT,
        accentDerivation = GlowContourZoneDerivation.ACCENT_MINUS_PROGRESS,
        progressDerivation = GlowContourZoneDerivation.PROGRESS,
    )
}

private val DEFAULT_GLOW_CONTOUR_ZONE_LAYER_SPEC = resolveGlowContourZoneLayerSpec()

private object GlowContourCardShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        return Outline.Generic(
            createScaledGlowContourPath(
                pathData = DEFAULT_GLOW_CONTOUR_ZONE_LAYER_SPEC.shellPathData,
                size = size,
                svgWidth = DEFAULT_GLOW_CONTOUR_ZONE_LAYER_SPEC.svgWidth,
                svgHeight = DEFAULT_GLOW_CONTOUR_ZONE_LAYER_SPEC.svgHeight,
            ),
        )
    }
}

private fun createScaledGlowContourPath(
    pathData: String,
    size: Size,
    svgWidth: Float,
    svgHeight: Float,
): Path {
    val androidPath = PathParser.createPathFromPathData(pathData) ?: android.graphics.Path()
    val matrix = AndroidMatrix().apply {
        setScale(size.width / svgWidth, size.height / svgHeight)
    }
    androidPath.transform(matrix)
    return androidPath.asComposePath()
}

private data class GlowContourZonedPaths(
    val shellPath: Path,
    val accentBasePath: Path,
    val posterPath: Path,
    val accentPath: Path,
    val progressPath: Path,
)

private fun deriveGlowContourZonePath(
    derivation: GlowContourZoneDerivation,
    shellPath: Path,
    accentBasePath: Path,
    progressPath: Path,
): Path {
    return when (derivation) {
        GlowContourZoneDerivation.SHELL_MINUS_ACCENT -> Path.combine(
            operation = PathOperation.Difference,
            path1 = shellPath,
            path2 = accentBasePath,
        )
        GlowContourZoneDerivation.ACCENT_MINUS_PROGRESS -> Path.combine(
            operation = PathOperation.Difference,
            path1 = accentBasePath,
            path2 = progressPath,
        )
        GlowContourZoneDerivation.PROGRESS -> progressPath
    }
}

internal sealed interface GlowContourFooterContent {
    data object ContinueAction : GlowContourFooterContent
    data class ProgressPercent(val value: Int) : GlowContourFooterContent
    data object None : GlowContourFooterContent
}

internal data class GlowContourUnifiedBlendSpec(
    val topCardBackgroundAlpha: Float,
    val topCarryGlowAlpha: Float,
    val textTopFadeSurfaceAlpha: Float,
    val textBaseSurfaceAlpha: Float,
    val textTopGlowAlpha: Float,
)

internal fun resolveGlowContourUnifiedBlendSpec(isDark: Boolean): GlowContourUnifiedBlendSpec {
    return if (isDark) {
        GlowContourUnifiedBlendSpec(
            topCardBackgroundAlpha = 0f,
            topCarryGlowAlpha = 0.18f,
            textTopFadeSurfaceAlpha = 0.06f,
            textBaseSurfaceAlpha = 0.18f,
            textTopGlowAlpha = 0.2f,
        )
    } else {
        GlowContourUnifiedBlendSpec(
            topCardBackgroundAlpha = 0f,
            topCarryGlowAlpha = 0.12f,
            textTopFadeSurfaceAlpha = 0.04f,
            textBaseSurfaceAlpha = 0.12f,
            textTopGlowAlpha = 0.12f,
        )
    }
}

internal fun resolveGlowContourFooterContent(
    progressPercent: Int?,
    onClickContinueViewing: (() -> Unit)?,
): GlowContourFooterContent {
    return when {
        onClickContinueViewing != null -> GlowContourFooterContent.ContinueAction
        progressPercent != null -> GlowContourFooterContent.ProgressPercent(progressPercent)
        else -> GlowContourFooterContent.None
    }
}

private fun createGlowContourZonedPaths(
    size: Size,
    spec: GlowContourZoneLayerSpec = DEFAULT_GLOW_CONTOUR_ZONE_LAYER_SPEC,
): GlowContourZonedPaths {
    val shellPath = createScaledGlowContourPath(
        pathData = spec.shellPathData,
        size = size,
        svgWidth = spec.svgWidth,
        svgHeight = spec.svgHeight,
    )
    val accentBasePath = Path.combine(
        operation = PathOperation.Intersect,
        path1 = createScaledGlowContourPath(
            pathData = spec.accentPathData,
            size = size,
            svgWidth = spec.svgWidth,
            svgHeight = spec.svgHeight,
        ),
        path2 = shellPath,
    )
    val progressPath = Path.combine(
        operation = PathOperation.Intersect,
        path1 = createScaledGlowContourPath(
            pathData = spec.progressPathData,
            size = size,
            svgWidth = spec.svgWidth,
            svgHeight = spec.svgHeight,
        ),
        path2 = shellPath,
    )
    val accentPath = deriveGlowContourZonePath(
        derivation = spec.accentDerivation,
        shellPath = shellPath,
        accentBasePath = accentBasePath,
        progressPath = progressPath,
    )
    val posterPath = deriveGlowContourZonePath(
        derivation = spec.posterDerivation,
        shellPath = shellPath,
        accentBasePath = accentBasePath,
        progressPath = progressPath,
    )
    val resolvedProgressPath = deriveGlowContourZonePath(
        derivation = spec.progressDerivation,
        shellPath = shellPath,
        accentBasePath = accentBasePath,
        progressPath = progressPath,
    )

    return GlowContourZonedPaths(
        shellPath = shellPath,
        accentBasePath = accentBasePath,
        posterPath = posterPath,
        accentPath = accentPath,
        progressPath = resolvedProgressPath,
    )
}

@Composable
fun GlowContourLibraryGridItem(
    title: String,
    subtitle: String?,
    coverData: Any?,
    progressPercent: Int?,
    cardAspectRatio: Float,
    modifier: Modifier = Modifier,
    textSpec: GlowContourLibraryTextSpec,
    badge: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onClickContinueViewing: (() -> Unit)? = null,
    isSelected: Boolean = false,
    gridColumns: Int? = null,
) {
    val colors = AuroraTheme.colors
    val blendSpec = resolveGlowContourUnifiedBlendSpec(colors.isDark)
    val isUnifiedTextContainer = textSpec.showTextBlock && textSpec.useUnifiedContainer
    val itemShape = if (isUnifiedTextContainer) {
        GLOW_CONTOUR_UNIFIED_OUTER_SHAPE
    } else {
        RoundedCornerShape(12.dp)
    }
    val itemBorderColor = if (isSelected) {
        colors.accent.copy(alpha = 0.92f)
    } else if (colors.isDark) {
        Color.White.copy(alpha = 0.06f)
    } else {
        Color.LightGray.copy(alpha = 0.36f)
    }
    val itemBorderWidth = if (isSelected) 2.dp else 1.dp
    val itemModifier = modifier.combinedClickable(
        onClick = onClick,
        onLongClick = onLongClick,
    )

    if (isUnifiedTextContainer) {
        Column(
            modifier = itemModifier
                .fillMaxWidth()
                .clip(itemShape)
                .border(
                    width = itemBorderWidth,
                    color = itemBorderColor,
                    shape = itemShape,
                ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            GlowContourLibraryCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(cardAspectRatio),
                coverData = coverData,
                progressPercent = progressPercent,
                badge = badge,
                isSelected = false,
                isUnifiedContainerMode = true,
                blendSpec = blendSpec,
                onClickContinueViewing = onClickContinueViewing,
                gridColumns = gridColumns,
            )
            GlowContourLibraryTextBlock(
                title = title,
                subtitle = subtitle,
                textSpec = textSpec,
                blendSpec = blendSpec,
                isUnifiedContainerMode = true,
            )
        }
    } else {
        Column(
            modifier = itemModifier,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            GlowContourLibraryCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(cardAspectRatio),
                coverData = coverData,
                progressPercent = progressPercent,
                badge = badge,
                isSelected = isSelected,
                isUnifiedContainerMode = false,
                blendSpec = blendSpec,
                onClickContinueViewing = onClickContinueViewing,
                gridColumns = gridColumns,
            )

            if (textSpec.showTextBlock) {
                GlowContourLibraryTextBlock(
                    title = title,
                    subtitle = subtitle,
                    textSpec = textSpec,
                    blendSpec = blendSpec,
                    isUnifiedContainerMode = false,
                )
            }
        }
    }
}

@Composable
private fun GlowContourLibraryTextBlock(
    title: String,
    subtitle: String?,
    textSpec: GlowContourLibraryTextSpec,
    blendSpec: GlowContourUnifiedBlendSpec,
    isUnifiedContainerMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val coverTitleFontFamily = LocalCoverTitleFontFamily.current
    val containerModifier = if (isUnifiedContainerMode) {
        modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .clip(GLOW_CONTOUR_UNIFIED_TEXT_BLOCK_SHAPE)
    } else {
        modifier.fillMaxWidth()
    }

    Column(
        modifier = containerModifier
            .drawWithCache {
                val textSurfaceBrush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.34f to colors.surface.copy(alpha = blendSpec.textTopFadeSurfaceAlpha),
                        1f to colors.surface.copy(alpha = blendSpec.textBaseSurfaceAlpha),
                    ),
                    startY = 0f,
                    endY = size.height,
                )
                val textCarryGlowBrush = Brush.verticalGradient(
                    colors = listOf(
                        colors.gradientPurple.copy(alpha = blendSpec.textTopGlowAlpha),
                        colors.progressCyan.copy(alpha = blendSpec.textTopGlowAlpha * 0.78f),
                        Color.Transparent,
                    ),
                    startY = 0f,
                    endY = size.height * 0.62f,
                )
                onDrawBehind {
                    drawRect(brush = textSurfaceBrush)
                    drawRect(brush = textCarryGlowBrush)
                }
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            color = colors.textPrimary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            minLines = if (textSpec.titleMaxLines > 1) 2 else 1,
            maxLines = textSpec.titleMaxLines,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                fontFamily = coverTitleFontFamily,
                lineBreak = LineBreak.Heading,
                hyphens = Hyphens.None,
            ),
        )
        if (!subtitle.isNullOrBlank() && textSpec.subtitleMaxLines > 0) {
            Text(
                text = subtitle,
                color = colors.textSecondary.copy(alpha = 0.8f),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = textSpec.subtitleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GlowContourLibraryCard(
    coverData: Any?,
    progressPercent: Int?,
    badge: @Composable (() -> Unit)?,
    isSelected: Boolean,
    isUnifiedContainerMode: Boolean,
    blendSpec: GlowContourUnifiedBlendSpec,
    onClickContinueViewing: (() -> Unit)?,
    gridColumns: Int?,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val placeholderPainter = rememberAuroraCoverPlaceholderPainter()
    val footerContent = resolveGlowContourFooterContent(
        progressPercent = progressPercent,
        onClickContinueViewing = onClickContinueViewing,
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .drawWithCache {
                val zones = createGlowContourZonedPaths(size)
                val selectedStrokeBrush = Brush.horizontalGradient(
                    colors = listOf(
                        colors.accent.copy(alpha = 0.92f),
                        colors.progressCyan.copy(alpha = 0.88f),
                    ),
                    startX = zones.shellPath.getBounds().left,
                    endX = size.width,
                )

                onDrawWithContent {
                    drawContent()

                    if (isSelected) {
                        drawPath(
                            path = zones.shellPath,
                            brush = selectedStrokeBrush,
                            alpha = 0.95f,
                            style = Stroke(width = 2.2.dp.toPx()),
                        )
                    }
                }
            }
            .background(
                colors.surface.copy(
                    alpha = if (isUnifiedContainerMode) {
                        blendSpec.topCardBackgroundAlpha
                    } else if (colors.isDark) {
                        0.18f
                    } else {
                        0.08f
                    },
                ),
            )
            .clip(GlowContourCardShape)
            .then(
                if (isUnifiedContainerMode) {
                    Modifier.clip(GLOW_CONTOUR_UNIFIED_TOP_CLIP_SHAPE)
                } else {
                    Modifier
                },
            ),
    ) {
        val overlaySpec = resolveAuroraCardOverlaySpec(
            gridColumns = gridColumns,
            cardWidthDp = maxWidth.value,
        )

        AsyncImage(
            model = resolveAuroraCoverModel(coverData),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .drawWithCache {
                    val posterClipPath = createGlowContourZonedPaths(size).posterPath
                    onDrawWithContent {
                        clipPath(posterClipPath) {
                            this@onDrawWithContent.drawContent()
                        }
                    }
                },
            error = placeholderPainter,
            fallback = placeholderPainter,
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithCache {
                    val zones = createGlowContourZonedPaths(size)
                    val accentBounds = zones.accentPath.getBounds()
                    val progressBounds = zones.progressPath.getBounds()
                    val accentGlassBrush = Brush.verticalGradient(
                        colors = listOf(
                            colors.surface.copy(alpha = if (colors.isDark) 0.28f else 0.2f),
                            colors.glass.copy(alpha = if (colors.isDark) 0.45f else 0.3f),
                            colors.surface.copy(alpha = if (colors.isDark) 0.58f else 0.42f),
                        ),
                        startY = accentBounds.top,
                        endY = accentBounds.bottom,
                    )
                    val progressGlassBrush = Brush.verticalGradient(
                        colors = listOf(
                            colors.surface.copy(alpha = if (colors.isDark) 0.72f else 0.54f),
                            colors.glass.copy(alpha = if (colors.isDark) 0.84f else 0.7f),
                            colors.surface.copy(alpha = if (colors.isDark) 0.94f else 0.82f),
                        ),
                        startY = progressBounds.top,
                        endY = progressBounds.bottom,
                    )
                    val dividerGlowBrush = Brush.horizontalGradient(
                        colors = listOf(
                            colors.gradientPurple.copy(alpha = 0.92f),
                            colors.glowEffect.copy(alpha = 1f),
                            colors.progressCyan.copy(alpha = 0.92f),
                        ),
                        startX = accentBounds.left,
                        endX = accentBounds.right,
                    )
                    val dividerGlowStrokeWidths = listOf(6.dp.toPx(), 3.dp.toPx())
                    val bottomFrameGlowStrokeWidths = listOf(5.dp.toPx(), 2.4.dp.toPx())
                    val progressPocketBrush = Brush.radialGradient(
                        colors = listOf(
                            colors.progressCyan.copy(alpha = 0.14f),
                            Color.Transparent,
                        ),
                        center = Offset(
                            x = progressBounds.left + progressBounds.width * 0.7f,
                            y = progressBounds.top + progressBounds.height * 0.35f,
                        ),
                        radius = progressBounds.width * 0.9f,
                    )

                    onDrawBehind {
                        drawPath(
                            path = zones.accentPath,
                            brush = accentGlassBrush,
                        )
                        drawPath(
                            path = zones.progressPath,
                            brush = progressGlassBrush,
                        )
                        drawPath(
                            path = zones.progressPath,
                            brush = progressPocketBrush,
                        )
                        dividerGlowStrokeWidths.forEachIndexed { index, strokeWidth ->
                            drawPath(
                                path = zones.accentBasePath,
                                brush = dividerGlowBrush,
                                alpha = 0.24f / (index + 1),
                                style = Stroke(width = strokeWidth),
                            )
                        }
                        drawPath(
                            path = zones.accentBasePath,
                            brush = dividerGlowBrush,
                            alpha = 0.9f,
                            style = Stroke(width = 1.6.dp.toPx()),
                        )
                        clipRect(top = size.height * 0.52f) {
                            bottomFrameGlowStrokeWidths.forEachIndexed { index, strokeWidth ->
                                drawPath(
                                    path = zones.shellPath,
                                    brush = dividerGlowBrush,
                                    alpha = 0.14f / (index + 1),
                                    style = Stroke(width = strokeWidth),
                                )
                            }
                            drawPath(
                                path = zones.shellPath,
                                brush = dividerGlowBrush,
                                alpha = 0.42f,
                                style = Stroke(width = 1.2.dp.toPx()),
                            )
                        }
                    }
                },
        )

        if (isUnifiedContainerMode) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawWithCache {
                        val carryGlowBrush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.72f to Color.Transparent,
                                1f to colors.accent.copy(alpha = blendSpec.topCarryGlowAlpha),
                            ),
                            startY = 0f,
                            endY = size.height,
                        )
                        onDrawBehind {
                            drawRect(brush = carryGlowBrush)
                        }
                    },
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .fillMaxWidth(0.4f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            colors.surface.copy(alpha = if (colors.isDark) 0.12f else 0.08f),
                        ),
                    ),
                )
                .padding(
                    horizontal = overlaySpec.footerHorizontalPaddingDp,
                    vertical = overlaySpec.footerVerticalPaddingDp,
                ),
        ) {
            when (val content = footerContent) {
                GlowContourFooterContent.ContinueAction -> {
                    FilledIconButton(
                        onClick = { onClickContinueViewing?.invoke() },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = colors.accent.copy(alpha = 0.88f),
                            contentColor = colors.textOnAccent,
                        ),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 1.dp)
                            .requiredSize(overlaySpec.buttonSizeDp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = stringResource(MR.strings.action_resume),
                            modifier = Modifier.requiredSize(overlaySpec.buttonIconSizeDp),
                        )
                    }
                }
                is GlowContourFooterContent.ProgressPercent -> {
                    Text(
                        text = "${content.value}%",
                        color = colors.textPrimary.copy(alpha = 0.98f),
                        fontSize = overlaySpec.progressTextSizeSp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .wrapContentWidth(unbounded = true, align = Alignment.End)
                            .padding(end = overlaySpec.progressTextEndInsetDp),
                    )
                }
                GlowContourFooterContent.None -> Unit
            }
        }

        if (badge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
            ) {
                badge()
            }
        }
    }
}
