package eu.kanade.presentation.util

import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@Stable
class BottomNavVisibilityController {
    var isVisible by mutableStateOf(true)
        private set

    fun updateVisible(visible: Boolean) {
        this.isVisible = visible
    }

    fun reset() {
        isVisible = true
    }
}

val LocalBottomNavVisibilityController = compositionLocalOf<BottomNavVisibilityController> {
    error("BottomNavVisibilityController was not provided")
}
