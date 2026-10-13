package eu.kanade.presentation.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntSize

/**
 * Isolates [RuntimeShader] (AGSL) usage for [GlitchStack].
 *
 * [RuntimeShader] only exists on API 33+. Keeping every reference in this
 * [@RequiresApi] class prevents ART from resolving the missing class when
 * [GlitchStack] composes on older devices (NoClassDefFoundError).
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class GlitchStackShader {

    val shader: RuntimeShader? = try {
        android.util.Log.d("GlitchStack", "Attempting to compile GLITCH_AGSL...")
        RuntimeShader(GLITCH_AGSL)
    } catch (e: Throwable) {
        android.util.Log.e("GlitchStack", "Failed to compile GLITCH_AGSL shader", e)
        null
    }

    private val cachedRenderEffect = shader?.let {
        RenderEffect
            .createRuntimeShaderEffect(it, "content")
            .asComposeRenderEffect()
    }

    val isAvailable: Boolean get() = shader != null && cachedRenderEffect != null

    fun Modifier.attach(
        time: Float,
        intensity: Float,
        config: GlitchConfig,
        size: IntSize,
    ): Modifier {
        val activeShader = shader ?: return this
        val effect = cachedRenderEffect ?: return this
        if (size.width == 0 || size.height == 0) return this
        return this.graphicsLayer {
            activeShader.setFloatUniform("resolution", size.width.toFloat(), size.height.toFloat())
            activeShader.setFloatUniform("time", time)
            activeShader.setFloatUniform("intensity", intensity)
            activeShader.setFloatUniform("ca", if (config.chromaticAberration) 0.010f else 0f)
            activeShader.setFloatUniform("redBleed", config.redBleed)
            renderEffect = effect
            clip = true
            compositingStrategy = CompositingStrategy.Offscreen
        }
    }
}

private const val GLITCH_AGSL = """
uniform shader content;
uniform float2 resolution;
uniform float time;
uniform float intensity;
uniform float ca;        // сила хроматической аберрации
uniform float redBleed;  // подмешивание алого

float rand(float2 p) {
    p = fract(p * float2(12.9898, 78.233));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / resolution;

    // Смещение блоков (datamosh): экран нарезан на горизонтальные полосы,
    // часть из них съезжает вбок случайным образом.
    float block = floor(uv.y * 42.0);
    float n = rand(float2(block, floor(time * 6.0)));
    float shift = (n - 0.5) * 0.05 * intensity;
    // редкие сильные разрывы кадра (screen tear)
    float tear = step(0.93, rand(float2(block * 1.7, floor(time * 9.0)))) * 0.18 * intensity;
    float2 off = float2(shift + tear, 0.0);

    // Хроматическая аберрация (RGB-split)
    float aberr = ca * intensity + tear * 0.5;
    float2 coord_g = clamp((uv + off) * resolution, float2(0.0), resolution - 0.5);
    float2 coord_r = clamp((uv + off + float2(aberr, 0.0)) * resolution, float2(0.0), resolution - 0.5);
    float2 coord_b = clamp((uv + off - float2(aberr, 0.0)) * resolution, float2(0.0), resolution - 0.5);
    half4 cr = content.eval(coord_r);
    half4 cg = content.eval(coord_g);
    half4 cb = content.eval(coord_b);
    half4 col = half4(cr.r, cg.g, cb.b, cg.a);

    // Скан-линии (бегущая развёртка)
    float scan = 0.92 + 0.08 * sin(uv.y * resolution.y * 1.4 - time * 12.0);
    col.rgb *= mix(1.0, scan, intensity * 0.6);

    // Статик-шум (зерно ТВ-помех)
    float grain = rand(fragCoord + time * 60.0);
    col.rgb += (grain - 0.5) * 0.16 * intensity;

    // Красное подмешивание (аварийный тон)
    col.r += intensity * redBleed;
    return col;
}
"""
