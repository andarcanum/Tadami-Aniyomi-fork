package eu.kanade.presentation.library.novel

import androidx.compose.runtime.Immutable
import eu.kanade.tachiyomi.ui.library.LibrarySearchQuery
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.asNovelCover
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.series.novel.model.LibraryNovelSeries
import tachiyomi.domain.source.novel.service.NovelSourceManager

sealed interface NovelLibraryItem {
    val id: Long
    val category: Long
    val pinned: Boolean
    val unreadCount: Long
    val readCount: Long
    val lastRead: Long
    val totalChapters: Long
    val hasStarted: Boolean
    val hasBookmarks: Boolean
    val dateAdded: Long
    val title: String
    val isDownloaded: Boolean
    val sourceLanguage: String

    /** Returns the underlying [Novel] for cover display, or null for Series. */
    val coverNovel: Novel?

    fun matches(query: LibrarySearchQuery, sourceManager: NovelSourceManager): Boolean

    @Immutable
    data class Single(
        val libraryNovel: LibraryNovel,
        override val isDownloaded: Boolean = false,
        override val sourceLanguage: String = "",
    ) : NovelLibraryItem {
        override val id = libraryNovel.id
        override val category = libraryNovel.category
        override val pinned = libraryNovel.pinned
        override val unreadCount = libraryNovel.unreadCount
        override val readCount = libraryNovel.readCount
        override val lastRead = libraryNovel.lastRead
        override val totalChapters = libraryNovel.totalChapters
        override val hasStarted = libraryNovel.hasStarted
        override val hasBookmarks = libraryNovel.hasBookmarks
        override val dateAdded = libraryNovel.novel.dateAdded
        override val title = libraryNovel.novel.displayTitle
        override val coverNovel = libraryNovel.novel

        override fun matches(query: LibrarySearchQuery, sourceManager: NovelSourceManager): Boolean {
            query.id?.let { id -> return libraryNovel.id == id }
            return libraryNovel.novel.matches(query, sourceManager)
        }
    }

    @Immutable
    data class Series(
        val librarySeries: LibraryNovelSeries,
        override val isDownloaded: Boolean = false,
        override val sourceLanguage: String = "",
    ) : NovelLibraryItem {
        override val id = -librarySeries.id // Negative to prevent collisions with single novels in compose keys
        override val category = librarySeries.categoryId
        override val pinned = librarySeries.pinned
        override val unreadCount = librarySeries.unreadCount
        override val readCount = librarySeries.readCount
        override val lastRead = librarySeries.lastRead
        override val totalChapters = librarySeries.totalChapters
        override val hasStarted = librarySeries.hasStarted
        override val hasBookmarks = librarySeries.entries.any { it.hasBookmarks }
        override val dateAdded = librarySeries.series.dateAdded
        override val title = librarySeries.title
        override val coverNovel = librarySeries.selectedCoverNovel ?: librarySeries.coverNovels.firstOrNull()
        val covers = librarySeries.coverNovels.map { it.asNovelCover() }

        override fun matches(query: LibrarySearchQuery, sourceManager: NovelSourceManager): Boolean {
            query.id?.let { id -> return librarySeries.id == id }
            if (librarySeries.title.contains(query.raw, ignoreCase = true)) return true
            return librarySeries.entries.any { it.novel.matches(query, sourceManager) }
        }
    }

    companion object {
        private fun Novel.matches(
            query: LibrarySearchQuery,
            sourceManager: NovelSourceManager,
        ): Boolean {
            val sourceName by lazy { sourceManager.getOrStub(source).name }
            return title.contains(query.raw, ignoreCase = true) ||
                displayTitle.contains(query.raw, ignoreCase = true) ||
                (author?.contains(query.raw, ignoreCase = true) ?: false) ||
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
