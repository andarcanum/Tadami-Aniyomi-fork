package eu.kanade.presentation.library.novel

import tachiyomi.domain.entries.novel.model.asNovelCover
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.series.novel.model.LibraryNovelSeries

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

    /** Returns the underlying [Novel] for cover display, or null for Series. */
    val coverNovel: tachiyomi.domain.entries.novel.model.Novel?

    data class Single(val libraryNovel: LibraryNovel) : NovelLibraryItem {
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
        override val title = libraryNovel.novel.title
        override val coverNovel = libraryNovel.novel
    }

    data class Series(val librarySeries: LibraryNovelSeries) : NovelLibraryItem {
        override val id = -librarySeries.id // Negative to prevent collisions with single novels in compose keys
        override val category = librarySeries.categoryId
        override val pinned = librarySeries.pinned
        override val unreadCount = librarySeries.unreadCount
        override val readCount = librarySeries.readCount
        override val lastRead = librarySeries.lastRead
        override val totalChapters = librarySeries.totalChapters
        override val hasStarted = librarySeries.hasStarted
        override val hasBookmarks = false // or define a query for series bookmarks later
        override val dateAdded = librarySeries.series.dateAdded
        override val title = librarySeries.title
        override val coverNovel = librarySeries.coverNovels.firstOrNull()
        val covers = librarySeries.coverNovels.map { it.asNovelCover() }
    }
}
