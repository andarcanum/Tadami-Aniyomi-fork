package eu.kanade.tachiyomi.ui.reels.components

import eu.kanade.tachiyomi.animesource.model.WebLoginCookie
import java.net.URI

/**
 * Pure plumbing of the web-login session dump (contract v24 audit): request-id correlation,
 * origin-scoped cookie records and origin seeding. Kept out of the WebView composable so the
 * correlation and origin rules are unit-testable without Robolectric.
 */

internal const val DEEP_DUMP_MARKER = "__DUMP2__:"

/** Monotonic dump ids are embedded in the JS prompt so a late answer maps to its own request. */
internal fun localStorageDumpJs(requestId: Long): String =
    "(function(){" +
        "  var base={};" +
        "  try{for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);" +
        "base[k]=localStorage.getItem(k);}}catch(e){}" +
        "  try{for(var i=0;i<sessionStorage.length;i++){var k=sessionStorage.key(i);" +
        "base['ss.'+k]=sessionStorage.getItem(k);}}catch(e){}" +
        "  var finished=false;" +
        "  try{if(window.__rgHandshake){base['__handshake__']=window.__rgHandshake;}}catch(e){}" +
        "  try{if(window.__rgBearer){base['__rg_bearer__']=window.__rgBearer;}}catch(e){}" +
        "  function finish(obj){if(finished)return;finished=true;" +
        // The id is digits only, so it is safe to splice into the JS literal.
        "window.prompt('" + DEEP_DUMP_MARKER + requestId + ":'+JSON.stringify(obj));}" +
        "  if(window.kinde&&typeof window.kinde.getToken==='function'){" +
        "    setTimeout(function(){finish(base);},4000);" +
        "    try{window.kinde.getToken().then(function(t){" +
        "if(t){base['__kinde_token__']=String(t);}finish(base);},function(){finish(base);});}" +
        "    catch(e){finish(base);}" +
        "    return;" +
        "  }" +
        "  return JSON.stringify(base);" +
        "})()"

/** Payload of a `window.prompt` dump answer: which request produced it and its raw JSON. */
internal data class DumpPrompt(val requestId: Long, val json: String)

/**
 * Parses `__DUMP2__:<requestId>:<json>`. Returns null for anything else (including the
 * pre-v24 `__DUMP2__:<json>` shape), so a foreign prompt can never be mistaken for a dump.
 */
internal fun parseDumpPrompt(message: String?): DumpPrompt? {
    if (message == null || !message.startsWith(DEEP_DUMP_MARKER)) return null
    val rest = message.removePrefix(DEEP_DUMP_MARKER)
    val separator = rest.indexOf(':')
    if (separator <= 0) return null
    val requestId = rest.substring(0, separator).toLongOrNull() ?: return null
    return DumpPrompt(requestId, rest.substring(separator + 1))
}

/**
 * Normalizes a URL or origin to `scheme://host[:port]`. Unlike the previous Android-Uri
 * version this keeps a non-default port (two services can share a host on different ports)
 * and never includes path/query/fragment. Default ports are folded into the scheme so
 * `https://example.com:443` and `https://example.com` count as one origin.
 */
internal fun originOf(url: String?): String? {
    val uri = parseUri(url) ?: return null
    return originString(uri)
}

/**
 * Origins whose cookies the dump reads: the source's explicit origins (verbatim when they do
 * not parse), plus the start origin. The `api.` sibling is guessed ONLY when the source did
 * not supply explicit origins (contract v22 `extraSessionCookieOrigins`): the guess strips
 * `www.` instead of taking the last two labels, which broke multi-part suffixes such as
 * `service.example.co.uk` (it produced `api.co.uk`).
 */
internal fun seedOrigins(startUrl: String, extra: List<String> = emptyList()): MutableSet<String> {
    val origins = mutableSetOf<String>()
    extra.forEach { raw ->
        if (raw.isNotBlank()) origins += originOf(raw) ?: raw.trim()
    }
    val uri = parseUri(startUrl) ?: return origins
    val scheme = uri.scheme ?: return origins
    val host = uri.host ?: return origins
    origins += originString(uri)!!

    if (extra.isEmpty()) {
        val bare = host.removePrefix("www.")
        if (bare.contains('.') && !isIpv4(bare)) {
            origins += "$scheme://api.$bare" + portSuffix(uri)
        }
    }
    return origins
}

/**
 * Legacy `name -> value` merge for sources that only implement
 * [eu.kanade.tachiyomi.animesource.AnimeFeedWebLoginSource]. Iteration order matches the old
 * origin-by-origin dump, so a duplicate name still resolves to the last origin's value —
 * exactly the behavior old plugins were written against.
 */
internal fun legacyCookieMap(cookies: List<WebLoginCookie>): Map<String, String> =
    buildMap { cookies.forEach { put(it.name, it.value) } }

private fun parseUri(raw: String?): URI? =
    try {
        raw?.trim()?.takeIf { it.isNotEmpty() }?.let { URI(it) }
    } catch (_: Exception) {
        null
    }

private fun originString(uri: URI): String? {
    val scheme = uri.scheme ?: return null
    val host = uri.host ?: return null
    return "$scheme://$host" + portSuffix(uri)
}

private fun portSuffix(uri: URI): String {
    val port = uri.port
    if (port < 0) return ""
    val default = when (uri.scheme?.lowercase()) {
        "http" -> 80
        "https" -> 443
        else -> -1
    }
    return if (port == default) "" else ":$port"
}

private fun isIpv4(host: String): Boolean =
    host.count { it == '.' } == 3 && host.all { it.isDigit() || it == '.' }
