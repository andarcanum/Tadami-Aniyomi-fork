package eu.kanade.presentation.easteregg.crystal

import android.animation.ValueAnimator
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.isActive

internal data class CrystalMotionPolicy(val resumed: Boolean, val systemAnimations: Boolean)

@Composable
internal fun rememberCrystalMotionPolicy(): CrystalMotionPolicy {
    val owner = LocalLifecycleOwner.current
    val resumed = remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val animations = remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed.value = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (resumed.value) animations.value = ValueAnimator.areAnimatorsEnabled()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return CrystalMotionPolicy(resumed.value, animations.value)
}

/** One active frame source per caller. Background intervals are never counted. */
@Composable
internal fun rememberCrystalTime(
    running: Boolean,
    resetKey: Any? = Unit,
    minFrameIntervalNanos: Long = 15_000_000L,
): State<Float> {
    val policy = rememberCrystalMotionPolicy()
    val clock = remember(resetKey) { CrystalFrameClock() }
    val time = remember(resetKey) { mutableFloatStateOf(0f) }
    LaunchedEffect(running, policy.resumed, clock, minFrameIntervalNanos) {
        clock.pause()
        if (running && policy.resumed) {
            var lastEmitNanos = 0L
            try {
                while (isActive) {
                    withFrameNanos { now ->
                        if (minFrameIntervalNanos > 0L && lastEmitNanos != 0L &&
                            now - lastEmitNanos < minFrameIntervalNanos
                        ) {
                            return@withFrameNanos
                        }
                        lastEmitNanos = now
                        time.floatValue = clock.advance(now, true)
                    }
                }
            } finally {
                clock.pause()
            }
        }
    }
    return time
}

/** Sensor ownership follows the visible screen. No sensor or permission is required for drag input. */
@Composable
internal fun rememberCrystalSensor(enabled: Boolean): State<Offset> {
    val context = LocalContext.current.applicationContext
    val policy = rememberCrystalMotionPolicy()
    val result = remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(context, enabled, policy.resumed) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = if (enabled &&
            policy.resumed
        ) {
            manager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        } else {
            null
        }
        val matrix = FloatArray(9)
        val orientation = FloatArray(3)
        var baseline: Offset? = null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                SensorManager.getOrientation(matrix, orientation)
                val current = Offset(orientation[2], orientation[1])
                val origin = baseline ?: current.also { baseline = it }
                val target =
                    Offset(
                        ((current.x - origin.x) / 0.35f).coerceIn(-1f, 1f),
                        ((current.y - origin.y) / 0.35f).coerceIn(-1f, 1f),
                    )
                result.value = result.value + (target - result.value) * 0.12f
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        result.value = Offset.Zero
        if (sensor != null) manager?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose {
            manager?.unregisterListener(listener)
            result.value = Offset.Zero
        }
    }
    return result
}
