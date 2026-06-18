package eu.kanade.tachiyomi.data.suggestions.sources

import java.io.Serializable

/**
 * Media type hint for recommendation sources.
 * NOVEL maps to MANGA on tracker APIs that don't distinguish.
 */
enum class SuggestionMediaType : Serializable {
    ANIME,
    MANGA,
    NOVEL, // aliases to MANGA for tracker queries
}

fun SuggestionMediaType.toAniListType(): String = when (this) {
    SuggestionMediaType.ANIME -> "ANIME"
    SuggestionMediaType.MANGA, SuggestionMediaType.NOVEL -> "MANGA"
}
