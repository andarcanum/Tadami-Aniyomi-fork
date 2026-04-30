package eu.kanade.tachiyomi.ui.reader.viewer.autoscroll

/**
 * Data class representing the current state of auto-scroll functionality.
 *
 * @property isActive Whether auto-scroll is currently enabled and running.
 * @property speed The scroll speed value (1-100), where higher values indicate faster scrolling.
 * @property isPaused Whether auto-scroll is temporarily paused while remaining active.
 */
data class AutoScrollState(
    val isActive: Boolean = false,
    val speed: Int = 50,
    val isPaused: Boolean = false,
    val cooldownUntilMs: Long = 0L,
)
