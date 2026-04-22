package tachiyomi.domain.series.novel.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.series.model.SeriesCoverMode
import tachiyomi.domain.series.novel.interactor.ResolveNovelSeriesCover
import java.nio.file.Path

class NovelSeriesCoverTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `entry mode returns selected novel when it belongs to series`() {
        val selected = libraryNovel(id = 2L, title = "Two")
        val series = librarySeries(
            coverMode = SeriesCoverMode.ENTRY,
            coverEntryId = 2L,
            entries = listOf(libraryNovel(id = 1L, title = "One"), selected),
        )
        val resolver = ResolveNovelSeriesCover(getCustomCoverFile = { null })

        resolver(series) shouldBe NovelSeriesCover.Entry(selected.novel)
    }

    @Test
    fun `entry mode falls back to auto when selected novel is missing`() {
        val series = librarySeries(
            coverMode = SeriesCoverMode.ENTRY,
            coverEntryId = 7L,
            entries = listOf(libraryNovel(id = 1L, title = "One")),
        )
        val resolver = ResolveNovelSeriesCover(getCustomCoverFile = { null })

        resolver(series) shouldBe NovelSeriesCover.Auto
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
        val resolver = ResolveNovelSeriesCover(getCustomCoverFile = { customFile })

        resolver(series) shouldBe NovelSeriesCover.Custom(customFile, customFile.lastModified())
    }

    @Test
    fun `custom mode falls back to auto when file is missing`() {
        val missing = tempDir.resolve("missing.jpg").toFile()
        val series = librarySeries(
            coverMode = SeriesCoverMode.CUSTOM,
            coverEntryId = null,
            entries = emptyList(),
        )
        val resolver = ResolveNovelSeriesCover(
            getCustomCoverFile = { missing },
            customCoverExists = { false },
        )

        resolver(series) shouldBe NovelSeriesCover.Auto
    }

    private fun librarySeries(
        coverMode: SeriesCoverMode,
        coverEntryId: Long?,
        entries: List<LibraryNovel>,
    ): LibraryNovelSeries {
        return LibraryNovelSeries(
            series = NovelSeries(
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

    private fun libraryNovel(id: Long, title: String): LibraryNovel {
        val novel = Novel.create().copy(
            id = id,
            title = title,
            source = 1L,
        )
        return LibraryNovel(
            novel = novel,
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
