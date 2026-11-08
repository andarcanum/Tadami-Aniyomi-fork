package eu.kanade.presentation.easteregg.crystal

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

internal enum class CrystalLivingToyKind { YARN, BALL }

internal enum class CrystalLivingPart {
    HEAD,
    BODY,
    CHEST,
    TAIL,
    EAR_LEFT,
    EAR_RIGHT,
    WHISKERS_LEFT,
    WHISKERS_RIGHT,
    MUZZLE,
    PAW_LEFT,
    PAW_RIGHT,
    CUSHION,
    CRYSTAL,
    FEATHER,
    POM,
    IRIS,
}

/** Origins belong to the artwork; translations are in that part's own viewBox coordinates. */
internal data class CrystalLivingTransform(
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val rotationDegrees: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
)

internal data class CrystalLivingToyState(
    val kind: CrystalLivingToyKind,
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val angle: Float,
    val squash: Float,
    val radius: Float,
)

internal data class CrystalLivingFrame(
    val elapsedSeconds: Float,
    val parts: Map<CrystalLivingPart, CrystalLivingTransform>,
    val toys: List<CrystalLivingToyState>,
    val gazeX: Float,
    val gazeY: Float,
    val eyeClosure: Float,
    val pupilRadius: Float,
    val happiness: Float,
    val playing: Boolean,
    val contactCount: Int,
    val dragging: Boolean,
)

/**
 * Pure fixed-v4 spring/toy engine. The host supplies active-time deltas, never wall-clock catch-up.
 * pause() releases captured drag only; free momentum/guide/rig resume on the next active advance().
 * All physics and rig channels share <=1/120s substeps, with the prototype's 50ms frame cap.
 */
internal class CrystalLivingEngine(private val seed: Int = 0) {
    private val bodies = listOf(
        Body(CrystalLivingToyKind.YARN, 542.0, 336.0, 25.0),
        Body(CrystalLivingToyKind.BALL, 251.0, 353.0, 17.0),
    )
    private val gazeX = Spring(95.0, 15.0)
    private val gazeY = Spring(95.0, 15.0)
    private val head = Spring(72.0, 11.0)
    private val earLeft = Spring(190.0, 12.0)
    private val earRight = Spring(190.0, 12.0)
    private val whisker = Spring(150.0, 11.0)
    private val pawLeft = Spring(140.0, 14.0)
    private val pawRight = Spring(140.0, 14.0)
    private val tail = Spring(48.0, 8.0)
    private val crystal = Spring(32.0, 2.4)
    private val feather = Spring(110.0, 7.5)
    private val pom = Spring(95.0, 6.2)
    private val springs = listOf(
        gazeX, gazeY, head, earLeft, earRight, whisker, pawLeft, pawRight, tail, crystal, feather, pom,
    )
    private var time = 0.0
    private var reduced = false
    private var randomState = seed
    private var guide: Guide? = null
    private var drag: Drag? = null
    private var action: Action? = null
    private var contactCount = 0
    private var cooldownUntil = 0.0
    private var happyUntil = 0.0
    private var happiness = 0.0
    private var pointerX = 0.0
    private var pointerY = 0.0
    private var pointerUntil = 0.0
    private var autoX = 0.0
    private var autoY = 0.0
    private var nextGaze = 1.8
    private var nextEar = 3.1
    private var nextBlink = 2.6
    private var blinkStart = -1.0
    private var anticipation = 0.0
    private var closure = 0.0
    private var pupil = 5.2

    fun advance(deltaSeconds: Float): CrystalLivingFrame {
        if (reduced || !deltaSeconds.isFinite() || deltaSeconds <= 0f) return snapshot()
        val bounded = deltaSeconds.coerceAtMost(0.05f)
        // Multiplication in Float preserves exact 1/30, 1/60, 1/120 tick counts supplied by the host.
        val steps = ceil((bounded * 120f).toDouble()).toInt().coerceAtLeast(1)
        val dt = minOf(bounded.toDouble() / steps, 1.0 / 120.0)
        repeat(steps) {
            time += dt
            advanceBodies(dt)
            advanceRig(dt)
        }
        return snapshot()
    }

    fun snapshot(): CrystalLivingFrame = if (reduced) restingSnapshot() else frame(resting = false)

    /** Nonmutating static view: no closed eyes/deformation, but toys stay where the user left them. */
    fun restingSnapshot(): CrystalLivingFrame = frame(resting = true)

    fun pause() {
        endDrag(cancelled = true)
    }

    fun setReduced(value: Boolean) {
        if (reduced == value) return
        reduced = value
        if (value) {
            pause()
            guide = null
            restRig()
            bodies.forEach {
                it.vx = 0.0
                it.vy = 0.0
                it.squash = 0.0
                it.squashV = 0.0
            }
        }
    }

    /** Explicit positional reset, unlike lifecycle pause/reduced motion. */
    fun reset() {
        time = 0.0
        randomState = seed
        guide = null
        drag = null
        contactCount = 0
        cooldownUntil = 0.0
        restRig()
        bodies.forEach {
            it.x = it.baseX
            it.y = it.baseY
            it.vx = 0.0
            it.vy = 0.0
            it.angle = 0.0
            it.squash = 0.0
            it.squashV = 0.0
        }
    }

    fun play(): Boolean = beginPlay(CrystalLivingToyKind.YARN, offer = true)

    fun pet(): Boolean {
        if (reduced || action != null) return false
        happyUntil = time + 2.1
        head.v -= 14.0
        earLeft.v += 28.0
        earRight.v -= 28.0
        whisker.v += 22.0
        return true
    }

    fun activateProp(part: CrystalLivingPart): Boolean {
        if (reduced) return false
        when (part) {
            CrystalLivingPart.CRYSTAL -> {
                pointerUntil = 0.0
                crystal.v += if (random() > 0.5) 42.0 else -42.0
                autoX = -0.32
                autoY = -0.68
                nextGaze = time + 2.8
                earLeft.v += 35.0
                earRight.v += 25.0
            }
            CrystalLivingPart.FEATHER -> {
                feather.v -= 68.0
                autoX = -0.65
                autoY = 0.42
                nextGaze = time + 2.4
                earLeft.v += 40.0
            }
            CrystalLivingPart.POM -> {
                pom.v += 75.0
                autoX = 0.72
                autoY = 0.25
                nextGaze = time + 2.4
                earRight.v -= 40.0
            }
            else -> return false
        }
        return true
    }

    /** Normalized gaze in [-1,1], retained for 2600ms of active time. */
    fun lookAt(nx: Float, ny: Float) {
        if (reduced || !nx.isFinite() || !ny.isFinite()) return
        pointerX = nx.toDouble().coerceIn(-1.0, 1.0)
        pointerY = ny.toDouble().coerceIn(-1.0, 1.0)
        pointerUntil = time + 2.6
    }

    fun hitToy(x: Float, y: Float, minRadius: Float = 0f): CrystalLivingToyKind? {
        if (!x.isFinite() || !y.isFinite() || !minRadius.isFinite()) return null
        // Reverse artwork order: the later-painted ball wins if expanded touch regions overlap.
        return bodies.asReversed().firstOrNull {
            hypot(x.toDouble() - it.x, y.toDouble() - it.y) <= maxOf(it.radius, minRadius.toDouble())
        }?.kind
    }

    fun beginDrag(kind: CrystalLivingToyKind, x: Float, y: Float): Boolean {
        if (reduced || drag != null || !x.isFinite() || !y.isFinite()) return false
        val body = bodies.first { it.kind == kind }
        guide = null
        drag = Drag(body, x.toDouble(), y.toDouble())
        body.vx = 0.0
        body.vy = 0.0
        body.squash = 0.18
        return true
    }

    fun dragTo(x: Float, y: Float, deltaSeconds: Float) {
        val captured = drag ?: return
        if (reduced || !x.isFinite() || !y.isFinite()) return
        val dt = if (deltaSeconds.isFinite()) maxOf(0.016, deltaSeconds.toDouble()) else 0.016
        val dx = x.toDouble() - captured.x
        val dy = y.toDouble() - captured.y
        val body = captured.body
        body.x += dx
        body.y += dy
        body.angle += dx / body.radius * 90.0 / PI
        body.vx = (dx / dt).coerceIn(-280.0, 280.0)
        body.vy = (dy / dt).coerceIn(-190.0, 190.0)
        captured.x = x.toDouble()
        captured.y = y.toDouble()
        clamp(body)
    }

    fun endDrag(cancelled: Boolean = false) {
        val body = drag?.body ?: return
        if (cancelled) {
            body.vx = 0.0
            body.vy = 0.0
            body.squash = 0.0
            body.squashV = 0.0
        }
        drag = null
    }

    private fun beginPlay(kind: CrystalLivingToyKind, offer: Boolean): Boolean {
        if (reduced || action != null || time < happyUntil || happiness > 0.1) return false
        if (offer) {
            if (drag != null) return false
            val body = bodies.first { it.kind == kind }
            body.vx = 0.0
            body.vy = 0.0
            guide = Guide(body, body.x, body.y)
        }
        action = Action(kind, time, if (offer) 0.62 else 0.0)
        cooldownUntil = time + 4.6
        return true
    }

    private fun advanceBodies(dt: Double) {
        for (body in bodies) {
            body.squashV += (-180.0 * body.squash - 18.0 * body.squashV) * dt
            body.squash += body.squashV * dt
            if (abs(body.squash) < 0.002 && abs(body.squashV) < 0.01) {
                body.squash = 0.0
                body.squashV = 0.0
            }
            if (drag?.body === body) continue
            val offered = guide
            if (offered != null && offered.body === body) {
                offered.elapsed += dt
                val progress = minOf(1.0, offered.elapsed / 0.62)
                val ease = 1.0 - (1.0 - progress).pow(3)
                val previous = body.x
                body.x = offered.x + (447.0 - offered.x) * ease
                body.y = offered.y + (339.0 - offered.y) * ease
                body.vx = (body.x - previous) / maxOf(0.008, dt)
                body.angle += (body.x - previous) / body.radius * 180.0 / PI
                if (progress == 1.0) {
                    body.vx = 0.0
                    body.vy = 0.0
                    guide = null
                }
                continue
            }
            applyBedRepulsion(body, dt)
            val dx = body.vx * dt
            body.x += dx
            body.y += body.vy * dt
            body.angle += dx / body.radius * 180.0 / PI
            val damping = exp(-2.1 * dt)
            body.vx *= damping
            body.vy *= damping
            clamp(body)
        }
        val a = bodies[0]
        val b = bodies[1]
        val dx = b.x - a.x
        val dy = b.y - a.y
        val distance = hypot(dx, dy)
        val radius = a.radius + b.radius
        if (distance >= radius) return
        val nx = if (distance > 0.001) dx / distance else 1.0
        val ny = if (distance > 0.001) dy / distance else 0.0
        val move = (radius - distance) / 2.0
        a.x -= nx * move
        a.y -= ny * move
        b.x += nx * move
        b.y += ny * move
        val relative = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny
        if (relative < 0.0) {
            val impulse = -relative * 0.88
            a.vx -= nx * impulse
            a.vy -= ny * impulse
            b.vx += nx * impulse
            b.vy += ny * impulse
            a.squash = minOf(0.85, impulse / 110.0)
            b.squash = minOf(0.85, impulse / 110.0)
        }
        clamp(a)
        clamp(b)
    }

    private fun applyBedRepulsion(body: Body, dt: Double) {
        if (dt <= 0.0 || guide?.body === body || drag?.body === body) return
        val ex = (body.x - 402.0) / 122.0
        val ey = (body.y - 338.0) / 36.0
        val distance = hypot(ex, ey)
        if (distance >= 1.0 || distance <= 0.01) return
        // Acceleration, not an impulse: scaled by dt exactly once per substep.
        val acceleration = (1.0 - distance) * 300.0
        body.vx += ex / distance * acceleration * dt
        body.vy += ey / distance * acceleration * dt
    }

    private fun clamp(body: Body) {
        val minX = 198.0 + body.radius
        val maxX = 618.0 - body.radius
        val minY = 304.0 + body.radius
        val maxY = 390.0 - body.radius
        if (body.x < minX) {
            body.x = minX
            body.vx = abs(body.vx) * 0.75
            body.squash = minOf(1.0, abs(body.vx) / 140.0)
        }
        if (body.x > maxX) {
            body.x = maxX
            body.vx = -abs(body.vx) * 0.75
            body.squash = minOf(1.0, abs(body.vx) / 140.0)
        }
        if (body.y < minY) {
            body.y = minY
            body.vy = abs(body.vy) * 0.75
            body.squash = minOf(1.0, abs(body.vy) / 120.0)
        }
        if (body.y > maxY) {
            body.y = maxY
            body.vy = -abs(body.vy) * 0.75
            body.squash = minOf(1.0, abs(body.vy) / 120.0)
        }
    }

    private fun advanceRig(dt: Double) {
        if (action == null && time > cooldownUntil && happiness < 0.1) {
            val nearby = bodies.firstOrNull {
                (drag?.body === it || hypot(it.vx, it.vy) > 12.0) &&
                    hypot(it.x - if (it.kind == CrystalLivingToyKind.YARN) 416.0 else 387.0, it.y - 336.0) < 58.0
            }
            if (nearby != null) beginPlay(nearby.kind, offer = false)
        }
        val currentAction = action
        val toy = currentAction?.let { active -> bodies.first { it.kind == active.kind } }
        val elapsed = currentAction?.let { time - it.start - it.approach } ?: 0.0
        val recovery = 1.0 - smooth(0.7, 1.24, elapsed)
        val reach = if (currentAction != null) smooth(0.32, 0.62, elapsed) * recovery else 0.0
        anticipation = if (currentAction != null) smooth(0.0, 0.32, elapsed) * recovery else 0.0

        if (time > nextGaze && currentAction == null && time > pointerUntil) {
            val roll = random()
            if (roll < 0.24) {
                autoX = -0.25
                autoY = -0.52
            } else if (roll < 0.72) {
                val focus = bodies[(random() * bodies.size).toInt()]
                autoX = ((focus.x - 403.0) / 135.0).coerceIn(-1.0, 1.0) * 0.7
                autoY = 0.34
            } else {
                autoX = (random() - 0.5) * 0.18
                autoY = (random() - 0.5) * 0.14
            }
            nextGaze = time + 2.8 + random() * 2.6
        }
        if (time > nextEar && happiness < 0.2) {
            if (random() > 0.45) {
                earLeft.v += 26.0 + random() * 18.0
            } else {
                earRight.v -= 26.0 + random() * 18.0
            }
            nextEar = time + 3.2 + random() * 3.4
        }
        val moving = bodies.firstOrNull { drag?.body === it || hypot(it.vx, it.vy) > 8.0 }
        var targetX = if (time < pointerUntil) pointerX else autoX
        var targetY = if (time < pointerUntil) pointerY else autoY
        if (toy != null) {
            targetX = ((toy.x - 403.0) / 115.0).coerceIn(-1.0, 1.0)
            targetY = ((toy.y - 166.0) / 210.0).coerceIn(-1.0, 1.0)
        } else if (moving != null) {
            targetX = ((moving.x - 403.0) / 120.0).coerceIn(-1.0, 1.0)
            targetY = ((moving.y - 175.0) / 210.0).coerceIn(-1.0, 1.0)
        }
        gazeX.step(targetX, dt)
        gazeY.step(targetY, dt)
        val gx = gazeX.x
        happiness += ((if (time < happyUntil) 1.0 else 0.0) - happiness) * (1.0 - exp(-7.5 * dt))
        val happy = happiness
        val breathe = breathe()
        head.step(gx * 3.4 - happy * 3.5 + sin(time * 1.3) * happy * 0.8 - anticipation * 1.6, dt)
        earLeft.step(-gx * 2.2 - happy * 4.5 + anticipation * 3.2, dt)
        earRight.step(-gx * 2.2 + happy * 4.5 - anticipation * 3.2, dt)
        whisker.step(happy * 4.2 + anticipation * 3.5 + breathe * 0.9, dt)
        tail.step(sin(time * 1.15) * (2.6 + anticipation * 3.8 + happy * 1.5) + cos(time * 0.575) * 0.9, dt)
        pupil = 5.15 + anticipation * 1.15 + (if (moving != null) 0.55 else 0.0) - happy * 0.4
        if (time > nextBlink && happy < 0.1) {
            blinkStart = time
            nextBlink = time + 3.6 + random() * 2.4
        }
        val blinkAge = time - blinkStart
        closure = maxOf(smooth(0.0, 0.075, blinkAge) * (1.0 - smooth(0.105, 0.25, blinkAge)), happy * 0.96)
        pawLeft.step(
            if (currentAction?.kind ==
                CrystalLivingToyKind.BALL
            ) {
                17.5 * reach - 3.2 * anticipation
            } else {
                0.0
            },
            dt,
        )
        pawRight.step(
            if (currentAction?.kind ==
                CrystalLivingToyKind.YARN
            ) {
                -17.5 * reach + 3.2 * anticipation
            } else {
                0.0
            },
            dt,
        )
        crystal.step(sin(time * 1.3) * 3.2, dt)
        feather.step(sin(time * 1.8) * 1.1, dt)
        pom.step(sin(time * 2.1) * 1.8, dt)

        if (currentAction != null && toy != null && elapsed >= 0.62 && elapsed <= 1.24 && !currentAction.tapped) {
            val angle = (if (toy.kind == CrystalLivingToyKind.YARN) pawRight.x else pawLeft.x) * PI / 180.0
            val shoulderX = if (toy.kind == CrystalLivingToyKind.YARN) 184.0 else 150.0
            val tipX = 262.0 + (shoulderX - 128.0 * sin(angle)) * 300.0 / 360.0
            val tipY = 74.0 + (186.0 + 128.0 * cos(angle)) * 317.0 / 380.0
            if (hypot(toy.x - tipX, toy.y - tipY) < toy.radius + 16.0 && drag?.body !== toy) {
                // A rejected contact never marks tapped: retry while the physical window is open.
                guide = null
                toy.vx = if (toy.kind == CrystalLivingToyKind.YARN) 134.0 else -124.0
                toy.vy = 6.0
                toy.squash = minOf(0.75, hypot(toy.vx, toy.vy) / 190.0)
                currentAction.tapped = true
                contactCount++
                earLeft.v += 22.0
                earRight.v -= 22.0
            }
        }
        if (currentAction != null && elapsed > 2.45) action = null
    }

    private fun frame(resting: Boolean): CrystalLivingFrame {
        val gx = if (resting) 0.0 else gazeX.x
        val gy = if (resting) 0.0 else gazeY.x
        val happy = if (resting) 0.0 else happiness
        val breathe = if (resting) 0.0 else breathe()
        val purr = if (happy > 0.08) sin(time * 42.0) * 0.35 * happy else 0.0
        val anticipate = if (resting) 0.0 else anticipation
        val parts = linkedMapOf(
            CrystalLivingPart.HEAD to transform(
                gx * 2.6,
                gy * 2.7 - breathe * 0.65 + happy * 1.2 + anticipate * 1.4 + purr,
                if (resting) 0.0 else head.x,
                1.06,
                1.06,
            ),
            CrystalLivingPart.BODY to
                transform(gx * 0.45, -breathe * 0.42 + purr, sx = 1.0 + breathe * 0.006, sy = 1.0 + breathe * 0.009),
            CrystalLivingPart.CHEST to
                transform(gx * 0.85, -breathe * 0.58 + purr, sx = 1.0 + breathe * 0.004, sy = 1.0 + breathe * 0.007),
            CrystalLivingPart.TAIL to transform(rotation = if (resting) 0.0 else tail.x),
            CrystalLivingPart.EAR_LEFT to transform(rotation = if (resting) 0.0 else earLeft.x),
            CrystalLivingPart.EAR_RIGHT to transform(rotation = if (resting) 0.0 else earRight.x),
            CrystalLivingPart.WHISKERS_LEFT to transform(rotation = if (resting) 0.0 else -whisker.x),
            CrystalLivingPart.WHISKERS_RIGHT to transform(rotation = if (resting) 0.0 else whisker.x),
            CrystalLivingPart.MUZZLE to transform(gx * 0.75, gy * 0.55),
            CrystalLivingPart.PAW_LEFT to transform(rotation = if (resting) 0.0 else pawLeft.x),
            CrystalLivingPart.PAW_RIGHT to transform(rotation = if (resting) 0.0 else pawRight.x),
            CrystalLivingPart.CUSHION to
                transform(y = breathe * 0.28, sx = 1.0 + breathe * 0.004, sy = 1.0 - breathe * 0.005),
            CrystalLivingPart.CRYSTAL to transform(rotation = if (resting) 0.0 else crystal.x),
            CrystalLivingPart.FEATHER to transform(rotation = if (resting) 0.0 else feather.x),
            CrystalLivingPart.POM to transform(rotation = if (resting) 0.0 else pom.x),
            CrystalLivingPart.IRIS to transform(gx * 2.5, gy * 2.0),
        )
        return CrystalLivingFrame(
            elapsedSeconds = time.toFloat(),
            parts = parts,
            toys = bodies.map { body ->
                CrystalLivingToyState(
                    body.kind,
                    body.x.toFloat(),
                    body.y.toFloat(),
                    if (resting) 0f else body.vx.toFloat(),
                    if (resting) 0f else body.vy.toFloat(),
                    body.angle.toFloat(),
                    if (resting) 0f else body.squash.toFloat(),
                    body.radius.toFloat(),
                )
            },
            gazeX = gx.toFloat(), gazeY = gy.toFloat(),
            eyeClosure = if (resting) 0f else closure.toFloat(),
            pupilRadius = if (resting) 5.2f else pupil.toFloat(),
            happiness = happy.toFloat(), playing = !resting && action != null,
            contactCount = contactCount, dragging = !resting && drag != null,
        )
    }

    private fun restRig() {
        action = null
        happiness = 0.0
        happyUntil = 0.0
        pointerX = 0.0
        pointerY = 0.0
        pointerUntil = 0.0
        autoX = 0.0
        autoY = 0.0
        anticipation = 0.0
        closure = 0.0
        pupil = 5.2
        blinkStart = -1.0
        nextBlink = time + 2.6
        nextGaze = time + 1.8
        nextEar = time + 3.1
        springs.forEach {
            it.x = 0.0
            it.v = 0.0
        }
    }

    private fun breathe(): Double = sin(time * 1.75) * 0.78 + sin(time * 3.5) * 0.22

    private fun random(): Double {
        randomState += 0x6d2b79f5
        var value = (randomState xor (randomState ushr 15)) * (randomState or 1)
        value = value xor (value + (value xor (value ushr 7)) * (value or 61))
        return ((value xor (value ushr 14)).toLong() and 0xffffffffL) / 4294967296.0
    }

    private class Body(val kind: CrystalLivingToyKind, val baseX: Double, val baseY: Double, val radius: Double) {
        var x = baseX
        var y = baseY
        var vx = 0.0
        var vy = 0.0
        var angle = 0.0
        var squash = 0.0
        var squashV = 0.0
    }

    private class Spring(private val stiffness: Double, private val damping: Double) {
        var x = 0.0
        var v = 0.0
        fun step(target: Double, dt: Double) {
            v += (-stiffness * (x - target) - damping * v) * dt
            x += v * dt
        }
    }

    private class Guide(val body: Body, val x: Double, val y: Double, var elapsed: Double = 0.0)
    private class Drag(val body: Body, var x: Double, var y: Double)
    private class Action(
        val kind: CrystalLivingToyKind,
        val start: Double,
        val approach: Double,
        var tapped: Boolean = false,
    )
}

private fun smooth(a: Double, b: Double, value: Double): Double {
    val x = ((value - a) / (b - a)).coerceIn(0.0, 1.0)
    return x * x * (3.0 - 2.0 * x)
}

private fun transform(
    x: Double = 0.0,
    y: Double = 0.0,
    rotation: Double = 0.0,
    sx: Double = 1.0,
    sy: Double = 1.0,
): CrystalLivingTransform = CrystalLivingTransform(
    x.toFloat(),
    y.toFloat(),
    rotation.toFloat(),
    sx.toFloat(),
    sy.toFloat(),
)
