package eu.kanade.presentation.easteregg.crystal

/** Timing only: a print stays at its birth position until it dissolves. */
internal object CrystalPawMotion {
    const val PAW_COUNT = 5
    const val CYCLE_MS = 16_000f
    private const val BIRTH_INTERVAL_MS = 860f
    private const val LIFE_MS = 2300f
    private const val ENTER_MS = 300f
    private const val FADE_MS = 1250f
    private const val PEAK_ALPHA = 0.18f

    fun localTimeMs(timeMs: Float): Float =
        if (timeMs.isFinite()) timeMs.coerceAtLeast(0f) % CYCLE_MS else 0f

    fun routeVariant(timeMs: Float): Int =
        if (timeMs.isFinite()) ((timeMs.coerceAtLeast(0f) / CYCLE_MS).toLong() % 3).toInt() else 0

    fun opacityAt(timeMs: Float, index: Int): Float {
        if (index !in 0 until PAW_COUNT) return 0f
        val age = localTimeMs(timeMs) - index * BIRTH_INTERVAL_MS
        if (age <= 0f || age >= LIFE_MS) return 0f
        val enter = smooth(age / ENTER_MS)
        // Quintic symmetry: equivalent to 1 - smooth((age - 1050) / 1250),
        // without subtracting nearly equal floats at the end of the fade.
        val fade = smooth((LIFE_MS - age) / FADE_MS)
        return (enter * fade * PEAK_ALPHA).coerceIn(0f, PEAK_ALPHA)
    }

    private fun smooth(value: Float): Float {
        val v = value.coerceIn(0f, 1f)
        return v * v * v * (v * (v * 6f - 15f) + 10f)
    }
}
