package eu.kanade.domain.entries.manga.model

import eu.kanade.domain.items.chapter.model.applyFilters
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.ui.entries.manga.ChapterList
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter

class MangaFilterPolicyTest {

    @Test
    fun `downloadedFilter reflects the stored chapter flag`() {
        manga(downloadedFilterRaw = Manga.CHAPTER_SHOW_DOWNLOADED).downloadedFilter shouldBe TriState.ENABLED_IS
        manga(downloadedFilterRaw = Manga.CHAPTER_SHOW_NOT_DOWNLOADED).downloadedFilter shouldBe TriState.ENABLED_NOT
        manga(downloadedFilterRaw = 0L).downloadedFilter shouldBe TriState.DISABLED
    }

    @Test
    fun `effectiveDownloadedFilter forces downloaded-only mode when requested`() {
        val manga = manga()

        manga.effectiveDownloadedFilter(downloadedOnly = true) shouldBe TriState.ENABLED_IS
        manga.effectiveDownloadedFilter(downloadedOnly = false) shouldBe TriState.DISABLED
    }

    @Test
    fun `chaptersFiltered treats downloaded-only mode as an active filter`() {
        val manga = manga()

        manga.chaptersFiltered(downloadedOnly = true) shouldBe true
        manga.chaptersFiltered(downloadedOnly = false) shouldBe false
    }

    @Test
    fun `custom cover helper requires an explicit cache`() {
        Manga::hasCustomCover.parameters.last().isOptional shouldBe false
    }

    @Test
    fun `chapter filter entry point keeps only downloaded items when downloaded-only mode is enabled`() {
        val filtered = listOf(
            chapterItem(id = 1L, downloaded = true),
            chapterItem(id = 2L, downloaded = false),
        ).applyFilters(manga(), downloadedOnly = true)
            .map { it.chapter.id }
            .toList()

        filtered.shouldContainExactly(1L)
    }

    private fun manga(downloadedFilterRaw: Long = 0L): Manga {
        return Manga.create().copy(
            id = 1L,
            title = "Manga",
            source = 1L,
            chapterFlags = downloadedFilterRaw,
        )
    }

    private fun chapterItem(id: Long, downloaded: Boolean): ChapterList.Item {
        return ChapterList.Item(
            chapter = Chapter.create().copy(
                id = id,
                name = "Chapter $id",
                chapterNumber = id.toDouble(),
            ),
            downloadState = if (downloaded) MangaDownload.State.DOWNLOADED else MangaDownload.State.NOT_DOWNLOADED,
            downloadProgress = 0,
        )
    }
}
