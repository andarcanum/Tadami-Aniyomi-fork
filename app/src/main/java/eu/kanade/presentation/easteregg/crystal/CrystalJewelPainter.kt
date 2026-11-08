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
        }
        canvas.restoreToCount(save)
    }

    fun medallion(canvas: Canvas, width: Float, height: Float, colors: CrystalPalette, inkColor: Int? = null) {
        palette = colors
        ink = inkColor
        val s = canvas.save()
        val scale = min(width, height) / 80f
        canvas.translate((width - 80 * scale) / 2, (height - 80 * scale) / 2)
        canvas.scale(scale, scale)
        metal(0f, 0f, 80f, 80f)
        val rim =
            svg(
                "M40 4 L47 8.5 L55.5 8.2 L60.2 15.2 L68 18.5 L69.5 26.8 L75.2 33 L73.2 41.2 L76 49.2 L70.5 55.8 L69.8 64.2 L62.2 68 L58 75.2 L49.5 75.2 L40 78 L30.5 75.2 L22 75.2 L17.8 68 L10.2 64.2 L9.5 55.8 L4 49.2 L6.8 41.2 L4.8 33 L10.5 26.8 L12 18.5 L19.8 15.2 L24.5 8.2 L33 8.5Z",
            )
        if (ink == null) canvas.drawPath(rim, paint)
        stroke(0xFFFFF9EB.toInt(), 1f, 0.9f)
        canvas.drawPath(rim, paint)
        well(30f, 30f, 53f)
        if (ink == null) canvas.drawCircle(40f, 41f, 30.5f, paint)
        metal(10f, 10f, 70f, 70f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.2f
        canvas.drawCircle(40f, 41f, 30.5f, paint)
        stroke(0xFFFFF4DC.toInt(), 0.85f, 0.7f)
        paint.pathEffect = DashPathEffect(floatArrayOf(2.2f, 3.2f), 0f)
        canvas.drawCircle(40f, 41f, 27.5f, paint)
        paint.pathEffect = null
        paw(canvas, 18f, 19f, 44f, colors, 1f, inkColor)
        fill(0xFFFFF8EA.toInt())
        canvas.drawPath(svg("M40 8 L42.2 13.2 L47.5 15 L42.2 16.8 L40 22 L37.8 16.8 L32.5 15 L37.8 13.2Z"), paint)
        if (ink == null) {
            fill(Color.WHITE, 0.22f)
            canvas.drawPath(svg("M18 29 A26 26 0 0 1 58 21 A28 24 0 0 0 18 29Z"), paint)
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
        val photoRadius = (min(width, height) / 2 - avatarInset).coerceAtLeast(1f)
        val scale = radius / 42f
        val s = canvas.save()
        canvas.translate(cx - 64 * scale, cy - 70 * scale)
        canvas.scale(scale, scale)
        if (ink == null) {
            stroke(colors.rose, 0.16f, 5f)
            canvas.drawCircle(64f, 70f, 44f, paint)
            stroke(0xFF182032.toInt(), 1f, 4.2f)
            canvas.drawCircle(64f, 70f, 42f, paint)
        }
        metal(22f, 28f, 106f, 112f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.6f
        canvas.drawCircle(64f, 70f, 42f, paint)
        if (variant == CrystalFrameVariant.Fold) {
            stroke(0xFFFFF9EB.toInt(), 0.55f, 0.6f)
            paint.pathEffect = DashPathEffect(floatArrayOf(3f, 4f), 0f)
            canvas.drawCircle(64f, 70f, 39.2f, paint)
            paint.pathEffect = null
            ear(canvas, "M24 44 C17 33,21 19,35 20 C48 21,54 31,49 40 L39 36 C33 37,28 40,24 44Z", 35f)
            ear(canvas, "M104 44 C111 33,107 19,93 20 C80 21,74 31,79 40 L89 36 C95 37,100 40,104 44Z", 93f)
            stroke(0xFFFFF3DB.toInt(), 0.8f, 1.2f)
            canvas.drawPath(svg("M25 31 C34 27,43 29,48 39 M103 31 C94 27,85 29,80 39"), paint)
            fill(0xFFFFF8EA.toInt())
            canvas.drawPath(
                svg("M64 105 L66 110.5 L71.5 112.5 L66 114.5 L64 120 L62 114.5 L56.5 112.5 L62 110.5Z"),
                paint,
            )
        } else if (variant == CrystalFrameVariant.Point) {
            ear(canvas, "M24 46 L25 14 Q26 9,32 14 L52 34 L42 41Z", 32f)
            ear(canvas, "M104 46 L103 14 Q102 9,96 14 L76 34 L86 41Z", 96f)
            stroke(0xFFFFF6E0.toInt(), 0.85f, 0.9f)
            canvas.drawPath(svg("M29 19 L33 35 L43 33 M99 19 L95 35 L85 33"), paint)
            fill(Color.WHITE)
            canvas.drawCircle(29f, 19f, 1.6f, paint)
            canvas.drawCircle(99f, 19f, 1.6f, paint)
        } else {
            for (tilt in listOf(-25f, 25f)) {
                val o = canvas.save()
                canvas.rotate(tilt, 64f, 70f)
                stroke(colors.mint, 0.65f, 1.4f)
                rect.set(19f, 29f, 109f, 111f)
                canvas.drawOval(rect, paint)
                canvas.restoreToCount(o)
            }
            well(64f, 108f, 15f)
            if (ink == null) canvas.drawCircle(64f, 112f, 11f, paint)
            metal(53f, 101f, 75f, 123f)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.8f
            canvas.drawCircle(64f, 112f, 11f, paint)
            paw(canvas, 54f, 102f, 20f, colors, 1f, inkColor)
        }
        canvas.restoreToCount(s)
        if (ink == null) drawFrameOrbit(canvas, cx, cy, radius, photoRadius, if (reduced) 1.2f else seconds, variant)
    }

    private fun drawFrameOrbit(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        photoRadius: Float,
        t: Float,
        variant: CrystalFrameVariant,
    ) {
        val saved = canvas.save()
        innerMask.reset()
        innerMask.addCircle(cx, cy, photoRadius, Path.Direction.CW)
        canvas.clipOutPath(innerMask) // API26 baseline: all particles are strictly outside the photo.
        val count = if (variant == CrystalFrameVariant.Point) 7 else 5
        for (i in 0 until count) {
            val theta = t * (0.65f + i % 2 * 0.22f) + i * 2f * PI.toFloat() / count
            val tilt = if (i % 2 == 0) -0.34f else 0.28f
            for (step in 8 downTo 0) {
                val angle = theta - step * 0.045f
                val x = cos(angle) * radius * 1.18f
                val y = sin(angle) * radius * 0.48f
                val z = sin(angle)
                val decay = 1 - step / 9f
                val a = 0.22f + 0.78f * (z + 1) * 0.5f
                fill(if (step == 0) 0xFFFFFAEE.toInt() else palette.gold, a * if (step == 0) 0.95f else decay * 0.42f)
                canvas.drawCircle(
                    cx + x * cos(tilt) - y * sin(tilt),
                    cy + x * sin(tilt) + y * cos(tilt),
                    (if (step == 0) 2.4f else 1.6f * decay) * (0.7f + 0.35f * (z + 1) * 0.5f),
                    paint,
                )
            }
        }
        canvas.restoreToCount(saved)
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

    private fun ear(canvas: Canvas, data: String, center: Float) {
        fill(palette.rose)
        if (ink == null) {
            paint.shader = RadialGradient(
                center,
                33f,
                18f,
                intArrayOf(0xB8F2C6D8.toInt(), 0x529E6D88, 0xEB192030.toInt()),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        val p = svg(data)
        if (ink == null) canvas.drawPath(p, paint)
        metal(center - 15, 15f, center + 15, 44f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.4f
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
    }
}
