/*
 * Pure helpers for Trakt tracking that don't depend on network or platform state, kept in their
 * own file so they can be exercised by unit tests without instantiating the full tracker.
 */
package eu.kanade.tachiyomi.data.track.trakt

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToInt

/**
 * Resolve the Trakt season number and per-season episode number for a stored [lastSeen] value
 * using a season-prefixed convention (e.g. `2.13` → season 2, episode 13). Whole values fall
 * back to `null` season so the caller can derive the season via show metadata instead.
 */
internal fun resolveTraktSeasonEpisode(lastSeen: Double): Pair<Int?, Int> {
    val plain = runCatching {
        java.math.BigDecimal.valueOf(lastSeen).stripTrailingZeros().toPlainString()
    }.getOrNull()
    if (!plain.isNullOrBlank() && plain.contains('.')) {
        val parts = plain.split('.', limit = 2)
        val season = parts.getOrNull(0)?.toIntOrNull()?.takeIf { it > 0 } ?: 1
        val fraction = parts.getOrNull(1).orEmpty()
        val episode = fraction.trimStart('0').toIntOrNull()
            ?: fraction.toIntOrNull()
            ?: lastSeen.roundToInt().coerceAtLeast(1)
        return season to episode
    }
    return null to lastSeen.roundToInt().coerceAtLeast(1)
}

/**
 * Extract the most useful poster URL from a Trakt `images.poster` payload. Trakt may return
 * a single string, an array of strings, or an object with size buckets such as `full`/`medium`.
 * Returns an empty string when no usable URL is present.
 */
internal fun extractTraktPosterUrl(posterEl: JsonElement?): String {
    val raw = when (posterEl) {
        null -> null
        is JsonObject -> posterEl["full"]?.jsonPrimitive?.contentOrNull
            ?: posterEl["medium"]?.jsonPrimitive?.contentOrNull
            ?: posterEl["thumb"]?.jsonPrimitive?.contentOrNull
            ?: posterEl.entries.firstOrNull()?.value?.let(::extractTraktPosterUrl)

        is JsonArray -> posterEl.firstOrNull()?.jsonPrimitive?.contentOrNull
        else -> posterEl.jsonPrimitive.contentOrNull
    }?.trim().takeUnless { it.isNullOrBlank() } ?: return ""

    return when {
        raw.startsWith("//") -> "https:$raw"
        raw.startsWith("http://") || raw.startsWith("https://") -> raw
        raw.startsWith("/") -> raw
        else -> "https://$raw"
    }
}
