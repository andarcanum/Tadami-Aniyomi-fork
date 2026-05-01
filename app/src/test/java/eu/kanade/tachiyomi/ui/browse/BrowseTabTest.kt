package eu.kanade.tachiyomi.ui.browse

import eu.kanade.tachiyomi.ui.browse.anime.feed.AnimeFeedScreenState
import eu.kanade.tachiyomi.ui.browse.novel.feed.NovelFeedScreenState
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class BrowseTabTest {

    @Test
    fun `buildBrowseSections returns only anime when manga section hidden`() {
        BrowseTab.buildBrowseSections(
            showMangaSection = false,
            showNovelSection = false,
        ).shouldContainExactly(
            BrowseTab.BrowseSection.Anime,
        )
    }

    @Test
    fun `buildBrowseSections includes anime manga and novel when sections shown`() {
        BrowseTab.buildBrowseSections(
            showMangaSection = true,
            showNovelSection = true,
        ).shouldContainExactly(
            BrowseTab.BrowseSection.Anime,
            BrowseTab.BrowseSection.Manga,
            BrowseTab.BrowseSection.Novel,
        )
    }

    @Test
    fun `buildBrowseSections includes anime and novel when manga section hidden`() {
        BrowseTab.buildBrowseSections(
            showMangaSection = false,
            showNovelSection = true,
        ).shouldContainExactly(
            BrowseTab.BrowseSection.Anime,
            BrowseTab.BrowseSection.Novel,
        )
    }

    @Test
    fun `extension tab index is 1 when feed tab hidden`() {
        BrowseTab.extensionTabIndex(hideFeedTab = true) shouldBe 1
    }

    @Test
    fun `extension tab index is 2 when feed tab visible`() {
        BrowseTab.extensionTabIndex(hideFeedTab = false) shouldBe 2
    }

    @Test
    fun `AnimeFeedScreenModelTest default state is loading and empty`() {
        val state = AnimeFeedScreenState()

        state.isLoading shouldBe true
        state.isEmpty shouldBe true
        state.isLoadingItems shouldBe false
    }

    @Test
    fun `NovelFeedScreenModelTest default state is loading and empty`() {
        val state = NovelFeedScreenState()

        state.isLoading shouldBe true
        state.isEmpty shouldBe true
        state.isLoadingItems shouldBe false
    }
}
