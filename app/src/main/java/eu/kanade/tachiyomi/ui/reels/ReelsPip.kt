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
     * The window shape follows the active reel: a landscape video in a portrait PiP window
     * is either cropped or rotated. The source rect stays the full screen hint.
     */
    fun buildParams(activity: Activity, isLandscapeVideo: Boolean?): PictureInPictureParams {
        val bounds = Rect(
            0,
            0,
            activity.resources.displayMetrics.widthPixels,
            activity.resources.displayMetrics.heightPixels,
        )
        val builder = PictureInPictureParams.Builder()
            .setSourceRectHint(bounds)
        reelsPipAspectRatio(isLandscapeVideo)?.let { (width, height) ->
            builder.setAspectRatio(Rational(width, height))
        }
        return builder.build()
    }
}
