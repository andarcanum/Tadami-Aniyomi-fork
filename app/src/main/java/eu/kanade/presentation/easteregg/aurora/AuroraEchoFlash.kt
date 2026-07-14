package eu.kanade.presentation.easteregg.aurora

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.domain.easteregg.aurora.AuroraLocalization
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val FLASH_ANIM_MS = 4000
private const val FLASH_HOLD_MS = 500L
private const val SPARKS = 18

private fun seg(t: Float, a: Float, b: Float): Float = ((t - a) / (b - a)).coerceIn(0f, 1f)

private fun easeOut(x: Float): Float {
    val i = 1f - x
    return 1f - i * i * i
}

/**
 * Кинематографичная вспышка пройденного этапа (~4.5 с):
 *  1. Мир гаснет: затемнение + кашетирование (чёрные полосы).
 *  2. «Инверсия»: частицы стягиваются ИЗ краёв в центр (время вспять).
 *  3. Точка сжатия: белая микро-вспышка.
 *  4. Взрыв: три медленных кольца + искры наружу.
 *  5. Заголовок эха «съезжается» трекингом букв, точки прогресса.
 *  6. Всё растворяется в темноте.
 *
 * Показывать ТОЛЬКО на AuroraEcho.Progress. Касания не перехватывает.
 * stageIndex = число ПРОЙДЕННЫХ этапов.
 */
@Composable
fun AuroraEchoFlash(
    echoTitle: String,
    stageIndex: Int,
    totalStages: Int,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    bleedColor: Color? = null,
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(echoTitle, stageIndex) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(FLASH_ANIM_MS, easing = LinearEasing))
        delay(FLASH_HOLD_MS)
        onFinished()
    }
    val t = anim.value
    val fade = 1f - seg(t, 0.86f, 1f)
    val dim = seg(t, 0f, 0.10f) * fade
    val bars = seg(t, 0f, 0.14f) * fade
    val implode = easeOut(seg(t, 0.06f, 0.36f))
    val flashPoint = seg(t, 0.34f, 0.42f)
    val titleIn = easeOut(seg(t, 0.36f, 0.64f))
    val dotsIn = seg(t, 0.58f, 0.72f)

    // 3D-like camera shake + scale during explosion for more cinematic / volumetric feel
    val shake = if (t in 0.35f..0.85f) sin((t - 0.35f) * 25f) * (1f - (t - 0.35f) / 0.5f) * 8f else 0f
    val baseScale = if (t in 0.35f..0.85f) 1.02f else 1f
    val camScale = baseScale + (if (t in 0.4f..0.7f) (0.08f * sin((t - 0.4f) * (3.1415927f / 0.3f))) else 0f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = shake
                translationY = shake * 0.6f
                scaleX = camScale
                scaleY = camScale
                // slight perspective tilt during peak
                rotationY = if (t in 0.42f..0.65f) sin((t - 0.42f) * 18f) * 1.8f else 0f
            },
        contentAlignment = Alignment.Center,
    ) {
        // Лёгкий «наезд камеры» во время разлёта колец
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .scale(1f + 0.03f * easeOut(seg(t, 0.40f, 0.90f))),
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxR = size.minDimension * 0.44f

            // Затемнение мира + виньетка
            drawRect(Color.Black.copy(alpha = 0.6f * dim))
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f * dim)),
                    center = center,
                    radius = size.maxDimension * 0.7f,
                ),
            )

            // Кашетирование: чёрные полосы сверху и снизу
            val barH = size.height * 0.11f * bars
            if (barH > 0f) {
                drawRect(Color.Black, size = Size(size.width, barH))
                drawRect(
                    Color.Black,
                    topLeft = Offset(0f, size.height - barH),
                    size = Size(size.width, barH),
                )
            }

            // Фаза инверсии: частицы стягиваются к центру, с хвостами
            val impAlpha = implode * (1f - flashPoint) * fade
            if (impAlpha > 0.01f) {
                repeat(SPARKS) { i ->
                    val angle = i * 6.2831855f / SPARKS + 0.4f * i
                    val dist = maxR * (1.05f - implode)
                    val pos = Offset(
                        center.x + cos(angle) * dist,
                        center.y + sin(angle) * dist,
                    )
                    val tail = Offset(
                        center.x + cos(angle) * (dist + maxR * 0.10f),
                        center.y + sin(angle) * (dist + maxR * 0.10f),
                    )
                    val col = when (i % 3) {
                        0 -> AuroraPublicPalette.Green
                        1 -> AuroraPublicPalette.Blue
                        else -> AuroraPublicPalette.Violet
                    }
                    drawLine(
                        color = col.copy(alpha = 0.30f * impAlpha),
                        start = tail,
                        end = pos,
                        strokeWidth = 3f,
                    )
                    drawCircle(
                        color = col,
                        radius = 1.5f + 3f * implode,
                        center = pos,
                        alpha = 0.85f * impAlpha,
                    )
                }
            }

            // Точка сжатия: короткая белая вспышка
            if (flashPoint > 0f) {
                val fp = 1f - abs(flashPoint * 2f - 1f)
                drawCircle(
                    color = Color.White.copy(alpha = 0.75f * fp * fade),
                    radius = 8f + 70f * fp,
                    center = center,
                )
                drawCircle(
                    color = AuroraPublicPalette.Green.copy(alpha = 0.45f * fp * fade),
                    radius = 110f * fp,
                    center = center,
                )
            }

            // Взрыв: три медленных кольца со сдвигом по времени
            val rings = listOf(
                0.00f to AuroraPublicPalette.Green,
                0.05f to AuroraPublicPalette.Blue,
                0.10f to AuroraPublicPalette.Violet,
            )
            for ((lag, col) in rings) {
                val rp = easeOut(seg(t, 0.40f + lag, 0.84f + lag))
                if (rp > 0f && rp < 1f) {
                    drawCircle(
                        color = col.copy(alpha = 0.55f * (1f - rp) * fade),
                        radius = maxR * rp,
                        center = center,
                        style = Stroke(width = 3f + 16f * (1f - rp)),
                    )
                }
            }

            // Просачивание секретной палитры (data-driven; по умолчанию выкл.)
            bleedColor?.let { bc ->
                val bp = easeOut(seg(t, 0.46f, 0.92f))
                if (bp > 0f && bp < 1f) {
                    drawCircle(
                        color = bc.copy(alpha = 0.16f * (1f - bp) * fade),
                        radius = maxR * 1.1f * bp,
                        center = center,
                        style = Stroke(width = 22f * (1f - bp)),
                    )
                }
            }

            // Искры наружу, медленно гаснут
            val out = easeOut(seg(t, 0.42f, 0.92f))
            if (out > 0f) {
                repeat(SPARKS) { i ->
                    val angle = i * 6.2831855f / SPARKS + 0.7f + 0.33f * i
                    val dist = maxR * 0.95f * out
                    val pos = Offset(
                        center.x + cos(angle) * dist,
                        center.y + sin(angle) * dist,
                    )
                    val col = when (i % 3) {
                        0 -> AuroraPublicPalette.Violet
                        1 -> AuroraPublicPalette.Green
                        else -> AuroraPublicPalette.Blue
                    }
                    drawCircle(
                        color = col,
                        radius = 2f + 4f * (1f - out),
                        center = pos,
                        alpha = 0.8f * (1f - out) * fade,
                    )
                }
            }
        }

        // True AGSL volumetric explosion (god-rays + caustics + plasma folds via fbm noise in shader)
        // Layered during peak for depth; combined with 3D camera (shake/tilt/scale) + rings.
        if (flashPoint > 0.08f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.85f * flashPoint),
            ) {
                AuroraEchoExplosion(
                    intensity = flashPoint * 2.2f,
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = AuroraLocalization.translate(echoTitle).orEmpty(),
                color = Color(0xFFEAF6FF),
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold,
                // трекинг: буквы «съезжаются» из разряженного состояния
                letterSpacing = (12f - 9.5f * titleIn).sp,
                modifier = Modifier
                    .alpha(titleIn * fade)
                    .scale(0.96f + 0.04f * titleIn),
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(modifier = Modifier.alpha(dotsIn * fade)) {
                repeat(totalStages) { i ->
                    val reached = i < stageIndex
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (reached) 8.dp else 6.dp)
                            .background(
                                color = if (reached) {
                                    AuroraPublicPalette.Green
                                } else {
                                    Color(0x33EAF6FF)
                                },
                                shape = CircleShape,
                            ),
                    )
                }
            }
        }
    }
}
