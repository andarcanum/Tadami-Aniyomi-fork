package eu.kanade.tachiyomi.ui.deeplink.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter

class DeepLinkNovelScreenModelStateTest {

    @Test
    fun `returns no results when novel is null`() {
        val state = resolveDeepLinkNovelState(
            novel = null,
            chapter = NovelChapter.create(),
        )

        state shouldBe DeepLinkNovelScreenModel.State.NoResults
    }

    @Test
    fun `returns novel result without chapter when chapter is null`() {
        val novel = Novel.create().copy(id = 10L, title = "Novel")

        val state = resolveDeepLinkNovelState(
            novel = novel,
            chapter = null,
        )

        state shouldBe DeepLinkNovelScreenModel.State.Result(
            novel = novel,
            chapterId = null,
        )
    }

    @Test
    fun `returns novel result with chapter id when chapter exists`() {
        val novel = Novel.create().copy(id = 10L, title = "Novel")
        val chapter = NovelChapter.create().copy(id = 44L, novelId = novel.id)

        val state = resolveDeepLinkNovelState(
            novel = novel,
            chapter = chapter,
        )

        state shouldBe DeepLinkNovelScreenModel.State.Result(
            novel = novel,
            chapterId = 44L,
        )
    }
}
