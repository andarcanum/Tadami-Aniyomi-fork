package eu.kanade.presentation.easteregg.aurora

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import kotlin.math.floor
import kotlin.math.sin

/**
 * Анимированный фон «северное сияние» для экранов квеста.
 *
 * - Android 13+ (API 33): полноценный AGSL-шейдер (fbm-шум, три «занавеса»
 *   сияния, мерцающие звёзды).
 * - Ниже API 33: fallback — мягкий анимированный вертикальный градиент.
 *
 * ВАЖНО (анти-чит): палитра ниже — ОБЩЕДОСТУПНАЯ «северная» палитра
 * (приложение и так называется com.tadami.aurora — это идеальный камуфляж).
 * НЕ подставляйте сюда цвета секретной темы из payload — они приходят
 * только после расшифровки и передаются параметрами на финальном экране.
 */
private const val AURORA_SKSL = """
uniform float2 iResolution;
uniform float iTime;
uniform float iIntensity;
uniform float iVeilThinness; // for time-of-night cinematic effect (Nolan time as character)
uniform float iRitualIntensity; // player ritual depth for 3D/volumetric
uniform float3 cA;
uniform float3 cB;
uniform float3 cC;

float hash(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453123);
}

float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float a = hash(i);
    float b = hash(i + float2(1.0, 0.0));
    float c = hash(i + float2(0.0, 1.0));
    float d = hash(i + float2(1.0, 1.0));
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

float fbm(float2 p) {
    float v = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 5; i++) {
        v += amp * noise(p);
        p *= 2.0;
        amp *= 0.5;
    }
    return v;
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / iResolution;
    float veil = 0.5 + 0.5 * iVeilThinness; // time dilation effect
    float3 col = float3(0.015, 0.035, 0.075) * (1.2 - uv.y * 0.6 * veil);

    float sparkle = hash(floor(fragCoord / 2.0));
    float star = step(0.9985, sparkle);
    col += float3(star * (0.4 + 0.6 * sin(iTime * 2.0 + sparkle * 6.2831)) * 0.8);

    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float wave = fbm(float2(uv.x * 3.0 + fi * 7.31, iTime * 0.12 + fi * 2.7));
        float center = 0.30 + 0.28 * wave + fi * 0.07;
        float d = uv.y - center;
        // 3D depth simulation with ritual intensity
        float depth = 1.0 + iRitualIntensity * 0.5 * sin(fi);
        float curtain = exp(-d * d * 55.0 * depth);
        float shimmer = 0.35 + 0.65 * fbm(float2(uv.x * 7.0 - iTime * 0.25, fi * 3.17 + iTime * 0.05));
        float3 tone = mix(cA, cB, uv.x);
        if (i == 2) { tone = mix(tone, cC, 0.65); }
        col += tone * curtain * shimmer * iIntensity * (0.85 - fi * 0.22) * (1.0 + iRitualIntensity * 0.3);
    }

    // subtle time warp distortion for Nolan feel
    float warp = sin(uv.y * 10.0 + iTime * 0.5) * iVeilThinness * 0.01;
    col = mix(col, col * (1.0 + warp), 0.1);

    return half4(half3(col), 1.0);
}
"""

// New AGSL for 3D EchoFlashExplosion per plan AC6 / Shader Roadmap
private const val ECHO_FLASH_EXPLOSION_SKSL = """
uniform float2 iResolution;
uniform float iTime;
uniform float iIntensity;
uniform float3 cA;
uniform float3 cB;
uniform float3 cC;

float hash(float2 p) { return fract(sin(dot(p, float2(127.1,311.7))) * 43758.5453); }
float noise(float2 p) {
  float2 i = floor(p); float2 f = fract(p);
  float a = hash(i); float b = hash(i+float2(1,0)); float c = hash(i+float2(0,1)); float d = hash(i+float2(1,1));
  float2 u = f*f*(3.0-2.0*f);
  return mix(a, b, u.x) + (c - a)*u.y*(1.0-u.x) + (d - b)*u.x*u.y*(1.0-u.y);
}

half4 main(float2 fragCoord) {
    float2 uv = (fragCoord - 0.5 * iResolution) / iResolution.y;
    float r = length(uv);
    float ang = atan(uv.y, uv.x);
    float t = iTime * 2.0;
    float wave = sin(r * 10.0 - t) * 0.5 + 0.5;
    float3 col = mix(cA, cB, wave) * exp(-r * 3.0) * iIntensity;
    // volumetric god-ray / caustics + self-shadowing simulation (fbm noise)
    float vol = noise(uv * 8.0 + t * 0.7) * 0.6 + noise(uv * 18.0 - t * 1.3) * 0.4;
    col += cC * pow(1.0 - r, 4.5) * (0.6 + 0.8 * vol) * iIntensity * (0.7 + 0.3 * sin(ang * 5.0 + t));
    // angular plasma folds for depth
    col += mix(cB, cC, 0.5 + 0.5 * sin(ang * 3.0 + r * 12.0 - t * 1.5)) * (1.0 - r) * r * 0.8 * iIntensity;
    return half4(half3(col), 1.0 - r * 0.9);
}
"""

// New AGSL for SigilProjection - lines burn into living aurora with temporal echo
private const val SIGIL_PROJECTION_SKSL = """
uniform float2 iResolution;
uniform float iTime;
uniform float iIntensity;
uniform float3 cA;

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / iResolution;
    float d = length(uv - 0.5);
    float pulse = sin(iTime * 5.0 + d * 20.0) * 0.5 + 0.5;
    float3 col = cA * (1.0 - d) * pulse * iIntensity;
    return half4(half3(col), 0.8);
}
"""

/** Общедоступная «камуфляжная» палитра сияния (НЕ секретная тема!). */
object AuroraPublicPalette {
    val Green = Color(0xFF3DDC97)
    val Blue = Color(0xFF5E8BFF)
    val Violet = Color(0xFFB07CFF)
    val Night = Color(0xFF04070D)
}

/**
 * Полноэкранный фон-сияние. Кладите первым ребёнком в Box,
 * контент — поверх него.
 *
 * @param intensity 0.6f — спокойное, 1.0f — обычное, 1.6f+ — финальное торжество.
 */
@Composable
fun AuroraBackdrop(
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
    veilThinness: Float = 1f, // cinematic time effect
    ritualIntensity: Float = 0f, // for 3D/volumetric
    colorA: Color = AuroraPublicPalette.Green,
    colorB: Color = AuroraPublicPalette.Blue,
    colorC: Color = AuroraPublicPalette.Violet,
) {
    val time by produceState(0f) {
        while (true) {
            withFrameNanos { value = it / 1_000_000_000f }
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shader = remember { RuntimeShader(AURORA_SKSL) }
        val brush = remember(shader) { ShaderBrush(shader) }
        Canvas(modifier = modifier.fillMaxSize()) {
            shader.setFloatUniform("iResolution", size.width, size.height)
            shader.setFloatUniform("iTime", time)
            shader.setFloatUniform("iIntensity", intensity)
            shader.setFloatUniform("iVeilThinness", veilThinness)
            shader.setFloatUniform("iRitualIntensity", ritualIntensity)
            shader.setFloatUniform("cA", colorA.red, colorA.green, colorA.blue)
            shader.setFloatUniform("cB", colorB.red, colorB.green, colorB.blue)
            shader.setFloatUniform("cC", colorC.red, colorC.green, colorC.blue)
            drawRect(brush)
        }
    } else {
        // Fallback для Android < 13 (v2): три дрейфующих «занавеса» из
        // градиентов + мерцающие звёзды. Только Canvas, без AGSL.
        Canvas(modifier = modifier.fillMaxSize()) {
            drawRect(AuroraPublicPalette.Night)

            // Звёзды: детерминированные псевдослучайные позиции + мерцание
            repeat(42) { i ->
                val fx = fract(sin(i * 127.1f) * 43758.547f)
                val fy = fract(sin(i * 311.7f) * 43758.547f)
                val tw = 0.35f + 0.65f * ((sin(time * (1.1f + fx) + i) + 1f) / 2f)
                drawCircle(
                    color = Color.White.copy(alpha = 0.45f * tw),
                    radius = 1.2f + 1.6f * fy,
                    center = Offset(fx * size.width, fy * size.height * 0.65f),
                )
            }

            // Три «занавеса»: вертикальные градиенты, дрейфующие по высоте
            listOf(colorA, colorB, colorC).forEachIndexed { i, c ->
                val drift = sin(time * (0.10f + 0.04f * i) + i * 2.1f)
                val cy = size.height * (0.28f + 0.09f * i + 0.06f * drift)
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            c.copy(alpha = (0.16f + 0.05f * sin(time * 0.5f + i)) * intensity),
                            Color.Transparent,
                        ),
                        startY = cy - size.height * 0.18f,
                        endY = cy + size.height * 0.22f,
                    ),
                )
            }
        }
    }
}

private fun fract(x: Float): Float = x - floor(x)

@Composable
fun AuroraEchoExplosion(
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
    colorA: Color = AuroraPublicPalette.Green,
    colorB: Color = AuroraPublicPalette.Blue,
    colorC: Color = AuroraPublicPalette.Violet,
) {
    val time by produceState(0f) {
        while (true) {
            withFrameNanos { value = it / 1_000_000_000f }
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shader = remember { RuntimeShader(ECHO_FLASH_EXPLOSION_SKSL) }
        val brush = remember(shader) { ShaderBrush(shader) }
        Canvas(modifier = modifier.fillMaxSize()) {
            shader.setFloatUniform("iResolution", size.width, size.height)
            shader.setFloatUniform("iTime", time)
            shader.setFloatUniform("iIntensity", intensity)
            shader.setFloatUniform("cA", colorA.red, colorA.green, colorA.blue)
            shader.setFloatUniform("cB", colorB.red, colorB.green, colorB.blue)
            shader.setFloatUniform("cC", colorC.red, colorC.green, colorC.blue)
            drawRect(brush)
        }
    } else {
        // fallback simple explosion
        Canvas(modifier = modifier.fillMaxSize()) {
            val r = size.minDimension * 0.4f * intensity
            drawCircle(colorA.copy(alpha = 0.6f * intensity), r * 0.5f, center)
            drawCircle(colorB.copy(alpha = 0.4f * intensity), r, center)
        }
    }
}

@Composable
fun AuroraSigilProjection(
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
    colorA: Color = AuroraPublicPalette.Green,
) {
    val time by produceState(0f) {
        while (true) {
            withFrameNanos { value = it / 1_000_000_000f }
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shader = remember { RuntimeShader(SIGIL_PROJECTION_SKSL) }
        val brush = remember(shader) { ShaderBrush(shader) }
        Canvas(modifier = modifier.fillMaxSize()) {
            shader.setFloatUniform("iResolution", size.width, size.height)
            shader.setFloatUniform("iTime", time)
            shader.setFloatUniform("iIntensity", intensity)
            shader.setFloatUniform("cA", colorA.red, colorA.green, colorA.blue)
            drawRect(brush)
        }
    }
}
