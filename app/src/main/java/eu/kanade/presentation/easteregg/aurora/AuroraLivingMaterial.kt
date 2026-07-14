package eu.kanade.presentation.easteregg.aurora

import android.content.Context
import android.graphics.RuntimeShader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.platform.LocalContext
import eu.kanade.domain.easteregg.aurora.AuroraPayload
import kotlin.math.sin

/**
 * «Живой материал» Авроры (v3.1).
 *
 * Концепция: тема-награда — не палитра, а ПОВЕРХНОСТЬ.
 * Спекулярный блик скользит по ней от НАКЛОНА устройства — как свет
 * по голографической карточке; поверх — тонкая иридисценция
 * (перелив оттенков, как у тонкой плёнки) и анизотропная «шлифовка».
 *
 * Все параметры — data-driven из payload.themeMaterial (живут в ваулте):
 *   "style": "aurora-metal" (обязательно), "base"/"sheen": hex-цвета,
 *   "iridescence"/"gloss": "0..1".
 *
 * Датчики: только акселерометр (без разрешений, ничего не пишем в лог).
 * Нет датчика (ТВ/эмулятор) — блик медленно дышит сам от времени.
 * Android < 13 — фолбэк: градиентная полоса света от того же наклона.
 */
data class AuroraMaterialSpec(
    val baseColor: Color,
    val sheenColor: Color,
    val iridescence: Float,
    val gloss: Float,
) {
    companion object {
        /** Собирает спек из payload; null — материал не заявлен (обычная тема). */
        fun from(payload: AuroraPayload?): AuroraMaterialSpec? {
            val material = payload?.themeMaterial ?: return null
            if (material["style"] != "aurora-metal") return null

            fun hex(value: String?): Color? = value
                ?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }

            fun num(key: String, def: Float): Float =
                material[key]?.toFloatOrNull()?.coerceIn(0f, 1f) ?: def

            val base = hex(material["base"])
                ?: hex(payload.themeColors?.get("surface"))
                ?: Color(0xFF0A1626)
            return AuroraMaterialSpec(
                baseColor = base,
                sheenColor = hex(material["sheen"]) ?: Color(0xFFEAF6FF),
                iridescence = num("iridescence", 0.4f),
                gloss = num("gloss", 0.8f),
            )
        }
    }
}

/** Наклон устройства в -1..1 с низкочастотным фильтром против дрожи. */
@Composable
fun rememberAuroraTilt(): State<Offset> {
    val context = LocalContext.current
    val tilt = remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(Unit) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val nx = (-event.values[0] / 9.81f).coerceIn(-1f, 1f)
                val ny = (event.values[1] / 9.81f).coerceIn(-1f, 1f)
                val prev = tilt.value
                tilt.value = Offset(
                    prev.x + (nx - prev.x) * 0.12f,
                    prev.y + (ny - prev.y) * 0.12f,
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (manager != null && sensor != null) {
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose { manager?.unregisterListener(listener) }
    }
    return tilt
}

private const val AURORA_METAL_SKSL = """
uniform float2 res;
uniform float  time;
uniform float2 tilt;
uniform float3 base;
uniform float3 sheen;
uniform float  irid;
uniform float  gloss;

half4 main(float2 frag) {
    float2 uv = frag / res;
    // Диагональная координата блика; наклон устройства двигает полосу
    float band = dot(uv - 0.5, normalize(float2(0.75, 0.66))) + 0.5;
    float pos = 0.5 + 0.42 * tilt.x - 0.18 * tilt.y + 0.04 * sin(time * 0.5);
    float d = (band - pos) * (5.0 + 6.0 * gloss);
    float spec = exp(-d * d);
    // Широкий вторичный отсвет вокруг полосы
    float wide = exp(-d * d * 0.12) * 0.35;
    // Иридисценция: перелив оттенков вдоль полосы (тонкая плёнка)
    float3 shift = 0.5 + 0.5 * cos(6.28318 * (band * 1.7 + tilt.y * 0.35) + float3(0.0, 2.09, 4.18));
    // Анизотропная «шлифовка» металла
    float grain = 0.5 + 0.5 * sin(frag.x * 0.55 + frag.y * 0.11);
    float3 col = base;
    col += sheen * (spec * 0.55 + wide) * gloss;
    col += shift * irid * 0.10 * (0.4 + 0.6 * spec);
    col *= 0.965 + 0.035 * grain;
    return half4(col, 1.0);
}
"""

/**
 * Подложка «живой металл». Вешать ВМЕСТО background после clip():
 *
 *     Modifier.clip(shape).then(spec?.let { Modifier.auroraMetal(it) } ?: Modifier.background(...))
 */
@Composable
fun Modifier.auroraMetal(spec: AuroraMaterialSpec): Modifier {
    val tilt by rememberAuroraTilt()
    val time by produceState(0f) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> value = (now - start) / 1_000_000_000f }
        }
    }
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shader = remember { RuntimeShader(AURORA_METAL_SKSL) }
        val brush = remember(shader) { ShaderBrush(shader) }
        drawBehind {
            shader.setFloatUniform("res", size.width, size.height)
            shader.setFloatUniform("time", time)
            shader.setFloatUniform("tilt", tilt.x, tilt.y)
            shader.setFloatUniform("base", spec.baseColor.red, spec.baseColor.green, spec.baseColor.blue)
            shader.setFloatUniform("sheen", spec.sheenColor.red, spec.sheenColor.green, spec.sheenColor.blue)
            shader.setFloatUniform("irid", spec.iridescence)
            shader.setFloatUniform("gloss", spec.gloss)
            drawRect(brush)
        }
    } else {
        // Fallback < API 33: полоса света градиентом, движимая тем же наклоном
        drawBehind {
            drawRect(spec.baseColor)
            val pos = 0.5f + 0.42f * tilt.x - 0.18f * tilt.y + 0.04f * sin(time * 0.5f)
            val dir = Offset(size.width, size.height * 0.88f)
            val start = Offset(dir.x * (pos - 0.35f), dir.y * (pos - 0.35f))
            val end = Offset(dir.x * (pos + 0.35f), dir.y * (pos + 0.35f))
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        spec.sheenColor.copy(alpha = 0.30f * spec.gloss),
                        Color.Transparent,
                    ),
                    start = start,
                    end = end,
                ),
            )
        }
    }
}
