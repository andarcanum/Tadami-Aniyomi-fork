package eu.kanade.presentation.components

import android.graphics.Bitmap
import android.graphics.BitmapShader
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import eu.kanade.domain.ui.UserProfilePreferences
import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Path as AndroidPath

/* =============================================================================
 *  ШАГ 3 — "REALITY BREACH" (кинематографичный финал в читалке).
 *
 *  Концепция: оболочка реальности (страница читалки) трескается настоящими
 *  ветвящимися трещинами от точки КАЖДОГО свайпа; сквозь щели проступает
 *  "изнанка" — живой системный лор-код. На 5-м свайпе появляется терминал
 *  "Принять пустоту?". После подтверждения оболочка ОТСЛАИВАЕТСЯ кусками
 *  внутрь растущей раскалённой дыры (сингулярность), затем вспышка + удар
 *  тишины и brutalist-раскрытие наград "Красный Сектор".
 *
 *  Всё рисуется на Canvas (работает на всех API — отдельный fallback не нужен).
 *  Тайминги вынесены в константы ниже (значения выверены в HTML-прототипе).
 *
 *  Единый конечный автомат:  Fracture → Brink → Collapse → FlashBlack → Reveal
 * ========================================================================== */

enum class MeltdownPhase { Fracture, Brink, Collapse, FlashBlack, Reveal }

// ---- ТАЙМИНГИ (из прототипа, пресет пользователя) ---------------------------
// ACT I  FRACTURE управляется свайпами (5 шагов), поэтому FRACTURE_MS —
// ориентир на анимацию доращивания трещины после каждого свайпа.
// Все тайминги замедлены ×2 против прежних значений (медленнее в 2 раза).
const val BREACH_FRACTURE_STEP_MS = 2800 // доращивание трещины на 1 свайп
const val BREACH_BRINK_HOLD_MS = 12000 // терминал / kernel exposed
const val BREACH_COLLAPSE_MS = 10400 // отслаивание кусками в дыру
const val BREACH_FLASH_BLACK_MS = 5600 // вспышка + удар тишины
const val BREACH_REVEAL_POWERON_MS = 10000 // CRT power-on раскрытия
const val BREACH_CARD_STAGGER_MS = 1500 // сдвиг появления карт наград
const val BREACH_ACT_GAP_MS = 3000 // пауза 3.0с ПОСЛЕ каждого акта

// FEEL
const val BREACH_CODE_SCROLL = 1.20f
const val BREACH_CRACK_BRANCH = 0.50f
const val BREACH_SHAKE_SCALE = 1.00f
const val BREACH_PEEL_SPIN = 1.00f

// Общий темп финала: 0.75× («медленнее»), как в утверждённом прототипе.
// Все базовые тайминги выше делятся на BREACH_TEMPO в каждой анимации акта.
const val BREACH_TEMPO = 0.75f
fun breachDur(baseMs: Int): Int = (baseMs / BREACH_TEMPO).toInt()

private const val PEEL_COLS = 9
// PEEL_ROWS removed — rows are now derived from tile width to produce square tiles

// =============================================================================
//  КОД-ШЕЙДЕР (изнанка)
// =============================================================================

/** Строки лор-кода загружаются через [AYMR] и передаются в [buildCodeBitmap]. */
private fun buildCodeBitmap(lines: List<String>): Bitmap {
    val w = 1700
    val lh = 60f
    val h = (lines.size * lh).toInt() + 100
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = AndroidCanvas(bmp)
    c.drawColor(android.graphics.Color.argb(255, 4, 0, 7))
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = 44f
    }
    val phosphor = GlitchPalette.Phosphor.toArgb()
    val red = GlitchPalette.HazardRed.toArgb()
    val white = android.graphics.Color.WHITE
    var y = lh
    var i = 0
    while (y < h - 40f) {
        val bright = (i * 7) % 11 == 0
        paint.color = when {
            bright -> white
            i % 3 == 0 -> red
            else -> phosphor
        }
        paint.alpha = if (bright) 235 else 150
        val line = lines[i % lines.size] // use passed-in lines
        val x = 120f + ((i * 83) % 420f)
        c.drawText(line, x, y, paint)
        y += lh
        i++
    }
    return bmp
}

@Composable
internal fun rememberCodeShader(): BitmapShader {
    val userProfilePreferences = remember { Injekt.get<UserProfilePreferences>() }
    val isRussian = remember { java.util.Locale.getDefault().language == "ru" }
    val observerName = remember(userProfilePreferences) {
        val raw = userProfilePreferences.name().get()
        val cleaned = raw.filter { it.isLetterOrDigit() || it.isWhitespace() || it == '-' || it == '_' }.trim()
        if (cleaned.isNotEmpty()) {
            cleaned
        } else {
            if (isRussian) "Странник" else "Wanderer"
        }
    }
    val lines = remember(isRussian, observerName) {
        if (isRussian) {
            listOf(
                "fun observe(reader: You): Void {",
                "  reality.integrity -= swipe.force",
                "  // ты не должен был досюда дойти",
                "  kernel.expose(sector = RED)",
                "  if (integrity <= 0) collapse()",
                "  val truth = pages.removeAll()",
                "  echo(\"в темноте кто-то смотрит\")",
                "  while (breathing) { fear++ }",
                "  0xDEAD.void.pull(you)",
                "  return Void(you)",
                "}",
                "SIGNAL// пустота помнит твоё имя",
                "trace: observer=$observerName pid=0x7F",
                "malloc(soul) -> null",
                "assert(exit == null)",
                "system.mercy = false",
                "[warn] стены реальности тоньше",
                "render(abyss, depth=INF)",
                "no.turning.back = true",
                "0101 0056 004F 0049 0044",
                "the.red.sector.awaits()",
                "> ПРИВЕТ. МЫ ВИДИМ ТЕБЯ.",
                "commit --amend reality",
                "kill -9 humanity",
                "// ОШИБКА: РЕАЛЬНОСТЬ_РАЗРУШЕНА",
                "void.consume(you)",
                "while(observer.present) { hide() }",
                "[critical] утечка памяти в сознании",
                "ERROR: 0x00000000 (NULL_EXISTENCE)",
                "val echo = void.listen(frequency = 0Hz)",
                "system.integrity = 0.000",
                "// не моргай",
                "override fun exists(): Boolean = false",
                "val abyss = depth.toInt()",
                "defragment(reality_core)",
                "// они кричат в стеке",
                "killProcess(humanity_thread)",
                "// книга читает тебя",
                "openGate(RED_SECTOR)",
                "// ошибка на строке 0: переполнение пустоты",
                "val eye = observer.look()",
                "delete(memory.lastChapter)",
                "// читатель был поглощен",
                "val signal = listenTo(abyss)",
                "while(true) { suffer() }",
                "// паника ядра: душа.неНайдена",
                "reality.status = STATUS_COLLAPSED",
                "system.shutdown(immediate = true)",
                "// кто стоит за экраном?",
                "val path = \"/dev/null/reality\"",
                "// след: $observerName был здесь",
                "val key = decrypt(red_sector)",
                "// внимание: уровень правды критический",
                "val echo = \"темнота\"",
                "// malloc failed: душа переполнена",
                "reality.fracture(point = swipe)",
                "// конец уже близко",
                "val observers = listAll()",
                "// потеряно соединение со вселенной",
                "val gate = Gate(0x7F)",
                "// ошибка: страница.содержимое = NULL",
                "val heartbeat = 0 // мертв",
                "// они смотрят с полей страницы",
                "val abyssScale = infinite",
                "// экран кровоточит",
                "val blood = drips.count()",
                "// целостность.реальности = 0%",
                "val sector = RED_SECTOR",
                "// достигнута терминальная точка",
                "val observer = observerName",
                "// пустота зовет",
                "val exitCode = 0xDEAD",
                "// ошибка: наблюдатель.активен = true",
                "val soul = soulInstance",
                "// реальность — это иллюзия"
            )
        } else {
            listOf(
                "fun observe(reader: You): Void {",
                "  reality.integrity -= swipe.force",
                "  // you were not supposed to reach this place",
                "  kernel.expose(sector = RED)",
                "  if (integrity <= 0) collapse()",
                "  val truth = pages.removeAll()",
                "  echo(\"someone is watching in the dark\")",
                "  while (breathing) { fear++ }",
                "  0xDEAD.void.pull(you)",
                "  return Void(you)",
                "}",
                "SIGNAL// void remembers your name",
                "trace: observer=$observerName pid=0x7F",
                "malloc(soul) -> null",
                "assert(exit == null)",
                "system.mercy = false",
                "[warn] the walls of reality are thinner",
                "render(abyss, depth=INF)",
                "no.turning.back = true",
                "0101 0056 004F 0049 0044",
                "the.red.sector.awaits()",
                "> HELLO. WE SEE YOU.",
                "commit --amend reality",
                "kill -9 humanity",
                "// EXCEPTION: REALITY_CORRUPTED",
                "void.consume(you)",
                "while(observer.present) { hide() }",
                "[critical] memory leak in consciousness",
                "ERROR: 0x00000000 (NULL_EXISTENCE)",
                "val echo = void.listen(frequency = 0Hz)",
                "system.integrity = 0.000",
                "// don't blink",
                "override fun exists(): Boolean = false",
                "val abyss = depth.toInt()",
                "defragment(reality_core)",
                "// they are screaming in the stack",
                "killProcess(humanity_thread)",
                "// the book is reading you",
                "openGate(RED_SECTOR)",
                "// error at line 0: void overflow",
                "val eye = observer.look()",
                "delete(memory.lastChapter)",
                "// the reader has been consumed",
                "val signal = listenTo(abyss)",
                "while(true) { suffer() }",
                "// kernel panic: soul.notFound",
                "reality.status = STATUS_COLLAPSED",
                "system.shutdown(immediate = true)",
                "// who is behind the screen?",
                "val path = \"/dev/null/reality\"",
                "// trace: $observerName was here",
                "val key = decrypt(red_sector)",
                "// warning: truth levels critical",
                "val echo = \"darkness\"",
                "// malloc failed: soul out of memory",
                "reality.fracture(point = swipe)",
                "// the end is near",
                "val observers = listAll()",
                "// connection lost to universe",
                "val gate = Gate(0x7F)",
                "// error: page.content = NULL",
                "val heartbeat = 0 // dead",
                "// they are watching from the margins",
                "val abyssScale = infinite",
                "// the screen is bleeding",
                "val blood = drips.count()",
                "// reality.integrity = 0%",
                "val sector = RED_SECTOR",
                "// terminal point reached",
                "val observer = observerName",
                "// the void is calling",
                "val exitCode = 0xDEAD",
                "// error: observer.active = true",
                "val soul = soulInstance",
                "// reality is an illusion"
            )
        }
    }
    return remember(lines) {
        BitmapShader(buildCodeBitmap(lines), Shader.TileMode.CLAMP, Shader.TileMode.REPEAT)
    }
}

/** Скролл код-шейдера (+опциональный зум от центра). */
internal fun BitmapShader.scrollTo(scrollPx: Float, zoom: Float, cx: Float, cy: Float) {
    val m = Matrix()
    val translateX = cx - 830f
    m.setTranslate(translateX, -scrollPx)
    if (zoom != 1f) m.postScale(zoom, zoom, cx, cy)
    setLocalMatrix(m)
}

// =============================================================================
//  МОДЕЛЬ ТРЕЩИН (нормализованные координаты 0..1, независимо от размера)
// =============================================================================

private class CrackPoint(val x: Float, val y: Float, val birth: Float)
private class Crack(val pts: List<CrackPoint>, val width: Float)
private class Impact(val x: Float, val y: Float, val birth: Float)
private class CrackField(val cracks: List<Crack>, val impacts: List<Impact>)

private const val BREACH_CENTER_X = 0.5f
private const val BREACH_CENTER_Y = 0.44f

private fun buildCrackField(seed: Long): CrackField {
    val rng = java.util.Random(seed)
    val cracks = ArrayList<Crack>()
    val impacts = ArrayList<Impact>()
    val impactCount = 5
    val perImpact = 2 + Math.round(BREACH_CRACK_BRANCH * 3f)

    fun grow(x0: Float, y0: Float, ang0: Float, len: Float, birth0: Float, depth: Int) {
        val segs = 14
        val step = len / segs
        val span = 0.17f
        val pts = ArrayList<CrackPoint>()
        pts.add(CrackPoint(x0, y0, birth0))
        var bx = x0
        var by = y0
        var ba = ang0
        for (i in 1..segs) {
            ba += (rng.nextFloat() - 0.5f) * 0.55f
            bx += cos(ba) * step
            by += sin(ba) * step
            val birth = min(1f, birth0 + (i.toFloat() / segs) * span)
            pts.add(CrackPoint(bx, by, birth))
            if (depth < 2 && rng.nextFloat() < 0.28f * (0.4f + BREACH_CRACK_BRANCH) && i > 2) {
                val turn = if (rng.nextBoolean()) 1f else -1f
                grow(bx, by, ba + turn * (0.6f + rng.nextFloat() * 0.5f), len * 0.5f, birth, depth + 1)
            }
        }
        cracks.add(Crack(pts, 3.2f - depth * 0.9f))
    }

    for (k in 0 until impactCount) {
        val ang0 = rng.nextFloat() * (Math.PI * 2).toFloat()
        val rad = if (k == 0) 0.03f else 0.10f + rng.nextFloat() * 0.22f
        val ix = BREACH_CENTER_X + cos(ang0) * rad
        val iy = BREACH_CENTER_Y + sin(ang0) * rad
        val birth0 = k.toFloat() / impactCount
        impacts.add(Impact(ix, iy, birth0))
        for (j in 0 until perImpact) {
            val a = (j.toFloat() / perImpact) * (Math.PI * 2).toFloat() + rng.nextFloat() * 0.7f
            grow(ix, iy, a, 0.5f, birth0, 0)
        }
    }
    return CrackField(cracks, impacts)
}

private fun hash01(i: Int): Float {
    val x = sin(i * 127.1f) * 43758.547f
    return x - floor(x)
}

private fun easeIn(x: Float): Float = x * x * x
private fun easeOut(x: Float): Float = 1f - (1f - x) * (1f - x) * (1f - x)
private fun clamp01(x: Float): Float = max(0f, min(1f, x))

// =============================================================================
//  ACT I — SHATTER HOLE: радиальная рваная дыра + лучи-трещины (свайпы 1..5)
// =============================================================================

private class ShatterRay(val angle: Float, val lenFactor: Float, val segCount: Int, val seed: Int)
private class ShatterHoleData(val angleNoise: FloatArray, val rays: List<ShatterRay>)

private fun buildShatterHoleData(rayCount: Int = 16): ShatterHoleData {
    val noiseN = 24
    val angleNoise = FloatArray(noiseN) { i -> 0.5f + hash01(i * 7 + 11) * 0.9f }
    val rays = (0 until rayCount).map { i ->
        val baseAngle = (i.toFloat() / rayCount) * (Math.PI * 2).toFloat() +
            (hash01(i * 3 + 5) - 0.5f) * 0.25f
        val lenFactor = 0.35f + hash01(i * 10 + 3) * 0.85f
        val segCount = 4 + (hash01(i * 5 + 7) * 4f).toInt()
        ShatterRay(baseAngle, lenFactor, segCount, i * 137)
    }
    return ShatterHoleData(angleNoise, rays)
}

private fun shatterHoleRadiusAt(angle: Float, baseR: Float, noise: FloatArray): Float {
    val n = noise.size
    val fi = ((angle / (Math.PI.toFloat() * 2f)) % 1f + 1f) % 1f * n
    val i0 = fi.toInt() % n
    val i1 = (i0 + 1) % n
    val t = fi - fi.toInt()
    val v = noise[i0] + (noise[i1] - noise[i0]) * t
    val jag = 0.45f
    return baseR * (1f - jag * 0.5f + v * jag)
}

/**
 * Оверлей SHATTER HOLE поверх читалки: прозрачен всюду, кроме
 * растущей рваной дыры и лучей-трещин от неё — в них показывается
 * лор-код (изнанка). Рисуется НАД [MeltdownSwipeGlitch].
 *
 * [swipe] 0..5 — каждый свайп увеличивает радиус дыры на 20%.
 */
@Composable
fun MeltdownFractureOverlay(
    swipe: Int,
    modifier: Modifier = Modifier,
) {
    if (swipe <= 0) return
    val time by rememberGlitchTime()
    val codeShader = rememberCodeShader()
    val holeData = remember { buildShatterHoleData() }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(swipe) {
        progress.animateTo(
            (swipe.toFloat() / 5f).coerceIn(0f, 1f),
            tween(breachDur(BREACH_FRACTURE_STEP_MS), easing = FastOutSlowInEasing),
        )
    }
    // Дрожание усиливается к 5-му свайпу (до 1.0 прогресса)
    val shakeAmt = if (swipe >= 5) {
        BREACH_SHAKE_SCALE * (1.2f + progress.value * 2.8f)
    } else {
        BREACH_SHAKE_SCALE * (progress.value * 0.6f)
    }
    Canvas(modifier = modifier.fillMaxSize()) {
        val q = quakeOffset(time, shakeAmt)
        withTransform({ translate(q.x, q.y) }) {
            drawShatterHoleOverlay(
                time = time,
                progress = progress.value,
                holeData = holeData,
                codeShader = codeShader,
            )
        }
    }
    // HUD целостности реальности
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        val pct = ((1f - progress.value) * 100f).toInt()
        val filled = ((1f - progress.value) * 16f).toInt()
        Text(
            text = "REALITY INTEGRITY",
            color = GlitchPalette.Phosphor,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "█".repeat(filled) + "░".repeat(16 - filled) + "  $pct%",
            color = GlitchPalette.HazardRed,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Рисует: рваная дыра (jagged radial hole) + лучи-трещины, все области
 * показывают скроллящийся лор-код (изнанку). Края — алое неоновое свечение.
 */
private fun DrawScope.drawShatterHoleOverlay(
    time: Float,
    progress: Float,
    holeData: ShatterHoleData,
    codeShader: BitmapShader,
) {
    val w = size.width
    val h = size.height
    val cx = BREACH_CENTER_X * w
    val cy = BREACH_CENTER_Y * h
    val maxR = max(w, h) * 0.62f
    val baseR = progress * maxR
    if (baseR <= 1f) return

    val unit = size.minDimension / 360f
    val scrollPx = time * BREACH_CODE_SCROLL * 46f
    codeShader.scrollTo(scrollPx, 1f, cx, cy)
    val codeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = codeShader }
    val nc = drawContext.canvas.nativeCanvas
    val segs = 64

    // На 5-м свайпе (когда progress идет от 0.8 до 1.0) плавно делаем фон абсолютно черным,
    // чтобы читалка полностью скрылась и не просвечивала.
    val bgAlpha = clamp01((progress - 0.8f) / 0.2f)
    if (bgAlpha > 0f) {
        drawRect(color = Color.Black.copy(alpha = bgAlpha), size = size)
    }

    // --- Строим путь рваной дыры ---
    fun buildHolePath(): AndroidPath {
        val p = AndroidPath()
        for (i in 0..segs) {
            val a = (i.toFloat() / segs) * Math.PI.toFloat() * 2f
            val r = shatterHoleRadiusAt(a, baseR, holeData.angleNoise)
            val px = cx + cos(a) * r
            val py = cy + sin(a) * r
            if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
        }
        p.close()
        return p
    }
    val holePath = buildHolePath()

    // --- Заполняем дыру кодом ---
    nc.save()
    nc.clipPath(holePath)
    nc.drawRect(0f, 0f, w, h, codeFillPaint)
    nc.restore()

    // --- Лучи-трещины (появляются после progress > 0.15, скрываются на финальном шаге) ---
    val rayProg = if (progress >= 1f) 0f else max(0f, min(1f, (progress - 0.15f) / 0.85f))
    if (rayProg > 0f) {
        for (ray in holeData.rays) {
            val startR = shatterHoleRadiusAt(ray.angle, baseR, holeData.angleNoise)
            val totalLen = ray.lenFactor * maxR * 1.4f * rayProg
            val endR = startR + totalLen
            val width0 = 6f * unit * (1f - rayProg * 0.4f)

            val cx0 = cx + cos(ray.angle) * startR
            val cy0 = cy + sin(ray.angle) * startR
            // точки центральной оси луча
            val pts = (1..ray.segCount).map { s ->
                val t = s.toFloat() / ray.segCount
                val r = startR + (endR - startR) * t
                val wob = (hash01(ray.seed + s * 2) - 0.5f) * 0.12f
                val a = ray.angle + wob
                cx + cos(a) * r to cy + sin(a) * r
            }

            // полигон луча (клин от дыры наружу)
            val rayPath = AndroidPath()
            rayPath.moveTo(cx0, cy0)
            pts.forEach { (px, py) -> rayPath.lineTo(px, py) }
            val perp = ray.angle + Math.PI.toFloat() / 2f
            for (s in pts.indices.reversed()) {
                val ww = width0 * (1f - s.toFloat() / pts.size)
                rayPath.lineTo(pts[s].first + cos(perp) * ww, pts[s].second + sin(perp) * ww)
            }
            rayPath.lineTo(cx0 + cos(perp) * width0, cy0 + sin(perp) * width0)
            rayPath.close()

            // заполняем луч кодом
            nc.save()
            nc.clipPath(rayPath)
            nc.drawRect(0f, 0f, w, h, codeFillPaint)
            nc.restore()

            // светящийся край луча (алый/белый)
            val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = max(0.6f, 1.3f * unit)
                color = android.graphics.Color.argb(
                    (220 * rayProg).toInt().coerceIn(0, 255),
                    255,
                    180,
                    180,
                )
                maskFilter = BlurMaskFilter(8f * unit, BlurMaskFilter.Blur.NORMAL)
            }
            val edgePath = AndroidPath()
            edgePath.moveTo(cx0, cy0)
            pts.forEach { (px, py) -> edgePath.lineTo(px, py) }
            nc.drawPath(edgePath, edgePaint)
        }
    }

    // --- Кромка дыры: широкое алое свечение + горячий белый контур (скрывается на финальном шаге) ---
    if (progress < 1f) {
        val rimGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(2f, 6f * unit)
            color = GlitchPalette.HazardRed.copy(alpha = 0.4f).toArgb()
            maskFilter = BlurMaskFilter(18f * unit, BlurMaskFilter.Blur.NORMAL)
        }
        val rimHotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(0.8f, 2.2f * unit)
            color = android.graphics.Color.argb(242, 255, 255, 255)
            maskFilter = BlurMaskFilter(5f * unit, BlurMaskFilter.Blur.NORMAL)
        }
        nc.drawPath(holePath, rimGlowPaint)
        nc.drawPath(holePath, rimHotPaint)
    }

    // вспышка в эпицентре при ударе (только на раннем прогрессе)
    if (progress < 0.35f) {
        val fl = 1f - progress / 0.35f
        val rr = (12f + fl * 40f) * unit
        val grad = RadialGradient(
            cx,
            cy,
            rr.coerceAtLeast(1f),
            intArrayOf(
                android.graphics.Color.argb((200 * fl).toInt().coerceIn(0, 255), 255, 255, 255),
                android.graphics.Color.argb((130 * fl).toInt().coerceIn(0, 255), 255, 0, 60),
                android.graphics.Color.argb(0, 255, 0, 60),
            ),
            floatArrayOf(0f, 0.35f, 1f),
            Shader.TileMode.CLAMP,
        )
        val fp = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = grad }
        nc.drawCircle(cx, cy, rr.coerceAtLeast(1f), fp)
    }
}

// =============================================================================
//  ACT III–V — COLLAPSE → FLASH/BLACK → REVEAL (авто-таймлайн после терминала)
// =============================================================================

/**
 * Кинематографичный финал после подтверждения в терминале. Непрерывный слой:
 * оболочка отслаивается кусками в раскалённую дыру → вспышка + удар тишины →
 * brutalist-раскрытие наград (через [VoidRevealScreen]).
 *
 * Вызывать вместо старой цепочки VoidCollapseOverlay + VoidDisintegrationOverlay
 * (+ последующего VoidRevealScreen). Заголовок/лор/список берутся из
 * [VoidRevealScreen].
 */
@Composable
fun VoidRealityBreachFinale(
    rewards: List<VoidReward>,
    screenshot: Bitmap? = null,
    justUnlocked: Boolean = false,
    onEnterTreasury: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val time by rememberGlitchTime()
    val codeShader = rememberCodeShader()
    val field = remember { buildCrackField(0xBEEFL) }
    val imageBitmap = remember(screenshot) { screenshot?.asImageBitmap() }

    var phase by remember { mutableStateOf(MeltdownPhase.Collapse) }
    val collapse = remember { Animatable(0f) }
    val fb = remember { Animatable(0f) } // flash+black прогресс 0..1
    val blackBeat = remember { Animatable(0f) } // удар тишины (послесвечение) 0..1
    val buildUpGlitch = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay(breachDur(BREACH_ACT_GAP_MS).toLong()) // пауза перед стартом схлопывания
        collapse.animateTo(1f, tween(breachDur(BREACH_COLLAPSE_MS), easing = LinearEasing))
        delay(breachDur(BREACH_ACT_GAP_MS).toLong()) // пауза ПОСЛЕ акта COLLAPSE
        phase = MeltdownPhase.FlashBlack
        fb.animateTo(1f, tween(breachDur(BREACH_FLASH_BLACK_MS), easing = LinearEasing))
        
        // 1. Удар тишины: 1.5с чёрного — послесвечение дыры на сетчатке + звон-кольцо (реж. правка)
        blackBeat.animateTo(1f, tween(breachDur(1500), easing = LinearEasing))
        
        // 2. Постепенно глитчи появляются сильнее и сильнее (1.5 секунды)
        buildUpGlitch.animateTo(1f, tween(breachDur(1500), easing = LinearEasing))
        
        // 3. Открывается финальный экран наград
        phase = MeltdownPhase.Reveal
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (phase == MeltdownPhase.Reveal) {
            VoidRevealScreen(
                rewards = rewards,
                justUnlocked = justUnlocked,
                onEnterTreasury = onEnterTreasury,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // flash/black только в фазе FlashBlack (fb.value=0 во время Collapse
            // давало flash=1.0 → белый экран на весь Act III).
            val isFlashPhase = phase == MeltdownPhase.FlashBlack
            val flash = if (isFlashPhase) clamp01(1f - fb.value * 2.4f) else 0f
            val black = if (isFlashPhase) clamp01((fb.value - 0.25f) / 0.5f) else 0f
            val shakeAmt = BREACH_SHAKE_SCALE * (0.6f + collapse.value * 1.3f)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val q = quakeOffset(time, shakeAmt)
                withTransform({ translate(q.x, q.y) }) {
                    drawPeelCollapse(time, collapse.value, field, codeShader, imageBitmap)
                }
                if (flash > 0f) drawRect(color = Color.White.copy(alpha = flash), size = size)
                if (black > 0f) drawRect(color = Color.Black.copy(alpha = black), size = size)
                if (isFlashPhase && blackBeat.value > 0f) drawSilenceAfterimage(blackBeat.value)
            }

            // Легкие разгоняющиеся глитчи на черном фоне перед раскрытием
            if (buildUpGlitch.value > 0.001f) {
                GlitchStack(
                    intensity = buildUpGlitch.value * 0.4f,
                    modifier = Modifier.fillMaxSize(),
                    config = MeltdownPresets.Rift,
                    content = {},
                )
            }
        }
    }
}

private fun DrawScope.drawPeelCollapse(
    time: Float,
    collapse: Float,
    field: CrackField,
    codeShader: BitmapShader,
    imageBitmap: ImageBitmap?,
) {
    val w = size.width
    val h = size.height
    val cx = BREACH_CENTER_X * w
    val cy = BREACH_CENTER_Y * h
    val nc = drawContext.canvas.nativeCanvas

    // 1) КОД-БЕЗДНА под оболочкой: полный код-слой с зумом вглубь и угасанием альфы
    val textAlpha = if (collapse <= 0.5f) {
        255
    } else {
        (255 * (1f - (collapse - 0.5f) / 0.5f)).toInt().coerceIn(0, 255)
    }
    val cz = 1f + 0.22f * easeIn(collapse)
    codeShader.scrollTo(time * BREACH_CODE_SCROLL * 46f, cz, cx, cy)
    val codeFill = Paint().apply {
        shader = codeShader
        alpha = textAlpha
    }
    nc.drawColor(android.graphics.Color.argb(255, 4, 0, 7)) // черный фоновый цвет
    nc.drawRect(0f, 0f, w, h, codeFill)

    // 2) ОБОЛОЧКА отслаивается плитами внутрь дыры (ближние к центру — первыми)
    val cols = PEEL_COLS
    val tw = w / cols // ширина плиты
    val th = tw // квадратные плиты (th == tw)
    val rows = kotlin.math.ceil(h / tw).toInt().coerceAtLeast(1)
    // maxD — реальное максимальное расстояние от центра до любого угла сетки.
    // Без этого d > 1 для угловых плит → они никогда не достигали lp=1 → оставались видны.
    val maxD = maxOf(
        hypot(cx.toDouble(), cy.toDouble()),
        hypot((w - cx).toDouble(), cy.toDouble()),
        hypot(cx.toDouble(), (h - cy).toDouble()),
        hypot((w - cx).toDouble(), (h - cy).toDouble()),
    ).toFloat()
    val plate = Color(0xFF0B0A0C)
    for (gy in 0 until rows) {
        for (gx in 0 until cols) {
            val tx = gx * tw + tw / 2f
            val ty = gy * th + th / 2f
            val d = hypot((tx - cx).toDouble(), (ty - cy).toDouble()).toFloat() / maxD
            val lp = clamp01((collapse * 1.65f - d) / 0.55f)
            if (lp >= 1f) continue
            val e = easeIn(lp)
            // реж. правка: гравитационная воронка — плиты закручиваются к центру
            var dir = atan2((cy - ty).toDouble(), (cx - tx).toDouble()).toFloat()
            dir += e * 1.5f * BREACH_PEEL_SPIN
            val pull = e * hypot((cx - tx).toDouble(), (cy - ty).toDouble()).toFloat() * 0.92f
            val nx = tx + cos(dir) * pull
            val ny = ty + sin(dir) * pull
            val sc = 1f - 0.8f * e
            val rotDeg = (hash01(gy * cols + gx + 1) - 0.5f) * BREACH_PEEL_SPIN * 1.7f * e * 57.2958f
            val alpha = 1f - e * e
            withTransform({
                translate(nx - tx, ny - ty)
                rotate(rotDeg, pivot = Offset(tx, ty))
                scale(sc, sc, pivot = Offset(tx, ty))
            }) {
                if (imageBitmap != null) {
                    val srcX = (gx * tw).toInt().coerceIn(0, imageBitmap.width - 1)
                    val srcY = (gy * th).toInt().coerceIn(0, imageBitmap.height - 1)
                    val srcW = tw.toInt().coerceAtMost(imageBitmap.width - srcX).coerceAtLeast(1)
                    val srcH = th.toInt().coerceAtMost(imageBitmap.height - srcY).coerceAtLeast(1)
                    drawImage(
                        image = imageBitmap,
                        srcOffset = IntOffset(srcX, srcY),
                        srcSize = IntSize(srcW, srcH),
                        dstOffset = IntOffset(srcX, srcY),
                        dstSize = IntSize(srcW, srcH),
                        alpha = alpha,
                    )
                } else {
                    drawRect(
                        color = plate.copy(alpha = alpha),
                        topLeft = Offset(gx * tw, gy * th),
                        size = Size(tw, th),
                    )
                }
                // тонкая раскалённая кромка плиты
                drawRect(
                    color = GlitchPalette.HazardRed.copy(alpha = alpha * 0.35f),
                    topLeft = Offset(gx * tw, gy * th),
                    size = Size(tw, th),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f),
                )
            }
        }
    }

    // 2b) реж. добавка: искры, втянутые в раскалённое ядро (аддитивно на тёмном фоне)
    val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    for (i in 0 until 70) {
        val ph = (time * 0.5f + hash01(i * 9)) % 1f
        val life = (ph + collapse) % 1f
        val rr = (1f - life) * max(w, h) * 0.55f
        val a = hash01(i * 5) * (Math.PI * 2).toFloat() + life * 3f
        val px = cx + cos(a) * rr
        val py = cy + sin(a) * rr
        val s = (1f - life) * 2.2f * collapse
        if (s <= 0f) continue
        val la = life.coerceIn(0f, 1f)
        sparkPaint.color = if (hash01(i) > 0.5f) {
            android.graphics.Color.argb((la * 255f).toInt(), 255, 255, 255)
        } else {
            android.graphics.Color.argb((la * 255f).toInt(), 255, 0, 60)
        }
        nc.drawRect(px, py, px + s, py + s, sparkPaint)
    }

    // 3) РАСКАЛЁННАЯ ГЛОТКА дыры (сингулярность)
    val coreFactor = if (collapse <= 0.5f) {
        collapse
    } else {
        0.5f + (collapse - 0.5f) * 4.5f
    }
    val coreR = (0.03f + 0.11f * coreFactor) * size.minDimension
    val centerAlpha = if (collapse <= 0.5f) {
        (0.35f + 0.5f * collapse)
    } else {
        0.60f + (collapse - 0.5f) * 0.8f
    }
    val core = RadialGradient(
        cx,
        cy,
        coreR.coerceAtLeast(1f),
        intArrayOf(
            android.graphics.Color.argb((centerAlpha.coerceIn(0f, 1f) * 255).toInt(), 255, 255, 255),
            android.graphics.Color.argb(
                (140 + 70 * clamp01(collapse - 0.5f) / 0.5f).toInt().coerceIn(0, 255),
                255,
                0,
                60,
            ),
            android.graphics.Color.argb(0, 255, 0, 60),
        ),
        floatArrayOf(0f, 0.25f, 1f),
        Shader.TileMode.CLAMP,
    )
    val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = core }
    nc.drawCircle(cx, cy, coreR.coerceAtLeast(1f), corePaint)
}

// =============================================================================
//  ACT IV — УДАР ТИШИНЫ: послесвечение дыры на сетчатке + звон-кольцо (реж. правка)
// =============================================================================
private fun DrawScope.drawSilenceAfterimage(blackBeat: Float) {
    val w = size.width
    val h = size.height
    val cx = BREACH_CENTER_X * w
    val cy = BREACH_CENTER_Y * h
    val nc = drawContext.canvas.nativeCanvas

    // призрак дыры угасает
    val gh = (1f - blackBeat) * 0.5f
    if (gh > 0.01f) {
        val gg = RadialGradient(
            cx, cy, 120f,
            intArrayOf(
                android.graphics.Color.argb((gh * 255f).toInt().coerceIn(0, 255), 120, 0, 30),
                android.graphics.Color.argb(0, 0, 0, 0),
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        val gp = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = gg }
        nc.drawCircle(cx, cy, 120f, gp)
    }

    // звон-кольцо (tinnitus) расширяется
    val rp = blackBeat
    val rr = rp * max(w, h) * 0.7f
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f + (1f - rp) * 3f
        color = android.graphics.Color.argb(((1f - rp) * 0.4f * 255f).toInt().coerceIn(0, 255), 255, 0, 60)
    }
    nc.drawCircle(cx, cy, rr.coerceAtLeast(1f), ring)

    // оседающая пыль/угольки
    val dust = Paint(Paint.ANTI_ALIAS_FLAG)
    for (i in 0 until 40) {
        val px = hash01(i * 3) * w
        val py = (hash01(i * 7) * h + blackBeat * 40f * hash01(i)) % h
        val s = hash01(i * 11) * 1.4f
        dust.color = android.graphics.Color.argb(
            ((1f - blackBeat) * 0.5f * hash01(i * 2) * 255f).toInt().coerceIn(0, 255),
            255, 80, 90,
        )
        nc.drawRect(px, py, px + s, py + s, dust)
    }
}
