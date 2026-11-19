package eu.kanade.tachiyomi.ui.player

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

internal data class DisplayModeInfo(
    val modeId: Int,
    val physicalWidth: Int,
    val physicalHeight: Int,
    val refreshRate: Float,
)

internal object DisplayRefreshRateMatcher {
    private const val MAX_CADENCE_ERROR = 0.025
    private const val TIE_EPSILON = 0.003

    /**
     * Selects the display mode at the current resolution whose refresh rate is the cleanest
     * integer multiple of [videoFps] (e.g., 23.976fps -> 120Hz or 48Hz/72Hz instead of 60Hz)
     * to eliminate 3:2 pulldown judder.
     *
     * Returns null when no better same-resolution mode exists or [videoFps] is invalid.
     */
    fun selectBestModeId(
        videoFps: Double,
        availableModes: List<DisplayModeInfo>,
        currentModeId: Int,
    ): Int? {
        if (videoFps <= 0.0 || videoFps > 120.0 || availableModes.size <= 1) return null

        val currentMode = availableModes.firstOrNull { it.modeId == currentModeId } ?: return null
        val sameResolutionModes = availableModes.filter {
            it.physicalWidth == currentMode.physicalWidth &&
                it.physicalHeight == currentMode.physicalHeight &&
                it.refreshRate >= videoFps - 0.5
        }
        if (sameResolutionModes.size <= 1) return null

        val scored = sameResolutionModes.mapNotNull { mode ->
            val ratio = mode.refreshRate.toDouble() / videoFps
            val nearestMultiple = max(1, ratio.roundToInt())
            val cadenceError = abs(ratio - nearestMultiple) / nearestMultiple.toDouble()
            if (cadenceError <= MAX_CADENCE_ERROR) {
                ScoredMode(mode = mode, cadenceError = cadenceError)
            } else {
                null
            }
        }
        if (scored.isEmpty()) return null

        val bestError = scored.minOf { it.cadenceError }
        val topCandidates = scored.filter { it.cadenceError <= bestError + TIE_EPSILON }

        // Among equally clean multiples (e.g. 48Hz vs 120Hz for 24fps), prefer the highest
        // refresh rate so overlay animations and UI remain smooth.
        val chosen = topCandidates.maxByOrNull { it.mode.refreshRate }?.mode ?: return null
        return chosen.modeId.takeIf { it != currentModeId }
    }

    private data class ScoredMode(
        val mode: DisplayModeInfo,
        val cadenceError: Double,
    )
}
