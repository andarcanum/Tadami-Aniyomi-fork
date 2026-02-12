package eu.kanade.domain.entries.novel.model

import eu.kanade.tachiyomi.novelsource.model.SNovel
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.entries.novel.model.Novel

fun Novel.toSNovel(): SNovel = SNovel.create().also {
    it.url = url
    it.title = title
    it.author = author
    it.description = normalizeNovelDescription(description)
    it.genre = genre?.joinToString()
    it.status = status.toInt()
    it.thumbnail_url = thumbnailUrl
    it.update_strategy = updateStrategy
    it.initialized = initialized
}

// TODO: move these into the domain model
val Novel.downloadedFilter: TriState
    get() {
        // Novels don't support offline downloads yet; do NOT apply the global "downloaded only" filter.
        // Keep flag-based filtering only (UI currently doesn't expose it).
        return when (downloadedFilterRaw) {
            Novel.CHAPTER_SHOW_DOWNLOADED -> TriState.ENABLED_IS
            Novel.CHAPTER_SHOW_NOT_DOWNLOADED -> TriState.ENABLED_NOT
            else -> TriState.DISABLED
        }
    }

fun Novel.chaptersFiltered(): Boolean {
    return unreadFilter != TriState.DISABLED ||
        downloadedFilter != TriState.DISABLED ||
        bookmarkedFilter != TriState.DISABLED
}

fun Novel.copyFrom(other: SNovel): Novel {
    val author = other.author ?: author
    val description = normalizeNovelDescription(other.description) ?: this.description
    val genres = if (other.genre != null) {
        other.getGenres()
    } else {
        genre
    }
    val thumbnailUrl = other.thumbnail_url ?: thumbnailUrl
    return this.copy(
        author = author,
        description = description,
        genre = genres,
        thumbnailUrl = thumbnailUrl,
        status = other.status.toLong(),
        updateStrategy = other.update_strategy,
        initialized = other.initialized && initialized,
    )
}

fun SNovel.toDomainNovel(sourceId: Long): Novel {
    return Novel.create().copy(
        url = url,
        title = title,
        author = author,
        description = normalizeNovelDescription(description),
        genre = getGenres(),
        status = status.toLong(),
        thumbnailUrl = thumbnail_url,
        updateStrategy = update_strategy,
        initialized = initialized,
        source = sourceId,
    )
}
