package eu.kanade.tachiyomi.ui.home

import eu.kanade.tachiyomi.ui.library.anime.AnimeLibraryTab
import eu.kanade.tachiyomi.ui.updates.UpdatesTab
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class HomeScreenNavigationLogicTest {

    @Test
    fun `resolve home start tab returns default when default is visible`() {
        resolveHomeStartTab(
            defaultTab = AnimeLibraryTab,
            currentMoreTab = UpdatesTab,
        ) shouldBe AnimeLibraryTab
    }

    @Test
    fun `resolve home start tab falls back when default moved to more`() {
        resolveHomeStartTab(
            defaultTab = UpdatesTab,
            currentMoreTab = UpdatesTab,
        ) shouldBe AnimeLibraryTab
    }

    @Test
    fun `back handler enabled when current tab is moved to more tab`() {
        shouldHandleBackInHome(
            currentTab = UpdatesTab,
            defaultTab = AnimeLibraryTab,
            currentMoreTab = UpdatesTab,
        ) shouldBe true
    }

    @Test
    fun `back handler disabled when on default tab and default is visible`() {
        shouldHandleBackInHome(
            currentTab = AnimeLibraryTab,
            defaultTab = AnimeLibraryTab,
            currentMoreTab = UpdatesTab,
        ) shouldBe false
    }
}
