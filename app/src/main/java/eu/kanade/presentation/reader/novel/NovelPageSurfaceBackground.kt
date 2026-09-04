package eu.kanade.presentation.reader.novel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.res.imageResource
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundTexture

@Composable
internal fun NovelPageSurfaceBackground(
    backgroundTexture: NovelReaderBackgroundTexture,
    nativeTextureStrengthPercent: Int,
    surfaceColor: Color? = null,
) {
    val textureIntensityFactor = remember(nativeTextureStrengthPercent) {
        resolveNativeTextureIntensityFactor(nativeTextureStrengthPercent)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (surfaceColor != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(surfaceColor),
            )
        }
        if (
            backgroundTexture == NovelReaderBackgroundTexture.PAPER_GRAIN ||
            backgroundTexture == NovelReaderBackgroundTexture.LINEN ||
            backgroundTexture == NovelReaderBackgroundTexture.PARCHMENT
        ) {
            // Mirrors NovelAtmosphereBackground: PARCHMENT shares the paper grain bitmap plus the
            // radial aging layers. Page-turn surfaces (CURL/BOOK_FLIP) draw their own background
            // through this composable, so without the PARCHMENT branch those pages came out flat.
            val imageRes = when (backgroundTexture) {
                NovelReaderBackgroundTexture.PAPER_GRAIN,
                NovelReaderBackgroundTexture.PARCHMENT,
                -> com.tadami.aurora.R.drawable.texture_paper
                else -> com.tadami.aurora.R.drawable.texture_linen
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

            if (backgroundTexture == NovelReaderBackgroundTexture.PARCHMENT) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val agingCenter1 = Offset(size.width * 0.2f, size.height * 0.2f)
                    val agingCenter2 = Offset(size.width * 0.8f, size.height * 0.75f)
                    drawRect(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0.0f to Color.White.copy(alpha = 0.14f),
                                0.45f to Color.Transparent,
                            ),
                            center = agingCenter1,
                            radius = size.width * 0.9f,
                        ),
                    )
                    drawRect(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Black.copy(alpha = 0.12f),
                                0.42f to Color.Transparent,
                            ),
                            center = agingCenter2,
                            radius = size.width * 0.9f,
                        ),
                    )
                }
            }
        }
    }
}
