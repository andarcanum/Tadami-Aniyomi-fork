package eu.kanade.tachiyomi.ui.reels

import eu.kanade.tachiyomi.animesource.AnimeCreatorDisplaySource
import eu.kanade.tachiyomi.animesource.AnimeFeedPlaybackHeadersSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import kotlinx.coroutines.CancellationException

/** Display only: callers must continue routing and persisting the original creator ID. */
internal fun reelsCreatorLabel(source: AnimeSource?, creatorId: String?, fallbackLabel: String? = null): String? {
    if (creatorId == null) return fallbackLabel
    return reelsCreatorDisplayName(source, creatorId)
        ?: fallbackLabel ?: creatorId.trim().trimStart('@').takeIf { it.isNotEmpty() }?.let { "@$it" }
}

private fun reelsCreatorDisplayName(source: AnimeSource?, creatorId: String): String? =
    try {
        (source as? AnimeCreatorDisplaySource)?.creatorDisplayName(creatorId)?.trim()?.takeIf { it.isNotEmpty() }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        null
    }

/**
 * New IDs match exactly. For opted-in sources, old display-name hides also match the current
 * local label (including name collisions, as before). Never guess a route or rewrite stored rows.
 */
internal fun reelsCreatorIsHidden(source: AnimeSource?, creatorId: String?, hiddenCreators: Set<String>): Boolean {
    if (creatorId == null || hiddenCreators.isEmpty()) return false
    if (creatorId in hiddenCreators) return true
    val display = reelsCreatorDisplayName(source, creatorId) ?: return false
    // A canonical route stored by the new plugin is an ID, not a legacy name-wide hide.
    if (display.startsWith("/model/") || display.startsWith("/pornstar/") || display.startsWith("/channels/")) {
        return false
    }
    return display in hiddenCreators
}

/** Dynamic providers export only public playback context, never credentials to media/CDN redirects. */
internal fun reelsPlaybackHeaders(source: AnimeSource?): Map<String, String> {
    if (source is AnimeFeedPlaybackHeadersSource) {
        val headers = try {
            source.playbackHeaders()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Opt-in is a credential-isolation boundary: a failed public provider must not
            // fall back to raw HttpSource headers containing site cookies or authorization.
            return emptyMap()
        }
        return buildMap {
            headers.forEach { (name, value) ->
                val publicName = when {
                    name.equals("User-Agent", ignoreCase = true) -> "User-Agent"
                    name.equals("Origin", ignoreCase = true) -> "Origin"
                    name.equals("Referer", ignoreCase = true) -> "Referer"
                    else -> null
                }
                if (publicName != null && value.isNotBlank() && '\r' !in value && '\n' !in value) {
                    put(publicName, value)
                }
            }
        }
    }
    return (source as? AnimeHttpSource)?.headers?.associate { it.first to it.second }.orEmpty()
}
