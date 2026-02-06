package eu.kanade.tachiyomi.ui.browse

import io.kotest.matchers.collections.shouldContainExactly
import org.junit.jupiter.api.Test

class BrowseTabTest {

    @Test
    fun `buildBrowseSections returns only anime when manga section hidden`() {
        BrowseTab.buildBrowseSections(showMangaSection = false).shouldContainExactly(
            BrowseTab.BrowseSection.Anime,
        )
    }

    @Test
    fun `buildBrowseSections includes anime manga and novel when manga section shown`() {
        BrowseTab.buildBrowseSections(showMangaSection = true).shouldContainExactly(
            BrowseTab.BrowseSection.Anime,
            BrowseTab.BrowseSection.Manga,
            BrowseTab.BrowseSection.Novel,
        )
    }
}
