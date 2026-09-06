package eu.kanade.presentation.reader.novel

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos

/**
 * Two-phase unmount for WebView interop branches.
 *
 * Removing a *focused* AndroidView from the composition makes the Android view system re-focus the
 * window root (`ViewGroup.removeViewInternal` → `View.rootViewRequestFocus`). That cascade reaches
 * `AndroidComposeView.requestFocus` → `focusSearch` → `forceRemeasure` → a synchronous
 * `measureAndLayout` **inside** the running `applyChanges`, whose slot-table writer is still open;
 * a lazy-layout subcomposition disposed in the same frame then collides with it:
 * `ComposeRuntimeError: Cannot start a writer when another writer is pending`
 * (https://issuetracker.google.com/issues/507508113, unfixed upstream as of Compose BOM 2026.06).
 *
 * The novel reader hits this on every WebView → native renderer switch (rich-native toggle, bionic,
 * custom CSS/JS, page reader, unsupported-content fallback): the WebView normally holds focus
 * because the user just tapped or selected text in it, and the switch removes it in the same frame
 * the native lazy list appears.
 *
 * The fix never removes the interop branch in the frame the decision flips:
 *  - phase 1 (decision frame): the branch stays mounted while [prepareForFocusSafeUnmount] strips
 *    focus and focusability, so nothing can re-focus the subtree while it is still on screen;
 *  - phase 2 (next frame): with the subtree unfocused, `removeViewInternal` performs no re-focus,
 *    the cascade never reaches Compose and the removal is safe.
 *
 * The mount direction stays immediate: adding a view never re-enters layout.
 */
internal data class InteropUnmountDecision(
    /** Effective mount state after the transition: `true` = the interop branch is removed. */
    val unmounted: Boolean,
    /** Strip focus and focusability now; the branch stays mounted for one more frame. */
    val runPrepare: Boolean = false,
    /** Restore focusability: the unmount was cancelled before it committed. */
    val runCancelPrepare: Boolean = false,
)

/**
 * One transition of the two-phase unmount machine.
 *
 * `prepared` means phase 1 already ran; the caller is responsible for letting one frame pass
 * between preparing and acting on the commit decision (see [rememberFocusSafeInteropUnmount]).
 */
internal fun resolveInteropUnmountDecision(
    unmountRequested: Boolean,
    unmounted: Boolean,
    prepared: Boolean,
): InteropUnmountDecision = when {
    // Flipped back after the preparation ran but before the commit: undo the focus stripping.
    !unmountRequested && prepared ->
        InteropUnmountDecision(unmounted = false, runCancelPrepare = true)
    // Flipped back (or staying mounted): mounting is safe immediately.
    !unmountRequested -> InteropUnmountDecision(unmounted = false)
    // First frame of the unmount request: stay mounted, strip focus.
    !unmounted && !prepared -> InteropUnmountDecision(unmounted = false, runPrepare = true)
    // Prepared and a frame has passed, or nothing was ever mounted: commit.
    else -> InteropUnmountDecision(unmounted = true)
}

/**
 * Lags the unmount direction of [unmountRequested] by one focus-safe frame and returns the
 * effective unmounted state. The mount direction is applied immediately.
 *
 * @param prepareUnmount called while the branch is still mounted; must strip Android focus from
 * the interop view (see [prepareForFocusSafeUnmount]).
 * @param cancelPrepare called when the request flips back after [prepareUnmount] already ran;
 * must restore focusability (see [cancelFocusSafeUnmount]).
 */
@Composable
internal fun rememberFocusSafeInteropUnmount(
    unmountRequested: Boolean,
    prepareUnmount: () -> Unit,
    cancelPrepare: () -> Unit,
): Boolean {
    var unmounted by remember { mutableStateOf(unmountRequested) }
    var prepared by remember { mutableStateOf(false) }
    LaunchedEffect(unmountRequested) {
        val decision = resolveInteropUnmountDecision(unmountRequested, unmounted, prepared)
        when {
            decision.runCancelPrepare -> {
                cancelPrepare()
                prepared = false
                unmounted = false
            }
            decision.runPrepare -> {
                prepareUnmount()
                prepared = true
                // One clean frame with the view unfocused and unfocusable before it is removed.
                withFrameNanos { }
                unmounted = true
                prepared = false
            }
            else -> unmounted = decision.unmounted
        }
    }
    return unmounted
}

/**
 * Strips Android focus from an interop view that is about to leave the composition, so its removal
 * cannot trigger the window-root re-focus cascade. Also blocks re-focusing during the one frame the
 * view stays mounted after the renderer decision flipped.
 */
internal fun View.prepareForFocusSafeUnmount() {
    clearFocus()
    isFocusable = false
    isFocusableInTouchMode = false
}

/**
 * Restores the default focusability of a WebView after a cancelled [prepareForFocusSafeUnmount]
 * (the renderer decision flipped back before the unmount committed).
 */
internal fun View.cancelFocusSafeUnmount() {
    isFocusable = true
    isFocusableInTouchMode = true
}
