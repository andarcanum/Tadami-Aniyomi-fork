package eu.kanade.presentation.easteregg.crystal

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

internal data class CrystalPoint3(val x: Float, val y: Float, val z: Float)
internal data class CrystalProjectedPoint(val x: Float, val y: Float, val z: Float, val perspective: Float)

/** Pure projection shared by the memorial and jewel previews. Angles are radians. */
internal object CrystalSceneGeometry {
    const val NODE_COUNT = 82

    fun nodeDepth(index: Int): Float {
        val x = CrystalSceneData.face[index * 2]
        val y = CrystalSceneData.face[index * 2 + 1]
        return when (CrystalSceneData.roles[index]) {
            "nose" -> 0.44f
            "mouth" -> 0.36f - abs(x) * 0.25f
            "eye" -> 0.26f - abs(abs(x) - 0.28f) * 0.3f
            "whisker" -> 0.20f - abs(x) * 0.32f
            else -> if (y < -0.52f) -0.06f + (y + 0.52f) * 0.28f else 0.16f - (x * x + y * y) * 0.38f
        }
    }

    fun rotate(point: CrystalPoint3, rx: Float, ry: Float, rz: Float = 0f): CrystalPoint3 {
        val x = point.x * cos(ry) + point.z * sin(ry)
        val z = -point.x * sin(ry) + point.z * cos(ry)
        val y = point.y * cos(rx) - z * sin(rx)
        val zz = point.y * sin(rx) + z * cos(rx)
        return CrystalPoint3(x * cos(rz) - y * sin(rz), x * sin(rz) + y * cos(rz), zz)
    }

    fun projectFace(
        index: Int,
        width: Float,
        height: Float,
        rx: Float = 0f,
        ry: Float = 0f,
        fittedArtwork: Boolean = false,
    ): CrystalProjectedPoint {
        val point =
            rotate(
                CrystalPoint3(CrystalSceneData.face[index * 2], CrystalSceneData.face[index * 2 + 1], nodeDepth(index)),
                rx,
                ry,
            )
        val scale = min(width * 0.275f, height * if (fittedArtwork) 0.33f else 0.115f)
        val perspective = 2.6f / (2.6f - point.z * 0.55f)
        return CrystalProjectedPoint(
            width / 2 + point.x * scale * perspective,
            height * (if (fittedArtwork) 0.5f else 0.315f) + point.y * scale * perspective,
            point.z,
            perspective,
        )
    }

    /** Same 32-bit mix as the prototype; stable across recomposition and replay. */
    fun hash(index: Int): Float {
        var x = (index + 1) * 0x9E3779B1.toInt()
        x = x xor (x ushr 16)
        x *= 0x85EBCA6B.toInt()
        x = x xor (x ushr 13)
        return ((x.toLong() and 0xFFFFFFFFL).toDouble() / 4294967295.0).toFloat()
    }
}

internal object CrystalTimeline {
    const val GATHER_MS = 1800f
    const val DISSOLVE_MS = 14000f
    const val FLIGHT_MS = 4800f
    const val TOTAL_MS = GATHER_MS + DISSOLVE_MS + FLIGHT_MS
    const val REDUCED_MS = 1600f

    fun smooth(value: Float): Float {
        val v = value.coerceIn(0f, 1f)
        return v * v * v * (v * (v * 6f - 15f) + 10f)
    }

    fun total(reduced: Boolean): Float = if (reduced) REDUCED_MS else TOTAL_MS
    fun epitaphOpacity(timeMs: Float, reduced: Boolean): Float = 1f - smooth(timeMs / if (reduced) 250f else 1100f)
    const val HOLD_MS = 1000f
    const val RELEASE_MS = 240f
    const val FAREWELL = "Farewell"

    fun holdProgress(timeMs: Float): Float = (timeMs / HOLD_MS).coerceIn(0f, 1f)

    private fun farewellStart(reduced: Boolean): Float = if (reduced) 350f else GATHER_MS + DISSOLVE_MS * 0.82f

    fun farewellOpacity(timeMs: Float, reduced: Boolean): Float =
        smooth((timeMs - farewellStart(reduced)) / if (reduced) 300f else 700f)

    fun farewellLetterOpacity(timeMs: Float, reduced: Boolean, index: Int): Float {
        val stagger = if (reduced) 45f else 145f
        val duration = if (reduced) 110f else 420f
        return smooth((timeMs - farewellStart(reduced) - index * stagger) / duration)
    }

    fun farewellNameOpacity(timeMs: Float, reduced: Boolean): Float {
        val completed = farewellStart(reduced) + (FAREWELL.length - 1) * if (reduced) 45f else 145f
        val start = completed + if (reduced) 110f else 420f
        return smooth((timeMs - start) / if (reduced) 180f else 550f)
    }
}

/** Input epochs survive coalesced up/down events; old pointer cleanup cannot cancel a new contact. */
internal data class CrystalHoldInput(
    val generation: Int = 0,
    val touchToken: Int? = null,
    val keyPressed: Boolean = false,
) {
    val pressed: Boolean get() = touchToken != null || keyPressed

    fun touchDown(): CrystalHoldInput = copy(generation = generation + 1, touchToken = generation + 1)

    fun touchUp(token: Int): CrystalHoldInput =
        if (touchToken == token) copy(generation = generation + 1, touchToken = null) else this

    fun keyDown(): CrystalHoldInput =
        if (keyPressed) this else copy(generation = generation + 1, keyPressed = true)

    fun keyUp(): CrystalHoldInput =
        if (keyPressed) copy(generation = generation + 1, keyPressed = false) else this

    fun cancel(): CrystalHoldInput =
        if (pressed) copy(generation = generation + 1, touchToken = null, keyPressed = false) else this

    fun canCommit(attempt: Int): Boolean = pressed && generation == attempt
}

/** Active foreground time only; a resumed frame never incorporates background time. */
internal class CrystalFrameClock {
    var elapsedMs = 0f
        private set
    private var lastNanos: Long? = null
    private var wasActive = false

    fun advance(nanos: Long, active: Boolean): Float {
        lastNanos?.let { if (active && wasActive) elapsedMs += (nanos - it).coerceAtLeast(0L) / 1_000_000f }
        lastNanos = nanos
        wasActive = active
        return elapsedMs
    }

    fun pause() {
        lastNanos = null
        wasActive = false
    }
    fun seek(milliseconds: Float) {
        elapsedMs = milliseconds.coerceAtLeast(0f)
        pause()
    }
}
