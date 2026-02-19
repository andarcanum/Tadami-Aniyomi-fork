package eu.kanade.presentation.library.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.novel.model.NovelLibrarySort

class NovelLibrarySettingsDialogOptionsTest {

    @Test
    fun `novel sort options contain expected parity modes`() {
        novelLibrarySortOptions().map { it.second } shouldBe listOf(
            NovelLibrarySort.Type.Alphabetical,
            NovelLibrarySort.Type.TotalChapters,
            NovelLibrarySort.Type.LastRead,
            NovelLibrarySort.Type.LastUpdate,
            NovelLibrarySort.Type.UnreadCount,
            NovelLibrarySort.Type.LatestChapter,
            NovelLibrarySort.Type.ChapterFetchDate,
            NovelLibrarySort.Type.DateAdded,
            NovelLibrarySort.Type.Random,
        )
    }

    @Test
    fun `novel display modes contain expected parity modes`() {
        novelLibraryDisplayModes().map { it.second } shouldBe listOf(
            LibraryDisplayMode.CompactGrid,
            LibraryDisplayMode.ComfortableGrid,
            LibraryDisplayMode.CoverOnlyGrid,
            LibraryDisplayMode.List,
        )
    }
}
