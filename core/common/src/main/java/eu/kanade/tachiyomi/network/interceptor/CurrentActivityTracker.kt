package eu.kanade.tachiyomi.network.interceptor

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.webkit.WebView
import logcat.LogPriority
import logcat.logcat
import java.lang.ref.WeakReference

/**
 * Keeps a weak reference to the resumed activity so background WebViews (the Cloudflare challenge
 * solve) can be attached to a real window.
 *
 * A WebView that is never added to a view hierarchy does not render, and Cloudflare's managed
 * challenge handshake stalls without rendering signals: the solve then times out even on devices
 * where the very same challenge passes in a visible WebView.
 */
object CurrentActivityTracker : Application.ActivityLifecycleCallbacks {

    @Volatile
    private var resumed: WeakReference<Activity>? = null

    fun install(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
    }

    val current: Activity?
        get() = resumed?.get()

    override fun onActivityResumed(activity: Activity) {
        resumed = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumed?.get() === activity) {
            resumed = null
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) {
        if (resumed?.get() === activity) {
            resumed = null
        }
    }
}

/**
 * Attaches [webView] to the resumed activity's window as an invisible overlay and reports whether
 * it happened.
 *
 * Cloudflare's managed challenge only completes in a WebView that actually renders: an unattached
 * one never draws a frame, so the handshake stalls until the solve timeout while the same
 * challenge passes in a visible WebView. The overlay is fully transparent and refuses touches, so
 * the reader never notices it. Without a resumed activity the WebView stays unattached and the
 * caller keeps the previous, detached behaviour.
 */
fun attachWebViewToWindow(webView: WebView): Boolean {
    val activity = CurrentActivityTracker.current ?: return false
    val decorView = activity.window?.decorView as? android.widget.FrameLayout ?: return false
    return runCatching {
        if (webView.parent === decorView) return true
        (webView.parent as? android.view.ViewGroup)?.removeView(webView)
        webView.alpha = 0.01f
        webView.isClickable = false
        webView.isFocusable = false
        webView.isFocusableInTouchMode = false
        decorView.addView(
            webView,
            android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        true
    }.onFailure { error ->
        logcat(TAG, LogPriority.WARN) { "Failed to attach WebView to the activity window: $error" }
    }.getOrDefault(false)
}

private const val TAG = "CFWebView"
