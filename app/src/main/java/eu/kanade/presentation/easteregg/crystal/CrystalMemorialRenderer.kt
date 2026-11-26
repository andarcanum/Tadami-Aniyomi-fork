package eu.kanade.presentation.easteregg.crystal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Rendered in logical dp; the Compose host applies density once at the canvas boundary. */
internal class CrystalMemorialRenderer(private val pool: CrystalPortraitPool) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val imagePaint = Paint().apply { isFilterBitmap = pool.style == CrystalPortrait.Photo }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val clearPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    private val mask = requireNotNull(pool.bitmap.copy(Bitmap.Config.ARGB_8888, true))
    private val maskCanvas = Canvas(mask)
    private val destination = RectF()
    private var released = 0
    private var previousElapsed = -1f
    private val visibility = FloatArray(82)
    private val pulses = FloatArray(82)
    private val projected = Array(82) { CrystalProjectedPoint(0f, 0f, 0f, 1f) }
    private val star =
        radialSprite(
            96,
            intArrayOf(0xFFFFFAE8.toInt(), 0xD9FFF2CE.toInt(), 0x42EACDA2, 0x0FDEB2C6, 0),
            floatArrayOf(0f, 0.08f, 0.26f, 0.58f, 1f),
        )
    private val frontier =
        radialSprite(64, intArrayOf(0xEBFFF4D6.toInt(), 0x73F2CC98, 0x24DEACC4, 0), floatArrayOf(0f, 0.35f, 0.7f, 1f))

    // Keep every star carrier, but sample ordinary dust so the dissolve never becomes a glowing sheet.
    private val flightParticles = Array(pool.cells.size) { index ->
        val assigned = pool.cells[index].target >= 0
        val depth = CrystalSceneGeometry.hash(index + 41)
        val soft = !assigned && depth > 0.72f
        FlightParticle(
            visible = assigned || index % 3 == 0,
            sprite = if (soft) frontier else star,
            size = 6f + depth * 14f,
            opacity = (0.35f + depth * 0.35f) * if (soft) 0.55f else 1f,
            speed = 0.65f + depth * 0.7f,
            trail = assigned && index % 7 == 0 && depth > 0.6f,
        )
    }
    private val grayscale = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
    private val random = CrystalVisualRandom(42017)
    private val stars = List(78) {
        floatArrayOf(
            random.next(),
            random.next(),
            0.35f + random.next() * 0.95f,
            0.14f + random.next() * 0.38f,
            random.next() * TAU,
            0.25f + random.next() * 0.75f,
            random.next(),
        )
    }
    private val bokeh = List(14) {
        floatArrayOf(
            random.next(),
            random.next(),
            14f + random.next() * 24f,
            (random.next() - 0.5f) * 6f,
            -5f - random.next() * 7f,
            random.next() * TAU,
        )
    }

    fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        elapsedMs: Float,
        ambientSeconds: Float,
        pointerX: Float,
        pointerY: Float,
        reduced: Boolean,
        eInk: Boolean = false,
        monochrome: Boolean = false,
        inkBackground: Int = Color.WHITE,
        inkForeground: Int = Color.BLACK,
        fittedArtwork: Boolean = false,
        ceremonyBlend: Float = 0f,
        entranceReveal: Float = 1f,
    ) {
        if (width <= 0f || height <= 0f) return
        val m = width / 404f
        val ambient = if (reduced) 0f else ambientSeconds
        val blend = if (fittedArtwork) 0f else ceremonyBlend.coerceIn(0f, 1f)
        val reveal = if (eInk || reduced) 1f else entranceReveal.coerceIn(0f, 1f)
        val atmosphereVisibility = if (reveal < 1f) CrystalLunarVeil.atmosphereAlpha(reveal) else 1f
        canvas.drawColor(if (eInk) inkBackground else 0xFF050811.toInt())
        if (!eInk) {
            drawAtmosphere(canvas, width, height, ambient, pointerX, pointerY, reduced, blend, atmosphereVisibility)
        }
        val rx = (if (reduced) 0f else cos(ambient * 0.52f) * 0.07f) - pointerY * 0.18f
        val ry = (if (reduced) 0f else sin(ambient * 0.65f) * 0.11f) + pointerX * 0.22f
        for (i in projected.indices) {
            projected[i] = CrystalSceneGeometry.projectFace(i, width, height, rx, ry, fittedArtwork, blend)
        }
        drawConstellation(canvas, elapsedMs, m, reduced, eInk, inkForeground)

        val image = pool.bitmap
        val scale = if (fittedArtwork) {
            min(width * 0.78f / image.width, height * 0.85f / image.height)
        } else {
            min(width * 0.65f / image.width, min(height * 0.54f, width * 1.24f) / image.height)
        }
        val pw = image.width * scale
        val ph = image.height * scale
        val cx = width * 0.5f
        val cy = height * if (fittedArtwork) 0.5f else 0.425f
        val breath = if (reduced) 0f else sin(ambient * 1.1f)
        val gather = CrystalTimeline.smooth(elapsedMs / CrystalTimeline.GATHER_MS)
        val veilPortraitAlpha = if (reveal < 1f && elapsedMs < CrystalTimeline.GATHER_MS) {
            CrystalLunarVeil.portraitAlpha(reveal)
        } else {
            1f
        }
        val veilPortraitScale = if (reveal < 1f && elapsedMs < CrystalTimeline.GATHER_MS) {
            CrystalLunarVeil.portraitScale(reveal)
        } else {
            1f
        }
        val transformScale = (1 + breath * 0.004f) * (1 + 0.032f * gather) * veilPortraitScale
        val transformY = -breath * 1.8f * m - 4f * m * gather
        val save = canvas.save()
        canvas.translate(cx, cy + transformY)
        canvas.scale(transformScale, transformScale)
        imagePaint.colorFilter = if (monochrome) grayscale else null
        imagePaint.alpha = 255
        destination.set(-pw / 2, -ph / 2, pw / 2, ph / 2)
        val e = elapsedMs - CrystalTimeline.GATHER_MS
        if (reduced) {
            imagePaint.alpha = alpha(1 - CrystalTimeline.smooth(elapsedMs / 750f))
            canvas.drawBitmap(image, null, destination, imagePaint)
        } else if (elapsedMs < CrystalTimeline.GATHER_MS) {
            if (released > 0) resetMask()
            if (veilPortraitAlpha > 0.003f) {
                imagePaint.alpha = alpha(veilPortraitAlpha)
                canvas.drawBitmap(image, null, destination, imagePaint)
            }
        } else {
            advanceMask(e)
            if (released < pool.cells.size) {
                canvas.drawBitmap(mask, null, destination, imagePaint)
                for (i in max(0, released - 25) until min(pool.cells.size, released + 95) step 2) {
                    val cell = pool.cells[i]
                    drawSprite(
                        canvas,
                        frontier,
                        (cell.nx - 0.5f) * pw,
                        (cell.ny - 0.5f) * ph,
                        28f * m,
                        0.28f * cell.alpha,
                    )
                }
            }
            val sx = pw / image.width
            val sy = ph / image.height
            for (i in 0 until released) {
                val cell = pool.cells[i]
                val age = e - cell.birth * CrystalTimeline.DISSOLVE_MS
                if (age < 0f || age >= CrystalTimeline.FLIGHT_MS) continue
                val u = age / CrystalTimeline.FLIGHT_MS
                val seconds = age * 0.001f
                val particle = flightParticles[i]
                val baseX = flightX(cell, particle, seconds, u, pw, m)
                val baseY = flightY(cell, particle, seconds, u, ph, m)
                if (u < 0.55f) {
                    val dissolve = CrystalTimeline.smooth(u / 0.55f)
                    val shrink = 1 - 0.65f * dissolve
                    val dw = cell.source.width() * sx * shrink
                    val dh = cell.source.height() * sy * shrink
                    imagePaint.alpha = alpha(1 - dissolve)
                    destination.set(baseX - dw / 2, baseY - dh / 2, baseX + dw / 2, baseY + dh / 2)
                    canvas.drawBitmap(image, cell.source, destination, imagePaint)
                }
                if (!particle.visible || eInk) continue
                val dest = if (cell.target >= 0) projected[cell.target] else null
                val dx = if (dest != null) (dest.x - cx) / transformScale else baseX
                val dy = if (dest != null) (dest.y - cy - transformY) / transformScale else baseY
                val toward = if (dest != null) CrystalTimeline.smooth((u - 0.12f) / 0.88f) else 0f
                val px = mix(baseX, dx, toward)
                val py = mix(baseY, dy, toward)
                val a = CrystalTimeline.smooth(u / 0.18f) *
                    (1 - CrystalTimeline.smooth((u - 0.84f) / 0.16f)) * cell.alpha * particle.opacity
                if (a < 1f / 255f) continue
                val size = particle.size * m
                if (particle.trail) {
                    // A few bright carriers leave only 54ms of light, never a stroked flight path.
                    for (k in 3 downTo 1) {
                        val previousSeconds = max(0f, seconds - k * 0.018f)
                        val previousU = previousSeconds / (CrystalTimeline.FLIGHT_MS * 0.001f)
                        val previousToward = CrystalTimeline.smooth((previousU - 0.12f) / 0.88f)
                        val tx = mix(flightX(cell, particle, previousSeconds, previousU, pw, m), dx, previousToward)
                        val ty = mix(flightY(cell, particle, previousSeconds, previousU, ph, m), dy, previousToward)
                        val length = hypot(tx - px, ty - py)
                        val limit = 4f * m
                        val shorten = if (length > limit) limit / length else 1f
                        val taper = 1f - k / 4f
                        drawSprite(
                            canvas,
                            star,
                            px + (tx - px) * shorten,
                            py + (ty - py) * shorten,
                            size * (0.7f + taper * 0.3f),
                            a * taper * taper * 0.18f,
                        )
                    }
                }
                drawSprite(canvas, particle.sprite, px, py, size, a)
            }
        }
        canvas.restoreToCount(save)
        imagePaint.alpha = 255
        if (!eInk && !reduced) {
            for (b in bokeh) {
                val bx = positiveMod(b[0] * width + b[3] * ambient + pointerX * 22, width + 60) - 30
                val by = positiveMod(b[1] * height + b[4] * ambient + pointerY * 22, height + 60) - 30
                val pulse = 0.5f + 0.5f * sin(ambient * 0.9f + b[5])
                drawSprite(canvas, frontier, bx, by, b[2] * 2, (0.045f + pulse * 0.055f) * atmosphereVisibility)
            }
            if (reveal < 0.999f) {
                drawLunarVeilEntrance(canvas, width, height, cx, cy, m, reveal)
            }
        }
    }

    private fun flightX(
        cell: CrystalPortraitPool.Cell,
        particle: FlightParticle,
        seconds: Float,
        u: Float,
        width: Float,
        m: Float,
    ): Float = (cell.nx - 0.5f) * width + cell.vx * seconds * particle.speed * m +
        sin(seconds * 1.4f + cell.phase) * cell.curl * (1 + u * 1.6f) * m

    private fun flightY(
        cell: CrystalPortraitPool.Cell,
        particle: FlightParticle,
        seconds: Float,
        u: Float,
        height: Float,
        m: Float,
    ): Float = (cell.ny - 0.5f) * height - cell.vy * seconds * (1 + u * 0.6f) * particle.speed * m +
        cos(seconds * 1.1f + cell.phase) * cell.curl * 0.45f * m

    private class FlightParticle(
        val visible: Boolean,
        val sprite: Bitmap,
        val size: Float,
        val opacity: Float,
        val speed: Float,
        val trail: Boolean,
    )

    private fun drawAtmosphere(
        canvas: Canvas,
        w: Float,
        h: Float,
        time: Float,
        px: Float,
        py: Float,
        reduced: Boolean,
        ceremonyBlend: Float,
        visibility: Float = 1f,
    ) {
        val skyShift = ceremonyBlend * 0.12f
        val haloShift = ceremonyBlend * 0.17f
        fill(0xFF0B101D.toInt(), visibility)
        paint.shader = RadialGradient(
            w * 0.5f,
            h * (0.38f - skyShift),
            w * 0.85f,
            intArrayOf(0xFF181A2C.toInt(), 0xFF0B101D.toInt(), 0xFF050811.toInt()),
            floatArrayOf(0f, 0.62f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        fill(0xFFFFFFFF.toInt(), visibility)
        paint.shader = RadialGradient(
            w * 0.5f + px * 12,
            h * (0.425f - haloShift) - 40 + py * 12,
            w * 0.72f,
            intArrayOf(0x29EACEAC, 0x14BAA2D4, 0),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        for (s in stars) {
            val twinkle = if (reduced) 1f else 0.75f + 0.25f * sin(time * 1.8f + s[4])
            fill(0xFFF6ECD8.toInt(), s[3] * twinkle * visibility)
            canvas.drawCircle(s[0] * w + px * s[5] * 10, s[1] * h + py * s[5] * 10, s[2] * w / 404f, paint)
        }
    }

    /** Slow, cinematic "Lunar Veil Breathing" entrance overlay: lifting twilight vignette, fine golden breath-ring & central lunar star. */
    private fun drawLunarVeilEntrance(
        canvas: Canvas,
        width: Float,
        height: Float,
        cx: Float,
        cy: Float,
        m: Float,
        reveal: Float,
    ) {
        val veilLift = CrystalLunarVeil.veilLift(reveal)
        if (veilLift < 0.99f) {
            val outerOpacity = (1f - veilLift) * 0.78f
            val innerRadius = (width * 0.16f + veilLift * width * 0.82f).coerceAtLeast(1f)
            fill(0xFF050811.toInt(), outerOpacity)
            paint.shader = RadialGradient(
                cx,
                cy,
                innerRadius,
                intArrayOf(0x00050811, 0x8A050811.toInt(), 0xEE050811.toInt()),
                floatArrayOf(0f, 0.58f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, width, height, paint)
            paint.shader = null
        }

        val ringT = CrystalLunarVeil.ringProgress(reveal)
        if (ringT > 0.005f && ringT < 0.995f) {
            val easeOut = 1f - (1f - ringT) * (1f - ringT) * (1f - ringT)
            val radius = (14f + easeOut * 182f) * m
            val ringAlpha = sin(ringT * PI.toFloat()).coerceIn(0f, 1f)
            // Soft outer halo behind the fine golden breath-ring
            stroke(0xFFF6D9A4.toInt(), 0.18f * ringAlpha, (6.5f - ringT * 2.5f) * m)
            canvas.drawCircle(cx, cy, radius, paint)
            // Crisp lunar gold ring
            stroke(0xFFF6D9A4.toInt(), 0.64f * ringAlpha, (1.7f - ringT * 0.75f) * m)
            canvas.drawCircle(cx, cy, radius, paint)
            // Delicate central 4-point star ignition
            val starAlpha = ringAlpha * 0.90f
            drawSprite(canvas, star, cx, cy, (42f + 16f * ringAlpha) * m, starAlpha * 0.78f)
            val rayLen = (15f * ringAlpha) * m
            if (rayLen > 0.5f) {
                stroke(0xFFFFF9EC.toInt(), starAlpha * 0.85f, 1.15f * m)
                canvas.drawLine(cx - rayLen, cy, cx + rayLen, cy, paint)
                canvas.drawLine(cx, cy - rayLen, cx, cy + rayLen, paint)
            }
        }

        val echoT = CrystalLunarVeil.echoRingProgress(reveal)
        if (echoT > 0.005f && echoT < 0.995f) {
            val easeOut = 1f - (1f - echoT) * (1f - echoT) * (1f - echoT)
            val echoRadius = (18f + easeOut * 148f) * m
            val echoAlpha = sin(echoT * PI.toFloat()).coerceIn(0f, 1f) * 0.36f
            stroke(0xFFE6A8C4.toInt(), echoAlpha, (1.15f - echoT * 0.45f) * m)
            canvas.drawCircle(cx, cy, echoRadius, paint)
        }
    }

    private fun drawConstellation(canvas: Canvas, time: Float, m: Float, reduced: Boolean, eInk: Boolean, ink: Int) {
        val e = time - CrystalTimeline.GATHER_MS
        val gate = if (reduced) {
            CrystalTimeline.smooth((time - 250) / 800)
        } else {
            CrystalTimeline.smooth(
                (e - CrystalTimeline.DISSOLVE_MS * 0.45f) /
                    (CrystalTimeline.DISSOLVE_MS * 0.55f + CrystalTimeline.FLIGHT_MS * 0.55f),
            )
        }
        if (gate <= 0) return
        val arrivalWindow = min(850f, CrystalTimeline.FLIGHT_MS * 0.18f)
        for (i in visibility.indices) {
            val arrival = pool.firstBirth[i] * CrystalTimeline.DISSOLVE_MS + CrystalTimeline.FLIGHT_MS * 0.9f
            val sinceArrival = e - arrival
            visibility[i] =
                if (reduced) {
                    gate
                } else {
                    gate *
                        CrystalTimeline.smooth((sinceArrival + arrivalWindow * 0.5f) / arrivalWindow)
                }
            pulses[i] =
                if (!reduced && sinceArrival > -120 &&
                    sinceArrival < 750
                ) {
                    sin(((sinceArrival + 120) / 870).coerceIn(0f, 1f) * PI.toFloat())
                } else {
                    0f
                }
        }
        for (group in CrystalSceneData.groups) {
            val ids = group.indices
            val edges = ids.size - 1 + if (group.closed) 1 else 0
            for (k in 0 until edges) {
                val ia = ids[k]
                val ib = ids[(k + 1) % ids.size]
                val visible = min(visibility[ia], visibility[ib])
                if (visible <= 0.01f) continue
                val a = projected[ia]
                val b = projected[ib]
                val zFactor = 0.65f + 0.35f * (a.z + b.z + 1) * 0.5f
                val pulse = max(pulses[ia], pulses[ib])
                val opacity = if (eInk) {
                    visible
                } else {
                    visible * (
                        if (group.kind ==
                            "outline"
                        ) {
                            0.24f
                        } else {
                            0.34f
                        }
                        ) * zFactor +
                        pulse * 0.35f
                }
                stroke(if (eInk) ink else 0xFFECD6B4.toInt(), opacity, (1.1f + pulse * 1.4f) * m)
                canvas.drawLine(a.x, a.y, b.x, b.y, paint)
            }
        }
        for (i in visibility.indices) {
            val q = visibility[i]
            if (q <= 0.01f) continue
            val p = projected[i]
            val role = CrystalSceneData.roles[i]
            val pulse = pulses[i]
            val glow =
                (
                    if (role == "nose") {
                        24f
                    } else if (role == "eye") {
                        18f
                    } else {
                        13f
                    }
                    ) * m * p.perspective * (1 + pulse * 0.8f)
            if (!eInk) drawSprite(canvas, star, p.x, p.y, glow, q * (0.52f + pulse * 0.45f))
            if (pulse > 0.04f && !eInk) {
                stroke(0xFFFFF5DC.toInt(), pulse * 0.65f * q, 1.1f)
                canvas.drawCircle(p.x, p.y, (4 + (1 - pulse) * 14) * m, paint)
            }
            val radius = (
                if (role == "nose") {
                    2.1f
                } else if (role == "eye") {
                    1.55f
                } else {
                    1.15f
                }
                ) * m * p.perspective
            fill(if (eInk) ink else 0xFFFFF9EC.toInt(), q * 0.95f)
            canvas.drawCircle(p.x, p.y, radius, paint)
            if (!eInk && (i == 63 || i == 17 || i == 34 || i == 53 || i == 58)) {
                val len = (if (i == 63) 8.5f else 5.5f) * m * (1 + pulse * 0.6f)
                stroke(0xFFFFF8E8.toInt(), q * 0.55f, 0.85f)
                canvas.drawLine(p.x - len, p.y, p.x + len, p.y, paint)
                canvas.drawLine(p.x, p.y - len, p.x, p.y + len, paint)
            }
        }
    }

    private fun resetMask() {
        maskCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        maskCanvas.drawBitmap(pool.bitmap, 0f, 0f, null)
        released = 0
        previousElapsed = -1f
    }

    private fun advanceMask(elapsed: Float) {
        if (elapsed < previousElapsed) resetMask()
        while (released < pool.cells.size && pool.cells[released].birth * CrystalTimeline.DISSOLVE_MS <= elapsed) {
            maskCanvas.drawRect(pool.cells[released].source, clearPaint)
            released++
        }
        previousElapsed = elapsed
    }

    private fun fill(color: Int, opacity: Float = 1f) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.alpha = alpha(opacity)
    }
    private fun stroke(color: Int, opacity: Float, width: Float) {
        fill(color, opacity)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.strokeCap = Paint.Cap.ROUND
    }
    private fun drawSprite(canvas: Canvas, sprite: Bitmap, x: Float, y: Float, size: Float, opacity: Float) {
        destination.set(x - size / 2, y - size / 2, x + size / 2, y + size / 2)
        glowPaint.alpha = alpha(opacity)
        canvas.drawBitmap(sprite, null, destination, glowPaint)
    }

    companion object {
        private val TAU = (2 * PI).toFloat()
        private fun alpha(value: Float) = (value.coerceIn(0f, 1f) * 255).toInt()
        private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t
        private fun positiveMod(value: Float, range: Float) = ((value % range) + range) % range
        private fun radialSprite(size: Int, colors: IntArray, stops: FloatArray): Bitmap {
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(size / 2f, size / 2f, size / 2f, colors, stops, Shader.TileMode.CLAMP)
            }
            Canvas(bitmap).drawCircle(size / 2f, size / 2f, size / 2f, paint)
            return bitmap
        }
    }
}

private class CrystalVisualRandom(private var seed: Int) {
    fun next(): Float {
        seed += 0x6D2B79F5
        var t = (seed xor (seed ushr 15)) * (1 or seed)
        t = t + ((t xor (t ushr 7)) * (61 or t)) xor t
        return (((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0).toFloat()
    }
}
