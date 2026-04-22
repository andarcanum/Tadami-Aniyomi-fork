package tachiyomi.domain.series.manga.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.series.manga.interactor.ResolveMangaSeriesCover
import tachiyomi.domain.series.model.SeriesCoverMode
import java.nio.file.Path

class MangaSeriesCoverTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `entry mode returns selected manga when it belongs to series`() {
        val selected = libraryManga(id = 2L, title = "Two")
        val series = librarySeries(
            coverMode = SeriesCoverMode.ENTRY,
            coverEntryId = 2L,
            entries = listOf(libraryManga(id = 1L, title = "One"), selected),
        )
        val resolver = ResolveMangaSeriesCover(getCustomCoverFile = { null })

        resolver(series) shouldBe MangaSeriesCover.Entry(selected.manga)
    }

    @Test
    fun `entry mode falls back to auto when selected manga is missing`() {
        val series = librarySeries(
            coverMode = SeriesCoverMode.ENTRY,
            coverEntryId = 7L,
            entries = listOf(libraryManga(id = 1L, title = "One")),
        )
        val resolver = ResolveMangaSeriesCover(getCustomCoverFile = { null })

        resolver(series) shouldBe MangaSeriesCover.Auto
    }

    @Test
    fun `custom mode returns custom state when file exists`() {
        val customFile = tempDir.resolve("cover.jpg").toFile().apply {
            writeText("cover")
        }
        val series = librarySeries(
            coverMode = SeriesCoverMode.CUSTOM,
            coverEntryId = null,
            entries = emptyList(),
        )
        val resolver = ResolveMangaSeriesCover(getCustomCoverFile = { customFile })

        resolver(series) shouldBe MangaSeriesCover.Custom(customFile, customFile.lastModified())
    }

    @Test
    fun `custom mode falls back to auto when file is missing`() {
        val missing = tempDir.resolve("missing.jpg").toFile()
        val series = librarySeries(
            coverMode = SeriesCoverMode.CUSTOM,
            coverEntryId = null,
            entries = emptyList(),
        )
        val resolver = ResolveMangaSeriesCover(
            getCustomCoverFile = { missing },
            customCoverExists = { false },
        )

        resolver(series) shouldBe MangaSeriesCover.Auto
    }

    private fun librarySeries(
        coverMode: SeriesCoverMode,
        coverEntryId: Long?,
        entries: List<LibraryManga>,
    ): LibraryMangaSeries {
        return LibraryMangaSeries(
            series = MangaSeries(
                id = 1L,
                title = "Series",
                description = null,
                categoryId = 0L,
                sortOrder = 0L,
                dateAdded = 0L,
                coverLastModified = 0L,
                coverMode = coverMode,
                coverEntryId = coverEntryId,
            ),
            entries = entries,
        )
    }

    private fun libraryManga(id: Long, title: String): LibraryManga {
        val manga = Manga.create().copy(
            id = id,
            title = title,
            source = 1L,
        )
        return LibraryManga(
            manga = manga,
            category = 0L,
            totalChapters = 0L,
            readCount = 0L,
            bookmarkCount = 0L,
            latestUpload = 0L,
            chapterFetchedAt = 0L,
            lastRead = 0L,
        )
    }
}
