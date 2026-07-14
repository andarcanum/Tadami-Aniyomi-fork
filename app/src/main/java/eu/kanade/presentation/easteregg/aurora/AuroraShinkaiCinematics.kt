package eu.kanade.presentation.easteregg.aurora

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * «Синкай-кинематика»: переиспользуемые декоративные слои в духе
 * «Твоего имени» (Kimi no Na wa.) и «Дитя погоды» (Tenki no Ko):
 *
 *  - [KatawareDokiVeil] — сумеречный «час кого-то» (かたわれ時): индиго вверху,
 *    роза и янтарь у горизонта, тёплое солнце за краем мира;
 *  - [AuroraCometShower] — одинокая падающая комета с хвостом (Тиамат);
 *  - [AuroraLightMotes] — парящие пылинки/боке света;
 *  - [AuroraAnamorphicFlare] — анаморфный блик линзы для пиковых кадров;
 *  - [AuroraGodRays] — тёплые лучи света сквозь облака (Tenki no Ko);
 *  - [AuroraLightRain] — «дождь света», разлетающийся после вспышки.
 *
 * Все слои — чистый Canvas (работают на любом API), дешёвые по кадру:
 * маленькое число частиц, без аллокаций в draw-фазе, касания не
 * перехватывают, секретных данных не содержат (палитра общедоступная).
 */
object ShinkaiPalette {
    val Indigo = Color(0xFF16224E)
    val Rose = Color(0xFFFF7BAA)
    val Amber = Color(0xFFFFB56B)
    val Peach = Color(0xFFFFD9A0)
    val SkyCyan = Color(0xFF9BD8FF)
    val StarWhite = Color(0xFFF4FAFF)
}

/** Общий покадровый секундомер для декоративных слоёв. */
@Composable
fun rememberAuroraFrameSeconds(): State<Float> = produceState(0f) {
    val start = withFrameNanos { it }
    while (true) {
        withFrameNanos { now -> value = (now - start) / 1_000_000_000f }
    }
}

private fun fractOf(x: Float): Float = x - floor(x)

private fun hashOf(i: Int): Float = fractOf(sin(i * 127.1f) * 43758.547f)

/**
 * Сумеречная вуаль «часа кого-то» (かたわれ時).
 * Кладите поверх фона-сияния; [alpha] управляет силой заката (0..1).
 */
@Composable
fun KatawareDokiVeil(
    modifier: Modifier = Modifier,
    alpha: Float = 0.35f,
) {
    if (alpha <= 0.01f) return
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                0.00f to ShinkaiPalette.Indigo.copy(alpha = 0.10f * alpha),
                0.55f to Color.Transparent,
                0.78f to ShinkaiPalette.Rose.copy(alpha = 0.20f * alpha),
                0.92f to ShinkaiPalette.Amber.copy(alpha = 0.30f * alpha),
                1.00f to ShinkaiPalette.Peach.copy(alpha = 0.38f * alpha),
            ),
        )
        // Тёплое «солнце за горизонтом»
        val sun = Offset(size.width * 0.5f, size.height * 1.08f)
        val sunR = size.width * 0.8f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ShinkaiPalette.Amber.copy(alpha = 0.28f * alpha),
                    Color.Transparent,
                ),
                center = sun,
                radius = sunR,
            ),
            radius = sunR,
            center = sun,
        )
    }
}

/**
 * Парящие пылинки света (боке). Медленно всплывают и мерцают.
 */
@Composable
fun AuroraLightMotes(
    modifier: Modifier = Modifier,
    count: Int = 20,
    alpha: Float = 0.5f,
    tint: Color = ShinkaiPalette.StarWhite,
) {
    val time by rememberAuroraFrameSeconds()
    Canvas(modifier = modifier.fillMaxSize()) {
        repeat(count) { i ->
            val sx = hashOf(i * 3 + 1)
            val sy = hashOf(i * 3 + 2)
            val sp = 0.012f + 0.020f * hashOf(i * 3 + 3)
            val x = fractOf(sx + 0.03f * sin(time * (0.3f + 0.4f * sx) + i))
            val y = fractOf(sy - time * sp)
            val tw = 0.4f + 0.6f * ((sin(time * (0.8f + sx) + i) + 1f) / 2f)
            val pos = Offset(x * size.width, y * size.height)
            val r = 1.8f + 4.5f * hashOf(i * 7 + 5)
            drawCircle(tint.copy(alpha = 0.10f * alpha * tw), radius = r * 3.2f, center = pos)
            drawCircle(tint.copy(alpha = 0.55f * alpha * tw), radius = r, center = pos)
        }
    }
}

/**
 * Падающая комета с хвостом — «ночь кометы» из «Твоего имени».
 * Комета редкая: видна лишь в первой трети каждого цикла [periodSeconds].
 */
@Composable
fun AuroraCometShower(
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    periodSeconds: Float = 9f,
    comets: Int = 1,
) {
    val time by rememberAuroraFrameSeconds()
    Canvas(modifier = modifier.fillMaxSize()) {
        repeat(comets) { c ->
            val cycle = time / periodSeconds + c * 0.5f
            val n = floor(cycle).toInt()
            val local = fractOf(cycle) / 0.34f
            if (local < 1f) {
                val seed = hashOf(n * 13 + c * 7 + 3)
                val startX = size.width * (0.15f + 0.75f * seed)
                val startY = -size.height * 0.05f
                val ang = (118f + 14f * hashOf(n * 17 + c)) * (3.1415927f / 180f)
                val dirX = cos(ang)
                val dirY = sin(ang)
                val travel = size.height * 0.85f
                val head = Offset(startX + dirX * travel * local, startY + dirY * travel * local)
                val fadeIn = (local / 0.12f).coerceAtMost(1f)
                val fadeOut = ((1f - local) / 0.25f).coerceAtMost(1f)
                val a = alpha * fadeIn * fadeOut
                val tailLen = size.height * 0.22f
                val tail = Offset(head.x - dirX * tailLen, head.y - dirY * tailLen)
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            ShinkaiPalette.SkyCyan.copy(alpha = 0.55f * a),
                            ShinkaiPalette.StarWhite.copy(alpha = 0.90f * a),
                        ),
                        start = tail,
                        end = head,
                    ),
                    start = tail,
                    end = head,
                    strokeWidth = 3.5f,
                    cap = StrokeCap.Round,
                )
                drawCircle(ShinkaiPalette.StarWhite.copy(alpha = 0.90f * a), radius = 3.5f, center = head)
                drawCircle(ShinkaiPalette.SkyCyan.copy(alpha = 0.30f * a), radius = 10f, center = head)
            }
        }
    }
}

/**
 * Анаморфный блик линзы: горизонтальный луч + короткий вертикальный + ядро.
 * [progress] 0..1 — и рождение, и затухание задаёт вызывающий.
 */
@Composable
fun AuroraAnamorphicFlare(
    modifier: Modifier = Modifier,
    progress: Float,
    color: Color = ShinkaiPalette.SkyCyan,
) {
    if (progress <= 0.01f) return
    Canvas(modifier = modifier.fillMaxSize()) {
        val c = center
        val w = size.width * (0.25f + 0.75f * progress)
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, color.copy(alpha = 0.75f * progress), Color.Transparent),
                startX = c.x - w,
                endX = c.x + w,
            ),
            start = Offset(c.x - w, c.y),
            end = Offset(c.x + w, c.y),
            strokeWidth = 2.5f + 2.5f * progress,
            cap = StrokeCap.Round,
        )
        val h = size.height * 0.12f * progress
        drawLine(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    ShinkaiPalette.StarWhite.copy(alpha = 0.60f * progress),
                    Color.Transparent,
                ),
                startY = c.y - h,
                endY = c.y + h,
            ),
            start = Offset(c.x, c.y - h),
            end = Offset(c.x, c.y + h),
            strokeWidth = 2f,
            cap = StrokeCap.Round,
        )
        drawCircle(ShinkaiPalette.StarWhite.copy(alpha = 0.85f * progress), radius = 5f + 14f * progress, center = c)
        drawCircle(color.copy(alpha = 0.25f * progress), radius = 30f + 80f * progress, center = c)
    }
}

/**
 * Тёплые лучи света из-за верхнего края (свет сквозь облака, Tenki no Ko).
 * Медленно вращаются вокруг точки над экраном.
 */
@Composable
fun AuroraGodRays(
    modifier: Modifier = Modifier,
    alpha: Float = 0.3f,
    color: Color = ShinkaiPalette.Peach,
) {
    val time by rememberAuroraFrameSeconds()
    Canvas(modifier = modifier.fillMaxSize()) {
        if (alpha <= 0.01f) return@Canvas
        val pivot = Offset(size.width * 0.5f, -size.height * 0.15f)
        repeat(6) { i ->
            val degrees = (i * 60f + time * 1.7f) % 360f
            rotate(degrees = degrees, pivot = pivot) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(color.copy(alpha = 0.16f * alpha), Color.Transparent),
                        startY = pivot.y,
                        endY = size.height,
                    ),
                    topLeft = Offset(pivot.x - size.width * 0.045f, pivot.y),
                    size = Size(size.width * 0.09f, size.height * 1.4f),
                )
            }
        }
    }
}

/**
 * «Дождь света»: тёплые и холодные штрихи, разлетающиеся из центра после
 * вспышки и опадающие вниз. [progress] 0..1 — фаза разлёта.
 */
@Composable
fun AuroraLightRain(
    modifier: Modifier = Modifier,
    progress: Float,
    alpha: Float = 1f,
) {
    if (progress <= 0.01f || progress >= 1f) return
    Canvas(modifier = modifier.fillMaxSize()) {
        val c = center
        repeat(26) { i ->
            val a0 = hashOf(i * 5 + 2) * 6.2831855f
            val speed = 0.55f + 0.75f * hashOf(i * 5 + 3)
            val d = size.minDimension * 0.5f * progress * speed
            val g = size.height * 0.35f * progress * progress
            val pos = Offset(c.x + cos(a0) * d, c.y + sin(a0) * d * 0.6f + g)
            val len = 14f + 26f * hashOf(i * 5 + 4)
            val fade = (1f - progress) * alpha
            val col = when (i % 3) {
                0 -> ShinkaiPalette.Rose
                1 -> ShinkaiPalette.SkyCyan
                else -> ShinkaiPalette.Peach
            }
            drawLine(
                color = col.copy(alpha = 0.7f * fade),
                start = pos,
                end = Offset(pos.x, pos.y + len * (0.4f + progress)),
                strokeWidth = 2.2f,
                cap = StrokeCap.Round,
            )
        }
    }
}
