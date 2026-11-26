package eu.kanade.presentation.easteregg.crystal

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.PathParser
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Shared native geometry for the relic, inventory icons and equipped profile jewellery. */
internal class CrystalJewelPainter {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val innerMask = Path()
    private val paths = mutableMapOf<String, Path>()
    private var palette = CrystalPalette.Moon
    private var ink: Int? = null

    fun relic(
        canvas: Canvas,
        width: Float,
        height: Float,
        seconds: Float,
        colors: CrystalPalette,
        reduced: Boolean,
        inkColor: Int? = null,
    ) {
        if (width <= 0 || height <= 0) return
        palette = colors
        ink = inkColor
        val t = if (reduced) 0.6f else seconds
        val cx = width * 0.5f
        val cy = height * 0.5f + if (reduced) 0f else sin(t * 2.1f) * 2.2f
        val r = min(width, height) * 0.36f
        val ry = t * 0.75f
        val rx = 0.32f + sin(t * 0.9f) * 0.14f
        val rz = cos(t * 0.65f) * 0.08f
        data class Vertex(val x: Float, val y: Float, val v: CrystalPoint3)
        data class Facet(
            val a: Vertex,
            val b: Vertex,
            val c: Vertex,
            val nx: Float,
            val ny: Float,
            val nz: Float,
            val z: Float,
            val index: Int,
        )
        val vertices = (0 until 14).map { i ->
            val p = CrystalSceneGeometry.rotate(
                CrystalPoint3(
                    CrystalSceneData.vertices[i * 3],
                    CrystalSceneData.vertices[
                        i *
                            3 +
                            1,
                    ],
                    CrystalSceneData.vertices[i * 3 + 2],
                ),
                rx,
                ry,
                rz,
            )
            val perspective = 3.4f / (3.4f - p.z * 0.55f)
            Vertex(cx + p.x * r * perspective, cy + p.y * r * perspective, p)
        }
        val facets = CrystalSceneData.facets.mapIndexed { index, indices ->
            val a = vertices[indices[0]]
            val b = vertices[indices[1]]
            val c = vertices[indices[2]]
            val ux = b.v.x - a.v.x
            val uy = b.v.y - a.v.y
            val uz = b.v.z - a.v.z
            val vx = c.v.x - a.v.x
            val vy = c.v.y - a.v.y
            val vz = c.v.z - a.v.z
            val nx = uy * vz - uz * vy
            val ny = uz * vx - ux * vz
            val nz = ux * vy - uy * vx
            val length = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.0001f)
            Facet(a, b, c, nx / length, ny / length, nz / length, (a.v.z + b.v.z + c.v.z) / 3, index)
        }.sortedBy { it.z }
        if (ink == null) {
            glow(canvas, cx, cy, r * 1.45f, withAlpha(colors.gold, 0.42f), withAlpha(colors.rose, 0.16f))
            fill(colors.gold)
            paint.shader = LinearGradient(
                cx - r * 1.5f,
                cy,
                cx + r * 1.5f,
                cy,
                intArrayOf(0, withAlpha(colors.gold, 0.48f), 0),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(cx - r * 1.5f, cy - 1.2f, cx + r * 1.5f, cy + 1.2f, paint)
        }
        orbit(canvas, cx, cy, r, t, true)
        for (face in facets) {
            val front = face.nz >= -0.08f
            val light = max(0f, face.nx * -0.45f + face.ny * -0.62f + face.nz * 0.64f)
            val specular = max(0f, face.nz * 0.8f + light * 0.5f).pow(9)
            val dichroic = 0.5f + 0.5f * sin(face.index * 0.7f + ry * 1.4f)
            path.reset()
            path.moveTo(face.a.x, face.a.y)
            path.lineTo(face.b.x, face.b.y)
            path.lineTo(face.c.x, face.c.y)
            path.close()
            if (ink == null) {
                val base = blend(colors.gold, blend(colors.rose, colors.mint, dichroic), 0.45f)
                fill(
                    Color.rgb(
                        (Color.red(base) + specular * 55).toInt().coerceIn(0, 255),
                        (Color.green(base) + specular * 50).toInt().coerceIn(0, 255),
                        (Color.blue(base) + specular * 45).toInt().coerceIn(0, 255),
                    ),
                    if (front) 0.46f + light * 0.42f else 0.22f,
                )
                canvas.drawPath(path, paint)
            }
            stroke(
                ink ?: if (front) 0xFFFFF8E8.toInt() else colors.gold,
                if (ink != null) {
                    1f
                } else if (front) {
                    0.28f + specular * 0.55f
                } else {
                    0.16f
                },
                if (front) 0.85f else 0.5f,
            )
            canvas.drawPath(path, paint)
        }
        if (ink == null) glow(canvas, cx, cy, r * 0.42f, 0xF2FFFCF2.toInt(), withAlpha(colors.gold, 0.55f))
        orbit(canvas, cx, cy, r, t, false)
    }

    private fun orbit(canvas: Canvas, cx: Float, cy: Float, r: Float, t: Float, back: Boolean) {
        val save = canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate((-0.38f + sin(t * 0.5f) * 0.08f) * RAD_TO_DEGREES)
        stroke(if (back) palette.mint else palette.gold, if (back) 0.36f else 0.68f, if (back) 1.2f else 1.4f)
        rect.set(-r * 1.28f, -r * 0.42f, r * 1.28f, r * 0.42f)
        canvas.drawArc(rect, if (back) 180f else 0f, 180f, false, paint)
        if (!back) {
            fill(0xFFFFF9EC.toInt())
            canvas.drawCircle(cos(t * 1.6f) * r * 1.28f, sin(t * 1.6f) * r * 0.42f, 2.2f, paint)
        }
        canvas.restoreToCount(save)
    }

    fun paw(
        canvas: Canvas,
        x: Float,
        y: Float,
        size: Float,
        colors: CrystalPalette = palette,
        opacity: Float = 1f,
        inkColor: Int? = null,
    ) {
        palette = colors
        ink = inkColor
        val save = canvas.save()
        canvas.translate(x, y)
        canvas.scale(size / 64f, size / 64f)
        if (ink == null) glow(canvas, 32f, 34f, 27f, withAlpha(colors.rose, 0.14f * opacity), 0)
        val pads = arrayOf(
            floatArrayOf(14.5f, 23.5f, 5.2f, 7.4f, -26f),
            floatArrayOf(25.5f, 15.5f, 5.5f, 7.8f, -10f),
            floatArrayOf(38.5f, 15.5f, 5.5f, 7.8f, 10f),
            floatArrayOf(49.5f, 23.5f, 5.2f, 7.4f, 26f),
        )
        for (p in pads) {
            val s = canvas.save()
            canvas.rotate(p[4], p[0], p[1])
            pad(p[0] - 2, p[1] - 3, p[3] * 1.6f, opacity)
            rect.set(p[0] - p[2], p[1] - p[3], p[0] + p[2], p[1] + p[3])
            canvas.drawOval(rect, paint)
            if (ink == null) {
                fill(Color.WHITE, 0.65f * opacity)
                rect.set(p[0] - 3.1f, p[1] - 5.3f, p[0] + 0.8f, p[1] + 0.3f)
                canvas.drawOval(rect, paint)
            }
            canvas.restoreToCount(s)
        }
        pad(25f, 30f, 28f, opacity)
        val center =
            svg(
                "M32 29.5 C24 23.5,18.5 26.5,14.8 34.2 C11.2 41.8,13.5 50.5,22.2 51.5 C26.2 52,28.5 47.8,32 47.8 C35.5 47.8,37.8 52,41.8 51.5 C50.5 50.5,52.8 41.8,49.2 34.2 C45.5 26.5,40 23.5,32 29.5Z",
            )
        canvas.drawPath(center, paint)
        stroke(0xFFFFF6E4.toInt(), 0.5f * opacity, 0.6f)
        canvas.drawPath(center, paint)
        if (ink == null) {
            stroke(Color.WHITE, 0.55f * opacity, 2.2f)
            canvas.drawPath(svg("M23 33.5 C26 30.2,31 30.2,35 32.5"), paint)
            stroke(0xFFFFF9EC.toInt(), 0.52f * opacity, 1.35f)
            canvas.drawPath(
                svg("M24 33.5 C28 30.2,36 30.2,40 33.5 M23 13.5 Q25.5 11.5,27.5 13.5 M36.5 13.5 Q38.5 11.5,41 13.5"),
                paint,
            )
        }
        canvas.restoreToCount(save)
    }

    /** Sculpted jewel paw pendant with soft breathing halo and periodic 4-point star twinkle. */
    fun pawPendant(
        canvas: Canvas,
        width: Float,
        height: Float,
        colors: CrystalPalette,
        seconds: Float,
        reduced: Boolean,
        inkColor: Int? = null,
    ) {
        if (width <= 0f || height <= 0f) return
        palette = colors
        ink = inkColor
        val s = canvas.save()
        val scale = min(width, height) / 80f
        canvas.translate((width - 80f * scale) / 2f, (height - 80f * scale) / 2f)
        canvas.scale(scale, scale)

        val t = if (reduced) 0.4f else seconds
        if (ink == null) {
            val breath = if (reduced) 0.92f else 0.86f + 0.14f * sin(t * 2.0f)
            glow(
                canvas,
                40f,
                42f,
                38f,
                withAlpha(colors.gold, 0.42f * breath),
                withAlpha(colors.rose, 0.16f * breath),
            )
        }

        paw(canvas, 7f, 10f, 66f, colors, 1f, inkColor)

        if (ink == null) {
            val cycle = if (reduced) 0.84f else ((t % 4.5f) + 4.5f) % 4.5f / 4.5f
            if (cycle > 0.68f) {
                val pulse = sin(((cycle - 0.68f) / 0.32f) * PI.toFloat()).coerceIn(0f, 1f)
                val starScale = 0.55f + pulse * 0.72f
                val starSave = canvas.save()
                canvas.translate(57f, 19f)
                canvas.scale(starScale, starScale)
                fill(Color.WHITE, 0.95f * pulse)
                canvas.drawPath(
                    svg("M0 -8 L2 -2 L8 0 L2 2 L0 8 L-2 2 L-8 0 L-2 -2 Z"),
                    paint,
                )
                canvas.restoreToCount(starSave)
            }
        }
        canvas.restoreToCount(s)
    }

    fun medallion(canvas: Canvas, width: Float, height: Float, colors: CrystalPalette, inkColor: Int? = null) {
        palette = colors
        ink = inkColor
        val s = canvas.save()
        val scale = min(width, height) / 80f
        canvas.translate((width - 80 * scale) / 2, (height - 80 * scale) / 2)
        canvas.scale(scale, scale)
        if (ink == null) {
            glow(canvas, 40f, 42f, 38f, withAlpha(colors.gold, 0.34f), withAlpha(colors.rose, 0.12f))
        }
        // 16-petal gold rosette outer bezel
        val rosette = svg(ROSETTE_16_PATH)
        if (ink == null) {
            fill(0xFF1A2230.toInt())
            canvas.drawPath(rosette, paint)
        }
        metal(4f, 4f, 76f, 76f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f
        paint.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(rosette, paint)

        // Guilloché midnight-sapphire enamel dial
        well(37f, 32f, 36f)
        if (ink == null) canvas.drawCircle(40f, 42f, 29.5f, paint)
        metal(10f, 12f, 70f, 72f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.7f
        canvas.drawCircle(40f, 42f, 29.5f, paint)

        // 12 radial sun-ray guilloché lines
        if (ink == null) {
            stroke(colors.gold, 0.16f, 0.65f)
            canvas.drawPath(svg(GUILLOCHE_RAYS_PATH), paint)
        }

        // Rose-gold milgrain beaded inner ring
        stroke(colors.rose, if (ink == null) 0.78f else 1f, 0.9f)
        paint.pathEffect = DashPathEffect(floatArrayOf(1.5f, 2.3f), 0f)
        canvas.drawCircle(40f, 42f, 26.2f, paint)
        paint.pathEffect = null

        // 3D sculpted paw cameo relief (drop shadow + warm gold/rose relief)
        if (ink == null) {
            paw(canvas, 20.5f, 22.8f, 39f, colors, 0.38f, 0xFF090E16.toInt())
        }
        paw(canvas, 20.5f, 21.3f, 39f, colors, 1f, inkColor)

        // North Star crown jewel at 12 o'clock
        metal(32f, 2.5f, 48f, 17.5f)
        val star = svg("M40 2.5 L42.2 7.8 L47.5 10 L42.2 12.2 L40 17.5 L37.8 12.2 L32.5 10 L37.8 7.8 Z")
        canvas.drawPath(star, paint)
        stroke(0xFFFFFAF0.toInt(), 0.9f, 0.55f)
        canvas.drawPath(star, paint)
        if (ink == null) {
            fill(Color.WHITE, 0.96f)
            canvas.drawCircle(40f, 10f, 1.55f, paint)
            fill(Color.WHITE, 0.16f)
            canvas.drawPath(svg("M18 30 A25 25 0 0 1 58 22 A27 23 0 0 0 18 30 Z"), paint)
        }
        canvas.restoreToCount(s)
    }

    fun avatarFrame(
        canvas: Canvas,
        width: Float,
        height: Float,
        variant: CrystalFrameVariant,
        colors: CrystalPalette,
        seconds: Float,
        reduced: Boolean,
        avatarInset: Float = 3f,
        inkColor: Int? = null,
    ) {
        if (min(width, height) <= avatarInset * 2) return
        palette = colors
        ink = inkColor
        val cx = width / 2
        val cy = height / 2
        val radius = min(width, height) / 2 - 1.3f
        val scale = radius / 42f
        val s = canvas.save()
        canvas.translate(cx - 64 * scale, cy - 70 * scale)
        canvas.scale(scale, scale)

        // 1. Exterior elements (orbital threads & radially aligned ears) clipped strictly outside the photo circle
        val extSave = canvas.save()
        innerMask.reset()
        innerMask.addCircle(64f, 70f, 41.4f, Path.Direction.CW)
        canvas.clipOutPath(innerMask)

        if (variant == CrystalFrameVariant.Thread) {
            for (tilt in listOf(-24f, 24f)) {
                val o = canvas.save()
                canvas.rotate(tilt, 64f, 70f)
                stroke(colors.mint, 0.72f, 1.2f)
                rect.set(18.5f, 30.2f, 109.5f, 109.8f)
                canvas.drawOval(rect, paint)
                canvas.restoreToCount(o)
            }
        }

        // Draw left (-38 deg) and right (+38 deg) ears strictly along the radial normal of the circle (64, 70)
        for (side in listOf(-1, 1)) {
            val earSave = canvas.save()
            canvas.rotate(side * 38f, 64f, 70f)
            when (variant) {
                CrystalFrameVariant.Fold -> {
                    earShell(
                        canvas,
                        "M49.8 31.8 C48.8 20.4,54.8 14.6,64.0 14.6 C73.2 14.6,79.2 20.4,78.2 31.8 Z",
                        64f,
                    )
                    earConcha(
                        canvas,
                        "M53.6 31.2 C53.0 22.4,57.4 18.0,64.0 18.0 C70.6 18.0,75.0 22.4,74.4 31.2 Z",
                        64f,
                    )
                }
                CrystalFrameVariant.Point -> {
                    earShell(
                        canvas,
                        "M50.2 31.8 C51.4 21.0,56.6 12.4,61.8 9.0 Q64.0 7.6,66.2 9.0 C71.4 12.4,76.6 21.0,77.8 31.8 Z",
                        64f,
                    )
                    earConcha(
                        canvas,
                        "M54.0 31.2 C55.0 22.4,59.0 15.2,62.6 12.4 Q64.0 11.4,65.4 12.4 C69.0 15.2,73.0 22.4,74.0 31.2 Z",
                        64f,
                    )
                }
                CrystalFrameVariant.Thread -> {
                    earAstral(
                        canvas,
                        "M50.2 31.8 C51.4 21.0,56.6 12.4,61.8 9.0 Q64.0 7.6,66.2 9.0 C71.4 12.4,76.6 21.0,77.8 31.8 Z",
                        64f,
                    )
                    earConcha(
                        canvas,
                        "M54.0 31.2 C55.0 22.4,59.0 15.2,62.6 12.4 Q64.0 11.4,65.4 12.4 C69.0 15.2,73.0 22.4,74.0 31.2 Z",
                        64f,
                    )
                    // Centered celestial emblem inside the violet-rose inner ear concha
                    fill(0xFFFFF8EA.toInt())
                    if (side == -1) {
                        canvas.drawPath(
                            svg(
                                "M64 17.2 L65.3 20.7 L68.8 22.0 L65.3 23.3 L64 26.8 L62.7 23.3 L59.2 22.0 L62.7 20.7 Z",
                            ),
                            paint,
                        )
                    } else {
                        canvas.drawPath(
                            svg(
                                "M66.2 17.6 C63.2 18.0,61.2 20.4,61.6 23.2 C62.0 25.6,64.5 27.0,67.0 26.5 C63.5 28.4,59.2 26.2,58.8 22.4 C58.4 18.8,62.2 16.0,66.2 17.6 Z",
                            ),
                            paint,
                        )
                    }
                }
            }
            canvas.restoreToCount(earSave)
        }
        canvas.restoreToCount(extSave)

        // 2. Smooth solid jewelry bezel around the avatar (cx=64, cy=70, r=42) — welds ear bases cleanly
        if (ink == null) {
            stroke(0xFF061018.toInt(), 0.45f, 4.4f)
            canvas.drawCircle(64f, 71.0f, 42f, paint)
        }
        metal(22f, 28f, 106f, 112f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3.1f
        canvas.drawCircle(64f, 70f, 42f, paint)
        stroke(colors.rose, if (ink == null) 0.68f else 1f, 0.9f)
        canvas.drawCircle(64f, 70f, 39.8f, paint)

        // 3. Bottom clasp & side accents
        when (variant) {
            CrystalFrameVariant.Fold -> {
                metal(55f, 104f, 73f, 119f)
                val starClasp = svg(
                    "M64 104.2 L66.1 109.8 L71.7 111.8 L66.1 113.8 L64 119.4 L61.9 113.8 L56.3 111.8 L61.9 109.8 Z",
                )
                canvas.drawPath(starClasp, paint)
                stroke(0xFFFFF9EB.toInt(), 0.85f, 0.6f)
                canvas.drawPath(starClasp, paint)
                if (ink == null) {
                    fill(0xFFFFFDF8.toInt())
                    canvas.drawCircle(64f, 111.8f, 1.7f, paint)
                }
            }
            CrystalFrameVariant.Point -> {
                well(64f, 109f, 11f)
                if (ink == null) canvas.drawCircle(64f, 111.5f, 8.0f, paint)
                metal(55f, 103f, 73f, 120f)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.6f
                canvas.drawCircle(64f, 111.5f, 8.0f, paint)
                paw(canvas, 57.4f, 104.9f, 13.2f, colors, 1f, inkColor)
            }
            CrystalFrameVariant.Thread -> {
                fill(colors.gold)
                canvas.drawCircle(19.5f, 65f, 1.6f, paint)
                canvas.drawCircle(108.5f, 65f, 1.6f, paint)
                val prism = svg("M64 103.5 L69.8 111.2 L64 119.5 L58.2 111.2 Z")
                if (ink == null) {
                    fill(colors.mint)
                    paint.shader = LinearGradient(
                        58f,
                        103.5f,
                        70f,
                        119.5f,
                        intArrayOf(0xFFF5FFFB.toInt(), colors.mint, colors.rose),
                        floatArrayOf(0f, 0.52f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                    canvas.drawPath(prism, paint)
                }
                metal(58.2f, 103.5f, 69.8f, 119.5f)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.45f
                canvas.drawPath(prism, paint)
                stroke(0xFFFFF9EB.toInt(), 0.72f, 0.8f)
                canvas.drawPath(svg("M64 103.5 V119.5 M58.2 111.2 H69.8"), paint)
            }
        }
        canvas.restoreToCount(s)
    }

    fun titleGlyph(canvas: Canvas, width: Float, height: Float, colors: CrystalPalette, inkColor: Int? = null) {
        palette = colors
        ink = inkColor
        val save = canvas.save()
        val scale = min(width, height) / 80f
        canvas.translate((width - 80 * scale) / 2, (height - 80 * scale) / 2)
        canvas.scale(scale, scale)
        for (tilt in listOf(-24f, 24f)) {
            val s = canvas.save()
            canvas.rotate(tilt, 40f, 40f)
            metal(7f, 22f, 73f, 58f)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.3f
            rect.set(7f, 22f, 73f, 58f)
            canvas.drawOval(rect, paint)
            canvas.restoreToCount(s)
        }
        well(32f, 32f, 32f)
        rect.set(16f, 25f, 64f, 55f)
        if (ink == null) canvas.drawRoundRect(rect, 15f, 15f, paint)
        metal(16f, 25f, 64f, 55f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f
        canvas.drawRoundRect(rect, 15f, 15f, paint)
        metal(18f, 16f, 62f, 64f)
        canvas.drawPath(svg("M40 16 L43.5 34.5 L62 40 L43.5 45.5 L40 64 L36.5 45.5 L18 40 L36.5 34.5Z"), paint)
        fill(Color.WHITE)
        canvas.drawCircle(40f, 40f, 4f, paint)
        canvas.restoreToCount(save)
    }

    fun paletteGlyph(canvas: Canvas, width: Float, height: Float, colors: CrystalPalette, inkColor: Int? = null) {
        palette = colors
        ink = inkColor
        val s = canvas.save()
        val scale = min(width, height) / 80f
        canvas.translate((width - 80 * scale) / 2, (height - 80 * scale) / 2)
        canvas.scale(scale, scale)
        metal(8f, 28f, 40f, 60f)
        canvas.drawCircle(24f, 44f, 16f, paint)
        fill(colors.mint)
        canvas.drawCircle(54f, 44f, 16f, paint)
        pad(34f, 23f, 30f, 1f)
        canvas.drawCircle(40f, 30f, 18f, paint)
        if (ink == null) {
            fill(Color.WHITE, 0.75f)
            rect.set(28f, 19.5f, 40f, 26.5f)
            canvas.drawOval(rect, paint)
        }
        canvas.restoreToCount(s)
    }

    fun catFace(canvas: Canvas, width: Float, height: Float, colors: CrystalPalette, inkColor: Int? = null) {
        palette = colors
        ink = inkColor
        val s = canvas.save()
        val scale = min(width, height) / 80f
        canvas.translate((width - 80f * scale) / 2f, (height - 80f * scale) / 2f)
        canvas.scale(scale, scale)

        if (ink == null) {
            glow(canvas, 40f, 42f, 38f, withAlpha(colors.gold, 0.30f), withAlpha(colors.rose, 0.12f))
        }
        // Medallion well backdrop
        well(34f, 34f, 38f)
        if (ink == null) canvas.drawCircle(40f, 42f, 34f, paint)
        metal(8f, 8f, 72f, 74f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.0f
        canvas.drawCircle(40f, 42f, 34f, paint)

        // Folded Scottish ears (left & right)
        val leftEar = svg("M15 35 C12 24,20 17,31 22 C33 24,30 30,22 35 Z")
        val rightEar = svg("M65 35 C68 24,60 17,49 22 C47 24,50 30,58 35 Z")
        if (ink == null) {
            fill(0xFF9C785B.toInt())
            canvas.drawPath(leftEar, paint)
            canvas.drawPath(rightEar, paint)
            fill(colors.rose, 0.78f)
            canvas.drawPath(svg("M18 32 C17 26,22 21,29 24 C27 28,23 31,18 32 Z"), paint)
            canvas.drawPath(svg("M62 32 C63 26,58 21,51 24 C53 28,57 31,62 32 Z"), paint)
        }
        stroke(ink ?: 0xFFF7E5CA.toInt(), 0.9f, 1.4f)
        canvas.drawPath(leftEar, paint)
        canvas.drawPath(rightEar, paint)

        // Round fluffy head silhouette
        val head = svg(
            "M40 21 C56 21,65 31,65 44 C65 57,55 64,40 64 C25 64,15 57,15 44 C15 31,24 21,40 21 Z",
        )
        if (ink == null) {
            fill(0xFFEBD2B0.toInt())
            paint.shader = RadialGradient(
                40f,
                38f,
                28f,
                intArrayOf(0xFFFDF3E1.toInt(), 0xFFE9CFA8.toInt(), 0xFFC59D77.toInt(), 0xFF8E6B4E.toInt()),
                floatArrayOf(0f, 0.50f, 0.82f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawPath(head, paint)
            // Forehead tabby stripes ("M" mark)
            stroke(0xFF6B4F3B.toInt(), 0.65f, 1.7f)
            canvas.drawPath(svg("M33 25 C34 29,36 31,37 33 M40 23 V32 M47 25 C46 29,44 31,43 33"), paint)
            // Soft rosy cheeks
            fill(colors.rose, 0.42f)
            rect.set(19f, 45f, 29f, 52f)
            canvas.drawOval(rect, paint)
            rect.set(51f, 45f, 61f, 52f)
            canvas.drawOval(rect, paint)
            // Warm cream muzzle puff
            fill(0xFFFFFBF2.toInt(), 0.88f)
            rect.set(30f, 45f, 50f, 58f)
            canvas.drawOval(rect, paint)
        }
        stroke(ink ?: 0xFFFFF4DE.toInt(), 0.85f, 1.4f)
        canvas.drawPath(head, paint)

        // Warm amber eyes with starlight catchlights
        if (ink == null) {
            for (ex in listOf(29.5f, 50.5f)) {
                fill(0xFFD99B38.toInt())
                paint.shader = RadialGradient(
                    ex,
                    42f,
                    5.6f,
                    intArrayOf(0xFFF7D070.toInt(), 0xFFD48B28.toInt(), 0xFF593818.toInt()),
                    floatArrayOf(0f, 0.68f, 1f),
                    Shader.TileMode.CLAMP,
                )
                canvas.drawCircle(ex, 41.5f, 5.2f, paint)
                fill(0xFF1A1412.toInt())
                canvas.drawCircle(ex, 41.5f, 3.1f, paint)
                fill(Color.WHITE, 0.95f)
                canvas.drawCircle(ex - 1.5f, 39.8f, 1.35f, paint)
                fill(Color.WHITE, 0.70f)
                canvas.drawCircle(ex + 1.4f, 43.0f, 0.7f, paint)
            }
        } else {
            stroke(inkColor ?: Color.BLACK, 1f, 1.6f)
            canvas.drawCircle(29.5f, 41.5f, 4.5f, paint)
            canvas.drawCircle(50.5f, 41.5f, 4.5f, paint)
        }

        // Cute pink nose & :3 mouth
        val nose = svg("M37 47.2 Q40 46,43 47.2 L40 50.6 Z")
        fill(ink ?: 0xFFE58FA2.toInt())
        canvas.drawPath(nose, paint)
        stroke(ink ?: 0xFF634838.toInt(), 0.9f, 1.45f)
        canvas.drawPath(svg("M40 50.4 C39.5 53.5,35.5 54.2,33.5 52.0 M40 50.4 C40.5 53.5,44.5 54.2,46.5 52.0"), paint)

        // Whiskers
        stroke(ink ?: 0xFFFFF8EB.toInt(), 0.82f, 1.15f)
        canvas.drawPath(
            svg("M23 48 Q14 47,9 49 M24 51 Q15 51.5,10 54 M57 48 Q66 47,71 49 M56 51 Q65 51.5,70 54"),
            paint,
        )

        // Forehead starlight gem
        fill(ink ?: 0xFFFFF8EA.toInt())
        canvas.drawPath(
            svg("M40 7 L41.8 11.8 L46.6 13.5 L41.8 15.2 L40 20 L38.2 15.2 L33.4 13.5 L38.2 11.8 Z"),
            paint,
        )
        canvas.restoreToCount(s)
    }

    fun cameoPortrait(canvas: Canvas, width: Float, height: Float, colors: CrystalPalette, inkColor: Int? = null) {
        palette = colors
        ink = inkColor
        val s = canvas.save()
        val scale = min(width, height) / 80f
        canvas.translate((width - 80f * scale) / 2f, (height - 80f * scale) / 2f)
        canvas.scale(scale, scale)

        innerMask.reset()
        innerMask.addCircle(40f, 40f, 39.5f, Path.Direction.CW)
        canvas.clipPath(innerMask)

        if (ink == null) {
            fill(0xFF172834.toInt())
            paint.shader = RadialGradient(
                40f,
                30f,
                46f,
                intArrayOf(0xFF2C4552.toInt(), 0xFF172834.toInt(), 0xFF0B151D.toInt()),
                floatArrayOf(0f, 0.62f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(40f, 40f, 40f, paint)
            fill(colors.gold, 0.14f)
            canvas.drawCircle(40f, 32f, 26f, paint)
        }

        // Tabby bust & cream bib
        val bust = svg("M11 82 C13 60,24 50,40 50 C56 50,67 60,69 82 Z")
        if (ink == null) {
            fill(0xFFC69F73.toInt())
            paint.shader = LinearGradient(
                18f,
                50f,
                62f,
                82f,
                intArrayOf(0xFFF2DFC1.toInt(), 0xFFC69F73.toInt(), 0xFF785B3F.toInt()),
                floatArrayOf(0f, 0.48f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawPath(bust, paint)
            fill(0xFFF5E6CC.toInt(), 0.92f)
            canvas.drawPath(svg("M23 55 C29 51,51 51,57 55 C59 64,52 72,40 74 C28 72,21 64,23 55 Z"), paint)
            stroke(0xFF6D5239.toInt(), 0.65f, 1.3f)
            paint.pathEffect = DashPathEffect(floatArrayOf(2.5f, 3f), 0f)
            canvas.drawPath(svg("M27 60 Q40 66,53 60"), paint)
            paint.pathEffect = null
        } else {
            stroke(inkColor ?: Color.BLACK, 1f, 1.4f)
            canvas.drawPath(bust, paint)
        }

        // Folded ears & round head
        val ears = svg("M21 24 C19 17,25 12,32 15 L29 25 Z M59 24 C61 17,55 12,48 15 L51 25 Z")
        if (ink == null) {
            fill(0xFF8C6B4A.toInt())
            canvas.drawPath(ears, paint)
        }
        stroke(ink ?: 0xFF5B432D.toInt(), 1f, 1f)
        canvas.drawPath(ears, paint)

        rect.set(20.5f, 16.8f, 59.5f, 51.2f)
        if (ink == null) {
            fill(0xFFC69F73.toInt())
            paint.shader = LinearGradient(
                22f,
                17f,
                58f,
                51f,
                intArrayOf(0xFFF2DFC1.toInt(), 0xFFC69F73.toInt(), 0xFF785B3F.toInt()),
                floatArrayOf(0f, 0.48f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawOval(rect, paint)
            fill(0xFFF6E8CF.toInt(), 0.90f)
            canvas.drawPath(svg("M26 37 C31 34,49 34,54 37 C56 44,50 50,40 50 C30 50,24 44,26 37 Z"), paint)
            stroke(0xFF4F3A27.toInt(), 0.82f, 1.5f)
            canvas.drawPath(svg("M34 19 L36 26 M40 18 V25 M46 19 L44 26"), paint)
        }
        stroke(ink ?: 0xFF5E4630.toInt(), 1f, 1f)
        rect.set(20.5f, 16.8f, 59.5f, 51.2f)
        canvas.drawOval(rect, paint)

        // Amber eyes & nose
        for (ex in listOf(32.5f, 47.5f)) {
            if (ink == null) {
                fill(0xFFE5AF49.toInt())
                rect.set(ex - 3.7f, 30.1f, ex + 3.7f, 36.9f)
                canvas.drawOval(rect, paint)
                stroke(0xFF43301F.toInt(), 1f, 0.8f)
                canvas.drawOval(rect, paint)
                fill(0xFF2B1E14.toInt())
                rect.set(ex - 1.2f, 31f, ex + 1.2f, 36f)
                canvas.drawOval(rect, paint)
                fill(0xFFFFF9EB.toInt())
                canvas.drawCircle(ex - 1.2f, 32.2f, 1f, paint)
            } else {
                stroke(inkColor ?: Color.BLACK, 1f, 1.2f)
                canvas.drawCircle(ex, 33.5f, 3.2f, paint)
            }
        }
        fill(ink ?: 0xFFD98286.toInt())
        canvas.drawPath(svg("M37.8 38.2 Q40 37.2,42.2 38.2 L40 40.8 Z"), paint)
        stroke(ink ?: 0xFF694E36.toInt(), 1f, 1.05f)
        canvas.drawPath(svg("M40 40.8 V43.2 M40 43.2 Q36.5 45.2,34 43.5 M40 43.2 Q43.5 45.2,46 43.5"), paint)

        if (ink == null) {
            fill(Color.WHITE, 0.13f)
            canvas.drawPath(svg("M6 24 C16 5,42 0,65 10 C47 6,22 13,9 32 Z"), paint)
        }
        canvas.restoreToCount(s)
    }

    private fun earShell(canvas: Canvas, data: String, center: Float) {
        val p = svg(data)
        if (ink == null) {
            fill(palette.gold)
            paint.shader = LinearGradient(
                center - 12f,
                9f,
                center + 12f,
                32f,
                intArrayOf(0xFFFFF6E4.toInt(), palette.gold, 0xFF6C5247.toInt()),
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawPath(p, paint)
        }
        metal(center - 14f, 9f, center + 14f, 32f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.0f
        paint.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(p, paint)
    }

    private fun earConcha(canvas: Canvas, data: String, center: Float) {
        val p = svg(data)
        if (ink == null) {
            fill(palette.rose)
            paint.shader = RadialGradient(
                center,
                24f,
                14f,
                intArrayOf(0xEBFFDCE8.toInt(), withAlpha(palette.rose, 0.82f), 0xDB4E3040.toInt()),
                floatArrayOf(0f, 0.60f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawPath(p, paint)
        }
        stroke(palette.rose, if (ink == null) 0.72f else 1f, 0.8f)
        canvas.drawPath(p, paint)
    }

    private fun earAstral(canvas: Canvas, data: String, center: Float) {
        val p = svg(data)
        if (ink == null) {
            fill(palette.mint)
            paint.shader = LinearGradient(
                center - 12f,
                9f,
                center + 12f,
                32f,
                intArrayOf(0xE6F5FFFB.toInt(), withAlpha(palette.mint, 0.82f), withAlpha(palette.rose, 0.78f)),
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawPath(p, paint)
        }
        metal(center - 14f, 9f, center + 14f, 32f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.0f
        paint.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(p, paint)
    }
    private fun pad(x: Float, y: Float, radius: Float, opacity: Float) {
        fill(palette.gold, opacity)
        if (ink == null) {
            paint.shader = RadialGradient(
                x,
                y,
                radius,
                intArrayOf(0xFFFFF9EC.toInt(), palette.gold, palette.rose, 0xFF9E788F.toInt()),
                floatArrayOf(0f, 0.42f, 0.82f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
    }
    private fun metal(x: Float, y: Float, x2: Float, y2: Float) {
        fill(palette.gold)
        if (ink == null) {
            paint.shader = LinearGradient(
                x,
                y,
                x2,
                y2,
                intArrayOf(0xFFFFF8EA.toInt(), palette.gold, 0xFFC9A97C.toInt(), palette.rose, 0xFFFFF2D6.toInt()),
                floatArrayOf(0f, 0.28f, 0.55f, 0.78f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
    }
    private fun well(x: Float, y: Float, radius: Float) {
        fill(0xFF131829.toInt())
        if (ink ==
            null
        ) {
            paint.shader =
                RadialGradient(
                    x,
                    y,
                    radius,
                    intArrayOf(0xFF2B2640.toInt(), 0xFF131829.toInt(), 0xFF080B14.toInt()),
                    floatArrayOf(0f, 0.52f, 1f),
                    Shader.TileMode.CLAMP,
                )
        }
    }
    private fun glow(canvas: Canvas, x: Float, y: Float, radius: Float, center: Int, middle: Int) {
        fill(Color.WHITE)
        paint.shader =
            RadialGradient(
                x,
                y,
                radius,
                intArrayOf(center, middle, 0),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
        canvas.drawCircle(x, y, radius, paint)
    }
    private fun fill(color: Int, opacity: Float = 1f) {
        paint.shader = null
        paint.pathEffect = null
        paint.style = Paint.Style.FILL
        paint.color = ink ?: color
        paint.alpha = if (ink != null) 255 else (opacity.coerceIn(0f, 1f) * 255).toInt()
    }
    private fun stroke(color: Int, opacity: Float, width: Float) {
        fill(color, opacity)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.strokeCap =
            Paint.Cap.ROUND
    }
    private fun svg(data: String) = paths.getOrPut(data) { requireNotNull(PathParser.createPathFromPathData(data)) }
    private fun withAlpha(color: Int, a: Float) = (color and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)
    private fun blend(a: Int, b: Int, t: Float) = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (
            Color.blue(a) +
                (Color.blue(b) - Color.blue(a)) * t
            ).toInt(),
    )
    companion object {
        private val RAD_TO_DEGREES = 180f / PI.toFloat()
        private const val ROSETTE_16_PATH =
            "M71.2 35.8 Q75.6 42.0 71.2 48.2 L71.2 48.2 Q72.9 55.6 66.4 59.7 L66.4 59.7 Q65.2 67.2 57.7 68.4 L57.7 68.4 Q53.6 74.9 46.2 73.2 L46.2 73.2 Q40.0 77.6 33.8 73.2 L33.8 73.2 Q26.4 74.9 22.3 68.4 L22.3 68.4 Q14.8 67.2 13.6 59.7 L13.6 59.7 Q7.1 55.6 8.8 48.2 L8.8 48.2 Q4.4 42.0 8.8 35.8 L8.8 35.8 Q7.1 28.4 13.6 24.3 L13.6 24.3 Q14.8 16.8 22.3 15.6 L22.3 15.6 Q26.4 9.1 33.8 10.8 L33.8 10.8 Q40.0 6.4 46.2 10.8 L46.2 10.8 Q53.6 9.1 57.7 15.6 L57.7 15.6 Q65.2 16.8 66.4 24.3 L66.4 24.3 Q72.9 28.4 71.2 35.8 Z"
        private const val GUILLOCHE_RAYS_PATH =
            "M40 42 L67 42 M40 42 L63.4 55.5 M40 42 L53.5 65.4 M40 42 L40 69 M40 42 L26.5 65.4 M40 42 L16.6 55.5 M40 42 L13 42 M40 42 L16.6 28.5 M40 42 L26.5 18.6 M40 42 L40 15 M40 42 L53.5 18.6 M40 42 L63.4 28.5"
    }
}
