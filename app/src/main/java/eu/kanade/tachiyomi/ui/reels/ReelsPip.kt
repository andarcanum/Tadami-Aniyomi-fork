package eu.kanade.tachiyomi.ui.reels

import android.app.Activity
import android.app.PictureInPictureParams
import android.graphics.Rect
import android.util.Rational
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Picture-in-picture entry point for the reels feed (B3.5, declutter redesign).
 *
 * The feed registers [handler] while it is the composed screen; [eu.kanade.tachiyomi.ui.main.MainActivity]
 * invokes it from `onUserLeaveHint` — the Home gesture during playback enters PiP (the
 * YouTube/PlayerActivity pattern), so no dedicated top-bar button is needed. The handler
 * itself applies the gates (feature toggle, real playback, not the offline playlist? — see
 * the registration site) and returns whether PiP was entered.
 */
object ReelsPip {

    @Volatile
    var handler: (() -> Boolean)? = null

    /**
     * U02: the PiP window is owned by the system, so the feed must not rotate its subtree
     * as if it were still filling the activity. MainActivity mirrors the real mode here.
     */
    var isInPictureInPictureMode by mutableStateOf(false)
        private set

    fun updatePictureInPictureMode(active: Boolean) {
        isInPictureInPictureMode = active
    }

    /**
     * The window shape follows the measured video frame (audit C03): aspect comes from the
     * real dimensions, and the source rect is the displayed content area (letterboxed in fit
     * mode, the whole window in crop mode) instead of the full portrait window. Without a
     * measured frame the old orientation fallback still applies; when even that is unknown
     * the system default stands. A software-rotated subtree (landscape fullscreen) covers
     * the window, so its content rect is the window itself.
     */
    fun buildParams(
        activity: Activity,
        videoWidth: Int,
        videoHeight: Int,
        cropMode: Boolean,
        landscapeFallback: Boolean?,
        rotated: Boolean,
    ): PictureInPictureParams {
        val decor = activity.window?.decorView
        val windowWidth = decor?.width?.takeIf { it > 0 }
            ?: activity.resources.displayMetrics.widthPixels
        val windowHeight = decor?.height?.takeIf { it > 0 }
            ?: activity.resources.displayMetrics.heightPixels
        val builder = PictureInPictureParams.Builder()
        val geometry = reelsPipVideoGeometry(
            windowWidth = windowWidth,
            windowHeight = windowHeight,
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            crop = cropMode,
        )
        if (geometry != null) {
            builder.setAspectRatio(Rational(geometry.aspectWidth, geometry.aspectHeight))
            val sourceRect = if (rotated) {
                Rect(0, 0, windowWidth, windowHeight)
            } else {
                Rect(geometry.left, geometry.top, geometry.right, geometry.bottom)
            }
            builder.setSourceRectHint(sourceRect)
        } else {
            reelsPipAspectRatio(landscapeFallback)?.let { (width, height) ->
                builder.setAspectRatio(Rational(width, height))
            }
        }
        return builder.build()
    }
}
