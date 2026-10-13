package eu.kanade.presentation.easteregg.lattice

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

/** Shader clock in seconds; ticks only while active. */
@Composable
fun rememberLatticeTimeSeconds(active: Boolean): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (true) {
            withFrameMillis { now -> time.floatValue = (now - start) / 1000f }
        }
    }
    return time
}

/** Grid floor background: AGSL on API 33+, static CPU-drawn grid as fallback. */
@Composable
fun Modifier.latticeGridFloor(): Modifier {
    val reduced = rememberLatticeReducedMotion()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !reduced) {
        return this.latticeShaderFill(LatticeShaders.GRID_FLOOR)
    }
    return this.drawBehind {
        // CPU Tron-ish floor
        drawRect(LatticeColors.Void)
        val horizon = size.height * 0.48f
        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    LatticeColors.Void,
                    LatticeColors.Signal.copy(alpha = 0.12f),
                    LatticeColors.Void,
                ),
                startY = horizon - 40f,
                endY = horizon + 40f,
            ),
        )
        val step = size.width / 12f
        var x = 0f
        while (x <= size.width) {
            drawLine(LatticeColors.GridLine, Offset(x, horizon), Offset(x, size.height), 1.2f)
            x += step
        }
        var y = horizon
        var row = 0
        while (y <= size.height) {
            drawLine(LatticeColors.Signal.copy(alpha = 0.12f + row * 0.01f), Offset(0f, y), Offset(size.width, y), 1f)
            y += step * 0.75f
            row++
        }
    }
}

/** Light-cycle jets racing over the grid floor. */
@Composable
fun Modifier.latticeLightCycles(): Modifier {
    val reduced = rememberLatticeReducedMotion()
    if (reduced) return this
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        return this.latticeShaderFill(LatticeShaders.LIGHT_CYCLES)
    }
    // CPU fallback: cycles racing toward the horizon (up the floor)
    val timeState = rememberLatticeTimeSeconds(active = true)
    return this.drawBehind {
        val time = timeState.value
        val horizon = size.height * 0.48f
        fun drawTowardHorizon(lane: Float, speed: Float, phase: Float, color: androidx.compose.ui.graphics.Color) {
            val t = (time * speed + phase).mod(1f) // 0 near camera, 1 at horizon
            val depth = 1f - t * 0.92f
            val y = horizon + depth * (size.height - horizon)
            val x = size.width * 0.5f + lane * size.width * 0.42f * depth
            val trailSteps = 10
            for (i in trailSteps downTo 1) {
                val td = (depth + i * 0.045f).coerceAtMost(1f)
                val ty = horizon + td * (size.height - horizon)
                val tx = size.width * 0.5f + lane * size.width * 0.42f * td
                val a = 0.08f + 0.5f * (1f - i / trailSteps.toFloat())
                drawCircle(color.copy(alpha = a), radius = 3.5f * td + 1f, center = Offset(tx, ty))
            }
            drawCircle(color.copy(alpha = 0.35f), radius = 10f * depth + 2f, center = Offset(x, y))
            drawCircle(color, radius = 4f * depth + 1.5f, center = Offset(x, y))
        }
        drawTowardHorizon(-0.55f, 0.18f, 0f, LatticeColors.Signal)
        drawTowardHorizon(0.15f, 0.14f, 0.33f, LatticeColors.Signal.copy(alpha = 0.9f))
        drawTowardHorizon(0.62f, 0.21f, 0.67f, LatticeColors.Service)
    }
}

private fun Float.mod(m: Float): Float {
    val r = this % m
    return if (r < 0f) r + m else r
}

/**
 * Terminal HUD frame: corner brackets + soft vignette + scanline wash.
 * Keeps the board readable while reading as "Grid shell".
 */
@Composable
fun Modifier.latticeTerminalFrame(): Modifier {
    return this.drawBehind {
        val inset = 18.dp.toPx()
        val arm = 28.dp.toPx()
        val stroke = 2.dp.toPx()
        val c = LatticeColors.Signal.copy(alpha = 0.55f)

        // corners
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(c, Offset(x, y), Offset(x + dx * arm, y), stroke, StrokeCap.Square)
            drawLine(c, Offset(x, y), Offset(x, y + dy * arm), stroke, StrokeCap.Square)
        }
        corner(inset, inset, 1f, 1f)
        corner(size.width - inset, inset, -1f, 1f)
        corner(inset, size.height - inset, 1f, -1f)
        corner(size.width - inset, size.height - inset, -1f, -1f)
        // vignette
        drawRect(
            brush = Brush.radialGradient(
                0.55f to LatticeColors.Void.copy(alpha = 0f),
                1f to LatticeColors.Void.copy(alpha = 0.55f),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = size.maxDimension * 0.72f,
            ),
        )
        // subtle scanlines
        var y = 0f
        while (y < size.height) {
            drawLine(LatticeColors.Signal.copy(alpha = 0.03f), Offset(0f, y), Offset(size.width, y), 1f)
            y += 3.dp.toPx()
        }
    }
}

/** Core glow: AGSL on API 33+, radial gradient as CPU fallback. */
@Composable
fun Modifier.latticeCoreGlow(): Modifier {
    val reduced = rememberLatticeReducedMotion()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !reduced) {
        return this.latticeShaderFill(LatticeShaders.CORE_GLOW)
    }
    return this.drawBehind {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    LatticeColors.Signal.copy(alpha = 0.5f),
                    LatticeColors.Signal.copy(alpha = 0f),
                ),
            ),
        )
    }
}

/**
 * Full-screen core ignition cinematic driven by [progress] 0..1.
 * AGSL on API 33+; expanding rings + radial wash fallback otherwise.
 */
@Composable
fun Modifier.latticeCoreIgnition(progress: Float): Modifier {
    val reduced = rememberLatticeReducedMotion()
    val p = progress.coerceIn(0f, 1f)
    if (reduced) {
        return this.drawBehind {
            val a = (0.15f + 0.55f * p).coerceIn(0f, 0.75f)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        LatticeColors.Signal.copy(alpha = a),
                        LatticeColors.Service.copy(alpha = a * 0.35f),
                        LatticeColors.Void.copy(alpha = 0f),
                    ),
                ),
            )
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        return this.latticeShaderProgressTimed(LatticeShaders.CORE_IGNITION, p)
    }
    // CPU rings fallback
    return this.drawBehind {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxR = size.minDimension * 0.65f
        for (i in 0..2) {
            val ringT = ((p - 0.22f - i * 0.08f) / 0.55f).coerceIn(0f, 1f)
            if (ringT <= 0f) continue
            val radius = maxR * (0.08f + ringT * 0.95f)
            val a = (1f - ringT) * 0.55f
            drawCircle(
                color = if (i ==
                    0
                ) {
                    LatticeColors.Signal.copy(alpha = a)
                } else {
                    LatticeColors.Service.copy(alpha = a * 0.7f)
                },
                radius = radius,
                center = Offset(cx, cy),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f + (1f - ringT) * 6f),
            )
        }
        val coreA = (0.2f + 0.6f * p.coerceAtMost(0.7f)).coerceIn(0f, 0.85f)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(
                    LatticeColors.TextPrimary.copy(alpha = coreA),
                    LatticeColors.Signal.copy(alpha = coreA * 0.5f),
                    LatticeColors.Void.copy(alpha = 0f),
                ),
                center = Offset(cx, cy),
                radius = maxR * 0.35f,
            ),
            radius = maxR * 0.35f,
            center = Offset(cx, cy),
        )
    }
}

/** De-rez dissolve overlay (progress 0..1). AGSL on API 33+, stipple fallback. */
@Composable
fun Modifier.latticeDerez(progress: Float): Modifier {
    val reduced = rememberLatticeReducedMotion()
    if (reduced) return this
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        return this.latticeShaderProgress(LatticeShaders.DEREZ, progress)
    }
    return this.drawBehind {
        val step = 14f
        var y = 0f
        while (y < size.height) {
            var x = 0f
            while (x < size.width) {
                val h = ((x * 12.9898f + y * 78.233f) % 1f + 1f) % 1f
                if (h < progress) {
                    drawRect(
                        LatticeColors.Signal.copy(alpha = 0.12f),
                        topLeft = Offset(x, y),
                        size = Size(step, step),
                    )
                }
                x += step
            }
            y += step
        }
    }
}

/** Horizontal light ribbon (circuit closed cue). */
@Composable
fun Modifier.latticeRibbon(): Modifier {
    val reduced = rememberLatticeReducedMotion()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !reduced) {
        return this.latticeShaderFill(LatticeShaders.RIBBON)
    }
    return this.drawBehind {
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(
                    LatticeColors.Signal.copy(alpha = 0f),
                    LatticeColors.Signal.copy(alpha = 0.7f),
                    LatticeColors.Signal.copy(alpha = 0f),
                ),
            ),
        )
    }
}

/** Title underline energy bar. */
@Composable
fun LatticeTitleEnergyBar(modifier: Modifier = Modifier) {
    val reduced = rememberLatticeReducedMotion()
    val timeState = rememberLatticeTimeSeconds(active = !reduced)
    Canvas(modifier = modifier) {
        val y = size.height / 2f
        drawLine(
            LatticeColors.Signal.copy(alpha = 0.25f),
            Offset(0f, y),
            Offset(size.width, y),
            strokeWidth = 1.5f,
        )
        if (!reduced) {
            val time = timeState.value
            val head = (time * 0.35f).mod(1f) * size.width
            drawLine(
                brush = Brush.horizontalGradient(
                    0f to LatticeColors.Signal.copy(alpha = 0f),
                    0.5f to LatticeColors.Signal,
                    1f to LatticeColors.Signal.copy(alpha = 0f),
                    startX = head - size.width * 0.12f,
                    endX = head + size.width * 0.12f,
                ),
                start = Offset(head - size.width * 0.12f, y),
                end = Offset(head + size.width * 0.12f, y),
                strokeWidth = 3f,
                cap = StrokeCap.Round,
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun Modifier.latticeShaderFill(source: String): Modifier {
    val timeState = rememberLatticeTimeSeconds(active = true)
    val runner = remember(source) { LatticeShaderRunner(source) }
    return this.drawWithCache {
        runner.onCache(size.width, size.height)
        onDrawBehind {
            with(runner) { drawFill(timeState.value) }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun Modifier.latticeShaderProgress(source: String, progress: Float): Modifier {
    val runner = remember(source) { LatticeShaderRunner(source) }
    return this.drawWithCache {
        runner.onCache(size.width, size.height, progress)
        onDrawBehind {
            with(runner) { drawProgress(progress) }
        }
    }
}

/** Like [latticeShaderProgress] but also drives uTime for living ignition FX. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun Modifier.latticeShaderProgressTimed(source: String, progress: Float): Modifier {
    val timeState = rememberLatticeTimeSeconds(active = true)
    val runner = remember(source) { LatticeShaderRunner(source) }
    return this.drawWithCache {
        runner.onCache(size.width, size.height, progress)
        onDrawBehind {
            with(runner) { drawProgressTimed(timeState.value, progress) }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class LatticeShaderRunner(source: String) {
    private val shader = RuntimeShader(source)
    private val brush = ShaderBrush(shader)

    fun onCache(width: Float, height: Float, progress: Float? = null) {
        shader.setFloatUniform("uResolution", width, height)
        if (progress != null) {
            shader.setFloatUniform("uProgress", progress.coerceIn(0f, 1f))
        }
        shader.setColorUniform(
            "uTint",
            android.graphics.Color.valueOf(
                LatticeColors.Signal.red,
                LatticeColors.Signal.green,
                LatticeColors.Signal.blue,
                1f,
            ),
        )
    }

    fun DrawScope.drawFill(time: Float) {
        shader.setFloatUniform("uTime", time)
        drawRect(brush)
    }

    fun DrawScope.drawProgress(progress: Float) {
        shader.setFloatUniform("uProgress", progress.coerceIn(0f, 1f))
        drawRect(brush)
    }

    fun DrawScope.drawProgressTimed(time: Float, progress: Float) {
        shader.setFloatUniform("uTime", time)
        shader.setFloatUniform("uProgress", progress.coerceIn(0f, 1f))
        drawRect(brush)
    }
}
