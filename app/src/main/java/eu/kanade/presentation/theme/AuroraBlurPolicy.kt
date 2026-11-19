package eu.kanade.presentation.theme

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.powerManager

/**
 * Hardware-accelerated RenderEffect backdrop blur requires Android 12 (API 31).
 * On older Android versions Haze falls back to software/GPU shader passes that
 * tank frame pacing during LazyColumn/LazyVerticalGrid scrolling.
 */
internal const val MIN_LIVE_BLUR_API_LEVEL = 31

/**
 * Optional screen-root override provided by [eu.kanade.tachiyomi.ui.home.HomeScreen]
 * so scrolling list rows can read the resolved blur tier in O(1) without performing
 * Binder IPC (`PowerManager.isPowerSaveMode`) per item recomposition.
 */
val LocalLiveBackdropBlurEnabled = compositionLocalOf<Boolean?> { null }

@Volatile
private var cachedIsLowRamDevice: Boolean? = null

private fun isLowRamDeviceCached(context: Context): Boolean {
    cachedIsLowRamDevice?.let { return it }
    return DeviceUtil.isLowRamDevice(context.applicationContext ?: context).also {
        cachedIsLowRamDevice = it
    }
}

/**
 * Pure policy deciding whether live GPU backdrop blur (Haze) should be enabled.
 *
 * Disabled on:
 * - Android 11 and below (API < 31, no native RenderEffect blur)
 * - E-Ink display mode
 * - Low-RAM devices (< 3 GB RAM)
 * - Active system Battery Saver mode
 */
fun shouldUseLiveBackdropBlur(
    sdkInt: Int,
    isEInkMode: Boolean,
    isLowRamDevice: Boolean,
    isPowerSaveMode: Boolean,
): Boolean {
    if (sdkInt < MIN_LIVE_BLUR_API_LEVEL) return false
    if (isEInkMode || isLowRamDevice || isPowerSaveMode) return false
    return true
}

/**
 * Resolves whether live backdrop blur should be active for the current composition.
 * Reuses [LocalLiveBackdropBlurEnabled] when available to avoid per-item IPC during scrolling.
 */
@Composable
fun rememberLiveBackdropBlurEnabled(
    isEInkMode: Boolean = LocalIsEInkMode.current,
): Boolean {
    val provided = LocalLiveBackdropBlurEnabled.current
    if (provided != null && isEInkMode == LocalIsEInkMode.current) {
        return provided
    }
    val context = LocalContext.current
    val isLowRam = remember(context) { isLowRamDeviceCached(context) }
    val isPowerSaveMode = remember(context) { context.powerManager.isPowerSaveMode }
    return shouldUseLiveBackdropBlur(
        sdkInt = Build.VERSION.SDK_INT,
        isEInkMode = isEInkMode,
        isLowRamDevice = isLowRam,
        isPowerSaveMode = isPowerSaveMode,
    )
}
