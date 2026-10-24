package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.WebLoginCookie

/**
 * Capability interface (contract v24): an origin-scoped variant of
 * [AnimeFeedWebLoginSource]'s session import.
 *
 * The host detects support with `source is AnimeFeedOriginScopedSessionSource` (instanceof,
 * the same marker-interface pattern as every other capability; no default members are added
 * to existing interfaces). A source implementing it MUST also implement
 * [AnimeFeedWebLoginSource]; the unscoped methods remain the fallback for hosts that predate
 * this capability, so both must stay functional.
 *
 * Why this exists: the legacy `Map<String, String>` dump merges cookies from every visited
 * origin by name, losing both the originating origin and any name collision
 * (`session` from the web app vs. `session` from the api host). [WebLoginCookie] keeps the
 * origin (`scheme://host[:port]`, port preserved) next to each name/value so the import is
 * unambiguous for multi-origin services and non-default ports.
 *
 * Implementing this capability is additive: it does not require a `LIB_VERSION` bump, and
 * plugins compiled against older `source-api` versions simply never match the instanceof.
 * Every member is abstract on purpose (a Kotlin default member would be unsafe for plugin
 * bytecode compiled with `-Xjvm-default=all`).
 */
interface AnimeFeedOriginScopedSessionSource {

    /**
     * Same contract as [AnimeFeedWebLoginSource.importWebSession], but [cookies] are
     * origin-scoped records rather than a merged name → value map. Duplicate names across
     * origins are expected and must be resolved per origin. [localStorage] is unchanged (the
     * host merges localStorage and sessionStorage of the current page).
     *
     * Returns true when a usable session was established; false keeps the dialog open.
     */
    suspend fun importWebSessionScoped(
        cookies: List<WebLoginCookie>,
        localStorage: Map<String, String>,
    ): Boolean

    /**
     * Same contract as [AnimeFeedWebLoginSource.importWebRedirect], with the session dump
     * taken at intercept time as origin-scoped records.
     */
    suspend fun importWebRedirectScoped(url: String, cookies: List<WebLoginCookie>): Boolean
}
