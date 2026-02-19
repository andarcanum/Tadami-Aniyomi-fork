package eu.kanade.tachiyomi.ui.entries.manga

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MangaScreenUrlNormalizationTest {

    @Test
    fun `normalizeMangaWebUrl converts inkstory api url to content page`() {
        normalizeMangaWebUrl("https://api.inkstory.net/v2/books/na-honjaman-level-up-ragnarok") shouldBe
            "https://inkstory.net/content/na-honjaman-level-up-ragnarok"
    }

    @Test
    fun `normalizeMangaWebUrl converts inkstory site api path to content page`() {
        normalizeMangaWebUrl("https://inkstory.net/v2/books/solo-leveling?tab=chapters") shouldBe
            "https://inkstory.net/content/solo-leveling"
    }

    @Test
    fun `normalizeMangaWebUrl keeps regular inkstory content url unchanged`() {
        normalizeMangaWebUrl("https://inkstory.net/content/solo-leveling?tab=chapters") shouldBe
            "https://inkstory.net/content/solo-leveling?tab=chapters"
    }

    @Test
    fun `normalizeMangaWebUrl keeps unknown hosts unchanged`() {
        normalizeMangaWebUrl("https://example.com/v2/books/solo-leveling") shouldBe
            "https://example.com/v2/books/solo-leveling"
    }
}
