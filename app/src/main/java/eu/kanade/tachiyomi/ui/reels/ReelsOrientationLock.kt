package eu.kanade.tachiyomi.ui.reels

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.runtime.mutableStateOf

/**
 * Shared orientation policy for the Reels chain.
 *
 * The feed is portrait-first: `landscape` is an explicit user choice that is shared by
 * every nested feed. The holder count keeps the request alive while two feeds overlap
 * during a Voyager transition; the intent is kept when the feed stays in the stack
 * (for example a screen pushed on top), and cleared when the chain is really left.
 */
object ReelsOrientationLock {

    private var holders = 0
    private var previousOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private val _landscape = mutableStateOf(false)

    val landscape: Boolean
        get() = _landscape.value

    fun acquire(activity: Activity) {
        if (holders == 0) {
            previousOrientation = activity.requestedOrientation
        }
        holders++
        apply(activity)
    }

    fun setLandscape(activity: Activity, landscape: Boolean) {
        _landscape.value = landscape
        if (holders > 0) apply(activity)
    }

    fun release(activity: Activity, keepIntent: Boolean) {
        holders = (holders - 1).coerceAtLeast(0)
        if (holders > 0) return
        if (activity.isChangingConfigurations) return
        if (!keepIntent || activity.isFinishing) {
            _landscape.value = false
        }
        activity.requestedOrientation = previousOrientation
    }

    private fun apply(activity: Activity) {
        activity.requestedOrientation = if (_landscape.value) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    }
}
