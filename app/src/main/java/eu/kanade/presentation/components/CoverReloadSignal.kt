package eu.kanade.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Global monotonically increasing signal that is bumped when network
 * connectivity is restored. Cover composables read it as a request key so
 * covers that previously settled on the error placeholder are retried
 * automatically instead of staying blank until the screen is recreated.
 */
object CoverReloadSignal {
    private val _flow = MutableStateFlow(0)

    val flow: StateFlow<Int>
        get() = _flow.asStateFlow()

    fun bump() {
        _flow.update { it + 1 }
    }
}

/**
 * Reads the global cover reload signal safely within Compose snapshot lifecycle.
 */
@Composable
fun rememberCoverReloadTick(): Int {
    return CoverReloadSignal.flow.collectAsState().value
}
