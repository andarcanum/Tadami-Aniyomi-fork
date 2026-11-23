package eu.kanade.tachiyomi.ui.library

import eu.kanade.presentation.library.novel.NovelLibraryItem
import eu.kanade.tachiyomi.ui.library.anime.AnimeLibraryItem
import eu.kanade.tachiyomi.ui.library.anime.AuroraLibrarySelectionHeaderState
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryItem
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.series.anime.model.AnimeSeries
import tachiyomi.domain.series.anime.model.LibraryAnimeSeries
import tachiyomi.domain.series.manga.model.LibraryMangaSeries
import tachiyomi.domain.series.manga.model.MangaSeries
import tachiyomi.domain.series.novel.model.LibraryNovelSeries
import tachiyomi.domain.series.novel.model.NovelSeries
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.source.local.entries.anime.LocalAnimeSource
import tachiyomi.source.local.entries.manga.LocalMangaSource
import tachiyomi.source.local.entries.novel.LocalNovelSource

class LibraryBulkUpdateSelectionTest {

    @Test
    fun `manga selection flattens series, skips local entries, and deduplicates ids`() {
        val mangaSourceManager = mockk<MangaSourceManager>(relaxed = true)
        val onlineSingle = MangaLibraryItem.Single(
            libraryMangaValue = libraryManga(id = 1L, source = 100L),
            sourceManager = mangaSourceManager,
        )
        val localSingle = MangaLibraryItem.Single(
            libraryMangaValue = libraryManga(id = 2L, source = LocalMangaSource.ID),
            sourceManager = mangaSourceManager,
        )
        val series = MangaLibraryItem.Series(
            LibraryMangaSeries(
                series = MangaSeries(
                    id = 50L,
                    title = "Series",
                    description = null,
                    categoryId = 0L,
                    sortOrder = 0L,
                    dateAdded = 0L,
                    coverLastModified = 0L,
                ),
                entries = listOf(
                    libraryManga(id = 1L, source = 100L),
                    libraryManga(id = 3L, source = 200L),
                    libraryManga(id = 4L, source = LocalMangaSource.ID),
                ),
            ),
            sourceManager = mangaSourceManager,
        )

        val result = resolveMangaBulkUpdateSelection(listOf(onlineSingle, localSingle, series))

        result.hasUpdatable.shouldBeTrue()
        result.updatableIds.toList().shouldContainExactly(1L, 3L)
        result.skippedLocalCount shouldBe 2
    }

    @Test
    fun `anime local-only selection reports zero updatable entries`() {
        val localAnime = AnimeLibraryItem.Single(libraryAnime(id = 10L, source = LocalAnimeSource.ID))
        val result = resolveAnimeBulkUpdateSelection(listOf(localAnime))

        result.hasUpdatable.shouldBeFalse()
        result.updatableCount shouldBe 0
        result.skippedLocalCount shouldBe 1
    }

    @Test
    fun `anime series expansion includes online seasons and skips local`() {
        val series = AnimeLibraryItem.Series(
            LibraryAnimeSeries(
                series = AnimeSeries(
                    id = 60L,
                    title = "Anime Series",
                    description = null,
                    categoryId = 0L,
                    sortOrder = 0L,
                    dateAdded = 0L,
                    coverLastModified = 0L,
                ),
                entries = listOf(
                    libraryAnime(id = 11L, source = 300L),
                    libraryAnime(id = 12L, source = LocalAnimeSource.ID),
                    libraryAnime(id = 13L, source = 301L),
                ),
            ),
        )

        val result = resolveAnimeBulkUpdateSelection(listOf(series))

        result.updatableIds.toList().shouldContainExactly(11L, 13L)
        result.skippedLocalCount shouldBe 1
    }

    @Test
    fun `novel selection flattens series and excludes local epub entries`() {
        val online = NovelLibraryItem.Single(libraryNovel(id = 21L, source = 400L))
        val local = NovelLibraryItem.Single(libraryNovel(id = 22L, source = LocalNovelSource.ID))
        val series = NovelLibraryItem.Series(
            LibraryNovelSeries(
                series = NovelSeries(
                    id = 70L,
                    title = "Novel Series",
                    description = null,
                    categoryId = 0L,
                    sortOrder = 0L,
                    dateAdded = 0L,
                    coverLastModified = 0L,
                ),
                entries = listOf(
                    libraryNovel(id = 23L, source = 401L),
                    libraryNovel(id = 22L, source = LocalNovelSource.ID),
                ),
            ),
        )

        val result = resolveNovelBulkUpdateSelection(listOf(online, local, series))

        result.updatableIds.toList().shouldContainExactly(21L, 23L)
        result.skippedLocalCount shouldBe 1
    }

    @Test
    fun `aurora selection header state detects full category selection`() {
        AuroraLibrarySelectionHeaderState().isSelectionMode.shouldBeFalse()
        AuroraLibrarySelectionHeaderState(
            selectionCount = 3,
            selectedInCurrentCategoryCount = 3,
            currentCategoryTotalCount = 9,
            currentCategoryName = "Reading",
        ).allInCurrentCategorySelected.shouldBeFalse()
        AuroraLibrarySelectionHeaderState(
            selectionCount = 9,
            selectedInCurrentCategoryCount = 9,
            currentCategoryTotalCount = 9,
            currentCategoryName = "Reading",
        ).allInCurrentCategorySelected.shouldBeTrue()
    }

    private fun libraryManga(id: Long, source: Long): LibraryManga {
        return LibraryManga(
            manga = Manga.create().copy(id = id, source = source, title = "Manga $id", favorite = true),
            category = 0L,
            totalChapters = 10L,
            readCount = 2L,
            bookmarkCount = 0L,
            latestUpload = 0L,
            chapterFetchedAt = 0L,
            lastRead = 0L,
        )
    }

    private fun libraryAnime(id: Long, source: Long): LibraryAnime {
        return LibraryAnime(
            anime = Anime.create().copy(id = id, source = source, title = "Anime $id", favorite = true),
            category = 0L,
            totalCount = 12L,
            seenCount = 3L,
            bookmarkCount = 0L,
            fillermarkCount = 0L,
            latestUpload = 0L,
            episodeFetchedAt = 0L,
            lastSeen = 0L,
        )
    }

    private fun libraryNovel(id: Long, source: Long): LibraryNovel {
        return LibraryNovel(
            novel = Novel.create().copy(id = id, source = source, title = "Novel $id", favorite = true),
            category = 0L,
            totalChapters = 50L,
            readCount = 10L,
            bookmarkCount = 0L,
            latestUpload = 0L,
            chapterFetchedAt = 0L,
            lastRead = 0L,
        )
    }
}
