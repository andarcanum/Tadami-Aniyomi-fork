package eu.kanade.presentation.easteregg.crystal

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp

/**
 * Cozy velvet-felt surface and braided cord border cached per measured keepsake card.
 *
 * Uses lightweight GPU-friendly dashed cord layers instead of StampedPathEffectStyle.Morph,
 * eliminating multi-thousand-verb CPU path morphing and SW Path Mask re-rasterization during zoom/scroll.
 */
@Composable
internal fun Modifier.crystalCollectionMaterial(shape: Shape, cord: Color, eInk: Boolean): Modifier {
    return drawWithCache {
        val border = Path().apply {
            when (val outline = shape.createOutline(size, layoutDirection, this@drawWithCache)) {
                is Outline.Rounded -> addRoundRect(outline.roundRect)
                is Outline.Generic -> addPath(outline.path)
                is Outline.Rectangle -> addRect(outline.rect)
            }
        }
        val surfaceGlow = Brush.linearGradient(
            colors = listOf(
                cord.copy(alpha = 0.11f),
                Color.Transparent,
                Color(0xFF061016).copy(alpha = 0.22f),
            ),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        )
        val eInkStroke = Stroke(width = 1.2.dp.toPx())
        val shadowStroke = Stroke(width = 3.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val coreStroke = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val dashOn = 3.4.dp.toPx()
        val dashGap = 2.2.dp.toPx()
        val grooveStroke = Stroke(
            width = 2.0.dp.toPx(),
            cap = StrokeCap.Butt,
            join = StrokeJoin.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.1.dp.toPx(), dashOn + dashGap - 1.1.dp.toPx()), 0f),
        )
        val braidStroke = Stroke(
            width = 2.1.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashOn, dashGap), dashGap * 0.6f),
        )
        val highlightStroke = Stroke(
            width = 1.0.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(dashOn * 0.52f, dashOn * 0.48f + dashGap),
                dashGap * 0.85f,
            ),
        )
        val shadowDx = 0.5.dp.toPx()
        val shadowDy = 0.9.dp.toPx()
        val hiShift = -0.35.dp.toPx()
        onDrawWithContent {
            if (!eInk) {
                clipPath(border) {
                    drawRect(surfaceGlow)
                }
            }
            drawContent()
            if (eInk) {
                drawPath(border, cord.copy(alpha = 0.75f), style = eInkStroke)
            } else {
                translate(shadowDx, shadowDy) {
                    drawPath(border, Color(0xFF050E14).copy(alpha = 0.26f), style = shadowStroke)
                }
                // Continuous warm under-cord so there are never empty gaps between twists.
                drawPath(border, cord.copy(alpha = 0.44f), style = coreStroke)
                // Dark recessed twist grooves between strands.
                drawPath(border, Color(0xFF101E24).copy(alpha = 0.52f), style = grooveStroke)
                // Main braided cord crests.
                drawPath(border, cord.copy(alpha = 0.84f), style = braidStroke)
                // Warm cream specular highlight along the top crest of each twist.
                translate(hiShift, hiShift) {
                    drawPath(border, Color(0xFFFFF7E8).copy(alpha = 0.48f), style = highlightStroke)
                }
            }
        }
    }
}
