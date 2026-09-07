package eu.kanade.tachiyomi.animesource

/**
 * Capability interface (contract v20): a feed source whose service authenticates through a
 * hosted web flow (email + one-time code, magic link, ...) instead of a password, so the host
 * opens it in a WebView and hands the resulting browser session back to the source.
 *
 * Two cooperating paths, both instance-of-detected (the marker-interface pattern of
 * [AnimeCreatorFeedSource]/[AnimeReelsFeedbackSource]; no default members are added to
 * existing interfaces):
 * 1. The service's own SPA runs in the WebView ([webLoginUrl]); on completion the source lifts
 *    whatever the SPA persisted via [importWebSession].
 * 2. The source's own OAuth2 authorization-code (+PKCE) URL [webLoginUrl] (fresh challenge per
 *    call); the host intercepts only redirects the source owns ([isOwnLoginRedirect], matched
 *    by state) and hands the code to [importWebRedirect] instead of loading it, so the SPA
 *    cannot consume it.
 *
 * A source implementing this capability SHOULD also implement [AnimeFeedLoginSource] for the
 * session-state members (isLoggedIn/loggedInAccount/logout); the host must then route login
 * through this capability and never call [AnimeFeedLoginSource.login] on it.
 */
interface AnimeFeedWebLoginSource {

    /**
     * Entry URL rendered in the host's WebView: the service's own web app, where the user
     * completes the hosted flow (captcha, email + one-time code, ...) as in a browser. The
     * service's SPA consumes its own OAuth redirect; the host never intercepts it.
     */
    fun webLoginUrl(): String

    /**
     * The source's OWN OAuth2 authorization-code (+PKCE) authorize URL, fresh verifier and
     * state per call. The host loads it into the same WebView after [webLoginUrl]'s flow
     * established the hosted session (the auth2 cookie makes it auto-complete), intercepts
     * [isOwnLoginRedirect] redirects and hands the code to [importWebRedirect] — this path
     * yields tokens to the source even when the SPA keeps them in memory only.
     */
    fun ownAuthorizeUrl(): String

    /** True when [url] is a redirect of THIS source's in-flight [webLoginUrl] flow (state match). */
    fun isOwnLoginRedirect(url: String): Boolean

    /**
     * Exchanges the authorization code carried by the intercepted redirect (with this flow's
     * own PKCE verifier and state check), persists the session and returns true on success;
     * false keeps the WebView open. [cookies] is the WebView's CookieManager dump taken at
     * intercept time (the service's session cookies are part of the credential set). The host
     * must not load the intercepted redirect when this returns true.
     */
    suspend fun importWebRedirect(url: String, cookies: Map<String, String>): Boolean

    /**
     * Imports a finished SPA session: [cookies] is the merged CookieManager dump (name →
     * value) for the service's domains, [localStorage] the merged WebView DOM-storage dump
     * (key → raw string value; the host merges localStorage and sessionStorage). The
     * implementation extracts and verifies (e.g. against a live profile call) whatever it
     * needs and returns true when a usable session was established. False means "not logged in
     * yet": the host keeps the WebView open and retries later. Failures may throw.
     */
    suspend fun importWebSession(cookies: Map<String, String>, localStorage: Map<String, String>): Boolean
}
