package eu.kanade.tachiyomi.ui.library.anime

import androidx.compose.runtime.Immutable
import eu.kanade.tachiyomi.source.anime.getNameForAnimeInfo
import eu.kanade.tachiyomi.ui.library.LibrarySearchQuery
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.asAnimeCover
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.series.anime.model.LibraryAnimeSeries
import tachiyomi.domain.source.anime.service.AnimeSourceManager

sealed interface AnimeLibraryItem {
    val id: Long
    val category: Long
    val pinned: Boolean
    val downloadCount: Long
    val unseenCount: Long
    val seenCount: Long
    val lastSeen: Long
    val totalEpisodes: Long
    val hasStarted: Boolean
    val hasBookmarks: Boolean
    val dateAdded: Long
    val title: String
    val isLocal: Boolean
    val sourceLanguage: String
    val coverAnime: Anime?
    val libraryAnime: LibraryAnime
    val latestAnime: LibraryAnime

    fun matches(query: LibrarySearchQuery, sourceManager: AnimeSourceManager): Boolean

    @Immutable
    data class Single(
        override val libraryAnime: LibraryAnime,
        override val downloadCount: Long = -1,
        override val unseenCount: Long = -1,
        override val isLocal: Boolean = false,
        override val sourceLanguage: String = "",
    ) : AnimeLibraryItem {
        override val id: Long = libraryAnime.id
        override val category: Long = libraryAnime.category
        override val pinned: Boolean = libraryAnime.pinned
        override val seenCount: Long = libraryAnime.seenCount
        override val lastSeen: Long = libraryAnime.lastSeen
        override val totalEpisodes: Long = libraryAnime.totalCount
        override val hasStarted: Boolean = libraryAnime.hasStarted
        override val hasBookmarks: Boolean = libraryAnime.hasBookmarks
        override val dateAdded: Long = libraryAnime.anime.dateAdded
        override val title: String = libraryAnime.anime.displayTitle
        override val coverAnime: Anime = libraryAnime.anime
        override val latestAnime: LibraryAnime = libraryAnime

        override fun matches(query: LibrarySearchQuery, sourceManager: AnimeSourceManager): Boolean {
            query.id?.let { id -> return libraryAnime.id == id }
            return libraryAnime.anime.matches(query, sourceManager)
        }
    }

    @Immutable
    data class Series(
        val librarySeries: LibraryAnimeSeries,
        override val downloadCount: Long = 0,
        override val isLocal: Boolean = false,
        override val sourceLanguage: String = "",
    ) : AnimeLibraryItem {
        override val id: Long = -librarySeries.id
        override val category: Long = librarySeries.categoryId
        override val pinned: Boolean = librarySeries.pinned
        override val unseenCount: Long = librarySeries.unseenCount
        override val seenCount: Long = librarySeries.seenCount
        override val lastSeen: Long = librarySeries.lastSeen
        override val totalEpisodes: Long = librarySeries.totalEpisodes
        override val hasStarted: Boolean = librarySeries.hasStarted
        override val hasBookmarks: Boolean = librarySeries.entries.any { it.hasBookmarks }
        override val dateAdded: Long = librarySeries.series.dateAdded
        override val title: String = librarySeries.title
        override val coverAnime: Anime? = librarySeries.selectedCoverAnime ?: librarySeries.coverAnimes.firstOrNull()
        override val libraryAnime: LibraryAnime = librarySeries.entries.first()
        override val latestAnime: LibraryAnime get() = librarySeries.entries.lastOrNull() ?: libraryAnime
        val covers = librarySeries.coverAnimes.map { it.asAnimeCover() }

        override fun matches(query: LibrarySearchQuery, sourceManager: AnimeSourceManager): Boolean {
            query.id?.let { id -> return librarySeries.id == id }
            if (librarySeries.title.contains(query.raw, ignoreCase = true)) return true
            return librarySeries.entries.any { it.anime.matches(query, sourceManager) }
        }
    }

    companion object {
        operator fun invoke(
            libraryAnime: LibraryAnime,
            downloadCount: Long = -1,
            unseenCount: Long = -1,
            isLocal: Boolean = false,
            sourceLanguage: String = "",
        ): AnimeLibraryItem = Single(
            libraryAnime = libraryAnime,
            downloadCount = downloadCount,
            unseenCount = unseenCount,
            isLocal = isLocal,
            sourceLanguage = sourceLanguage,
        )

        private fun Anime.matches(
            query: LibrarySearchQuery,
            sourceManager: AnimeSourceManager,
        ): Boolean {
            val sourceName by lazy { sourceManager.getOrStub(source).getNameForAnimeInfo() }
            return title.contains(query.raw, ignoreCase = true) ||
                displayTitle.contains(query.raw, ignoreCase = true) ||
                (author?.contains(query.raw, ignoreCase = true) ?: false) ||
                (artist?.contains(query.raw, ignoreCase = true) ?: false) ||
                (description?.contains(query.raw, ignoreCase = true) ?: false) ||
                query.terms.all { subconstraint ->
                    checkNegatableConstraint(subconstraint) {
                        sourceName.contains(it, ignoreCase = true) ||
                            (genre?.any { genre -> genre.equals(it, ignoreCase = true) } ?: false)
                    }
                }
        }

        private fun checkNegatableConstraint(
            constraint: String,
            predicate: (String) -> Boolean,
        ): Boolean {
            return if (constraint.startsWith("-")) {
                !predicate(constraint.substringAfter("-").trimStart())
            } else {
                predicate(constraint)
            }
        }
    }
}
