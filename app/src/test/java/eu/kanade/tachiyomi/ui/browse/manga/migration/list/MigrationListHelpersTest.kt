package eu.kanade.tachiyomi.ui.browse.manga.migration.list

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.CatalogueSource
import io.mockk.mockk
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.model.Manga
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MigrationListHelpersTest {

    @Test
    fun `buildMigrationSearchParams combines manual query and metadata`() {
        val manga = manga(
            author = "Riichiro Inagaki",
            artist = "Yusuke Murata",
            genre = listOf("Action", "Comedy"),
        )

        val params = buildMigrationSearchParams(
            manga = manga,
            manualExtraSearchQuery = "weekly",
            useAutoMetadata = true,
        )

        assertEquals("weekly Riichiro Inagaki Yusuke Murata Action Comedy", params)
    }

    @Test
    fun `selectMigrationSearchCandidate respects source strategy`() {
        val firstSource = mockk<CatalogueSource>()
        val secondSource = mockk<CatalogueSource>()
        val lowChapterCount = MigrationSearchCandidate(
            sourceIndex = 0,
            source = firstSource,
            manga = manga(title = "First"),
            chapterInfo = MigrationListScreenModel.ChapterInfo(
                latestChapter = 10.0,
                chapterCount = 10,
            ),
        )
        val highChapterCount = MigrationSearchCandidate(
            sourceIndex = 1,
            source = secondSource,
            manga = manga(title = "Second"),
            chapterInfo = MigrationListScreenModel.ChapterInfo(
                latestChapter = 20.0,
                chapterCount = 20,
            ),
        )

        assertEquals(
            lowChapterCount,
            selectMigrationSearchCandidate(
                candidates = listOf(lowChapterCount, highChapterCount),
                strategy = SourcePreferences.MigrationStrategy.FIRST_SOURCE,
            ),
        )
        assertEquals(
            highChapterCount,
            selectMigrationSearchCandidate(
                candidates = listOf(lowChapterCount, highChapterCount),
                strategy = SourcePreferences.MigrationStrategy.MOST_CHAPTERS,
            ),
        )
    }

    @Test
    fun `shouldIncludeMigrationEntry honors hide and new chapter filters`() {
        val current = migratingManga(
            latestChapter = 10.0,
            searchResult = MigrationListScreenModel.SearchResult.NotFound,
        )
        val unchanged = migratingManga(
            latestChapter = 10.0,
            searchResult = MigrationListScreenModel.SearchResult.Success(
                manga = manga(title = "Target"),
                source = "Source",
                chapterCount = 10,
                latestChapter = 10.0,
            ),
        )
        val updated = migratingManga(
            latestChapter = 10.0,
            searchResult = MigrationListScreenModel.SearchResult.Success(
                manga = manga(title = "Target"),
                source = "Source",
                chapterCount = 11,
                latestChapter = 11.0,
            ),
        )

        assertTrue(shouldIncludeMigrationEntry(current, hideNotFound = false, onlyNewChapters = false))
        assertFalse(shouldIncludeMigrationEntry(current, hideNotFound = true, onlyNewChapters = false))
        assertFalse(shouldIncludeMigrationEntry(unchanged, hideNotFound = false, onlyNewChapters = true))
        assertTrue(shouldIncludeMigrationEntry(updated, hideNotFound = false, onlyNewChapters = true))
    }

    @Test
    fun `visibleMigrationItems can recover entries when filters are relaxed`() {
        val notFound = migratingManga(
            latestChapter = 10.0,
            searchResult = MigrationListScreenModel.SearchResult.NotFound,
        )
        val unchanged = migratingManga(
            latestChapter = 10.0,
            searchResult = MigrationListScreenModel.SearchResult.Success(
                manga = manga(title = "Target"),
                source = "Source",
                chapterCount = 10,
                latestChapter = 10.0,
            ),
        )
        val allItems = listOf(notFound, unchanged)

        assertEquals(
            emptyList(),
            visibleMigrationItems(allItems, hideNotFound = true, onlyNewChapters = true),
        )
        assertEquals(
            allItems,
            visibleMigrationItems(allItems, hideNotFound = false, onlyNewChapters = false),
        )
    }

    @Test
    fun `migrationSkippedCount counts all non-success entries`() {
        val searching = migratingManga(
            latestChapter = 10.0,
            searchResult = MigrationListScreenModel.SearchResult.Searching,
        )
        val notFound = migratingManga(
            latestChapter = 10.0,
            searchResult = MigrationListScreenModel.SearchResult.NotFound,
        )
        val success = migratingManga(
            latestChapter = 10.0,
            searchResult = MigrationListScreenModel.SearchResult.Success(
                manga = manga(title = "Target"),
                source = "Source",
                chapterCount = 10,
                latestChapter = 10.0,
            ),
        )

        assertEquals(2, migrationSkippedCount(listOf(searching, notFound, success)))
    }

    @Test
    fun `isMigrationSearchComplete returns true when all visible items are resolved`() {
        val unresolved = migratingManga(
            latestChapter = 10.0,
            searchResult = MigrationListScreenModel.SearchResult.Searching,
        )
        val resolvedNotFound = migratingManga(
            latestChapter = 10.0,
            searchResult = MigrationListScreenModel.SearchResult.NotFound,
        )
        val resolvedSuccess = migratingManga(
            latestChapter = 10.0,
            searchResult = MigrationListScreenModel.SearchResult.Success(
                manga = manga(title = "Target"),
                source = "Source",
                chapterCount = 10,
                latestChapter = 10.0,
            ),
        )

        assertFalse(isMigrationSearchComplete(listOf(unresolved, resolvedNotFound)))
        assertTrue(isMigrationSearchComplete(listOf(resolvedNotFound)))
        assertTrue(isMigrationSearchComplete(listOf(resolvedNotFound, resolvedSuccess)))
    }

    private fun migratingManga(
        latestChapter: Double?,
        searchResult: MigrationListScreenModel.SearchResult,
    ) = MigrationListScreenModel.MigratingManga(
        manga = manga(title = "Library entry"),
        chapterCount = 10,
        latestChapter = latestChapter,
        source = "Source",
        searchResult = searchResult,
    )

    private fun manga(
        title: String = "Title",
        author: String? = null,
        artist: String? = null,
        genre: List<String>? = null,
    ): Manga {
        return Manga.create().copy(
            id = title.hashCode().toLong(),
            source = 1L,
            favorite = true,
            url = "/$title",
            title = title,
            author = author,
            artist = artist,
            genre = genre,
            initialized = true,
        )
    }
}
