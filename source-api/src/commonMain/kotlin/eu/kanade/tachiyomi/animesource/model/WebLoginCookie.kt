package eu.kanade.tachiyomi.animesource.model

/**
 * Contract v24: one browser cookie lifted from the host's web-login WebView, scoped to the
 * exact origin that issued it.
 *
 * The legacy web-login dump is a `Map<String, String>` of cookie name → value merged across
 * every origin, so two auth/service origins that both set a `session` cookie collapse into
 * one entry (last origin wins) and the source cannot tell which value belongs to which
 * service. [AnimeFeedOriginScopedSessionSource] receives these records instead, preserving
 * [origin] (scheme + host + optional port) and therefore allowing the source to pick the
 * right value per service.
 *
 * [origin] is normalized as `scheme://host` or `scheme://host:port` (non-default ports are
 * significant); a path, query or fragment is never included. Cookies without a resolvable
 * origin keep the raw CookieManager key as [origin].
 */
data class WebLoginCookie(
    val origin: String,
    val name: String,
    val value: String,
)
