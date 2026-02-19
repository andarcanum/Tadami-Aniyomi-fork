package eu.kanade.tachiyomi.ui.browse.novel

import eu.kanade.tachiyomi.novelsource.ConfigurableNovelSource
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.novelsource.PreferenceScreen
import eu.kanade.tachiyomi.ui.browse.novel.extension.details.novelSourcePreferencesScreen
import eu.kanade.tachiyomi.ui.browse.novel.extension.novelExtensionDetailsScreen
import eu.kanade.tachiyomi.ui.browse.novel.migration.sources.migrateNovelScreen
import eu.kanade.tachiyomi.ui.browse.novel.source.browse.novelSourcePreferencesScreenOrNull
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelBrowseNavigationTest {

    @Test
    fun `extension details navigation keeps plugin id`() {
        val screen = novelExtensionDetailsScreen("plugin-id")

        screen.pluginId shouldBe "plugin-id"
    }

    @Test
    fun `extension details source preferences navigation keeps source id`() {
        val screen = novelSourcePreferencesScreen(123L)

        screen.sourceId shouldBe 123L
    }

    @Test
    fun `browse source settings navigation is available for configurable source`() {
        val screen = novelSourcePreferencesScreenOrNull(
            sourceId = 55L,
            source = TestConfigurableNovelSource(),
        )

        requireNotNull(screen)
        screen.sourceId shouldBe 55L
    }

    @Test
    fun `browse source settings navigation is hidden for non configurable source`() {
        val screen = novelSourcePreferencesScreenOrNull(
            sourceId = 55L,
            source = TestNovelSource(),
        )

        screen.shouldBeNull()
    }

    @Test
    fun `migration source click navigation keeps source id`() {
        val screen = migrateNovelScreen(987L)

        screen.sourceId shouldBe 987L
    }
}

private class TestNovelSource : NovelSource {
    override val id: Long = 1L
    override val name: String = "Test source"
    override val lang: String = "en"
}

private class TestConfigurableNovelSource : ConfigurableNovelSource {
    override val id: Long = 2L
    override val name: String = "Configurable source"
    override val lang: String = "en"

    override fun setupPreferenceScreen(screen: PreferenceScreen) = Unit
}
