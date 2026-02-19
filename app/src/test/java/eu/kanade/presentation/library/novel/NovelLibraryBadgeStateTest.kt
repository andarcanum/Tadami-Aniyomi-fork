package eu.kanade.presentation.library.novel

import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.library.novel.LibraryNovel

class NovelLibraryBadgeStateTest {

    @Test
    fun `resolve badges respects preferences and data`() {
        val libraryNovel = LibraryNovel(
            novel = Novel.create().copy(
                id = 7L,
                source = 1L,
                updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
                title = "Test",
            ),
            category = 0L,
            totalChapters = 12L,
            readCount = 2L,
            bookmarkCount = 0L,
            latestUpload = 0L,
            chapterFetchedAt = 0L,
            lastRead = 0L,
        )

        val state = resolveNovelLibraryBadgeState(
            item = libraryNovel,
            showDownloadBadge = true,
            downloadedNovelIds = setOf(7L),
            showUnreadBadge = true,
            showLanguageBadge = true,
            sourceLanguage = "en",
        )

        state.showDownloaded shouldBe true
        state.unreadCount shouldBe 10L
        state.language shouldBe "en"
    }
}
