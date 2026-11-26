package eu.kanade.presentation.easteregg.crystal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class SilkMote(
    val x: Float,
    val y: Float,
    val radiusDp: Float,
    val speed: Float,
    val phase: Float,
    val tone: Int,
)

private val OPEN_SILK_MOTES = listOf(
    SilkMote(0.14f, 0.24f, 1.3f, 0.90f, 0.4f, 0),
    SilkMote(0.36f, 0.16f, 1.0f, 1.10f, 1.7f, 1),
    SilkMote(0.62f, 0.26f, 1.45f, 0.80f, 2.9f, 0),
    SilkMote(0.78f, 0.46f, 1.2f, 1.25f, 4.1f, 2),
    SilkMote(0.90f, 0.19f, 1.1f, 0.95f, 5.0f, 0),
    SilkMote(0.24f, 0.52f, 1.1f, 1.05f, 3.3f, 1),
    SilkMote(0.51f, 0.56f, 0.95f, 0.75f, 0.9f, 2),
)

/** Ex Libris signature title: italic serif, breathing 4-point star, and fading gold thread. */
@Composable
internal fun CrystalTitlePill(palette: CrystalPalette, animate: Boolean, modifier: Modifier = Modifier) {
    val colors = AuroraTheme.colors
    val policy = rememberCrystalMotionPolicy()
    val moving = animate && policy.systemAnimations && !colors.isEInk
    val time = rememberCrystalTime(moving, minFrameIntervalNanos = 32_000_000L)

    val gold = if (colors.isDark) Color(palette.gold) else lerp(Color(palette.gold), Color(0xFF6B4716), 0.62f)
    val rose = if (colors.isDark) Color(palette.rose) else lerp(Color(palette.rose), Color(0xFF7B385C), 0.52f)
    val shimmerHi = if (colors.isDark) Color(0xFFFFF8EC) else Color(0xFFFFF7E8)

    val textStyle = remember(colors.isEInk, colors.textPrimary, gold, rose, shimmerHi) {
        val base = TextStyle(
            fontFamily = CrystalFonts.Serif,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.35.sp,
            color = if (colors.isEInk) colors.textPrimary else gold,
        )
        if (colors.isEInk) {
            base
        } else {
            base.copy(brush = Brush.horizontalGradient(listOf(gold, shimmerHi, rose, gold)))
        }
    }
    val hairlineBrush = remember(colors.isEInk, colors.textPrimary, gold, rose) {
        if (colors.isEInk) {
            Brush.horizontalGradient(
                listOf(colors.textPrimary.copy(alpha = 0.55f), Color.Transparent),
            )
        } else {
            Brush.horizontalGradient(
                0f to gold.copy(alpha = 0.72f),
                0.55f to rose.copy(alpha = 0.45f),
                1f to Color.Transparent,
            )
        }
    }
    val unitStarPath = remember {
        Path().apply {
            val r = 1f
            val inner = 0.24f
            moveTo(0f, -r)
            lineTo(inner, -inner)
            lineTo(r, 0f)
            lineTo(inner, inner)
            lineTo(0f, r)
            lineTo(-inner, inner)
            lineTo(-r, 0f)
            lineTo(-inner, -inner)
            close()
        }
    }

    Column(
        modifier = modifier.width(IntrinsicSize.Max),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Canvas(Modifier.size(10.dp)) {
                val sec = if (moving) time.value / 1000f else 0f
                val starPulse = if (moving) 0.5f + 0.5f * sin(sec * (2f * PI.toFloat() / 3.8f)) else 0.7f
                val starAlpha = if (colors.isEInk) 1f else 0.64f + 0.36f * starPulse
                val starScale = if (colors.isEInk) 1f else 0.92f + 0.20f * starPulse
                val cx = size.width * 0.5f
                val cy = size.height * 0.5f
                val r = size.minDimension * 0.46f * starScale
                if (!colors.isEInk) {
                    drawCircle(
                        color = gold.copy(alpha = 0.26f * starAlpha),
                        radius = size.minDimension * 0.64f,
                        center = Offset(cx, cy),
                    )
                }
                val s = drawContext.canvas
                s.save()
                s.translate(cx, cy)
                s.scale(r, r)
                drawPath(
                    unitStarPath,
                    color = if (colors.isEInk) colors.textPrimary else gold.copy(alpha = starAlpha),
                )
                s.restore()
            }
            Text(
                text = stringResource(AYMR.strings.unlockable_title_crystal_light_keeper),
                style = textStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(hairlineBrush),
        )
    }
}

@Composable
internal fun CrystalProfileTitle(modifier: Modifier = Modifier) {
    val appearance = rememberCrystalAppearance()
    if (appearance.title) CrystalTitlePill(appearance.palette, !appearance.reduced, modifier)
}

/** Local profile atmosphere; never modifies global app-theme or aura preferences. */
@Composable
internal fun CrystalSilkBackdrop(modifier: Modifier = Modifier, openCanvas: Boolean = false) {
    val appearance = rememberCrystalAppearance()
    if (!appearance.silk) return
    CrystalSilkAtmosphere(appearance.palette, !appearance.reduced, modifier, openCanvas = openCanvas)
}

/**
 * Lunar Zenith atmosphere:
 * - [openCanvas] = true: full-bleed open-sky glow for the borderless Home Hub header.
 * - [openCanvas] = false: rich velvet-silk wash for framed Treasury & preview cards.
 */
@Composable
internal fun CrystalSilkAtmosphere(
    palette: CrystalPalette,
    animate: Boolean,
    modifier: Modifier = Modifier,
    openCanvas: Boolean = false,
) {
    val colors = AuroraTheme.colors
    val policy = rememberCrystalMotionPolicy()
    val moving = animate && policy.systemAnimations && !colors.isEInk
    val time = rememberCrystalTime(moving)
    val wavePath = remember { Path() }

    Canvas(
        if (openCanvas) {
            modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        } else {
            modifier
        },
    ) {
        if (size.width <= 1f || size.height <= 1f) return@Canvas
        if (colors.isEInk) {
            if (!openCanvas) {
                drawRoundRect(
                    colors.textSecondary,
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(1.dp.toPx()),
                )
            }
            return@Canvas
        }

        val sec = time.value / 1000f
        val toneMult = if (colors.isDark) 1f else 0.82f
        val colGold = if (colors.isDark) Color(palette.gold) else lerp(Color(palette.gold), Color(0xFF7B541B), 0.58f)
        val colRose = if (colors.isDark) Color(palette.rose) else lerp(Color(palette.rose), Color(0xFF8B4268), 0.50f)
        val colMint = if (colors.isDark) Color(palette.mint) else lerp(Color(palette.mint), Color(0xFF1F6E61), 0.52f)
        val colDeep = Color(palette.deep)

        if (openCanvas) {
            val w = size.width
            val h = size.height
            val drift = sin(sec * 0.42f) * w * 0.04f

            // Primary warm lunar zenith wash from top-right (near avatar), fading smoothly to 0
            val r1 = maxOf(w * 0.82f, h * 1.05f).coerceAtLeast(1f)
            val c1 = Offset(w * 0.74f + drift, h * 0.14f)
            drawRect(
                brush = Brush.radialGradient(
                    0f to colGold.copy(alpha = 0.24f * toneMult),
                    0.42f to colRose.copy(alpha = 0.11f * toneMult),
                    1f to Color.Transparent,
                    center = c1,
                    radius = r1,
                ),
            )

            // Counter-balance cool pearl/mint mist on top-left (behind greeting & nickname)
            val r2 = maxOf(w * 0.65f, h * 0.90f).coerceAtLeast(1f)
            val c2 = Offset(w * 0.22f - drift * 0.7f, h * 0.22f)
            drawRect(
                brush = Brush.radialGradient(
                    0f to colMint.copy(alpha = 0.14f * toneMult),
                    0.55f to colDeep.copy(alpha = 0.09f * toneMult),
                    1f to Color.Transparent,
                    center = c2,
                    radius = r2,
                ),
            )

            // Whisper-soft top rim light from y = 0 behind status bar
            drawRect(
                brush = Brush.verticalGradient(
                    0f to colGold.copy(alpha = 0.12f * toneMult),
                    1f to Color.Transparent,
                    startY = 0f,
                    endY = (h * 0.34f).coerceAtLeast(1f),
                ),
            )

            // Gentle twinkling starlight dust motes in upper open zone
            for (m in OPEN_SILK_MOTES) {
                val twk = 0.2f + 0.8f * (0.5f + 0.5f * sin(sec * m.speed * 1.3f + m.phase))
                val mx = (m.x + sin(sec * 0.22f + m.phase) * 0.012f) * w
                val my = m.y * h * 0.72f
                val tint = when (m.tone) {
                    0 -> colGold
                    1 -> colRose
                    else -> colMint
                }
                drawCircle(
                    color = tint.copy(alpha = 0.56f * twk * toneMult),
                    radius = m.radiusDp.dp.toPx(),
                    center = Offset(mx, my),
                )
            }

            // Guarantee seamless fade to pure 0 alpha before the bottom edge — never a hard horizontal cut.
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.White,
                    0.46f to Color.White,
                    0.78f to Color.White.copy(alpha = 0.26f),
                    0.97f to Color.Transparent,
                    1f to Color.Transparent,
                ),
                blendMode = BlendMode.DstIn,
            )
        } else {
            val w = size.width
            val h = size.height
            val drift = sin(sec * 0.5f) * w * 0.03f

            drawRect(
                brush = Brush.radialGradient(
                    0f to colGold.copy(alpha = 0.28f * toneMult),
                    0.55f to colRose.copy(alpha = 0.14f * toneMult),
                    1f to Color.Transparent,
                    center = Offset(w * 0.22f + drift, h * 0.34f),
                    radius = (w * 0.62f).coerceAtLeast(1f),
                ),
            )
            drawRect(
                brush = Brush.radialGradient(
                    0f to colMint.copy(alpha = 0.22f * toneMult),
                    1f to Color.Transparent,
                    center = Offset(w * 0.86f - drift * 0.6f, h * 0.74f),
                    radius = (w * 0.56f).coerceAtLeast(1f),
                ),
            )

            wavePath.reset()
            for (i in 0..24) {
                val u = i / 24f
                val x = u * w
                val y = h * 0.62f + sin(u * 2f * PI.toFloat() + sec * 0.55f) * h * 0.14f
                if (i == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
            }
            drawPath(
                path = wavePath,
                color = colGold.copy(alpha = 0.24f * toneMult),
                style = Stroke(width = 1.2.dp.toPx()),
            )

            for (i in 0 until 5) {
                val m = OPEN_SILK_MOTES[i]
                val twk = 0.25f + 0.75f * (0.5f + 0.5f * cos(sec * m.speed * 1.4f + m.phase))
                drawCircle(
                    color = colGold.copy(alpha = 0.45f * twk * toneMult),
                    radius = (m.radiusDp * 0.85f).dp.toPx(),
                    center = Offset(m.x * w, (0.18f + (i % 3) * 0.26f) * h),
                )
            }
        }
    }
}

@Composable
internal fun CrystalBackgroundOverlay(modifier: Modifier = Modifier) {
    val appearance = rememberCrystalAppearance()
    if (appearance.paws) CrystalPawsBackground(modifier.clipToBounds(), appearance.palette, !appearance.reduced)
}
