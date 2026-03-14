package eu.kanade.presentation.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.PathParser
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.rememberAuroraCoverPlaceholderPainter
import eu.kanade.presentation.components.resolveAuroraCoverModel
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import android.graphics.Matrix as AndroidMatrix

private const val GLOW_CONTOUR_SVG_WIDTH = 256f
private const val GLOW_CONTOUR_SVG_HEIGHT = 269f

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
) {
    val colors = AuroraTheme.colors

    Column(
        modifier = modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
        ),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        GlowContourLibraryCard(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(cardAspectRatio),
            coverData = coverData,
            progressPercent = progressPercent,
            badge = badge,
            isSelected = isSelected,
            onClickContinueViewing = onClickContinueViewing,
        )

        if (textSpec.showTextBlock) {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp),
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
                )
                if (!subtitle.isNullOrBlank() && textSpec.subtitleMaxLines > 0) {
                    Text(
                        text = subtitle,
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = textSpec.subtitleMaxLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun GlowContourLibraryCard(
    coverData: Any?,
    progressPercent: Int?,
    badge: @Composable (() -> Unit)?,
    isSelected: Boolean,
    onClickContinueViewing: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val placeholderPainter = rememberAuroraCoverPlaceholderPainter()
    val footerContent = resolveGlowContourFooterContent(
        progressPercent = progressPercent,
        onClickContinueViewing = onClickContinueViewing,
    )

    Box(
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
            .background(colors.surface.copy(alpha = if (colors.isDark) 0.18f else 0.08f))
            .clip(GlowContourCardShape),
    ) {
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
                .padding(horizontal = 10.dp, vertical = 9.dp),
        ) {
            when (val content = footerContent) {
                GlowContourFooterContent.ContinueAction -> {
                    FilledIconButton(
                        onClick = { onClickContinueViewing?.invoke() },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = colors.accent.copy(alpha = 0.88f),
                            contentColor = colors.textOnAccent,
                        ),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 1.dp)
                            .size(30.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = stringResource(MR.strings.action_resume),
                        )
                    }
                }
                is GlowContourFooterContent.ProgressPercent -> {
                    Text(
                        text = "${content.value}%",
                        color = colors.textPrimary.copy(alpha = 0.98f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.align(Alignment.BottomEnd),
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
