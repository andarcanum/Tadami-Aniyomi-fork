package eu.kanade.domain.entries.novel.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.entries.novel.model.Novel

class NovelFilterPolicyTest {

    @Test
    fun `downloadedFilter reflects the stored chapter flag`() {
        novel(downloadedFilterRaw = Novel.CHAPTER_SHOW_DOWNLOADED).downloadedFilter shouldBe TriState.ENABLED_IS
        novel(downloadedFilterRaw = Novel.CHAPTER_SHOW_NOT_DOWNLOADED).downloadedFilter shouldBe TriState.ENABLED_NOT
        novel(downloadedFilterRaw = 0L).downloadedFilter shouldBe TriState.DISABLED
    }

    @Test
    fun `effectiveDownloadedFilter forces downloaded-only mode when requested`() {
        val novel = novel()

        novel.effectiveDownloadedFilter(downloadedOnly = true) shouldBe TriState.ENABLED_IS
        novel.effectiveDownloadedFilter(downloadedOnly = false) shouldBe TriState.DISABLED
    }

    @Test
    fun `chaptersFiltered treats downloaded-only mode as an active filter`() {
        val novel = novel()

        novel.chaptersFiltered(downloadedOnly = true) shouldBe true
        novel.chaptersFiltered(downloadedOnly = false) shouldBe false
    }

    private fun novel(downloadedFilterRaw: Long = 0L): Novel {
        return Novel.create().copy(
            id = 1L,
            title = "Novel",
            source = 1L,
            chapterFlags = downloadedFilterRaw,
        )
    }
}
