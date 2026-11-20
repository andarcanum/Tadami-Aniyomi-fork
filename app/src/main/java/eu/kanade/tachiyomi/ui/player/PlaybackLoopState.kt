package eu.kanade.tachiyomi.ui.player

import androidx.compose.runtime.Immutable
import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.aniyomi.AYMR
import kotlin.math.max
import kotlin.math.roundToInt

@Immutable
data class AbLoopState(
    val pointA: Double? = null,
    val pointB: Double? = null,
) {
    val hasPointA: Boolean get() = pointA != null
    val isLooping: Boolean get() = pointA != null && pointB != null && pointB > pointA

    fun advance(currentPositionSec: Double): AbLoopState {
        val clampedPos = max(0.0, currentPositionSec)
        return when {
            pointA == null -> AbLoopState(pointA = clampedPos, pointB = null)
            pointB == null -> {
                if (clampedPos > pointA + 0.5) {
                    AbLoopState(pointA = pointA, pointB = clampedPos)
                } else {
                    AbLoopState(pointA = clampedPos, pointB = null)
                }
            }
            else -> AbLoopState()
        }
    }

    fun clear(): AbLoopState = AbLoopState()
}

internal fun formatBufferingCaption(
    bufferingPercent: Int?,
    cacheSpeedBytesPerSec: Long?,
): String? {
    val pctText = bufferingPercent
        ?.takeIf { it in 1..99 }
        ?.let { "$it%" }
    val speedText = cacheSpeedBytesPerSec
        ?.takeIf { it > 0L }
        ?.let(::formatNetworkRate)
    return when {
        pctText != null && speedText != null -> "$pctText · $speedText"
        pctText != null -> pctText
        speedText != null -> speedText
        else -> null
    }
}

internal fun formatNetworkRate(bytesPerSec: Long): String {
    if (bytesPerSec < 1024L) return "${bytesPerSec} B/s"
    val kb = bytesPerSec / 1024.0
    if (kb < 1024.0) return "${kb.roundToInt()} KB/s"
    val mb = kb / 1024.0
    val roundedTenths = (mb * 10.0).roundToInt() / 10.0
    return "$roundedTenths MB/s"
}

internal fun resolveMpvStatsPageLabelRes(page: Int): StringResource {
    return when (page) {
        0 -> AYMR.strings.player_sheets_tracks_off
        1 -> AYMR.strings.player_stats_page_codecs
        2 -> AYMR.strings.player_stats_page_timings
        3 -> AYMR.strings.player_stats_page_cache
        4 -> AYMR.strings.player_stats_page_keys
        5 -> AYMR.strings.player_stats_page_tracks
        else -> AYMR.strings.player_sheets_tracks_off
    }
}

internal fun formatVideoZoomPercent(mpvVideoZoom: Float): Int {
    // MPV video-zoom uses log2 scale: 0.0 -> 100%, 1.0 -> 200%, -0.5 -> ~71%
    val scale = Math.pow(2.0, mpvVideoZoom.toDouble())
    return (scale * 100.0).roundToInt()
}
