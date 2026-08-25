package eu.kanade.presentation.library.novel.quotes

import android.graphics.RuntimeShader
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.LocalIsEInkMode

/**
 * V6 «Чернила в воде» — полноэкранный фоновый шейдер для экрана цитат.
 *
 * - API 33+: анимированный AGSL-шейдер (fbm + двойной domain warp), каппинг ~30 fps.
 * - Reduced motion: тот же шейдер, но один статичный кадр.
 * - API < 33 или e-ink: статичный мягкий градиент (без анимации и шейдера).
 * Рисуется ТОЛЬКО на тёмной теме (вызов изолирован на месте использования).
 */
private const val FRAME_INTERVAL_NS = 33_000_000L // ~30 fps — туману достаточно

private const val INK_AGLS = """
    uniform float2 u_res;
    uniform float iTime;
    uniform half3 u_colorDeep;
    uniform half3 u_colorMid;
    uniform half3 u_colorAccent;

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

        // Плотность смещена к шапке, края затухают — текст не трогаем.
        float topWeight = mix(0.55, 1.0, smoothstep(0.0, 0.45, 1.0 - uv.y));
        float edgeFade = smoothstep(1.15, 0.35, length(uv - float2(0.5, 0.45)));
        float alpha = ink * 0.30 * topWeight * edgeFade;

        return half4(col * half(alpha), half(alpha));
    }
"""

@Composable
fun InkWaterBackground(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isEInk = LocalIsEInkMode.current
    val colors = AuroraTheme.colors
    val accent = colors.accent
    val accentVariant = colors.accentVariant
    val backgroundColor = colors.background

    val deepColor = remember(accentVariant) {
        Color(
            red = (accentVariant.red * 0.30f + 0.02f).coerceIn(0f, 1f),
            green = (accentVariant.green * 0.30f + 0.02f).coerceIn(0f, 1f),
            blue = (accentVariant.blue * 0.30f + 0.05f).coerceIn(0f, 1f),
        )
    }

    val animatorScale = remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
    }
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { RuntimeShader(INK_AGLS) }.getOrNull()
        } else {
            null
        }
    }
    val animate = shader != null && !isEInk && animatorScale > 0f
    val timeSec = remember { mutableFloatStateOf(20f) }

    LaunchedEffect(animate) {
        val activeShader = if (animate) shader else null
        if (activeShader == null) return@LaunchedEffect
        var lastFrameNs = 0L
        val startNs = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                if (now - lastFrameNs >= FRAME_INTERVAL_NS) {
                    lastFrameNs = now
                    timeSec.floatValue = (now - startNs) / 1_000_000_000f
                }
            }
        }
    }

    Canvas(modifier = modifier) {
        if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            shader.setFloatUniform("u_res", size.width, size.height)
            shader.setFloatUniform("iTime", timeSec.floatValue)
            shader.setFloatUniform("u_colorDeep", deepColor.red, deepColor.green, deepColor.blue)
            shader.setFloatUniform("u_colorMid", accentVariant.red, accentVariant.green, accentVariant.blue)
            shader.setFloatUniform("u_colorAccent", accent.red, accent.green, accent.blue)
            drawRect(
                brush = object : ShaderBrush() {
                    override fun createShader(size: androidx.compose.ui.geometry.Size) = shader
                },
            )
        } else {
            // Статичный фолбэк: мягкое акцентное пятно сверху + увод вправо.
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        accentVariant.copy(alpha = 0.35f),
                        Color.Transparent,
                    ),
                ),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(size.width * 0.85f, size.height * 0.25f),
                    radius = size.width * 0.9f,
                ),
                radius = size.width * 0.9f,
                center = Offset(size.width * 0.85f, size.height * 0.25f),
            )
        }

        // Шейд-виньетка поверх чернил (плотность к шапке, затемнение к низу под цвет темы).
        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    deepColor.copy(alpha = 0.25f),
                    Color.Transparent,
                    Color.Transparent,
                    backgroundColor.copy(alpha = 0.45f),
                ),
            ),
        )
    }
}
