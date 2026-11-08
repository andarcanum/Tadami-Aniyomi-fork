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
import androidx.compose.ui.graphics.StampedPathEffectStyle
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp

/** Cozy velvet-felt surface and braided sisal/silk rope border cached per measured keepsake card. */
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
        // Diagonal S-twisted rope strand stamp that morphs smoothly along straight edges and rounded corners.
        val strandLength = 5.4.dp.toPx()
        val strandThickness = 2.6.dp.toPx()
        val step = 3.9.dp.toPx()
        val ropeStrand = Path().apply {
            moveTo(-strandLength * 0.48f, -strandThickness * 0.45f)
            cubicTo(
                -strandLength * 0.15f,
                -strandThickness * 0.72f,
                strandLength * 0.25f,
                -strandThickness * 0.15f,
                strandLength * 0.48f,
                strandThickness * 0.42f,
            )
            cubicTo(
                strandLength * 0.15f,
                strandThickness * 0.72f,
                -strandLength * 0.25f,
                strandThickness * 0.15f,
                -strandLength * 0.48f,
                -strandThickness * 0.45f,
            )
            close()
        }
        val ropeHighlightStrand = Path().apply {
            moveTo(-strandLength * 0.34f, -strandThickness * 0.32f)
            cubicTo(
                -strandLength * 0.08f,
                -strandThickness * 0.50f,
                strandLength * 0.18f,
                -strandThickness * 0.08f,
                strandLength * 0.34f,
                strandThickness * 0.28f,
            )
            cubicTo(
                strandLength * 0.08f,
                strandThickness * 0.36f,
                -strandLength * 0.18f,
                0f,
                -strandLength * 0.34f,
                -strandThickness * 0.32f,
            )
            close()
        }
        val eInkStroke = Stroke(width = 1.2.dp.toPx())
        val shadowStroke = Stroke(width = 4.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val coreStroke = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val grooveEffect = PathEffect.stampedPathEffect(
            shape = ropeStrand,
            advance = step,
            phase = step * 0.5f,
            style = StampedPathEffectStyle.Morph,
        )
        val mainRopeEffect = PathEffect.stampedPathEffect(
            shape = ropeStrand,
            advance = step,
            phase = 0f,
            style = StampedPathEffectStyle.Morph,
        )
        val highlightRopeEffect = PathEffect.stampedPathEffect(
            shape = ropeHighlightStrand,
            advance = step,
            phase = 0f,
            style = StampedPathEffectStyle.Morph,
        )
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
                translate(0.5.dp.toPx(), 0.9.dp.toPx()) {
                    drawPath(border, Color(0xFF050E14).copy(alpha = 0.28f), style = shadowStroke)
                }
                // Continuous warm under-cord so there are never empty gaps between twists.
                drawPath(border, cord.copy(alpha = 0.42f), style = coreStroke)
                // Dark recessed twist grooves between strands.
                drawPath(
                    path = border,
                    color = Color(0xFF101E24).copy(alpha = 0.55f),
                    style = Stroke(width = 1f, pathEffect = grooveEffect),
                )
                // Main braided/twisted rope strands.
                drawPath(
                    path = border,
                    color = cord.copy(alpha = 0.88f),
                    style = Stroke(width = 1f, pathEffect = mainRopeEffect),
                )
                // Warm cream specular highlight along the top crest of each rope twist.
                translate(-0.3.dp.toPx(), -0.3.dp.toPx()) {
                    drawPath(
                        path = border,
                        color = Color(0xFFFFF7E8).copy(alpha = 0.48f),
                        style = Stroke(width = 1f, pathEffect = highlightRopeEffect),
                    )
                }
            }
        }
    }
}
