package tachiyomi.domain.series.anime.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.series.anime.interactor.ResolveAnimeSeriesCover
import tachiyomi.domain.series.model.SeriesCoverMode
import java.nio.file.Path

class AnimeSeriesCoverTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `entry mode returns selected anime when it belongs to series`() {
        val selected = libraryAnime(id = 2L, title = "Two")
        val series = librarySeries(
            coverMode = SeriesCoverMode.ENTRY,
            coverEntryId = 2L,
            entries = listOf(libraryAnime(id = 1L, title = "One"), selected),
        )
        val resolver = ResolveAnimeSeriesCover(getCustomCoverFile = { null })

        resolver(series) shouldBe AnimeSeriesCover.Entry(selected.anime)
    }

    @Test
    fun `entry mode falls back to auto when selected anime is missing`() {
        val series = librarySeries(
            coverMode = SeriesCoverMode.ENTRY,
            coverEntryId = 7L,
            entries = listOf(libraryAnime(id = 1L, title = "One")),
        )
        val resolver = ResolveAnimeSeriesCover(getCustomCoverFile = { null })

        resolver(series) shouldBe AnimeSeriesCover.Auto
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
        val resolver = ResolveAnimeSeriesCover(getCustomCoverFile = { customFile })

        resolver(series) shouldBe AnimeSeriesCover.Custom(customFile, customFile.lastModified())
    }

    @Test
    fun `custom mode falls back to auto when file is missing`() {
        val missing = tempDir.resolve("missing.jpg").toFile()
        val series = librarySeries(
            coverMode = SeriesCoverMode.CUSTOM,
            coverEntryId = null,
            entries = emptyList(),
        )
        val resolver = ResolveAnimeSeriesCover(
            getCustomCoverFile = { missing },
            customCoverExists = { false },
        )

        resolver(series) shouldBe AnimeSeriesCover.Auto
    }

    private fun librarySeries(
        coverMode: SeriesCoverMode,
        coverEntryId: Long?,
        entries: List<LibraryAnime>,
    ): LibraryAnimeSeries {
        return LibraryAnimeSeries(
            series = AnimeSeries(
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

    private fun libraryAnime(id: Long, title: String): LibraryAnime {
        val anime = Anime.create().copy(
            id = id,
            title = title,
            source = 1L,
        )
        return LibraryAnime(
            anime = anime,
            category = 0L,
            totalCount = 0L,
            seenCount = 0L,
            bookmarkCount = 0L,
            fillermarkCount = 0L,
            latestUpload = 0L,
            episodeFetchedAt = 0L,
            lastSeen = 0L,
        )
    }
}
