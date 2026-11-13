package eu.kanade.tachiyomi.ui.reels.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

internal const val REELS_SEEK_NOTICE_LANDSCAPE_INSET_DP = 88f
internal const val REELS_SEEK_NOTICE_PORTRAIT_INSET_DP = 14f

/**
 * Horizontal inset from the screen edge (inside `WindowInsets.safeDrawing` when landscape)
 * keeping the landscape seek caption clear of both the ~70dp right ActionRail and the 216dp
 * centered transport cluster.
 */
internal fun reelsSeekNoticeHorizontalPadding(isLandscape: Boolean): Dp =
    if (isLandscape) REELS_SEEK_NOTICE_LANDSCAPE_INSET_DP.dp else REELS_SEEK_NOTICE_PORTRAIT_INSET_DP.dp

internal class ReelsSeekNotice {
    var seconds by mutableStateOf<Int?>(null)
        private set
    var event by mutableIntStateOf(0)
        private set

    /**
     * Edge the caption belongs to. It survives [clear] on purpose: reading it while the exit
     * animation is still running must not flip the fading text to the other side.
     */
    var forward by mutableStateOf(true)
        private set

    /**
     * Adds [delta] to the current series. Repeated taps of the same direction accumulate
     * (10, 20, 30); a direction change or an expired series starts over. A zero delta is a seek
     * clamped by the video bounds: the honest total stays on screen instead of growing to "+0".
     */
    fun show(delta: Int) {
        val current = seconds
        if (delta != 0) forward = delta > 0
        seconds = when {
            delta == 0 -> current
            current != null && (current > 0) == (delta > 0) -> current + delta
            else -> delta
        }
        if (seconds != null) event++
    }

    fun clear() {
        seconds = null
    }
}

@Composable
internal fun rememberReelsSeekNotice(itemId: String, isActive: Boolean, inPictureInPicture: Boolean): ReelsSeekNotice {
    val notice = remember(itemId) { ReelsSeekNotice() }
    // Equal text can return before a frame; expiry belongs to the seek event, not its caption.
    val event = notice.event
    LaunchedEffect(notice, event, isActive, inPictureInPicture) {
        if (!isActive || inPictureInPicture) {
            notice.clear()
        } else if (notice.seconds != null) {
            delay(1000)
            if (notice.event == event) notice.clear()
        }
    }
    DisposableEffect(notice) {
        onDispose { notice.clear() }
    }
    return notice
}
