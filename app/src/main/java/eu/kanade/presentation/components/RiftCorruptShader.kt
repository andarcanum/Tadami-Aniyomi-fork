package eu.kanade.presentation.components

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.platform.LocalDensity

/**
 * Коррумпированный CRT-терминал для Step 3 пасхалки, целиком на AGSL.
 *
 * Шейдер рисует весь арт блока:
 *  - рамку-карточку со срезанными углами (SDF) + свечение;
 *  - RGB-датамош построчно + скан-линии;
 *  - центральный шов (расширяется при заряде);
 *  - 3D сферу-глобус с космосом и меридианами;
 *  - трещину-молнию на бреши.
 *
 * uniform-ы: uRes (px), uTime (сек), uCharge 0..1, uBreach 0..1, uPx (density, px/dp).
 */
private const val RIFT_CORRUPT_AGSL = """
uniform float2 uRes;
uniform float uTime;
uniform float uCharge;
uniform float uBreach;
uniform float uPx;

float hash(float2 p){ return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float noise(float2 p){
    float2 i = floor(p);
    float2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + float2(1.0, 0.0));
    float c = hash(i + float2(0.0, 1.0));
    float d = hash(i + float2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}
float sdCham(float2 p, float2 b, float ch){
    float2 q = abs(p);
    float d = length(max(q - b, float2(0.0))) + min(max(q.x - b.x, q.y - b.y), 0.0);
    float c = (q.x + q.y - (b.x + b.y - ch)) * 0.70710678;
    return max(d, c);
}

half4 main(float2 fragCoord){
    float2 fc = fragCoord;              // AGSL: начало сверху, флип по Y не нужен
    float PX = uPx;
    float pad = 3.0 * PX;
    float cham = 15.0 * PX;
    float TB = 22.0 * PX;
    float2 ctr = uRes * 0.5;
    float2 bh = uRes * 0.5 - float2(pad);
    float sdf = sdCham(fc - ctr, bh, cham);
    float aa = 1.0 * PX;
    float inside = smoothstep(aa, -aa, sdf);
    float cx = uRes.x * 0.5;
    float below = 1.0;
    float3 col = float3(0.031, 0.027, 0.039);

    // ---- RGB датамош ----
    float rowH = 3.0 * PX;
    float row = floor(fc.y / rowH);
    float seed = row * 13.0 + floor(uTime * 8.0);
    float g = hash(float2(seed, 1.7));
    if (g > 0.70 - uCharge * 0.28) {
        float bw = (10.0 + hash(float2(seed, 3.3)) * 40.0) * PX;
        float bxi = floor(fc.x / bw);
        float pick = hash(float2(bxi, seed));
        float3 cc = pick < 0.30 ? float3(1.0, 0.08, 0.24)
                  : pick < 0.55 ? float3(0.16, 1.0, 0.47)
                  : pick < 0.72 ? float3(0.24, 0.71, 1.0)
                  : pick < 0.86 ? float3(1.0, 1.0, 1.0)
                  : float3(1.0, 0.47, 0.08);
        float a = (0.10 + hash(float2(bxi, seed * 2.0)) * 0.5) * (0.5 + uCharge * 0.7);
        col += cc * a * below;
    }

    // ---- скан-линии + вуаль слева под текст ----
    float scan = step(1.5 * PX, mod(fc.y, 3.0 * PX));
    col *= mix(1.0, 0.78, (1.0 - scan) * below);
    float scrim = smoothstep(0.64, 0.0, fc.x / uRes.x) * 0.80 * below;
    col = mix(col, float3(0.024, 0.02, 0.031), scrim);

    // ---- центральный шов ----
    float sw = (2.0 + (0.10 + uCharge * 0.8) * 58.0) * PX;
    float seam = smoothstep(sw, 0.0, abs(fc.x - cx)) * below;
    col += float3(1.0, 0.72, 0.8) * seam * (0.28 + uCharge * 0.6);
    col += float3(1.0) * smoothstep(1.5 * PX, 0.0, abs(fc.x - cx)) * below * (0.45 + uCharge * 0.5);

    // ---- сфера-глобус ----
    float2 scs = float2(uRes.x * 0.775, TB + (uRes.y - TB) * 0.5);
    float rr = (uRes.y - TB) * 0.40;
    float2 sp = (fc - scs) / rr;
    float r2 = dot(sp, sp);
    if (r2 < 1.0 && below > 0.5) {
        float z = sqrt(max(0.0, 1.0 - r2));
        float3 n = float3(sp.x, sp.y, z);
        float neb = noise(n.xy * 3.2 + float2(uTime * 0.05, 0.0));
        float neb2 = noise(n.xy * 7.0 - float2(uTime * 0.03, uTime * 0.03));
        float3 space = mix(float3(0.02, 0.025, 0.07), float3(0.10, 0.05, 0.17), neb);
        space = mix(space, float3(0.18, 0.09, 0.25), neb2 * 0.5);
        float st = step(0.90, hash(floor(n.xy * 46.0))) * step(0.4, z);
        space += float3(1.0) * st * 0.4;
        space += float3(0.65, 0.75, 1.0) * smoothstep(0.55, 0.0, length(sp)) * 0.15;
        float a = uTime * 0.4;
        float lat = asin(clamp(n.y, -1.0, 1.0));
        float lon = atan(n.x, n.z) + a;
        float latL = abs(fract(lat * (5.0 / 3.14159) + 0.5) - 0.5);
        float lonL = abs(fract(lon * (6.0 / 3.14159) + 0.5) - 0.5);
        float grid = smoothstep(0.05, 0.0, latL) + smoothstep(0.05, 0.0, lonL);
        float3 wire = float3(1.0, 0.13, 0.26) * grid * (0.55 + uCharge * 0.35);
        float rim = smoothstep(0.82, 1.0, length(sp));
        wire += float3(1.0, 1.0, 1.0) * rim;
        float3 sphereCol = space + wire;
        float edge = smoothstep(1.0, 0.94, length(sp));
        col = mix(col, sphereCol, edge);
    }

    // ---- трещина на бреши ----
    float crackLine = abs((fc.x - cx) + (fc.y - TB) * 0.18 + sin(fc.y * 0.05 + uTime * 3.0) * 6.0 * PX);
    col += float3(1.0) * smoothstep(2.2 * PX, 0.0, crackLine) * uBreach * below;

    // ---- тайтлбар + обрезка по форме ----
    float tzone = step(fc.y, TB);
    col = mix(col, col + float3(0.06, 0.005, 0.01), tzone * 0.6);
    col *= inside;

    // ---- рамка ----
    float fp = 0.8 + 0.2 * sin(uTime * 24.0);
    float lw = 1.2 * PX;
    float bd = smoothstep(lw + aa, lw - aa, abs(sdf));
    float3 fcol = float3(1.0, 0.09, 0.16) * fp;
    col = mix(col, fcol, bd);
    float sdf2 = sdCham(fc - ctr, bh - float2(3.0 * PX), cham * 0.7);
    col = mix(col, float3(1.0, 0.16, 0.16) * 0.5, smoothstep(0.8 * PX, 0.0, abs(sdf2)) * 0.4 * inside);
    float sep = smoothstep(1.0 * PX, 0.0, abs(fc.y - TB));
    float gapv = step(13.0 * PX, abs(fc.x - cx));
    // col = mix(col, float3(1.0, 0.16, 0.22) * 0.7, sep * gapv * inside);
    float nub = smoothstep(1.2 * PX, 0.0, abs(fc.x - cx)) * step(fc.y, TB + 7.0 * PX) * inside;
    col += float3(1.0) * nub * (0.5 + 0.4 * fp);

    // ---- наружное свечение ----
    float glow = exp(-max(sdf, 0.0) / (3.0 * PX)) * (0.45 + 0.4 * fp);
    glow *= smoothstep(5.0 * PX, 1.0 * PX, sdf);
    col = mix(col, fcol, glow * (1.0 - inside));
    float alpha = clamp(inside + glow * (1.0 - inside), 0.0, 1.0);
    return half4(col, alpha);
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun Modifier.riftShader(time: Float, charge: Float, breach: Float, shader: RuntimeShader, brush: ShaderBrush, density: Float): Modifier =
    this.drawBehind {
        shader.setFloatUniform("uRes", size.width, size.height)
        shader.setFloatUniform("uTime", time)
        shader.setFloatUniform("uCharge", charge)
        shader.setFloatUniform("uBreach", breach)
        shader.setFloatUniform("uPx", density)
        drawRect(brush = brush)
    }

/**
 * Фон-терминал для GlitchRiftWidget. На API 33+ — AGSL-шейдер,
 * на старых версиях — фолбэк на [RiftDatamoshBackground].
 */
@Composable
fun RiftCorruptTerminal(
    time: Float,
    charge: Float,
    breach: Float,
    open: Float,
    modifier: Modifier = Modifier,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shader = remember { RuntimeShader(RIFT_CORRUPT_AGSL) }
        val brush = remember(shader) { ShaderBrush(shader) }
        val density = LocalDensity.current.density
        Box(modifier.riftShader(time, charge, breach, shader, brush, density))
    } else {
        RiftDatamoshBackground(time = time, open = open, modifier = modifier)
    }
}
