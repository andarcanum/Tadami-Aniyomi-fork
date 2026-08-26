package eu.kanade.presentation.components

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Общий рендер фона «Чернила в воде» (V6).
 * Шейдер рисуется ТОЛЬКО на API 33+ (изоляция RuntimeShader — как WeepingVoidShader),
 * всё остальное — статичный фолбэк. Тёмная вариация повторяет экран Цитат 1-в-1,
 * светлая — бледная чернильная отмывка на пергаменте (утверждена прототипом
 * prototype_ink_water_light.html: α≈0.18, чернильный индиго).
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class InkWaterShader private constructor() {

    private val shader = RuntimeShader(INK_WATER_AGSL)
    private val brush = ShaderBrush(shader)

    /** [density] — базовая плотность чернил: 0.30f (тёмная) / 0.18f (светлая). */
    fun DrawScope.drawInkWater(
        timeSec: Float,
        deepColor: Color,
        midColor: Color,
        accentColor: Color,
        density: Float,
    ) {
        synchronized(shader) {
            shader.setFloatUniform("u_res", size.width, size.height)
            shader.setFloatUniform("iTime", timeSec)
            shader.setFloatUniform("u_colorDeep", deepColor.red, deepColor.green, deepColor.blue)
            shader.setFloatUniform("u_colorMid", midColor.red, midColor.green, midColor.blue)
            shader.setFloatUniform("u_colorAccent", accentColor.red, accentColor.green, accentColor.blue)
            shader.setFloatUniform("u_alphaScale", density)
            drawRect(brush = brush)
        }
    }

    companion object {
        private var instance: InkWaterShader? = null

        fun getInstance(): InkWaterShader {
            return instance ?: synchronized(this) {
                instance ?: InkWaterShader().also { instance = it }
            }
        }
    }
}

/** Палитра вариации темы для чернильного фона. */
internal data class InkWaterPalette(
    val deep: Color,
    val mid: Color,
    val accent: Color,
    val background: Color,
    val dark: Boolean,
)

internal fun inkWaterPalette(
    accent: Color,
    accentVariant: Color,
    background: Color,
    dark: Boolean,
): InkWaterPalette {
    return if (dark) {
        InkWaterPalette(
            deep = Color(
                red = (accentVariant.red * 0.30f + 0.02f).coerceIn(0f, 1f),
                green = (accentVariant.green * 0.30f + 0.02f).coerceIn(0f, 1f),
                blue = (accentVariant.blue * 0.30f + 0.05f).coerceIn(0f, 1f),
            ),
            mid = accentVariant,
            accent = accent,
            background = background,
            dark = true,
        )
    } else {
        // Светлая тема: осветлённые чернила на пергаменте (значения согласованы
        // с утверждённым прототипом светлой вариации).
        InkWaterPalette(
            deep = Color(
                red = (accentVariant.red * 0.35f + 0.55f).coerceIn(0f, 1f),
                green = (accentVariant.green * 0.35f + 0.52f).coerceIn(0f, 1f),
                blue = (accentVariant.blue * 0.35f + 0.58f).coerceIn(0f, 1f),
            ),
            mid = Color(
                red = (accentVariant.red * 0.45f + 0.45f).coerceIn(0f, 1f),
                green = (accentVariant.green * 0.45f + 0.42f).coerceIn(0f, 1f),
                blue = (accentVariant.blue * 0.45f + 0.47f).coerceIn(0f, 1f),
            ),
            accent = accent,
            background = background,
            dark = false,
        )
    }
}

/** Виньетка поверх чернил: затемнение к низу (тёмная) или мягкий тон (светлая). */
internal fun DrawScope.drawInkWaterVignette(
    deepColor: Color,
    backgroundColor: Color,
    dark: Boolean,
) {
    drawRect(
        brush = Brush.verticalGradient(
            listOf(
                deepColor.copy(alpha = if (dark) 0.22f else 0.10f),
                Color.Transparent,
                Color.Transparent,
                backgroundColor.copy(alpha = if (dark) 0.35f else 0.18f),
            ),
        ),
    )
}

/** Статичный фолбэк без шейдера (API < 33, отключённые анимации, power save). */
internal fun DrawScope.drawInkWaterFallback(
    accent: Color,
    accentVariant: Color,
    dark: Boolean,
) {
    drawRect(
        brush = Brush.verticalGradient(
            listOf(accentVariant.copy(alpha = if (dark) 0.35f else 0.20f), Color.Transparent),
        ),
    )
    drawCircle(
        brush = Brush.radialGradient(
            listOf(accent.copy(alpha = if (dark) 0.12f else 0.08f), Color.Transparent),
            center = Offset(size.width * 0.85f, size.height * 0.25f),
            radius = size.width * 0.9f,
        ),
        radius = size.width * 0.9f,
        center = Offset(size.width * 0.85f, size.height * 0.25f),
    )
}

// Единый источник AGSL для экрана Цитат и награды Сокровищницы.
// Отличие от исходного INK_AGLS: плотность вынесена в uniform u_alphaScale.
private const val INK_WATER_AGSL = """
    uniform float2 u_res;
    uniform float iTime;
    uniform half3 u_colorDeep;
    uniform half3 u_colorMid;
    uniform half3 u_colorAccent;
    uniform float u_alphaScale;

    float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453123); }

    float noise(float2 p) {
        float2 i = floor(p);
        float2 f = fract(p);
        float2 u = f * f * (3.0 - 2.0 * f);
        return mix(
            mix(hash(i), hash(i + float2(1.0, 0.0)), u.x),
            mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x),
            u.y
        );
    }

    float fbm(float2 p) {
        float v = 0.0;
        float a = 0.5;
        for (int i = 0; i < 4; i++) {
            v += a * noise(p);
            p = p * 2.03 + float2(11.7, 7.3);
            a *= 0.5;
        }
        return v;
    }

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / u_res;
        float2 p = uv * float2(u_res.x / u_res.y, 1.0) * 2.2;
        float t = iTime * 0.05;

        // Двойной domain warp — «чернильный вихрь» (органика без идеальных кругов).
        float2 q = float2(fbm(p + float2(0.0, t)), fbm(p + float2(5.2, 1.3) - t * 0.7));
        float2 r = float2(
            fbm(p + 2.4 * q + float2(1.7, 9.2) + t * 0.6),
            fbm(p + 2.4 * q + float2(8.3, 2.8) - t * 0.4)
        );
        float v = fbm(p + 2.0 * r);

        float ink = smoothstep(0.35, 0.85, v);
        ink = pow(ink, 1.6);

        half3 col = mix(u_colorDeep, u_colorMid, half(smoothstep(0.2, 0.7, v)));
        col = mix(col, u_colorAccent, half(smoothstep(0.65, 0.95, v)) * half(0.55));

        // Плотность смещена к шапке, но внизу сохраняется живая структура (0.80)
        float topWeight = mix(0.80, 1.0, smoothstep(0.0, 0.45, 1.0 - uv.y));
        float edgeFade = smoothstep(1.35, 0.35, length(uv - float2(0.5, 0.50)));
        float alpha = ink * u_alphaScale * topWeight * edgeFade;

        return half4(col * half(alpha), half(alpha));
    }
"""
