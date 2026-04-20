package tachiyomi.presentation.core.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR

enum class HapticFeedbackMode(
    val titleRes: StringResource,
) {
    PARTIAL(MR.strings.pref_haptic_feedback_partial),
    FULL(MR.strings.pref_haptic_feedback_full),
}

interface AppHaptics {
    fun tap()
}

private object NoOpAppHaptics : AppHaptics {
    override fun tap() = Unit
}

private class AppHapticsImpl(
    private val hapticFeedback: HapticFeedback,
    private val hapticFeedbackMode: HapticFeedbackMode,
    private val isEInkMode: Boolean,
) : AppHaptics {
    override fun tap() {
        if (!isEInkMode && hapticFeedbackMode == HapticFeedbackMode.FULL) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
        }
    }
}

val LocalAppHaptics = staticCompositionLocalOf<AppHaptics> { NoOpAppHaptics }

@Composable
fun AppHapticsProvider(
    hapticFeedbackMode: HapticFeedbackMode,
    isEInkMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val appHaptics = remember(hapticFeedback, hapticFeedbackMode, isEInkMode) {
        createAppHaptics(
            hapticFeedback = hapticFeedback,
            hapticFeedbackMode = hapticFeedbackMode,
            isEInkMode = isEInkMode,
        )
    }

    CompositionLocalProvider(LocalAppHaptics provides appHaptics) {
        content()
    }
}

fun createAppHaptics(
    hapticFeedback: HapticFeedback,
    hapticFeedbackMode: HapticFeedbackMode,
    isEInkMode: Boolean = false,
): AppHaptics = AppHapticsImpl(
    hapticFeedback = hapticFeedback,
    hapticFeedbackMode = hapticFeedbackMode,
    isEInkMode = isEInkMode,
)
