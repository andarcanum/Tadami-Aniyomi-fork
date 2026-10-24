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
