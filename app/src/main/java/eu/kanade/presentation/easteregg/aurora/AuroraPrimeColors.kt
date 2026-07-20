package eu.kanade.presentation.easteregg.aurora

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import eu.kanade.domain.easteregg.aurora.AuroraPayload
import kotlinx.coroutines.delay
import kotlin.math.sin

/**
 * ЖИВАЯ тема AURORA_PRIME (v3.1).
 *
 * Концепция: наградная тема — не статичная палитра, а «металл под
 * северным небом»: акцентные цвета едва заметно бликуют — отблеск
 * гуляет по ним от НАКЛОНА устройства и медленного «дыхания»,
 * secondary чуть переливается в сторону accent (иридисценция).
 *
 * Все базовые цвета и параметры блика приходят из payload
 * (themeColors + themeMaterial) — до победы темы не существует в коде.
 *
 * Производительность: палитра обновляется ~11 раз/с (не каждый кадр):
 * для медленного перелива этого достаточно, а recomposition темы дёшев.
 * Быстрый 60-fps шейдерный металл — только на hero-поверхностях
 * через Modifier.auroraMetal (см. AuroraLivingMaterial.kt).
 */
data class AuroraPrimeColors(
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val background: Color,
    val surface: Color,
)

/**
 * Живая палитра AURORA_PRIME. null — тема ещё не разблокирована.
 * Если в payload нет themeMaterial — вернёт статичные цвета (как раньше).
 *
 * @param animated передай false (например, при энергосбережении) —
 *   получишь статичную палитру без тикера и сенсора.
 */
@Composable
fun rememberAuroraPrimeColors(
    payload: AuroraPayload?,
    animated: Boolean = true,
): AuroraPrimeColors? {
    val themeColors = payload?.themeColors

    fun hex(key: String): Color? = themeColors?.get(key)
        ?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }

    val primary = hex("primary") ?: AuroraPublicPalette.Green
    val secondary = hex("secondary") ?: AuroraPublicPalette.Violet
    val accent = hex("accent") ?: AuroraPublicPalette.Blue
    val background = hex("background") ?: AuroraPublicPalette.Night
    val surface = hex("surface") ?: Color(0xFF0A1626)
    val static = AuroraPrimeColors(primary, secondary, accent, background, surface)

    val material = AuroraMaterialSpec.from(payload)
    if (material == null || !animated) return static

    val tilt by rememberAuroraTilt()

    // Медленный тикер вместо покадровой анимации
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(90L)
            time += 0.09f
        }
    }

    // Блик: наклон устройства сдвигает фазу + медленное собственное дыхание
    val gleam = 0.5f + 0.5f * sin(time * 0.7f + tilt.x * 2.6f - tilt.y * 1.4f)
    // Нелинейно: блик «острый» и редкий, а не постоянное мерцание
    val g = gleam * gleam * material.gloss

    return AuroraPrimeColors(
        // Металлический отблеск: primary/accent вспыхивают к цвету блика
        primary = lerp(primary, material.sheenColor, 0.22f * g),
        accent = lerp(accent, material.sheenColor, 0.18f * g),
        // Иридисценция: secondary медленно переливается в сторону accent
        secondary = lerp(secondary, accent, 0.30f * material.iridescence * gleam),
        background = background,
        // Поверхности едва заметно теплеют от сияния
        surface = lerp(surface, primary, 0.04f * material.iridescence * gleam),
    )
}
