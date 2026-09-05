package eu.kanade.tachiyomi.ui.library.manga

import eu.kanade.tachiyomi.source.manga.getNameForMangaInfo
import eu.kanade.tachiyomi.ui.library.LibrarySearchQuery
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.asMangaCover
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.series.manga.model.LibraryMangaSeries
import tachiyomi.domain.source.manga.service.MangaSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

sealed interface MangaLibraryItem {
    val id: Long
    val category: Long
    val pinned: Boolean
    val downloadCount: Long
    val isLocal: Boolean
    val sourceLanguage: String
    val unreadCount: Long
    val readCount: Long
    val lastRead: Long
    val totalChapters: Long
    val hasStarted: Boolean
    val hasBookmarks: Boolean
    val dateAdded: Long
    val title: String
    val coverManga: Manga?

    /**
     * Compatibility access for existing renderers while the series UI is wired in.
     */
    val libraryManga: LibraryManga

    fun matches(constraint: String): Boolean

    fun matches(query: LibrarySearchQuery): Boolean

    data class Single(
        val libraryMangaValue: LibraryManga,
        val downloadCountValue: Long = -1,
        val isLocalValue: Boolean = false,
        val sourceLanguageValue: String = "",
        private val sourceManager: MangaSourceManager = Injekt.get(),
    ) : MangaLibraryItem {
        override val id = libraryMangaValue.id
        override val category = libraryMangaValue.category
        override val pinned = libraryMangaValue.pinned
        override val downloadCount = downloadCountValue
        override val isLocal = isLocalValue
        override val sourceLanguage = sourceLanguageValue
        override val unreadCount = libraryMangaValue.unreadCount
        override val readCount = libraryMangaValue.readCount
        override val lastRead = libraryMangaValue.lastRead
        override val totalChapters = libraryMangaValue.totalChapters
        override val hasStarted = libraryMangaValue.hasStarted
        override val hasBookmarks = libraryMangaValue.hasBookmarks
        override val dateAdded = libraryMangaValue.manga.dateAdded
        override val title = libraryMangaValue.manga.displayTitle
        override val coverManga = libraryMangaValue.manga
        override val libraryManga = libraryMangaValue

        override fun matches(constraint: String): Boolean {
            return matches(LibrarySearchQuery(constraint))
        }

        override fun matches(query: LibrarySearchQuery): Boolean {
            val sourceName by lazy { sourceManager.getOrStub(libraryMangaValue.manga.source).getNameForMangaInfo() }
            query.id?.let { id -> return libraryMangaValue.id == id }
            return libraryMangaValue.manga.title.contains(query.raw, true) ||
                title.contains(query.raw, true) ||
                (libraryMangaValue.manga.author?.contains(query.raw, true) ?: false) ||
                (libraryMangaValue.manga.artist?.contains(query.raw, true) ?: false) ||
                (libraryMangaValue.manga.description?.contains(query.raw, true) ?: false) ||
                query.terms.all { subconstraint ->
                    checkNegatableConstraint(subconstraint) {
                        sourceName.contains(it, true) ||
                            (libraryMangaValue.manga.genre?.any { genre -> genre.equals(it, true) } ?: false)
                    }
                }
        }
    }

    data class Series(
        val librarySeries: LibraryMangaSeries,
        val customCoverFile: File? = null,
        val downloadCountValue: Long = 0,
        val isLocalValue: Boolean = false,
        val sourceLanguageValue: String = "",
        private val sourceManager: MangaSourceManager = Injekt.get(),
    ) : MangaLibraryItem {
        override val id = -librarySeries.id
        override val category = librarySeries.categoryId
        override val pinned = librarySeries.pinned
        override val downloadCount = downloadCountValue
        override val isLocal = isLocalValue
        override val sourceLanguage = sourceLanguageValue
        override val unreadCount = librarySeries.unreadCount
        override val readCount = librarySeries.readCount
        override val lastRead = librarySeries.lastRead
        override val totalChapters = librarySeries.totalChapters
        override val hasStarted = librarySeries.hasStarted

        // D-M4: a series spans multiple entries; bookmark state aggregates over all volumes
        // (was hardcoded false, so the bookmark filter never matched series).
        override val hasBookmarks = librarySeries.entries.any { it.hasBookmarks }
        override val dateAdded = librarySeries.series.dateAdded
        override val title = librarySeries.title
        override val coverManga = librarySeries.selectedCoverManga ?: librarySeries.coverMangas.firstOrNull()
        override val libraryManga = librarySeries.entries.first()

        /**
         * D-M4: the most recent volume (entries are position-sorted). Status/source grouping and
         * the language filter used [libraryManga] (the FIRST volume), whose status/source can be
         * stale for the series as a whole.
         */
        val latestManga: LibraryManga get() = librarySeries.entries.lastOrNull() ?: libraryManga

        val covers = librarySeries.coverMangas.map { it.asMangaCover() }

        override fun matches(constraint: String): Boolean {
            return matches(LibrarySearchQuery(constraint))
        }

        override fun matches(query: LibrarySearchQuery): Boolean {
            query.id?.let { id -> return librarySeries.id == id }

            val titleMatches = librarySeries.title.contains(query.raw, true)
            if (titleMatches) return true

            return librarySeries.entries.any { libraryManga ->
                val sourceName by lazy { sourceManager.getOrStub(libraryManga.manga.source).getNameForMangaInfo() }
                libraryManga.manga.title.contains(query.raw, true) ||
                    libraryManga.manga.displayTitle.contains(query.raw, true) ||
                    (libraryManga.manga.author?.contains(query.raw, true) ?: false) ||
                    (libraryManga.manga.artist?.contains(query.raw, true) ?: false) ||
                    (libraryManga.manga.description?.contains(query.raw, true) ?: false) ||
                    query.terms.all { subconstraint ->
                        checkNegatableConstraint(subconstraint) {
                            sourceName.contains(it, true) ||
                                (libraryManga.manga.genre?.any { genre -> genre.equals(it, true) } ?: false)
                        }
                    }
            }
        }
    }

    companion object {
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
