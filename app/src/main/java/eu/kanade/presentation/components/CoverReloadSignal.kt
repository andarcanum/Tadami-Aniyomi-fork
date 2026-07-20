package eu.kanade.presentation.components

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf

/**
 * Global monotonically increasing signal that is bumped when network
 * connectivity is restored. Cover composables read it as a request key so
 * covers that previously settled on the error placeholder are retried
 * automatically instead of staying blank until the screen is recreated.
 */
object CoverReloadSignal {
    private val _tick = mutableIntStateOf(0)

    val tick: State<Int>
        get() = _tick

    fun bump() {
        _tick.intValue++
    }
}
