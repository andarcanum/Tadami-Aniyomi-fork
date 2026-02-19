@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.novelsource.model

import eu.kanade.tachiyomi.source.model.UpdateStrategy
import java.io.Serializable

interface SNovel : Serializable {

    var url: String

    var title: String

    var author: String?

    var description: String?

    var genre: String?

    var status: Int

    var thumbnail_url: String?

    var update_strategy: UpdateStrategy

    var initialized: Boolean

    fun getGenres(): List<String>? {
        if (genre.isNullOrBlank()) return null
        val tokens = genre
            .orEmpty()
            .split(Regex("[,;/|\\n\\r\\t•·]+"))
            .map {
                it.trim()
                    .trim('-', '–', '—', ',', ';', '/', '|', '•', '·')
            }
            .filter { it.isNotBlank() }

        if (tokens.isEmpty()) return null

        val seen = LinkedHashSet<String>()
        val normalized = buildList {
            tokens.forEach { token ->
                val dedupeKey = token.lowercase()
                if (seen.add(dedupeKey)) {
                    add(token)
                }
            }
        }

        return normalized.ifEmpty { null }
    }

    fun copy() = create().also {
        it.url = url
        it.title = title
        it.author = author
        it.description = description
        it.genre = genre
        it.status = status
        it.thumbnail_url = thumbnail_url
        it.update_strategy = update_strategy
        it.initialized = initialized
    }

    companion object {
        const val UNKNOWN = 0
        const val ONGOING = 1
        const val COMPLETED = 2
        const val LICENSED = 3
        const val PUBLISHING_FINISHED = 4
        const val CANCELLED = 5
        const val ON_HIATUS = 6

        fun create(): SNovel {
            return SNovelImpl()
        }
    }
}
