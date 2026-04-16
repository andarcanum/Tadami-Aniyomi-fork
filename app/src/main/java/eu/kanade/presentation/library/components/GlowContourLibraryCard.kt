package eu.kanade.presentation.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.platform.LocalContext
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
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.components.buildAuroraCoverImageRequest
import eu.kanade.presentation.components.rememberAuroraCoverPlaceholderPainter
import eu.kanade.presentation.components.resolveAuroraCardOverlaySpec
import eu.kanade.presentation.entries.components.aurora.rememberAuroraPosterColorFilter
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.LocalCoverTitleFontFamily
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
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
    val androidPath = PathParser.createPathFromPathData(pathData)
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
    data object None : GlowContourFooterContent
}

internal sealed interface GlowContourCornerIndicatorState {
    data object ContinueAction : GlowContourCornerIndicatorState
    data object CompletedJewel : GlowContourCornerIndicatorState
    data object NewContentJewel : GlowContourCornerIndicatorState
    data object NeutralJewel : GlowContourCornerIndicatorState
}

internal fun resolveGlowContourCornerIndicatorState(
    hasContinueAction: Boolean,
    remainingCount: Long,
    isFinished: Boolean,
): GlowContourCornerIndicatorState {
    return when {
        hasContinueAction && remainingCount > 0L -> GlowContourCornerIndicatorState.ContinueAction
        remainingCount > 0L -> GlowContourCornerIndicatorState.NewContentJewel
        isFinished -> GlowContourCornerIndicatorState.CompletedJewel
        else -> GlowContourCornerIndicatorState.NeutralJewel
    }
}

internal fun resolveGlowContourCornerIndicatorContentDescriptionRes(
    state: GlowContourCornerIndicatorState,
): StringResource? {
    return when (state) {
        GlowContourCornerIndicatorState.ContinueAction -> MR.strings.action_resume
        GlowContourCornerIndicatorState.CompletedJewel -> MR.strings.completed
        GlowContourCornerIndicatorState.NewContentJewel -> AYMR.strings.aurora_new_badge
        GlowContourCornerIndicatorState.NeutralJewel -> MR.strings.status
    }
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

internal data class GlowContourDividerRenderSpec(
    val glowStrokeWidths: List<androidx.compose.ui.unit.Dp>,
    val coreStrokeWidth: androidx.compose.ui.unit.Dp,
    val glowAlphaBase: Float,
    val coreAlpha: Float,
    val clipBottomToProgressTop: Boolean,
    val showBottomFrameGlow: Boolean,
)

internal fun resolveGlowContourDividerRenderSpec(): GlowContourDividerRenderSpec {
    return GlowContourDividerRenderSpec(
        glowStrokeWidths = listOf(3.dp),
        coreStrokeWidth = 1.25.dp,
        glowAlphaBase = 0.14f,
        coreAlpha = 0.78f,
        clipBottomToProgressTop = true,
        showBottomFrameGlow = false,
    )
}

internal fun resolveGlowContourFooterContent(
    progressPercent: Int?,
    onClickContinueViewing: (() -> Unit)?,
): GlowContourFooterContent {
    return when {
        onClickContinueViewing != null -> GlowContourFooterContent.ContinueAction
        else -> GlowContourFooterContent.None
    }
}

internal data class GlowContourTextBlockRenderSpec(
    val topSpacing: androidx.compose.ui.unit.Dp,
    val titleSubtitleSpacing: androidx.compose.ui.unit.Dp,
    val horizontalPadding: androidx.compose.ui.unit.Dp,
    val verticalPadding: androidx.compose.ui.unit.Dp,
    val useSurfaceBlend: Boolean,
    val titleMinLines: Int,
    val minTextBlockHeight: androidx.compose.ui.unit.Dp,
)

internal fun resolveGlowContourTextBlockRenderSpec(): GlowContourTextBlockRenderSpec {
    return GlowContourTextBlockRenderSpec(
        topSpacing = 8.dp,
        titleSubtitleSpacing = 2.dp,
        horizontalPadding = 6.dp,
        verticalPadding = 0.dp,
        useSurfaceBlend = false,
        titleMinLines = 1,
        minTextBlockHeight = 34.dp,
    )
}

internal data class GlowContourBottomMaskRenderSpec(
    val accentTopAlpha: Float,
    val accentMidAlpha: Float,
    val accentBottomAlpha: Float,
    val pocketTopAlpha: Float,
    val pocketMidAlpha: Float,
    val pocketBottomAlpha: Float,
    val pocketBloomAlpha: Float,
)

internal fun resolveGlowContourBottomMaskRenderSpec(
    isDark: Boolean,
    hasActionPocket: Boolean,
): GlowContourBottomMaskRenderSpec {
    return when {
        isDark && hasActionPocket -> GlowContourBottomMaskRenderSpec(
            accentTopAlpha = 0f,
            accentMidAlpha = 0.08f,
            accentBottomAlpha = 0.24f,
            pocketTopAlpha = 0.04f,
            pocketMidAlpha = 0.12f,
            pocketBottomAlpha = 0.26f,
            pocketBloomAlpha = 0.12f,
        )
        isDark -> GlowContourBottomMaskRenderSpec(
            accentTopAlpha = 0f,
            accentMidAlpha = 0.07f,
            accentBottomAlpha = 0.22f,
            pocketTopAlpha = 0.02f,
            pocketMidAlpha = 0.07f,
            pocketBottomAlpha = 0.16f,
            pocketBloomAlpha = 0.05f,
        )
        hasActionPocket -> GlowContourBottomMaskRenderSpec(
            accentTopAlpha = 0f,
            accentMidAlpha = 0.05f,
            accentBottomAlpha = 0.18f,
            pocketTopAlpha = 0.03f,
            pocketMidAlpha = 0.09f,
            pocketBottomAlpha = 0.2f,
            pocketBloomAlpha = 0.1f,
        )
        else -> GlowContourBottomMaskRenderSpec(
            accentTopAlpha = 0f,
            accentMidAlpha = 0.04f,
            accentBottomAlpha = 0.16f,
            pocketTopAlpha = 0.02f,
            pocketMidAlpha = 0.05f,
            pocketBottomAlpha = 0.12f,
            pocketBloomAlpha = 0.04f,
        )
    }
}

internal data class GlowContourPosterSurfaceSpec(
    val backgroundAlpha: Float,
    val clipBackgroundToShape: Boolean,
)

internal fun resolveGlowContourPosterSurfaceSpec(isDark: Boolean): GlowContourPosterSurfaceSpec {
    return GlowContourPosterSurfaceSpec(
        backgroundAlpha = if (isDark) 0.18f else 0.08f,
        clipBackgroundToShape = true,
    )
}

internal data class GlowContourActionButtonRenderSpec(
    val containerTopAlpha: Float,
    val containerBottomAlpha: Float,
    val borderTopAlpha: Float,
    val borderBottomAlpha: Float,
    val glowAlpha: Float,
    val glowElevation: androidx.compose.ui.unit.Dp,
)

internal fun resolveGlowContourActionButtonRenderSpec(isDark: Boolean): GlowContourActionButtonRenderSpec {
    return if (isDark) {
        GlowContourActionButtonRenderSpec(
            containerTopAlpha = 0.22f,
            containerBottomAlpha = 0.08f,
            borderTopAlpha = 0.42f,
            borderBottomAlpha = 0.14f,
            glowAlpha = 0.58f,
            glowElevation = 18.dp,
        )
    } else {
        GlowContourActionButtonRenderSpec(
            containerTopAlpha = 0.15f,
            containerBottomAlpha = 0.06f,
            borderTopAlpha = 0.35f,
            borderBottomAlpha = 0.12f,
            glowAlpha = 0.4f,
            glowElevation = 14.dp,
        )
    }
}

internal data class GlowContourProgressLineRenderSpec(
    val lineHeight: androidx.compose.ui.unit.Dp,
    val trackAlpha: Float,
    val glowAlpha: Float,
    val glowElevation: androidx.compose.ui.unit.Dp,
)

internal fun resolveGlowContourProgressLineRenderSpec(): GlowContourProgressLineRenderSpec {
    return GlowContourProgressLineRenderSpec(
        lineHeight = 2.5.dp,
        trackAlpha = 0.15f,
        glowAlpha = 0.56f,
        glowElevation = 14.dp,
    )
}

internal data class GlowContourProgressRenderState(
    val showTrack: Boolean,
    val fillFraction: Float?,
    val showGlow: Boolean,
)

internal fun resolveGlowContourProgressRenderState(progressPercent: Int?): GlowContourProgressRenderState {
    val normalized = progressPercent?.coerceIn(0, 100)
    val fillFraction = normalized
        ?.takeIf { it > 0 }
        ?.div(100f)
    return GlowContourProgressRenderState(
        showTrack = normalized != null,
        fillFraction = fillFraction,
        showGlow = fillFraction != null,
    )
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
internal fun GlowContourLibraryGridItem(
    title: String,
    subtitle: String?,
    coverData: Any?,
    progressPercent: Int?,
    cardAspectRatio: Float,
    cornerIndicatorState: GlowContourCornerIndicatorState,
    modifier: Modifier = Modifier,
    textSpec: GlowContourLibraryTextSpec,
    badge: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onClickContinueViewing: (() -> Unit)? = null,
    isSelected: Boolean = false,
    gridColumns: Int? = null,
    customCover: @Composable (() -> Unit)? = null,
) {
    val colors = AuroraTheme.colors
    val blendSpec = resolveGlowContourUnifiedBlendSpec(colors.isDark)
    val itemModifier = modifier.combinedClickable(
        onClick = onClick,
        onLongClick = onLongClick,
    )

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
            cornerIndicatorState = cornerIndicatorState,
            onClickContinueViewing = onClickContinueViewing,
            gridColumns = gridColumns,
            customCover = customCover,
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
    val renderSpec = resolveGlowContourTextBlockRenderSpec()
    val drawTextSurfaceModifier = if (renderSpec.useSurfaceBlend && isUnifiedContainerMode) {
        Modifier.drawWithCache {
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
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = renderSpec.topSpacing)
            .then(drawTextSurfaceModifier)
            .heightIn(min = renderSpec.minTextBlockHeight)
            .padding(
                horizontal = renderSpec.horizontalPadding,
                vertical = renderSpec.verticalPadding,
            ),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(renderSpec.titleSubtitleSpacing),
        ) {
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                minLines = renderSpec.titleMinLines,
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
        Spacer(modifier = Modifier.weight(1f, fill = true))
    }
}

@Composable
private fun GlowContourLibraryCard(
    coverData: Any?,
    progressPercent: Int?,
    cornerIndicatorState: GlowContourCornerIndicatorState,
    badge: @Composable (() -> Unit)?,
    isSelected: Boolean,
    isUnifiedContainerMode: Boolean,
    blendSpec: GlowContourUnifiedBlendSpec,
    onClickContinueViewing: (() -> Unit)?,
    gridColumns: Int?,
    modifier: Modifier = Modifier,
    customCover: @Composable (() -> Unit)? = null,
) {
    val colors = AuroraTheme.colors
    val context = LocalContext.current
    val placeholderPainter = rememberAuroraCoverPlaceholderPainter()
    val posterSurfaceSpec = resolveGlowContourPosterSurfaceSpec(colors.isDark)
    val footerContent = resolveGlowContourFooterContent(
        progressPercent = progressPercent,
        onClickContinueViewing = onClickContinueViewing,
    )
    val progressState = resolveGlowContourProgressRenderState(progressPercent)

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
            .clip(GlowContourCardShape)
            .then(
                if (posterSurfaceSpec.clipBackgroundToShape) {
                    Modifier.background(
                        colors.surface.copy(alpha = posterSurfaceSpec.backgroundAlpha),
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        val overlaySpec = resolveAuroraCardOverlaySpec(
            gridColumns = gridColumns,
            cardWidthDp = maxWidth.value,
        )

        Box(
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
        ) {
            if (customCover != null) {
                customCover()
            } else {
                val coverRequest = remember(coverData) {
                    buildAuroraCoverImageRequest(context, coverData)
                }

                AsyncImage(
                    model = coverRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = rememberAuroraPosterColorFilter(),
                    modifier = Modifier.fillMaxSize(),
                    error = placeholderPainter,
                    fallback = placeholderPainter,
                )
            }
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithCache {
                    val zones = createGlowContourZonedPaths(size)
                    val accentBounds = zones.accentPath.getBounds()
                    val progressBounds = zones.progressPath.getBounds()
                    val dividerSpec = resolveGlowContourDividerRenderSpec()
                    val hasActionPocket = footerContent == GlowContourFooterContent.ContinueAction
                    val bottomMaskSpec = resolveGlowContourBottomMaskRenderSpec(
                        isDark = colors.isDark,
                        hasActionPocket = hasActionPocket,
                    )
                    val accentGlassBrush = Brush.verticalGradient(
                        colors = listOf(
                            colors.surface.copy(alpha = bottomMaskSpec.accentTopAlpha),
                            colors.glass.copy(alpha = bottomMaskSpec.accentMidAlpha),
                            colors.surface.copy(alpha = bottomMaskSpec.accentBottomAlpha),
                        ),
                        startY = accentBounds.top,
                        endY = accentBounds.bottom,
                    )
                    val progressGlassBrush = Brush.verticalGradient(
                        colors = listOf(
                            colors.surface.copy(alpha = bottomMaskSpec.pocketTopAlpha),
                            colors.glass.copy(alpha = bottomMaskSpec.pocketMidAlpha),
                            colors.surface.copy(alpha = bottomMaskSpec.pocketBottomAlpha),
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
                    val dividerGlowStrokeWidths = dividerSpec.glowStrokeWidths.map { it.toPx() }
                    val dividerClipBottom = if (dividerSpec.clipBottomToProgressTop) {
                        progressBounds.top + 1.dp.toPx()
                    } else {
                        size.height
                    }
                    val progressPocketBrush = Brush.radialGradient(
                        colors = listOf(
                            colors.progressCyan.copy(alpha = bottomMaskSpec.pocketBloomAlpha),
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
                        if (hasActionPocket) {
                            drawPath(
                                path = zones.progressPath,
                                brush = progressGlassBrush,
                            )
                            drawPath(
                                path = zones.progressPath,
                                brush = progressPocketBrush,
                            )
                        }
                        clipRect(bottom = dividerClipBottom) {
                            dividerGlowStrokeWidths.forEachIndexed { index, strokeWidth ->
                                drawPath(
                                    path = zones.accentBasePath,
                                    brush = dividerGlowBrush,
                                    alpha = dividerSpec.glowAlphaBase / (index + 1),
                                    style = Stroke(width = strokeWidth),
                                )
                            }
                            drawPath(
                                path = zones.accentBasePath,
                                brush = dividerGlowBrush,
                                alpha = dividerSpec.coreAlpha,
                                style = Stroke(width = dividerSpec.coreStrokeWidth.toPx()),
                            )
                        }
                        if (dividerSpec.showBottomFrameGlow) {
                            clipRect(top = size.height * 0.52f) {
                                drawPath(
                                    path = zones.shellPath,
                                    brush = dividerGlowBrush,
                                    alpha = 0.14f,
                                    style = Stroke(width = 2.4.dp.toPx()),
                                )
                            }
                        }
                    }
                },
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithCache {
                    val carryGlowBrush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.78f to Color.Transparent,
                            1f to colors.accent.copy(alpha = blendSpec.topCarryGlowAlpha * 0.7f),
                        ),
                        startY = 0f,
                        endY = size.height,
                    )
                    onDrawBehind {
                        drawRect(brush = carryGlowBrush)
                    }
                },
        )

        if (progressState.showTrack) {
            val progressSpec = resolveGlowContourProgressLineRenderSpec()
            val progressShape = RoundedCornerShape(percent = 50)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(progressSpec.lineHeight),
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(progressShape)
                        .background(
                            if (colors.isDark) {
                                Color.White.copy(alpha = progressSpec.trackAlpha)
                            } else {
                                Color.Black.copy(alpha = 0.12f)
                            },
                        ),
                )
                progressState.fillFraction?.let { fillFraction ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fillFraction)
                            .height(progressSpec.lineHeight)
                            .then(
                                if (progressState.showGlow) {
                                    Modifier.shadow(
                                        elevation = progressSpec.glowElevation,
                                        shape = progressShape,
                                        ambientColor = colors.progressCyan.copy(alpha = progressSpec.glowAlpha),
                                        spotColor = colors.progressCyan.copy(alpha = progressSpec.glowAlpha),
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .clip(progressShape)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        colors.progressCyan.copy(alpha = 0.72f),
                                        colors.progressCyan.copy(alpha = 1f),
                                        colors.progressCyan.copy(alpha = 0.82f),
                                    ),
                                ),
                            ),
                    )
                }
            }
        }

        val buttonSpec = resolveGlowContourActionButtonRenderSpec(colors.isDark)
        if (footerContent == GlowContourFooterContent.ContinueAction) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .fillMaxWidth(0.4f)
                    .padding(
                        horizontal = overlaySpec.footerHorizontalPaddingDp,
                        vertical = overlaySpec.footerVerticalPaddingDp,
                    ),
            ) {
                IconButton(
                    onClick = { onClickContinueViewing?.invoke() },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = if (colors.isDark) {
                            Color.White.copy(alpha = 0.98f)
                        } else {
                            colors.accent.copy(alpha = 0.98f)
                        },
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 1.dp)
                        .requiredSize(overlaySpec.buttonSizeDp)
                        .shadow(
                            elevation = buttonSpec.glowElevation,
                            shape = CircleShape,
                            ambientColor = colors.accent.copy(alpha = buttonSpec.glowAlpha),
                            spotColor = colors.accent.copy(alpha = buttonSpec.glowAlpha),
                        )
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = if (colors.isDark) {
                                    listOf(
                                        Color.White.copy(alpha = buttonSpec.containerTopAlpha),
                                        Color.White.copy(alpha = buttonSpec.containerBottomAlpha),
                                    )
                                } else {
                                    listOf(
                                        colors.accent.copy(alpha = buttonSpec.containerTopAlpha),
                                        colors.accent.copy(alpha = buttonSpec.containerBottomAlpha),
                                    )
                                },
                            ),
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                colors = if (colors.isDark) {
                                    listOf(
                                        Color.White.copy(alpha = buttonSpec.borderTopAlpha),
                                        Color.White.copy(alpha = buttonSpec.borderBottomAlpha),
                                    )
                                } else {
                                    listOf(
                                        colors.accent.copy(alpha = buttonSpec.borderTopAlpha),
                                        colors.accent.copy(alpha = buttonSpec.borderBottomAlpha),
                                    )
                                },
                            ),
                            shape = CircleShape,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(MR.strings.action_resume),
                        modifier = Modifier.requiredSize(overlaySpec.buttonIconSizeDp),
                    )
                }
            }
        } else {
            GlowContourStatusJewel(
                state = cornerIndicatorState,
                buttonSpec = buttonSpec,
                overlaySpec = overlaySpec,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
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

@Composable
private fun GlowContourStatusJewel(
    state: GlowContourCornerIndicatorState,
    buttonSpec: GlowContourActionButtonRenderSpec,
    overlaySpec: eu.kanade.presentation.components.AuroraCardOverlaySpec,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val contentDescription = resolveGlowContourCornerIndicatorContentDescriptionRes(state)
        ?.let { stringResource(it) }
    val (icon, alphaMultiplier, iconAlpha, iconSize) = when (state) {
        GlowContourCornerIndicatorState.ContinueAction -> return
        GlowContourCornerIndicatorState.CompletedJewel ->
            JewelVisualSpec(
                icon = Icons.Filled.Check,
                alphaMultiplier = 0.9f,
                iconAlpha = 0.96f,
                iconSize = overlaySpec.buttonIconSizeDp,
            )
        GlowContourCornerIndicatorState.NewContentJewel ->
            JewelVisualSpec(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                alphaMultiplier = 0.88f,
                iconAlpha = 0.96f,
                iconSize = (overlaySpec.buttonIconSizeDp.value * 0.72f).dp,
            )
        GlowContourCornerIndicatorState.NeutralJewel ->
            JewelVisualSpec(
                icon = Icons.Filled.HourglassEmpty,
                alphaMultiplier = 0.45f,
                iconAlpha = 0.75f,
                iconSize = (overlaySpec.buttonIconSizeDp.value * 0.72f).dp,
            )
    }

    val jewelBgColors = if (colors.isDark) {
        listOf(
            Color.White.copy(alpha = buttonSpec.containerTopAlpha * alphaMultiplier),
            Color.White.copy(alpha = buttonSpec.containerBottomAlpha * alphaMultiplier),
        )
    } else {
        listOf(
            colors.accent.copy(alpha = buttonSpec.containerTopAlpha * alphaMultiplier),
            colors.accent.copy(alpha = buttonSpec.containerBottomAlpha * alphaMultiplier),
        )
    }
    val jewelBorderColors = if (colors.isDark) {
        listOf(
            Color.White.copy(alpha = buttonSpec.borderTopAlpha * alphaMultiplier),
            Color.White.copy(alpha = buttonSpec.borderBottomAlpha * alphaMultiplier),
        )
    } else {
        listOf(
            colors.accent.copy(alpha = buttonSpec.borderTopAlpha * alphaMultiplier),
            colors.accent.copy(alpha = buttonSpec.borderBottomAlpha * alphaMultiplier),
        )
    }
    val jewelIconTint = if (colors.isDark) {
        Color.White.copy(alpha = iconAlpha)
    } else {
        colors.accent.copy(alpha = iconAlpha)
    }

    Box(
        modifier = modifier
            .fillMaxWidth(0.4f)
            .padding(
                horizontal = overlaySpec.footerHorizontalPaddingDp,
                vertical = overlaySpec.footerVerticalPaddingDp,
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 1.dp)
                .requiredSize(overlaySpec.buttonSizeDp)
                .shadow(
                    elevation = buttonSpec.glowElevation,
                    shape = CircleShape,
                    ambientColor = colors.accent.copy(alpha = buttonSpec.glowAlpha * alphaMultiplier),
                    spotColor = colors.accent.copy(alpha = buttonSpec.glowAlpha * alphaMultiplier),
                )
                .clip(CircleShape)
                .background(Brush.linearGradient(colors = jewelBgColors))
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(colors = jewelBorderColors),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = jewelIconTint,
                modifier = Modifier.requiredSize(iconSize),
            )
        }
    }
}

private data class JewelVisualSpec(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val alphaMultiplier: Float,
    val iconAlpha: Float,
    val iconSize: androidx.compose.ui.unit.Dp,
)
