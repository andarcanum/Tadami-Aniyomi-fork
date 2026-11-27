package eu.kanade.tachiyomi.animesource

/**
 * Optional feed lifecycle capability (contract v31).
 *
 * Implemented by stateful feed plugins (e.g. persistent MTProto/TDLib engines) so the host
 * can notify them when the Reels surface becomes active or inactive (leaving Reels, switching
 * away from the source, or backgrounding the app).
 *
 * All members are abstract. Implementing requires `extensionLib` 31.0; older plugins inside
 * `12.0..30.0` keep loading untouched.
 */
interface AnimeFeedLifecycleSource {
    /**
     * Called by the host when this feed source transitions between active foreground usage
     * (`active = true`) and inactive/backgrounded state (`active = false`).
     *
     * Implementations must be fast, non-blocking, idempotent, and safe to call from any thread.
     */
    fun onFeedActiveChanged(active: Boolean)
}
