package eu.kanade.presentation.reader.novel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tadami.aurora.R
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundTexture

internal enum class NovelSpreadBackgroundHalf {
    FULL,
    LEFT,
    RIGHT,
}

@Composable
internal fun NovelAtmosphereBackground(
    backgroundColor: Color,
    backgroundTexture: NovelReaderBackgroundTexture,
    nativeTextureStrengthPercent: Int,
    oledEdgeGradient: Boolean,
    isDarkTheme: Boolean,
    pageEdgeShadow: Boolean,
    pageEdgeShadowAlpha: Float,
    backgroundImageModel: Any?,
    spreadHalf: NovelSpreadBackgroundHalf = NovelSpreadBackgroundHalf.FULL,
) {
    val textureIntensityFactor = remember(nativeTextureStrengthPercent) {
        resolveNativeTextureIntensityFactor(nativeTextureStrengthPercent)
    }
    val radialLayers = remember(backgroundTexture, oledEdgeGradient, isDarkTheme, textureIntensityFactor) {
        buildReaderAtmosphereRadialLayers(
            backgroundTexture = backgroundTexture,
            oledEdgeGradient = oledEdgeGradient,
            isDarkTheme = isDarkTheme,
            intensityFactor = textureIntensityFactor,
        )
    }

    val containerModifier = if (spreadHalf == NovelSpreadBackgroundHalf.FULL) {
        Modifier.fillMaxSize()
    } else {
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .layout { measurable, constraints ->
                if (!constraints.hasBoundedWidth) {
                    val placeable = measurable.measure(constraints)
                    return@layout layout(placeable.width, placeable.height) {
                        placeable.placeRelative(0, 0)
                    }
                }
                val halfWidth = constraints.maxWidth
                val fullWidth = (halfWidth * 2).coerceAtLeast(halfWidth)
                val placeable = measurable.measure(
                    constraints.copy(minWidth = fullWidth, maxWidth = fullWidth),
                )
                val xOffset = if (spreadHalf == NovelSpreadBackgroundHalf.LEFT) 0 else -halfWidth
                layout(halfWidth, placeable.height) {
                    placeable.placeRelative(xOffset, 0)
                }
            }
    }

    Box(modifier = containerModifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor),
        ) {
            if (backgroundImageModel != null) {
                AsyncImage(
                    model = backgroundImageModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (backgroundTexture == NovelReaderBackgroundTexture.PAPER_GRAIN ||
                backgroundTexture == NovelReaderBackgroundTexture.LINEN ||
                backgroundTexture == NovelReaderBackgroundTexture.PARCHMENT
            ) {
                val imageRes = when (backgroundTexture) {
                    NovelReaderBackgroundTexture.PAPER_GRAIN,
                    NovelReaderBackgroundTexture.PARCHMENT,
                    -> R.drawable.texture_paper
                    else -> R.drawable.texture_linen
                }

                val imageBitmap = ImageBitmap.imageResource(id = imageRes)
                val brush = remember(imageBitmap) {
                    ShaderBrush(
                        ImageShader(
                            image = imageBitmap,
                            tileModeX = TileMode.Repeated,
                            tileModeY = TileMode.Repeated,
                        ),
                    )
                }
                val baseTextureAlpha = textureIntensityFactor.coerceIn(0f, 1f)
                val boostTextureAlpha = ((textureIntensityFactor - 1f) / 3f).coerceIn(0f, 1f) * 0.45f
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (baseTextureAlpha > 0f) {
                        drawRect(brush = brush, alpha = baseTextureAlpha)
                    }
                    if (boostTextureAlpha > 0f) {
                        drawRect(
                            brush = brush,
                            alpha = boostTextureAlpha,
                            blendMode = BlendMode.Multiply,
                        )
                    }
                }
            }

            if (radialLayers.isNotEmpty()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    radialLayers.forEach { layer ->
                        val center = Offset(
                            x = size.width * layer.centerXFraction,
                            y = size.height * layer.centerYFraction,
                        )
                        val radius = calculateRadialGradientFarthestCornerRadius(
                            size = size,
                            center = center,
                        )
                        drawRect(
                            brush = Brush.radialGradient(
                                colorStops = layer.colorStops.toTypedArray(),
                                center = center,
                                radius = radius,
                            ),
                        )
                    }
                }
            }

            if (pageEdgeShadow) {
                val edgeColor = resolvePageEdgeShadowColor(
                    pageEdgeShadowAlpha = pageEdgeShadowAlpha,
                    backgroundColor = backgroundColor,
                )
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stopFraction = if (size.width > 0f) {
                        (16.dp.toPx() / size.width).coerceIn(0.01f, 0.08f)
                    } else {
                        0.04f
                    }
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0.0f to edgeColor,
                            stopFraction to Color.Transparent,
                            (1f - stopFraction) to Color.Transparent,
                            1.0f to edgeColor,
                        ),
                    )
                }
            }
        }
    }
}
