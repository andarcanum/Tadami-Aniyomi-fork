package eu.kanade.tachiyomi.ui.reels

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Software-fullscreen geometry for the reels feed (audit U02).
 *
 * The activity stays portrait-locked, so the fullscreen frame is a subtree rotation rather
 * than a real orientation change. Display.rotation stays pinned to portrait under that lock;
 * the physical landscape side comes from gravity, otherwise one side plays upside down.
 */
internal fun reelsSoftwareRotationDegrees(
    landscapeRotation: Int,
    windowIsLandscape: Boolean,
    inPictureInPicture: Boolean,
): Int {
    // The window is already landscape (split-screen/tablet/foldable) or the system owns the
    // PiP frame: rotating a subtree would make the content sideways or cropped.
    if (windowIsLandscape || inPictureInPicture) return 0
    return if (landscapeRotation == 270) 270 else 90
}

/**
 * Landscape side from the gravity vector, or null when the hold is ambiguous (upright,
 * flat, or near the 45° diagonal). The sensor reports the upward force in device
 * coordinates, so the natural top edge pointing left puts positive gravity on +x.
 */
internal fun reelsGravityRotation(x: Float, y: Float): Int? = when {
    abs(x) <= abs(y) -> null
    x > REELS_LANDSCAPE_GRAVITY_THRESHOLD -> 90
    x < -REELS_LANDSCAPE_GRAVITY_THRESHOLD -> 270
    else -> null
}

private const val REELS_LANDSCAPE_GRAVITY_THRESHOLD = 2f

/** Window geometry, not requestedOrientation, decides whether a subtree rotation is needed. */
internal fun reelsWindowIsLandscape(widthDp: Int, heightDp: Int): Boolean = widthDp > heightDp

/**
 * Re-maps window insets into the rotated content frame. Controls live inside the rotated
 * subtree, so a cutout on the window's top edge lands on a different content edge depending
 * on the rotation. Values are absolute: RTL must not mirror a physical cutout.
 */
internal fun reelsRotatedSafePadding(
    left: Dp,
    top: Dp,
    right: Dp,
    bottom: Dp,
    rotationZ: Int,
): PaddingValues {
    val (contentLeft, contentTop, contentRight, contentBottom) = when (rotationZ) {
        90 -> listOf(top, right, bottom, left)
        180 -> listOf(right, bottom, left, top)
        270 -> listOf(bottom, left, top, right)
        else -> listOf(left, top, right, bottom)
    }
    return object : PaddingValues {
        override fun calculateLeftPadding(layoutDirection: LayoutDirection) = contentLeft
        override fun calculateTopPadding() = contentTop
        override fun calculateRightPadding(layoutDirection: LayoutDirection) = contentRight
        override fun calculateBottomPadding() = contentBottom
    }
}

/** PiP aspect from the active reel; null keeps the system default until the player reports. */
internal fun reelsPipAspectRatio(isLandscapeVideo: Boolean?): Pair<Int, Int>? = when (isLandscapeVideo) {
    true -> 16 to 9
    false -> 9 to 16
    null -> null
}

/**
 * PiP window shape for the measured video frame (audit C03).
 *
 * [left]/[top]/[right]/[bottom] are the displayed content bounds in window coordinates:
 * crop mode fills the window, fit mode letterboxes the frame. The aspect comes from the
 * real frame, not the portrait window, so a landscape reel does not get a 9:16 crop.
 */
internal data class ReelsPipGeometry(
    val aspectWidth: Int,
    val aspectHeight: Int,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

/**
 * Aspect of the measured frame, reduced and clamped to the range the platform accepts for
 * PiP windows (1:2.39..2.39:1); a ratio outside it gives no defined window shape.
 */
internal fun reelsPipVideoAspect(videoWidth: Int, videoHeight: Int): Pair<Int, Int>? {
    if (videoWidth <= 0 || videoHeight <= 0) return null
    if (videoWidth.toLong() * 100 > videoHeight.toLong() * 239) return 239 to 100
    if (videoHeight.toLong() * 100 > videoWidth.toLong() * 239) return 100 to 239
    val divisor = reelsGcd(videoWidth, videoHeight)
    return videoWidth / divisor to videoHeight / divisor
}

/** Null until both the window and the video frame are measured. */
internal fun reelsPipVideoGeometry(
    windowWidth: Int,
    windowHeight: Int,
    videoWidth: Int,
    videoHeight: Int,
    crop: Boolean,
): ReelsPipGeometry? {
    if (windowWidth <= 0 || windowHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) return null
    val (aspectWidth, aspectHeight) = reelsPipVideoAspect(videoWidth, videoHeight) ?: return null
    if (crop) {
        return ReelsPipGeometry(aspectWidth, aspectHeight, 0, 0, windowWidth, windowHeight)
    }
    val videoRatio = videoWidth.toDouble() / videoHeight.toDouble()
    val windowRatio = windowWidth.toDouble() / windowHeight.toDouble()
    return if (videoRatio > windowRatio) {
        val contentHeight = (windowWidth / videoRatio).roundToInt().coerceIn(1, windowHeight)
        val top = (windowHeight - contentHeight) / 2
        ReelsPipGeometry(aspectWidth, aspectHeight, 0, top, windowWidth, top + contentHeight)
    } else {
        val contentWidth = (windowHeight * videoRatio).roundToInt().coerceIn(1, windowWidth)
        val left = (windowWidth - contentWidth) / 2
        ReelsPipGeometry(aspectWidth, aspectHeight, left, 0, left + contentWidth, windowHeight)
    }
}

private tailrec fun reelsGcd(a: Int, b: Int): Int = if (b == 0) a else reelsGcd(b, a % b)

@Composable
internal fun rememberReelsFullscreenSafePadding(rotationZ: Int): PaddingValues {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    return reelsRotatedSafePadding(
        left = insets.calculateLeftPadding(LayoutDirection.Ltr),
        top = insets.calculateTopPadding(),
        right = insets.calculateRightPadding(LayoutDirection.Ltr),
        bottom = insets.calculateBottomPadding(),
        rotationZ = rotationZ,
    )
}

/**
 * Tracks the physical landscape side while software fullscreen is active. Registered only
 * in fullscreen and only when the window is portrait: elsewhere the value is unused and a
 * live sensor would just burn battery. The last valid side survives portrait/flat holds.
 */
@Composable
internal fun rememberReelsLandscapeRotation(enabled: Boolean): Int {
    val context = LocalContext.current
    var rotation by remember(context) { mutableIntStateOf(90) }
    DisposableEffect(context, enabled) {
        if (!enabled) return@DisposableEffect onDispose {}
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (manager == null || sensor == null) return@DisposableEffect onDispose {}
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                reelsGravityRotation(event.values[0], event.values[1])?.let { rotation = it }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { manager.unregisterListener(listener) }
    }
    return rotation
}
