package eu.kanade.tachiyomi.ui.reels.player

/**
 * Monotonic seek command for [ReelsPlayerView].
 *
 * A raw `Float?` state drops repeated seeks to the same fraction (e.g. seeking to 0f twice) and
 * loses rapid double-tap ±10s bursts when paired with a `delay(50)` reset hack. Assigning a
 * monotonically increasing [id] guarantees every seek fires exactly once, and [liveScrub] switches
 * ExoPlayer to fast keyframe seeking (`SeekParameters.CLOSEST_SYNC`) while the finger is dragging.
 */
data class ReelsSeekRequest(
    val id: Long,
    val fraction: Float,
    val liveScrub: Boolean = false,
)
