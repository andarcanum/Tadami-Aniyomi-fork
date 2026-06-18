package eu.kanade.tachiyomi.data.suggestions

import eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType
import java.io.Serializable

data class SuggestionSeed(
    val mediaType: SuggestionMediaType,
    val primaryTitle: String,
    val candidateTitles: List<String>,
    val description: String?,
    val author: String? = null,
    val genres: List<String>? = null,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
