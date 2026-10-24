package eu.kanade.tachiyomi.ui.reels

import androidx.compose.runtime.mutableStateOf

/**
 * Window-level fullscreen ownership for nested reels feeds (audit U07).
 *
 * MainActivity paints a root navigation-bar scrim above the navigator; while a feed is in
 * software fullscreen the window belongs to the video, so that generic scrim must not paint
 * over it. Owner-based, like [ReelsOrientationLock]: Voyager keeps both screens composed
 * during feed→feed navigation and the outgoing screen disposes after the incoming one
 * acquires. Main-thread only (composition/disposal order).
 */
object ReelsFullscreen {

    private val activeOwners = mutableStateOf(emptySet<Any>())

    val isActive: Boolean
        get() = activeOwners.value.isNotEmpty()

    fun set(owner: Any, active: Boolean) {
        activeOwners.value = if (active) activeOwners.value + owner else activeOwners.value - owner
    }

    fun release(owner: Any) {
        activeOwners.value = activeOwners.value - owner
    }
}
