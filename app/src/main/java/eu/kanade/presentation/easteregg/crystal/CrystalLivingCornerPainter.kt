package eu.kanade.presentation.easteregg.crystal

import android.content.res.AssetManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.PathParser
import eu.kanade.domain.easteregg.crystal.CrystalDayPhase
import eu.kanade.domain.easteregg.crystal.CrystalWeather
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/**
 * The fixed authored scene, not a general SVG runtime. XML, numbers, paths, transforms, stroke
 * effects and gradient specifications are compiled once. Paints/shaders change only with colors.
 * Object-bounding-box radial gradients retain elliptical scaling. Android's centered radial
 * shader approximates the authored iris focal offset (fx/fy); its center/radius/stops are retained.
 * CSS drop-shadow filters are not reproduced: the exported layered contact gradients supply depth.
 */
internal class CrystalLivingCornerPainter(private val artwork: CrystalLivingArtwork) {
    private val gradients = artwork.ids.values.filter { it.tag.endsWith("Gradient") }
        .associate { it.attributes.getValue("id") to gradient(it) }
    private val clips = artwork.ids.values.filter { it.tag == "clipPath" }
        .associate { it.attributes.getValue("id") to clip(it) }
    private val drawables = mutableListOf<DrawNode>()
    private val intrinsic = artwork.viewBox
    private val previewViewBox = CrystalLivingViewBox(0f, -28f, 640f, 416f)
    private val worldBounds = RectF(
        intrinsic.x,
        intrinsic.y,
        intrinsic.x + intrinsic.width,
        intrinsic.y + intrinsic.height,
    )
    private val root = compile(artwork.root, Style(), worldBounds, emptyMap(), false, false, isRoot = true)
    private val thread = Path()
    private val dynamicLightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val dynamicLightMatrix = Matrix()
    private val crystalCeilingCordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.25f
        strokeCap = Paint.Cap.ROUND
    }
    private val shelfShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF101C26.toInt()
    }
    private val jewelPainter = CrystalJewelPainter()
    private var currentColors: CrystalLivingCornerColors? = null
    private val shaders = mutableMapOf<Pair<Int, Int>, Shader>()
    private val sunUnitGradient = RadialGradient(
        0f,
        0f,
        1f,
        intArrayOf(
            0xFAFFFDF5.toInt(),
            0xDEFFF2CB.toInt(),
            0x6BFFE1A0.toInt(),
            0x00FFD584,
        ),
        floatArrayOf(0f, 0.24f, 0.54f, 1f),
        Shader.TileMode.CLAMP,
    )
    private var cachedShaftPhase: CrystalDayPhase? = null
    private var cachedShaftRgb: Int = -1
    private var cachedShaftShader: LinearGradient? = null
    private val shaftPathsByPhase: Map<CrystalDayPhase, List<Pair<Path, Float>>> =
        CrystalDayPhase.entries.associateWith { phase ->
            val cones = when (phase) {
                CrystalDayPhase.DAWN -> arrayOf(
                    floatArrayOf(464f, 142f, 492f, 154f, 396f, 356f, 296f, 348f, 0.52f),
                    floatArrayOf(474f, 148f, 510f, 166f, 458f, 364f, 366f, 356f, 0.38f),
                )
                CrystalDayPhase.SUNSET -> arrayOf(
                    floatArrayOf(534f, 138f, 564f, 154f, 434f, 358f, 316f, 348f, 0.56f),
                    floatArrayOf(544f, 144f, 578f, 166f, 504f, 366f, 392f, 358f, 0.42f),
                )
                CrystalDayPhase.NIGHT -> arrayOf(
                    floatArrayOf(542f, 60f, 568f, 76f, 492f, 356f, 402f, 348f, 0.48f),
                    floatArrayOf(550f, 66f, 576f, 88f, 542f, 364f, 458f, 356f, 0.34f),
                )
                else -> arrayOf(
                    floatArrayOf(536f, 68f, 568f, 84f, 486f, 358f, 386f, 348f, 0.54f),
                    floatArrayOf(548f, 74f, 580f, 98f, 548f, 366f, 452f, 358f, 0.42f),
                )
            }
            cones.map { cone ->
                Path().apply {
                    moveTo(cone[0], cone[1])
                    lineTo(cone[2], cone[3])
                    lineTo(cone[4], cone[5])
                    lineTo(cone[6], cone[7])
                    close()
                } to cone[8]
            }
        }
    private var cachedFloorPoolRgb: Int = -1
    private var cachedFloorPoolGrad: RadialGradient? = null

    fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        frame: CrystalLivingFrame,
        colors: CrystalLivingCornerColors,
        phase: CrystalDayPhase,
        weather: CrystalWeather,
        reduced: Boolean = false,
        preview: Boolean = false,
        palette: CrystalPalette = CrystalPalette.Moon,
    ) {
        if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) return
        prepareColors(colors)
        val state = State(frame, colors, phase, weather, reduced || colors.eInk, preview, palette)
        updateThread(state.yarn, state.time)
        val save = canvas.save()
        canvas.clipRect(0f, 0f, width, height)
        canvas.drawColor(colors.wall)
        val cameraBox = if (preview) previewViewBox else intrinsic
        val viewport = cameraBox.fit(width, height)
        // Fit the authored root camera once. The compiled root must not apply its SVG viewport again.
        canvas.translate(viewport.offsetX, viewport.offsetY)
        canvas.scale(viewport.scale, viewport.scale)
        canvas.clipRect(worldBounds)
        render(canvas, root, state)
        canvas.restoreToCount(save)
    }

    private fun render(canvas: Canvas, node: DrawNode, state: State) {
        if (hidden(node, state)) return
        val opacity = opacity(node, state).coerceIn(0f, 1f)
        if (opacity <= 0f) return
        val save = canvas.save()
        if ("atmo-sun" in node.classes) {
            when (state.phase) {
                CrystalDayPhase.DAWN -> canvas.translate(475f, 152f)
                CrystalDayPhase.SUNSET -> canvas.translate(548f, 148f)
                else -> canvas.translate(552f, 75f)
            }
            drawSoftSun(canvas, state, opacity)
            canvas.restoreToCount(save)
            return
        }
        if ("atmo-volumetric-shafts" in node.classes) {
            drawPhaseVolumetricShafts(canvas, state, opacity)
            canvas.restoreToCount(save)
            return
        }
        if ("atmo-floor-light" in node.classes) {
            drawPhaseFloorPools(canvas, state, opacity)
            canvas.restoreToCount(save)
            return
        }
        if ("crystal-keepsake" in node.classes) {
            canvas.concat(node.matrix)
            // Place the live 3D Star of Crystal relic directly on top of the stack of books beside the lantern.
            canvas.translate(-32f, -12f)
            if (!state.colors.eInk) {
                shelfShadowPaint.alpha = (opacity * 0.48f * 255f).roundToInt().coerceIn(0, 255)
                canvas.drawOval(253f, 221.4f, 277f, 225.6f, shelfShadowPaint)
            }
            canvas.translate(248f, 187f)
            jewelPainter.relic(
                canvas = canvas,
                width = 34f,
                height = 34f,
                seconds = state.time,
                colors = state.palette,
                reduced = state.resting,
                inkColor = if (state.colors.eInk) state.colors.heading else null,
            )
            canvas.restoreToCount(save)
            return
        }
        canvas.concat(node.matrix)
        when {
            "crystal-placement" in node.classes -> {
                // Bring the hanging mobile's balance crossbar and top mount inside the ceiling viewBox.
                canvas.translate(0f, if (state.preview) 78f else 56f)
            }
            "shelf-lantern" in node.classes -> {
                canvas.translate(-13f, 0f)
            }
            "toy-spool" in node.classes || (node.cx == 232f && node.cy == 365f) -> {
                // Move thread spool & its contact shadow back next to the right side of the cat house.
                canvas.translate(-40f, -27f)
            }
            "toy-feather" in node.classes || (node.cx == 198f && node.cy == 364f) -> {
                // Move mint feather & its contact shadow back next to the left side of the cat house.
                canvas.translate(-152f, -27f)
            }
        }
        node.viewport?.let {
            if (it.clip) canvas.clipRect(it.x, it.y, it.x + it.width, it.y + it.height)
            canvas.concat(it.matrix)
        }
        applyMotion(canvas, node, state)
        if ("hanging-crystal" in node.classes) {
            crystalCeilingCordPaint.color = state.colors.token("scene-gold", 0xFFF6E0BA.toInt())
            crystalCeilingCordPaint.alpha = (0.78f * 255f).roundToInt().coerceIn(0, 255)
            canvas.drawCircle(354f, 24f, 4.8f, crystalCeilingCordPaint)
        }
        node.clip?.let(canvas::clipPath)
        if (node.children.isNotEmpty() && opacity < 1f) {
            // Group opacity composites overlapping descendants once, rather than multiplying each leaf.
            canvas.saveLayerAlpha(node.layerBounds, (opacity * 255f).roundToInt())
        }
        val shape = if (node.isThread && state.yarn != null) thread else node.path
        if (shape != null) {
            val leafOpacity = if (node.children.isEmpty()) opacity else 1f
            node.fillPaint?.let {
                it.alpha = (node.fillAlpha * leafOpacity * node.style.fillOpacity).roundToInt().coerceIn(0, 255)
                if (it.alpha > 0) canvas.drawPath(shape, it)
            }
            node.strokePaint?.let {
                it.alpha = (node.strokeAlpha * leafOpacity * node.style.strokeOpacity).roundToInt().coerceIn(0, 255)
                if (it.alpha > 0) canvas.drawPath(shape, it)
            }
        }
        node.children.forEach { render(canvas, it, state) }
        canvas.restoreToCount(save)
    }

    private fun drawSoftSun(canvas: Canvas, state: State, opacity: Float) {
        val haloScale = if (state.resting || state.weather != CrystalWeather.CLEAR) {
            1f
        } else {
            lerp(0.94f, 1.12f, pulse(state.time, 6.8f))
        }
        val radius = 36f * haloScale
        dynamicLightMatrix.setScale(radius, radius)
        sunUnitGradient.setLocalMatrix(dynamicLightMatrix)
        dynamicLightPaint.shader = sunUnitGradient
        dynamicLightPaint.alpha = (opacity * 235f).roundToInt().coerceIn(0, 255)
        canvas.drawCircle(0f, 0f, radius, dynamicLightPaint)
        dynamicLightPaint.shader = null
        dynamicLightPaint.color = 0xFFFFFDF7.toInt()
        dynamicLightPaint.alpha = (opacity * 240f).roundToInt().coerceIn(0, 255)
        canvas.drawCircle(0f, 0f, 7.5f, dynamicLightPaint)
    }

    private fun drawPhaseVolumetricShafts(canvas: Canvas, state: State, opacity: Float) {
        val baseColor = if (state.phase == CrystalDayPhase.NIGHT) {
            0xFFB4D2E4.toInt()
        } else {
            state.colors.token("sunlight", 0xFFFFE7BC.toInt())
        }
        val rgb = baseColor and 0x00FFFFFF
        val effectiveOpacity = if (state.phase == CrystalDayPhase.NIGHT && state.weather == CrystalWeather.CLEAR) {
            maxOf(opacity, 0.17f)
        } else {
            opacity
        }
        if (cachedShaftPhase != state.phase || cachedShaftRgb != rgb || cachedShaftShader == null) {
            cachedShaftPhase = state.phase
            cachedShaftRgb = rgb
            val sx: Float
            val sy: Float
            val ex: Float
            val ey: Float
            when (state.phase) {
                CrystalDayPhase.DAWN -> {
                    sx = 475f
                    sy = 152f
                    ex = 350f
                    ey = 360f
                }
                CrystalDayPhase.SUNSET -> {
                    sx = 548f
                    sy = 148f
                    ex = 380f
                    ey = 362f
                }
                CrystalDayPhase.NIGHT -> {
                    sx = 555f
                    sy = 68f
                    ex = 450f
                    ey = 360f
                }
                else -> {
                    sx = 552f
                    sy = 75f
                    ex = 440f
                    ey = 362f
                }
            }
            cachedShaftShader = LinearGradient(
                sx,
                sy,
                ex,
                ey,
                intArrayOf(
                    rgb or (0x6B shl 24),
                    rgb or (0x26 shl 24),
                    rgb,
                ),
                floatArrayOf(0f, 0.56f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        dynamicLightPaint.shader = cachedShaftShader
        val cones = shaftPathsByPhase[state.phase].orEmpty()
        for ((conePath, weight) in cones) {
            dynamicLightPaint.alpha = (effectiveOpacity * weight * 255f).roundToInt().coerceIn(0, 255)
            if (dynamicLightPaint.alpha > 0) canvas.drawPath(conePath, dynamicLightPaint)
        }
        dynamicLightPaint.shader = null
    }

    private fun drawPhaseFloorPools(canvas: Canvas, state: State, opacity: Float) {
        val baseColor = if (state.phase == CrystalDayPhase.NIGHT) {
            0xFFB4D2E4.toInt()
        } else {
            state.colors.token("sunlight", 0xFFFFE7BC.toInt())
        }
        val rgb = baseColor and 0x00FFFFFF
        val effectiveOpacity = if (state.phase == CrystalDayPhase.NIGHT && state.weather == CrystalWeather.CLEAR) {
            maxOf(opacity, 0.20f)
        } else {
            opacity
        }
        val driftX = if (state.resting || state.weather != CrystalWeather.CLEAR) {
            0f
        } else {
            lerp(-3f, 4f, pulse(state.time, 9f))
        }
        if (cachedFloorPoolRgb != rgb || cachedFloorPoolGrad == null) {
            cachedFloorPoolRgb = rgb
            cachedFloorPoolGrad = RadialGradient(
                0f,
                0f,
                1f,
                intArrayOf(
                    rgb or (0x9E shl 24),
                    rgb or (0x3D shl 24),
                    rgb,
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        val unitGrad = cachedFloorPoolGrad ?: return
        val basePools = when (state.phase) {
            CrystalDayPhase.DAWN -> FLOOR_POOLS_DAWN
            CrystalDayPhase.SUNSET -> FLOOR_POOLS_SUNSET
            CrystalDayPhase.NIGHT -> FLOOR_POOLS_NIGHT
            else -> FLOOR_POOLS_DAY
        }
        for (pool in basePools) {
            val cx = pool[0] + driftX
            val cy = pool[1]
            val rx = pool[2]
            val ry = pool[3]
            dynamicLightMatrix.reset()
            dynamicLightMatrix.setScale(rx, ry)
            dynamicLightMatrix.postTranslate(cx, cy)
            unitGrad.setLocalMatrix(dynamicLightMatrix)
            dynamicLightPaint.shader = unitGrad
            dynamicLightPaint.alpha = (effectiveOpacity * pool[4] * 255f).roundToInt().coerceIn(0, 255)
            if (dynamicLightPaint.alpha > 0) {
                canvas.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, dynamicLightPaint)
            }
        }
        dynamicLightPaint.shader = null
    }

    private fun hidden(node: DrawNode, state: State): Boolean {
        val c = node.classes
        val wet = state.weather != CrystalWeather.CLEAR
        val night = state.phase == CrystalDayPhase.NIGHT
        if (node.displayNone) return true
        if ("floor-detail" in c || "atmo-glass-sheen" in c || "atmo-sun-rays" in c) return true
        if (state.resting && ("ambient-motes-group" in c || "ambient-mote" in c)) return true
        if (state.colors.eInk && (node.fineDetail || c.any { it in INK_HIDDEN })) return true
        if (("atmo-sun" in c && night) || ("atmo-moon" in c && !night)) return true
        if ("atmo-stars" in c && (!night || wet)) return true
        if (c.any { it in WET_ONLY } && !wet) return true
        if (c.any { it in STORM_ONLY } && (state.weather != CrystalWeather.STORM || state.resting)) return true
        return false
    }

    private fun opacity(node: DrawNode, state: State): Float {
        val c = node.classes
        val time = state.time
        val wet = state.weather != CrystalWeather.CLEAR
        val closure = state.closure
        return when {
            "ambient-motes-group" in c && state.frame.playing -> 0.35f
            "eye-open" in c -> 1f - smooth(0.82f, 1f, closure)
            "pro-eyes-closed" in c -> smooth(0.70f, 0.98f, closure)
            "atmo-floor-light" in c -> state.colors.sunStrength
            "atmo-volumetric-shafts" in c -> state.colors.sunStrength * 0.85f
            "atmo-sun" in c || "atmo-moon" in c -> if (wet) 0.15f else node.opacity
            "atmo-rain-sheet" in c && "far" in c -> 0.45f
            "atmo-lightning" in c || "atmo-storm-wash" in c -> lightning(time)
            "atmo-sun-halo" in c && !wet && !state.resting -> lerp(0.22f, 0.38f, pulse(time, 6.8f))
            "atmo-floor-beams" in c && !wet && !state.resting -> lerp(0.82f, 1f, pulse(time, 9f))
            "atmo-drop-fall" in c ->
                if (state.resting) 0.75f else dropOpacity(fraction(time, node.duration, node.delay))
            "atmo-glass-sheen" in c -> sheenOpacity(fraction(time, 13f))
            "ambient-mote" in c -> lerp(0.12f, 0.52f, pulse(time, node.duration, node.delay))
            "lantern-glow-aura" in c -> if (state.resting) 0.65f else lerp(0.65f, 0.92f, pulse(time, 4.2f))
            "crystal-caustic-glint" in c && !state.resting -> node.opacity * lerp(0.6f, 1f, pulse(time, 5.2f))
            "crystal-spark-top" in c && !state.resting -> node.opacity * lerp(0.72f, 1f, pulse(time, 4.8f))
            else -> node.opacity
        }
    }

    private fun applyMotion(canvas: Canvas, node: DrawNode, state: State) {
        val part = node.part
        if (part != null) {
            val transform = if (state.resting) {
                if (part == CrystalLivingPart.HEAD) RESTING_HEAD else IDENTITY
            } else {
                state.frame.parts[part] ?: IDENTITY
            }
            canvas.translate(transform.translationX, transform.translationY)
            canvas.rotate(transform.rotationDegrees, node.originX, node.originY)
            canvas.scale(transform.scaleX, transform.scaleY, node.originX, node.originY)
        }
        val toy = when (node.toy) {
            CrystalLivingToyKind.YARN -> state.yarn
            CrystalLivingToyKind.BALL -> state.ball
            null -> null
        }
        if (toy != null) {
            canvas.translate(toy.x - node.baseX, toy.y - node.baseY)
            canvas.translate(node.baseX, node.baseY)
            canvas.scale(1f + toy.squash * 0.18f, 1f - toy.squash * 0.15f)
            canvas.rotate(toy.angle)
            canvas.translate(-node.baseX, -node.baseY)
        }
        val shadow = when {
            "shadow-yarn" in node.classes -> state.yarn
            "shadow-ball" in node.classes -> state.ball
            else -> null
        }
        if (shadow != null) {
            val offset = if (shadow.kind == CrystalLivingToyKind.YARN) 26f else 15f
            canvas.translate(shadow.x - node.cx, shadow.y + offset - node.cy)
            canvas.scale(1f + abs(shadow.squash) * 0.12f, 1f, node.cx, node.cy)
        }
        if ("eye-open" in node.classes) canvas.scale(1f, 1f - state.closure * 0.96f, node.originX, node.originY)
        if ("pro-pupil" in node.classes) {
            val radius = if (state.resting) 5.2f else state.frame.pupilRadius
            canvas.scale(radius / node.rx.coerceAtLeast(0.001f), 1f, node.cx, node.cy)
        }
        if (state.resting) return
        val time = state.time
        val c = node.classes
        when {
            "atmo-rain-sheet" in c -> canvas.translate(0f, 42f * fraction(time, if ("far" in c) 1.8f else 1.05f))
            "atmo-cloud-drift" in c -> canvas.translate(lerp(-11f, 11f, pulse(time, 18f)), 0f)
            "atmo-dark-clouds" in c -> canvas.translate(lerp(-4f, 5f, pulse(time, 14f)), 0f)
            "atmo-sun-halo" in c && state.weather == CrystalWeather.CLEAR -> {
                val scale = lerp(0.94f, 1.18f, pulse(time, 6.8f))
                canvas.scale(scale, scale, node.originX, node.originY)
            }
            "atmo-sun-rays" in c && state.weather == CrystalWeather.CLEAR ->
                canvas.rotate(fraction(time, 36f) * 360f, node.originX, node.originY)
            "atmo-drop-fall" in c -> canvas.translate(0f, node.distance * fraction(time, node.duration, node.delay))
            "atmo-glass-sheen" in c -> {
                val p = fraction(time, 13f)
                canvas.translate(lerp(-65f, 92f, ((p - 0.12f) / 0.4f).coerceIn(0f, 1f)), 0f)
            }
            "atmo-floor-beams" in c && state.weather == CrystalWeather.CLEAR -> {
                val p = pulse(time, 9f)
                canvas.translate(lerp(-4f, 6f, p), 0f)
                canvas.translate(node.originX, node.originY)
                canvas.skew(tan(lerp(-0.5f, 0.7f, p) * PI / 180.0).toFloat(), 0f)
                canvas.translate(-node.originX, -node.originY)
            }
            "ambient-mote" in c -> {
                val p = pulse(time, node.duration, node.delay)
                canvas.translate(node.dx * p, node.dy * p)
                val scale = lerp(0.85f, 1.2f, p)
                // Authored view-box transform origin defaults to (0,0), not the mote center.
                canvas.scale(scale, scale)
            }
            "lantern-glow-aura" in c -> {
                val scale = lerp(0.94f, 1.1f, pulse(time, 4.2f))
                canvas.scale(scale, scale, node.cx, node.cy)
            }
            "lantern-flame" in c -> {
                val p = fraction(time, 3.4f)
                val segment = if (p < 0.35f) {
                    0
                } else if (p < 0.7f) {
                    1
                } else {
                    2
                }
                val u = when (segment) {
                    0 -> p / 0.35f
                    1 -> (p - 0.35f) / 0.35f
                    else -> (p - 0.7f) / 0.3f
                }
                val eased = cssEase(u)
                val sx = when (segment) {
                    0 -> lerp(1f, 0.95f, eased)
                    1 -> lerp(0.95f, 1.04f, eased)
                    else -> lerp(1.04f, 1f, eased)
                }
                val sy = when (segment) {
                    0 -> lerp(1f, 1.07f, eased)
                    1 -> lerp(1.07f, 0.96f, eased)
                    else -> lerp(0.96f, 1f, eased)
                }
                val rotation = when (segment) {
                    0 -> lerp(-1.5f, 2f, eased)
                    1 -> lerp(2f, -1f, eased)
                    else -> lerp(-1f, -1.5f, eased)
                }
                canvas.scale(sx, sy, node.bounds.centerX(), node.bounds.top + node.bounds.height() * 0.9f)
                canvas.rotate(rotation, node.bounds.centerX(), node.bounds.top + node.bounds.height() * 0.9f)
            }
        }
    }

    private fun prepareColors(colors: CrystalLivingCornerColors) {
        if (currentColors == colors) return
        currentColors = colors
        shaders.clear()
        for (node in drawables) {
            val fill = when {
                colors.monochrome && "bed-felt" in node.classes -> PaintValue.Solid(0xFFD0D0D0.toInt())
                colors.monochrome && "yarn-fill" in node.classes -> PaintValue.Solid(0xFFAAAAAA.toInt())
                colors.monochrome && "sky-fill" in node.classes -> PaintValue.Solid(
                    if (colors.wall == 0xFF171717.toInt()) 0xFF444444.toInt() else 0xFFEEEEEE.toInt(),
                )
                else -> node.style.fill
            }
            node.fillAlpha = configure(node.fillPaint, fill, node, colors)
            val stroke =
                if (colors.monochrome && node.isRain) PaintValue.Solid(0xFF666666.toInt()) else node.style.stroke
            node.strokeAlpha = configure(node.strokePaint, stroke, node, colors)
        }
    }

    private fun configure(paint: Paint?, value: PaintValue, node: DrawNode, colors: CrystalLivingCornerColors): Int {
        if (paint == null || value == PaintValue.None) return 0
        paint.shader = null
        return when (value) {
            is PaintValue.Solid -> colors.normalize(value.color).let {
                paint.color = it
                it ushr 24
            }
            is PaintValue.Token -> colors.token(value.name, value.fallback).let {
                paint.color = it
                it ushr 24
            }
            is PaintValue.Gradient -> {
                val gradient = gradients.getValue(value.id)
                paint.color = Color.WHITE
                paint.shader = shaders.getOrPut(gradient.index to node.index) { shader(gradient, node, colors) }
                255
            }
            PaintValue.None -> 0
        }
    }

    private fun shader(gradient: Gradient, node: DrawNode, colors: CrystalLivingCornerColors): Shader {
        val stopColors = gradient.stops.map { stop ->
            val color = when (val value = stop.color) {
                is PaintValue.Solid -> colors.normalize(value.color)
                is PaintValue.Token -> colors.token(value.name, value.fallback)
                else -> error("Gradient stops must be colors")
            }
            (color and 0x00FFFFFF) or (((color ushr 24) * stop.opacity).roundToInt().coerceIn(0, 255) shl 24)
        }.toIntArray()
        val offsets = gradient.stops.map { it.offset }.toFloatArray()
        fun coordinate(value: Coordinate, horizontal: Boolean): Float {
            if (gradient.objectBox) return value.value
            return if (value.percent) {
                value.value * (if (horizontal) node.viewBox.width() else node.viewBox.height()) +
                    (if (horizontal) node.viewBox.left else node.viewBox.top)
            } else {
                value.value
            }
        }
        val result = if (gradient.radial) {
            val radius = if (gradient.objectBox || !gradient.radius.percent) {
                gradient.radius.value
            } else {
                val w = node.viewBox.width()
                val h = node.viewBox.height()
                gradient.radius.value * kotlin.math.sqrt((w * w + h * h) / 2f)
            }
            RadialGradient(
                coordinate(gradient.cx, true),
                coordinate(gradient.cy, false),
                radius.coerceAtLeast(0.0001f),
                stopColors,
                offsets,
                gradient.tile,
            )
        } else {
            LinearGradient(
                coordinate(gradient.x1, true),
                coordinate(gradient.y1, false),
                coordinate(gradient.x2, true),
                coordinate(gradient.y2, false),
                stopColors,
                offsets,
                gradient.tile,
            )
        }
        val matrix = if (gradient.objectBox) {
            Matrix().apply {
                setValues(
                    floatArrayOf(
                        node.bounds.width(),
                        0f,
                        node.bounds.left,
                        0f,
                        node.bounds.height(),
                        node.bounds.top,
                        0f,
                        0f,
                        1f,
                    ),
                )
            }
        } else {
            Matrix()
        }
        matrix.preConcat(gradient.matrix)
        result.setLocalMatrix(matrix)
        return result
    }

    private fun compile(
        raw: CrystalLivingSvgNode,
        inherited: Style,
        inheritedViewBox: RectF,
        inheritedVariables: Map<String, String>,
        inCat: Boolean,
        inThread: Boolean,
        isRoot: Boolean = false,
    ): DrawNode {
        val properties = raw.properties
        val style = style(raw, inherited)
        val variables = inheritedVariables + properties.filterKeys { it.startsWith("--") }
        val viewport = if (raw.tag == "svg" && !isRoot) viewport(raw) else null
        val viewBox = viewport?.viewBox ?: inheritedViewBox
        val cat = inCat || "crystal-illustrated-tabby" in raw.classes
        val threadAncestor = inThread || "yarn-thread" in raw.classes
        val rawChildren = if ("atelier-rug" in raw.classes) {
            // Omit the dashed inner ring & fringe lines on the floor rug so the rug is clean.
            raw.children.take(2)
        } else {
            raw.children
        }
        val children = rawChildren.filter { it.tag !in DEFINITIONS }.map {
            compile(it, style, viewBox, variables, cat, threadAncestor)
        }
        val path = geometry(raw)?.apply {
            fillType = if (style.evenOdd) Path.FillType.EVEN_ODD else Path.FillType.WINDING
        }
        val bounds = RectF()
        path?.computeBounds(bounds, true)
        for (child in children) {
            val childBounds = RectF(child.bounds)
            val matrix = Matrix(child.matrix)
            child.viewport?.let { matrix.preConcat(it.matrix) }
            matrix.mapRect(childBounds)
            bounds.union(childBounds)
        }
        val origin = numbers(properties["data-origin"].orEmpty())
        val part = PARTS.entries.firstOrNull { it.key in raw.classes }?.value
        val clip = properties["clip-path"]?.let { reference ->
            val id = paint(reference) as? PaintValue.Gradient ?: error("Expected local clip reference")
            val definition = clips.getValue(id.id)
            Path(definition.path).apply {
                if (definition.objectBox) {
                    transform(
                        Matrix().apply {
                            setValues(
                                floatArrayOf(
                                    bounds.width(),
                                    0f,
                                    bounds.left,
                                    0f,
                                    bounds.height(),
                                    bounds.top,
                                    0f,
                                    0f,
                                    1f,
                                ),
                            )
                        },
                    )
                }
            }
        }
        val durationName = if ("ambient-mote" in raw.classes) "--mote-dur" else "--drop-duration"
        val delayName = if ("ambient-mote" in raw.classes) "--mote-delay" else "--drop-delay"
        val node = DrawNode(
            index = raw.index, classes = raw.classes, path = path, children = children, style = style,
            matrix = transformMatrix(properties["transform"].orEmpty()), viewport = viewport, viewBox = viewBox,
            bounds = bounds, layerBounds = RectF(bounds).apply { inset(-64f, -64f) }, clip = clip, part = part,
            originX = origin.getOrNull(0) ?: 0f, originY = origin.getOrNull(1) ?: 0f,
            opacity = raw.number("opacity", 1f).coerceIn(0f, 1f), displayNone = properties["display"] == "none",
            toy = when (properties["data-toy"]) {
                "yarn" -> CrystalLivingToyKind.YARN
                "ball" -> CrystalLivingToyKind.BALL
                else -> null
            },
            baseX = raw.number("data-base-x"),
            baseY = raw.number("data-base-y"),
            cx = raw.number("cx"),
            cy = raw.number("cy"),
            rx = raw.number("rx"),
            duration = variables[durationName]?.removeSuffix("s")?.toFloatOrNull()?.coerceAtLeast(0.1f) ?: 6f,
            delay = variables[delayName]?.removeSuffix("s")?.toFloatOrNull() ?: 0f,
            distance = variables["--drop-distance"]?.removeSuffix("px")?.toFloatOrNull() ?: 42f,
            dx = variables["--mote-dx"]?.removeSuffix("px")?.toFloatOrNull() ?: -10f,
            dy = variables["--mote-dy"]?.removeSuffix("px")?.toFloatOrNull() ?: -14f,
            fineDetail = cat && path != null && style.fill == PaintValue.None &&
                (style.width <= 1f || (style.width <= 1.2f && properties["d"].orEmpty().length > 320)),
            isThread = threadAncestor && raw.tag == "path",
            isRain = "atmo-rain" in raw.classes || inherited.rain,
        )
        if (path != null) drawables += node
        return node
    }

    private fun style(raw: CrystalLivingSvgNode, inherited: Style): Style {
        val p = raw.properties
        val dash = p["stroke-dasharray"]?.let {
            if (it == "none") {
                null
            } else {
                val values = numbers(it)
                val repeated = if (values.size % 2 == 1) values + values else values
                if (repeated.isEmpty() || repeated.all { value -> value <= 0f }) {
                    null
                } else {
                    DashPathEffect(repeated, raw.number("stroke-dashoffset"))
                }
            }
        } ?: if (p.containsKey("stroke-dasharray")) null else inherited.dash
        return Style(
            fill = p["fill"]?.let(::paint) ?: inherited.fill,
            stroke = p["stroke"]?.let(::paint) ?: inherited.stroke,
            fillOpacity = raw.number("fill-opacity", inherited.fillOpacity).coerceIn(0f, 1f),
            strokeOpacity = raw.number("stroke-opacity", inherited.strokeOpacity).coerceIn(0f, 1f),
            width = raw.number("stroke-width", inherited.width).coerceAtLeast(0f),
            cap = when (p["stroke-linecap"]) {
                "round" -> Paint.Cap.ROUND
                "square" -> Paint.Cap.SQUARE
                "butt" -> Paint.Cap.BUTT
                else -> inherited.cap
            },
            join = when (p["stroke-linejoin"]) {
                "round" -> Paint.Join.ROUND
                "bevel" -> Paint.Join.BEVEL
                "miter" -> Paint.Join.MITER
                else -> inherited.join
            },
            miter = raw.number("stroke-miterlimit", inherited.miter), dash = dash,
            evenOdd = p["fill-rule"]?.let { it == "evenodd" } ?: inherited.evenOdd,
            rain = inherited.rain || "atmo-rain" in raw.classes,
        )
    }

    private fun geometry(node: CrystalLivingSvgNode): Path? {
        fun n(name: String, default: Float = 0f) = node.number(name, default)
        return when (node.tag) {
            "path" -> PathParser.createPathFromPathData(node.properties["d"].orEmpty()) ?: Path()
            "circle" -> Path().apply { addCircle(n("cx"), n("cy"), n("r").coerceAtLeast(0f), Path.Direction.CW) }
            "ellipse" -> Path().apply {
                addOval(
                    n("cx") - n("rx"),
                    n("cy") - n("ry"),
                    n("cx") + n("rx"),
                    n("cy") + n("ry"),
                    Path.Direction.CW,
                )
            }
            "rect" -> Path().apply {
                val w = n("width")
                val h = n("height")
                val rx = n("rx", n("ry")).coerceIn(0f, w.coerceAtLeast(0f) / 2f)
                val ry = n("ry", rx).coerceIn(0f, h.coerceAtLeast(0f) / 2f)
                addRoundRect(n("x"), n("y"), n("x") + w, n("y") + h, rx, ry, Path.Direction.CW)
            }
            "polygon" -> Path().apply {
                val points = numbers(node.properties["points"].orEmpty())
                require(points.size % 2 == 0) { "Malformed living polygon" }
                if (points.size >= 2) {
                    moveTo(points[0], points[1])
                    for (index in 2 until points.size step 2) lineTo(points[index], points[index + 1])
                    close()
                }
            }
            "svg", "g", "clipPath", "defs", "title", "desc", "linearGradient", "radialGradient", "stop" -> null
            else -> error("Unsupported living geometry: " + node.tag)
        }
    }

    private fun clip(node: CrystalLivingSvgNode): Clip {
        fun collect(raw: CrystalLivingSvgNode, parent: Matrix): Path {
            val matrix = Matrix(parent).apply { preConcat(transformMatrix(raw.properties["transform"].orEmpty())) }
            val result = geometry(raw)?.apply { transform(matrix) } ?: Path()
            raw.children.forEach { result.addPath(collect(it, matrix)) }
            result.fillType =
                if (raw.properties["clip-rule"] == "evenodd") Path.FillType.EVEN_ODD else Path.FillType.WINDING
            return result
        }
        return Clip(collect(node, Matrix()), node.properties["clipPathUnits"] == "objectBoundingBox")
    }

    private fun viewport(node: CrystalLivingSvgNode): Viewport {
        val box = numbers(node.properties.getValue("viewBox"))
        require(box.size == 4 && box[2] > 0f && box[3] > 0f) { "Invalid living viewBox" }
        val x = node.number("x")
        val y = node.number("y")
        val width = node.number("width", box[2])
        val height = node.number("height", box[3])
        val aspect = node.properties["preserveAspectRatio"] ?: "xMidYMid meet"
        var sx = width / box[2]
        var sy = height / box[3]
        var dx = 0f
        var dy = 0f
        if (!aspect.startsWith("none")) {
            val scale = if (aspect.contains("slice")) maxOf(sx, sy) else minOf(sx, sy)
            sx = scale
            sy = scale
            dx = (width - box[2] * scale) * when {
                aspect.contains("xMin") -> 0f
                aspect.contains("xMax") -> 1f
                else -> 0.5f
            }
            dy = (height - box[3] * scale) * when {
                aspect.contains("YMin") -> 0f
                aspect.contains("YMax") -> 1f
                else -> 0.5f
            }
        }
        val matrix = Matrix().apply {
            setValues(floatArrayOf(sx, 0f, x + dx - box[0] * sx, 0f, sy, y + dy - box[1] * sy, 0f, 0f, 1f))
        }
        return Viewport(
            x,
            y,
            width,
            height,
            RectF(box[0], box[1], box[0] + box[2], box[1] + box[3]),
            matrix,
            node.properties["overflow"] != "visible",
        )
    }

    private fun gradient(node: CrystalLivingSvgNode): Gradient {
        fun coordinate(name: String, fallback: String) = Coordinate.parse(node.properties[name] ?: fallback)
        var previous = 0f
        val stops = node.children.filter { it.tag == "stop" }.map {
            val offset = Coordinate.parse(it.properties["offset"] ?: "0").value.coerceIn(previous, 1f)
            previous = offset
            Stop(
                offset,
                paint(it.properties["stop-color"] ?: "#000000"),
                it.number("stop-opacity", 1f).coerceIn(0f, 1f),
            )
        }.let { if (it.size == 1) listOf(it.single().copy(offset = 0f), it.single().copy(offset = 1f)) else it }
        require(stops.size >= 2) { "Living gradient needs color stops" }
        return Gradient(
            node.index, node.tag == "radialGradient", node.properties["gradientUnits"] != "userSpaceOnUse",
            coordinate("x1", "0%"), coordinate("y1", "0%"), coordinate("x2", "100%"), coordinate("y2", "0%"),
            coordinate("cx", "50%"), coordinate("cy", "50%"), coordinate("r", "50%"), stops,
            transformMatrix(node.properties["gradientTransform"].orEmpty()),
            when (node.properties["spreadMethod"]) {
                "repeat" -> Shader.TileMode.REPEAT
                "reflect" -> Shader.TileMode.MIRROR
                else -> Shader.TileMode.CLAMP
            },
        )
    }

    private fun updateThread(toy: CrystalLivingToyState?, time: Float) {
        if (toy == null) return
        val speed = hypot(toy.vx, toy.vy)
        val motionAmp = (speed / 95f).coerceIn(0f, 1f) + abs(toy.squash) * 0.45f
        val angRad = (toy.angle * PI / 180.0).toFloat()
        val x = toy.x + 14f * cos(angRad * 0.35f)
        val y = toy.y + 14f + 3f * sin(angRad * 0.5f)
        val lagX = (-toy.vx * 0.11f).coerceIn(-28f, 28f)
        val lagY = (-toy.vy * 0.09f).coerceIn(-16f, 16f)
        val wave1 = sin(time * 9.5f + toy.angle * 0.06f) * (1.2f + motionAmp * 6.5f)
        val wave2 = cos(time * 11.2f - toy.angle * 0.08f) * (1.0f + motionAmp * 8.2f)
        val wave3 = sin(time * 13.4f + toy.angle * 0.09f) * (0.8f + motionAmp * 5.8f)
        thread.rewind()
        thread.moveTo(x, y)
        thread.cubicTo(
            x + 24f + lagX * 0.55f + wave1 * 0.5f,
            y + 5f + lagY * 0.35f + wave1,
            x + 24f + lagX + wave2 * 0.4f,
            y + 24f + lagY * 0.75f - wave2 * 0.6f,
            x + 8f + lagX * 0.85f + wave1 * 0.3f,
            y + 29f + lagY * 0.6f + wave2 * 0.5f,
        )
        thread.cubicTo(
            x - 6f + lagX * 0.5f - wave2 * 0.4f,
            y + 34f + lagY * 0.4f - wave1 * 0.7f,
            x - 7f + lagX * 0.3f + wave3 * 0.5f,
            y + 17f + lagY * 0.3f + wave2 * 0.8f,
            x - 18f + lagX * 0.55f + wave3 * 0.6f,
            y + 23f + lagY * 0.5f - wave3 * 0.5f,
        )
        thread.cubicTo(
            x - 24f + lagX * 0.7f,
            y + 27f + lagY * 0.55f + wave1 * 0.5f,
            x - 33f + lagX * 0.85f + wave2 * 0.5f,
            y + 27f + lagY * 0.6f - wave2 * 0.4f,
            x - 35f + lagX * 0.95f + wave3,
            y + 21f + lagY * 0.65f + wave3 * 0.9f,
        )
    }

    private data class State(
        val frame: CrystalLivingFrame,
        val colors: CrystalLivingCornerColors,
        val phase: CrystalDayPhase,
        val weather: CrystalWeather,
        val resting: Boolean,
        val preview: Boolean = false,
        val palette: CrystalPalette = CrystalPalette.Moon,
    ) {
        val time = frame.elapsedSeconds.takeIf { it.isFinite() && it >= 0f } ?: 0f
        val closure = if (resting) 0f else frame.eyeClosure.coerceIn(0f, 1f)
        val yarn = frame.toys.firstOrNull { it.kind == CrystalLivingToyKind.YARN }
        val ball = frame.toys.firstOrNull { it.kind == CrystalLivingToyKind.BALL }
    }

    private data class DrawNode(
        val index: Int,
        val classes: Set<String>,
        val path: Path?,
        val children: List<DrawNode>,
        val style: Style,
        val matrix: Matrix,
        val viewport: Viewport?,
        val viewBox: RectF,
        val bounds: RectF,
        val layerBounds: RectF,
        val clip: Path?,
        val part: CrystalLivingPart?,
        val originX: Float,
        val originY: Float,
        val opacity: Float,
        val displayNone: Boolean,
        val toy: CrystalLivingToyKind?,
        val baseX: Float,
        val baseY: Float,
        val cx: Float,
        val cy: Float,
        val rx: Float,
        val duration: Float,
        val delay: Float,
        val distance: Float,
        val dx: Float,
        val dy: Float,
        val fineDetail: Boolean,
        val isThread: Boolean,
        val isRain: Boolean,
    ) {
        val fillPaint: Paint? = if (path != null && style.fill != PaintValue.None) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.FILL
            }
        } else {
            null
        }
        val strokePaint: Paint? = if (path != null && style.stroke != PaintValue.None && style.width > 0f) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.STROKE
                strokeWidth = this@DrawNode.style.width
                strokeCap = this@DrawNode.style.cap
                strokeJoin = this@DrawNode.style.join
                strokeMiter = this@DrawNode.style.miter
                pathEffect = this@DrawNode.style.dash
            }
        } else {
            null
        }
        var fillAlpha = 0
        var strokeAlpha = 0
    }

    private data class Style(
        val fill: PaintValue = PaintValue.Solid(Color.BLACK),
        val stroke: PaintValue = PaintValue.None,
        val fillOpacity: Float = 1f,
        val strokeOpacity: Float = 1f,
        val width: Float = 1f,
        val cap: Paint.Cap = Paint.Cap.BUTT,
        val join: Paint.Join = Paint.Join.MITER,
        val miter: Float = 4f,
        val dash: DashPathEffect? = null,
        val evenOdd: Boolean = false,
        val rain: Boolean = false,
    )
    private sealed interface PaintValue {
        data object None : PaintValue
        data class Solid(val color: Int) : PaintValue
        data class Token(val name: String, val fallback: Int) : PaintValue
        data class Gradient(val id: String) : PaintValue
    }
    private data class Clip(
        val path: Path,
        val objectBox: Boolean,
    )
    private data class Viewport(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val viewBox: RectF,
        val matrix: Matrix,
        val clip: Boolean,
    )
    private data class Coordinate(
        val value: Float,
        val percent: Boolean,
    ) {
        companion object {
            fun parse(raw: String): Coordinate =
                if (raw.endsWith('%')) {
                    Coordinate(raw.dropLast(1).toFloat() / 100f, true)
                } else {
                    Coordinate(raw.toFloat(), false)
                }
        }
    }
    private data class Stop(
        val offset: Float,
        val color: PaintValue,
        val opacity: Float,
    )
    private data class Gradient(
        val index: Int,
        val radial: Boolean,
        val objectBox: Boolean,
        val x1: Coordinate,
        val y1: Coordinate,
        val x2: Coordinate,
        val y2: Coordinate,
        val cx: Coordinate,
        val cy: Coordinate,
        val radius: Coordinate,
        val stops: List<Stop>,
        val matrix: Matrix,
        val tile: Shader.TileMode,
    )

    private fun paint(raw: String): PaintValue {
        val value = raw.trim()
        if (value == "none") return PaintValue.None
        if (value.startsWith("url(")) {
            return PaintValue.Gradient(
                value.substringAfter('(').substringBefore(')').trim().trim('"', '\'').removePrefix("#"),
            )
        }
        if (value.startsWith("var(")) {
            val args = value.substringAfter('(').substringBeforeLast(')').split(',', limit = 2)
            require(args.size == 2) { "Living palette tokens need an authored fallback" }
            return PaintValue.Token(args[0].trim().removePrefix("--"), Color.parseColor(args[1].trim()))
        }
        return PaintValue.Solid(Color.parseColor(value))
    }

    private fun transformMatrix(raw: String): Matrix = Matrix().apply {
        TRANSFORMS.findAll(raw).forEach { match ->
            val values = numbers(match.groupValues[2])
            val operation = Matrix()
            when (match.groupValues[1]) {
                "matrix" -> {
                    require(values.size == 6)
                    operation.setValues(
                        floatArrayOf(
                            values[0],
                            values[2],
                            values[4],
                            values[1],
                            values[3],
                            values[5],
                            0f,
                            0f,
                            1f,
                        ),
                    )
                }
                "translate" -> operation.setTranslate(values[0], values.getOrElse(1) { 0f })
                "scale" -> operation.setScale(values[0], values.getOrElse(1) { values[0] })
                "rotate" ->
                    if (values.size == 3) {
                        operation.setRotate(values[0], values[1], values[2])
                    } else {
                        operation.setRotate(values[0])
                    }
                "skewX" -> operation.setSkew(tan(values[0] * PI / 180.0).toFloat(), 0f)
                "skewY" -> operation.setSkew(0f, tan(values[0] * PI / 180.0).toFloat())
                else -> error("Unsupported living transform: " + match.groupValues[1])
            }
            preConcat(operation)
        }
    }

    companion object {
        private val NUMBERS = Regex("""[-+]?(?:\d*\.?\d+)(?:[eE][-+]?\d+)?""")
        private val TRANSFORMS = Regex("""([A-Za-z]+)\s*\(([^)]*)\)""")
        private val DEFINITIONS = setOf("defs", "title", "desc", "linearGradient", "radialGradient", "stop", "clipPath")
        private val INK_HIDDEN = setOf(
            "scene-detail",
            "textile-detail",
            "contact-shadows",
            "pro-contact-shadow",
            "ambient-mote",
            "atmo-glass-drops",
            "atmo-glass-sheen",
            "atmo-floor-light",
            "atmo-volumetric-shafts",
            "atmo-lightning",
            "atmo-storm-wash",
            "crystal-refract-halo",
            "crystal-spark-top",
            "crystal-caustic-glint",
            "lantern-glow-aura",
        )
        private val WET_ONLY = setOf("atmo-rain", "atmo-dark-clouds", "atmo-glass-drops")
        private val STORM_ONLY = setOf("atmo-lightning", "atmo-storm-wash")
        private val FLOOR_POOLS_DAWN = arrayOf(
            floatArrayOf(374f, 354f, 108f, 20f, 0.52f),
            floatArrayOf(432f, 346f, 76f, 14f, 0.36f),
        )
        private val FLOOR_POOLS_SUNSET = arrayOf(
            floatArrayOf(406f, 354f, 122f, 21f, 0.54f),
            floatArrayOf(468f, 346f, 84f, 15f, 0.38f),
        )
        private val FLOOR_POOLS_NIGHT = arrayOf(
            floatArrayOf(466f, 352f, 98f, 19f, 0.48f),
            floatArrayOf(508f, 344f, 68f, 13f, 0.32f),
        )
        private val FLOOR_POOLS_DAY = arrayOf(
            floatArrayOf(458f, 352f, 114f, 22f, 0.54f),
            floatArrayOf(510f, 342f, 78f, 15f, 0.38f),
        )
        private val heroCacheLock = Any()

        @Volatile
        private var cachedHeroPainter: CrystalLivingCornerPainter? = null

        fun cachedHeroOrNull(): CrystalLivingCornerPainter? = cachedHeroPainter

        fun loadCachedHero(assets: AssetManager): CrystalLivingCornerPainter {
            cachedHeroPainter?.let { return it }
            return synchronized(heroCacheLock) {
                cachedHeroPainter ?: CrystalLivingCornerPainter(CrystalLivingArtwork.loadCached(assets)).also {
                    cachedHeroPainter = it
                }
            }
        }
        private val IDENTITY = CrystalLivingTransform()
        private val RESTING_HEAD = CrystalLivingTransform(scaleX = 1.06f, scaleY = 1.06f)
        private val PARTS = mapOf(
            "cat-head" to CrystalLivingPart.HEAD,
            "cat-body" to CrystalLivingPart.BODY,
            "pro-chest-bib" to CrystalLivingPart.CHEST,
            "cat-tail" to CrystalLivingPart.TAIL,
            "pro-ear-left" to CrystalLivingPart.EAR_LEFT,
            "pro-ear-right" to CrystalLivingPart.EAR_RIGHT,
            "pro-whiskers-left" to CrystalLivingPart.WHISKERS_LEFT,
            "pro-whiskers-right" to CrystalLivingPart.WHISKERS_RIGHT,
            "pro-muzzle-overlay" to CrystalLivingPart.MUZZLE,
            "forepaw-left" to CrystalLivingPart.PAW_LEFT,
            "forepaw-right" to CrystalLivingPart.PAW_RIGHT,
            "bed-cushion" to CrystalLivingPart.CUSHION,
            "hanging-crystal" to CrystalLivingPart.CRYSTAL,
            "toy-feather" to CrystalLivingPart.FEATHER,
            "post-dangler" to CrystalLivingPart.POM,
            "pro-iris-group" to CrystalLivingPart.IRIS,
        )
        private fun numbers(raw: String): FloatArray =
            NUMBERS.findAll(raw).map { it.value.toFloat() }.toList().toFloatArray()
        private fun fraction(time: Float, duration: Float, delay: Float = 0f): Float =
            ((time - delay) % duration + duration) % duration / duration
        private fun pulse(time: Float, duration: Float, delay: Float = 0f): Float {
            val p = fraction(time, duration, delay)
            return cssEase(if (p <= 0.5f) p * 2f else (1f - p) * 2f)
        }

        // CSS ease-in-out: invert the authored cubic Bezier x(.42,.58), then evaluate y(0,1).
        private fun cssEase(value: Float): Float {
            val x = value.coerceIn(0f, 1f)
            var lo = 0f
            var hi = 1f
            repeat(16) {
                val t = (lo + hi) / 2f
                val q = 1f - t
                val bx = 3f * q * q * t * 0.42f + 3f * q * t * t * 0.58f + t * t * t
                if (bx < x) lo = t else hi = t
            }
            val t = (lo + hi) / 2f
            return t * t * (3f - 2f * t)
        }
        private fun lerp(a: Float, b: Float, p: Float) = a + (b - a) * p
        private fun smooth(a: Float, b: Float, value: Float): Float {
            val p = ((value - a) / (b - a)).coerceIn(0f, 1f)
            return p * p * (3f - 2f * p)
        }
        private fun dropOpacity(p: Float): Float = when {
            p < 0.18f -> lerp(0f, 0.82f, p / 0.18f)
            p < 0.75f -> lerp(0.82f, 0.62f, (p - 0.18f) / 0.57f)
            else -> lerp(0.62f, 0f, (p - 0.75f) / 0.25f)
        }
        private fun sheenOpacity(p: Float): Float = when {
            p < 0.12f || p >= 0.52f -> 0f
            p < 0.28f -> lerp(0f, 0.36f, (p - 0.12f) / 0.16f)
            else -> lerp(0.36f, 0f, (p - 0.28f) / 0.24f)
        }
        private fun lightning(time: Float): Float {
            val p = fraction(time, 23f)
            return when {
                p < 0.43f || p >= 0.49f -> 0f
                p < 0.45f -> 0.65f * cssEase((p - 0.43f) / 0.02f)
                else -> 0.65f * (1f - cssEase((p - 0.45f) / 0.04f))
            }
        }
    }
}
