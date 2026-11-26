package eu.kanade.presentation.easteregg.crystal

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme

/** Warm feline keepsake capsule linking the farewell and ceremony to Crystal's corner. */
@Composable
internal fun CrystalFelineInvitationButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    palette: CrystalPalette = CrystalPalette.Moon,
    enabled: Boolean = true,
) {
    val colors = AuroraTheme.colors
    val policy = rememberCrystalMotionPolicy()
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val focused by interactions.collectIsFocusedAsState()
    val motion = !colors.isEInk && policy.resumed && policy.systemAnimations
    val scale by animateFloatAsState(
        crystalFelineInvitationPressScale(pressed, enabled, colors.isEInk, motion),
        animationSpec = if (motion) tween(120) else snap(),
        label = "threadInvitationPress",
    )
    val jewelPainter = remember { CrystalJewelPainter() }
    val gold = Color(palette.gold)
    val rose = Color(palette.rose)
    val mint = Color(palette.mint)
    val foreground = if (colors.isEInk) colors.textPrimary else Color(0xFFFFF6E6)
    Row(
        modifier.fillMaxWidth().sizeIn(minWidth = 48.dp, minHeight = CRYSTAL_FELINE_INVITATION_MIN_HEIGHT_DP.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else .55f
            }
            .drawWithCache {
                val radiusPx = 24.dp.toPx()
                val corner = CornerRadius(radiusPx)
                val borderStroke = Stroke(if (focused) 2.dp.toPx() else 1.2.dp.toPx())
                val innerCorner = CornerRadius((radiusPx - 1.5.dp.toPx()).coerceAtLeast(0f))
                val fillBrush = Brush.linearGradient(
                    listOf(
                        Color(0xFF1F2334).copy(alpha = 0.88f),
                        Color(0xFF282235).copy(alpha = 0.88f),
                        Color(0xFF1B2B36).copy(alpha = 0.88f),
                    ),
                )
                val glowBrush = Brush.horizontalGradient(
                    listOf(
                        gold.copy(alpha = 0.16f),
                        rose.copy(alpha = 0.14f),
                        mint.copy(alpha = 0.12f),
                    ),
                )
                val borderBrush = Brush.horizontalGradient(
                    listOf(
                        gold.copy(alpha = if (focused) 0.95f else 0.52f),
                        rose.copy(alpha = if (focused) 0.90f else 0.42f),
                        gold.copy(alpha = if (focused) 0.95f else 0.52f),
                    ),
                )
                onDrawBehind {
                    if (colors.isEInk) {
                        drawRoundRect(colors.surface, cornerRadius = corner)
                        drawRoundRect(foreground, cornerRadius = corner, style = borderStroke)
                    } else {
                        drawRoundRect(fillBrush, cornerRadius = corner)
                        drawRoundRect(glowBrush, cornerRadius = corner)
                        drawRoundRect(
                            Color.White.copy(alpha = 0.07f),
                            topLeft = Offset(1.5.dp.toPx(), 1.5.dp.toPx()),
                            size = Size(
                                (size.width - 3.dp.toPx()).coerceAtLeast(0f),
                                (size.height - 3.dp.toPx()).coerceAtLeast(0f),
                            ),
                            cornerRadius = innerCorner,
                            style = Stroke(0.8.dp.toPx()),
                        )
                        drawRoundRect(borderBrush, cornerRadius = corner, style = borderStroke)
                    }
                }
            }
            .clickable(
                interactionSource = interactions,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clearAndSetSemantics {}
                .drawWithCache {
                    val r = size.minDimension * 0.46f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val haloBrush = Brush.radialGradient(
                        listOf(
                            gold.copy(alpha = 0.28f),
                            rose.copy(alpha = 0.14f),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = r * 1.18f,
                    )
                    val wellBrush = Brush.radialGradient(
                        listOf(Color(0xFF2E263D), Color(0xFF171B2A)),
                        center = center,
                        radius = r,
                    )
                    val rimBrush = Brush.linearGradient(listOf(Color(0xFFFFF8EA), gold, rose))
                    val rimStroke = Stroke(1.15.dp.toPx())
                    val eInkStroke = Stroke(1.dp.toPx())
                    val inkArgb = if (colors.isEInk) foreground.toArgb() else null
                    onDrawBehind {
                        if (colors.isEInk) {
                            drawCircle(colors.surface, r, center)
                            drawCircle(foreground, r, center, style = eInkStroke)
                        } else {
                            drawCircle(haloBrush, radius = r * 1.18f, center = center)
                            drawCircle(wellBrush, radius = r, center = center)
                            drawCircle(rimBrush, radius = r, center = center, style = rimStroke)
                        }
                        drawIntoCanvas { composeCanvas ->
                            val native = composeCanvas.nativeCanvas
                            val saved = native.save()
                            native.scale(density, density)
                            val iconDp = 22f
                            val offsetDp = (34f - iconDp) / 2f
                            jewelPainter.paw(
                                native,
                                offsetDp,
                                offsetDp + 0.5f,
                                iconDp,
                                palette,
                                1f,
                                inkArgb,
                            )
                            native.restoreToCount(saved)
                        }
                    }
                },
        )
        Text(
            label,
            Modifier.weight(1f),
            color = foreground,
            fontFamily = CrystalFonts.Serif,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.3.sp,
            textAlign = TextAlign.Center,
        )
        Box(
            Modifier
                .size(28.dp)
                .clearAndSetSemantics {}
                .drawWithCache {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val r = size.minDimension * 0.44f
                    val ringStroke = Stroke(0.9.dp.toPx())
                    val chevron = Path().apply {
                        moveTo(center.x - 2.2.dp.toPx(), center.y - 4.2.dp.toPx())
                        lineTo(center.x + 2.4.dp.toPx(), center.y)
                        lineTo(center.x - 2.2.dp.toPx(), center.y + 4.2.dp.toPx())
                    }
                    val chevronStroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    val sparkX = center.x - 5.2.dp.toPx()
                    val sparkY = center.y - 4.2.dp.toPx()
                    val spark = Path().apply {
                        moveTo(sparkX, sparkY - 2.2.dp.toPx())
                        lineTo(sparkX + 0.7.dp.toPx(), sparkY - 0.7.dp.toPx())
                        lineTo(sparkX + 2.2.dp.toPx(), sparkY)
                        lineTo(sparkX + 0.7.dp.toPx(), sparkY + 0.7.dp.toPx())
                        lineTo(sparkX, sparkY + 2.2.dp.toPx())
                        lineTo(sparkX - 0.7.dp.toPx(), sparkY + 0.7.dp.toPx())
                        lineTo(sparkX - 2.2.dp.toPx(), sparkY)
                        lineTo(sparkX - 0.7.dp.toPx(), sparkY - 0.7.dp.toPx())
                        close()
                    }
                    onDrawBehind {
                        if (!colors.isEInk) {
                            drawCircle(gold.copy(alpha = 0.12f), radius = r, center = center)
                            drawCircle(gold.copy(alpha = 0.32f), radius = r, center = center, style = ringStroke)
                        }
                        drawPath(chevron, color = if (colors.isEInk) foreground else gold, style = chevronStroke)
                        if (!colors.isEInk) {
                            drawPath(spark, Color(0xFFFFF8EA).copy(alpha = 0.85f))
                        }
                    }
                },
        )
    }
}

internal const val CRYSTAL_FELINE_INVITATION_MIN_HEIGHT_DP = 58f

internal fun crystalFelineInvitationPressScale(
    pressed: Boolean,
    enabled: Boolean,
    eInk: Boolean,
    motionAllowed: Boolean,
): Float = if (pressed && enabled && !eInk && motionAllowed) .985f else 1f
