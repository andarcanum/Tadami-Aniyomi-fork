package eu.kanade.tachiyomi.data.discovery

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Bounded query-filter matching; final admission still uses observed item metadata.
 *
 * Two stages are deliberately separate:
 * 1. reliable strict matches ([GenreMatcher.matchesConstraint]);
 * 2. a soft alternative ([GenreMatcher.matches]) only when it is unambiguous —
 *    exactly one remaining option matches the constraint. Guessing between
 *    several similar checkboxes is dangerous: sources that combine checkboxes
 *    with AND would silently narrow the query to nothing.
 * Translation remains the last resort and is budgeted.
 */
class GenreTranslationFallback(
    private val translate: suspend (String) -> String?,
    private val maxTranslationsPerCall: Int = 12,
    private val translationTimeoutMs: Long = 1_000L,
) {
    private val cache = DiscoveryLruCache<String, List<String>>(256)

    init {
        require(maxTranslationsPerCall >= 0 && translationTimeoutMs > 0L)
    }

    suspend fun selectSourceGenres(
        userGenres: List<String>,
        sourceGenreNames: List<String>,
    ): List<String> {
        val wanted = userGenres.filter { it.isNotBlank() }.distinct()
        if (wanted.isEmpty()) return emptyList()
        val options = sourceGenreNames.filter { it.isNotBlank() }.distinct()
        val selected = options.filter { name ->
            wanted.any { GenreMatcher.matchesConstraint(it, name) }
        }.toMutableSet()
        val unresolved = wanted.filterNot { genre ->
            selected.any { GenreMatcher.matchesConstraint(genre, it) }
        }.toMutableSet()
        if (unresolved.isEmpty()) return options.filter { it in selected }

        // Controlled soft fallback: only an unambiguous option is accepted.
        for (genre in unresolved.toList()) {
            val alternatives = options.filter {
                it !in selected && GenreMatcher.matches(genre, it)
            }
            if (alternatives.size == 1) {
                selected += alternatives.single()
                // The constraint is covered by this option; do not spend a
                // translation or a second checkbox on it.
                unresolved -= genre
                unresolved.removeAll { candidate ->
                    selected.any { GenreMatcher.matchesConstraint(candidate, it) }
                }
            }
        }
        if (unresolved.isEmpty()) return options.filter { it in selected }

        var remaining = maxTranslationsPerCall
        for (name in options) {
            if (unresolved.isEmpty()) break
            if (name in selected) continue
            var translatedForms = cache.get(name)
            if (translatedForms == null) {
                // Known, unrelated canonical genres do not need network translation.
                if (GenreMatcher.resolve(name).isNotEmpty()) continue
                if (remaining == 0) continue
                remaining--
                val translated = try {
                    withTimeoutOrNull(translationTimeoutMs) { translate(name) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                translatedForms = listOfNotNull(translated?.takeIf { it.isNotBlank() })
                if (translatedForms.isNotEmpty()) cache.put(name, translatedForms)
            }
            val candidates = translatedForms.orEmpty()
            if (candidates.isEmpty()) continue
            // Only the translated form is matched here: matching the original
            // name would bypass the ambiguity guard above.
            val matched = unresolved.filter { genre ->
                candidates.any { GenreMatcher.matches(genre, it) }
            }
            if (matched.isNotEmpty()) {
                selected += name
                unresolved.removeAll(matched.toSet())
            }
        }
        return options.filter { it in selected }
    }
}
