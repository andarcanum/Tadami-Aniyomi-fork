package eu.kanade.tachiyomi.ui.browse

import io.kotest.matchers.collections.shouldContainExactly
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
}
