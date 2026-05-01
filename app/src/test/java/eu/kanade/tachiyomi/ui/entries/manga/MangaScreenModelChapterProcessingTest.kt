package eu.kanade.tachiyomi.ui.entries.manga

import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import io.kotest.matchers.collections.shouldContainExactly
import io.mockk.mockk
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter

class MangaScreenModelChapterProcessingTest {

    @Test
    fun `processed chapters keep duplicate chapter numbers`() {
        val manga = Manga.create().copy(
            chapterFlags = Manga.CHAPTER_SORTING_NUMBER or Manga.CHAPTER_SORT_ASC,
        )

        val state = MangaScreenModel.State.Success(
            manga = manga,
            source = mockk(relaxed = true),
            isFromSource = false,
            chapters = listOf(
                ChapterList.Item(
                    chapter(id = 1L, chapterNumber = 1.0, scanlator = "Main"),
                    MangaDownload.State.NOT_DOWNLOADED,
                    0,
                ),
                ChapterList.Item(
                    chapter(id = 2L, chapterNumber = 1.0, scanlator = "Side"),
                    MangaDownload.State.NOT_DOWNLOADED,
                    0,
                ),
                ChapterList.Item(
                    chapter(id = 3L, chapterNumber = 2.0, scanlator = "Main"),
                    MangaDownload.State.NOT_DOWNLOADED,
                    0,
                ),
            ),
            availableScanlators = emptySet(),
            scanlatorChapterCounts = emptyMap(),
            excludedScanlators = emptySet(),
        )

        state.processedChapters.map { it.chapter.id } shouldContainExactly listOf(1L, 2L, 3L)
    }

    private fun chapter(
        id: Long,
        chapterNumber: Double,
        scanlator: String?,
    ): Chapter {
        return Chapter.create().copy(
            id = id,
            chapterNumber = chapterNumber,
            scanlator = scanlator,
            sourceOrder = id,
        )
    }
}
