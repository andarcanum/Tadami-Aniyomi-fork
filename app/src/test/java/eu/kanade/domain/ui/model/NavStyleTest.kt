package eu.kanade.domain.ui.model

import eu.kanade.tachiyomi.ui.library.anime.AnimeLibraryTab
import eu.kanade.tachiyomi.ui.more.MoreTab
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NavStyleTest {

    @Test
    fun `move titles to more uses anime library as moved tab`() {
        NavStyle.MOVE_MANGA_TO_MORE.moreTab shouldBe AnimeLibraryTab
    }

    @Test
    fun `move titles to more removes anime library from bottom tabs`() {
        val tabs = NavStyle.MOVE_MANGA_TO_MORE.tabs

        tabs shouldNotContain AnimeLibraryTab
        tabs shouldContain MoreTab
        tabs.size shouldBe 5
    }
}
