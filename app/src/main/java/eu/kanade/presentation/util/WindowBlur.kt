package eu.kanade.presentation.util

import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.theme.AuroraTheme
import java.util.function.Consumer

/**
 * Returns whether real-time cross-window blur is supported and enabled on the current device.
 *
 * Checks:
 * 1. Android version >= 12 (API 31, Build.VERSION_CODES.S)
 * 2. Device is not in E-Ink mode
 * 3. System [WindowManager.isCrossWindowBlurEnabled] is true (accounts for OEM disablement,
 *    e.g. MIUI/HyperOS on budget devices like Redmi 10C, battery saver, or accessibility settings).
 */
@Composable
fun rememberSupportsBlurBehind(isEInk: Boolean = AuroraTheme.colors.isEInk): Boolean {
    if (isEInk || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false

    val context = LocalContext.current
    val windowManager = remember(context) {
        context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    } ?: return false

    var isBlurEnabled by remember(windowManager) {
        mutableStateOf(
            try {
                windowManager.isCrossWindowBlurEnabled
            } catch (_: Throwable) {
                false
            },
        )
    }

    DisposableEffect(windowManager) {
        val listener = Consumer<Boolean> { enabled ->
            isBlurEnabled = enabled
        }
        try {
            windowManager.addCrossWindowBlurEnabledListener(listener)
        } catch (_: Throwable) {
            // Some OEM ROMs might fail to register listeners
        }
        onDispose {
            try {
                windowManager.removeCrossWindowBlurEnabledListener(listener)
            } catch (_: Throwable) {
            }
        }
    }

    return resolveSupportsBlurBehind(
        sdkInt = Build.VERSION.SDK_INT,
        isEInk = isEInk,
        isCrossWindowBlurEnabled = isBlurEnabled,
    )
}

/**
 * Pure policy function for resolving whether window blur should be active.
 */
internal fun resolveSupportsBlurBehind(
    sdkInt: Int = Build.VERSION.SDK_INT,
    isEInk: Boolean = false,
    isCrossWindowBlurEnabled: Boolean = false,
): Boolean {
    if (isEInk || sdkInt < Build.VERSION_CODES.S) return false
    return isCrossWindowBlurEnabled
}
