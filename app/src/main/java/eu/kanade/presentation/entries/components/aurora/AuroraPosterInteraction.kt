package eu.kanade.presentation.entries.components.aurora

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

internal fun shouldAllowPosterDismissal(
    isZoomed: Boolean,
    isPanned: Boolean,
): Boolean {
    return !isZoomed && !isPanned
}

internal fun Modifier.auroraPosterLongPress(onLongPress: () -> Unit): Modifier {
    return pointerInput(onLongPress) {
        detectTapGestures(
            onLongPress = { onLongPress() },
        )
    }
}
