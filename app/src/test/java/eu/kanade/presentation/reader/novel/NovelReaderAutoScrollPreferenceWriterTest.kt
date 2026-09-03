package eu.kanade.presentation.reader.novel

import eu.kanade.tachiyomi.test.PersistingPreferenceStore
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderOverride
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class NovelReaderAutoScrollPreferenceWriterTest {

    private fun createPrefs() = NovelReaderPreferences(
        preferenceStore = PersistingPreferenceStore(),
        json = Json { encodeDefaults = true },
    )

    @Test
    fun `floating button visibility writes the source override when one exists`() {
        val prefs = createPrefs()
        prefs.setSourceOverride(7L, NovelReaderOverride())
        val writer = NovelReaderAutoScrollPreferenceWriter(prefs, sourceId = 7L, hasSourceOverride = true)

        writer.persistShowFloatingButtonPreference(true)

        prefs.getSourceOverride(7L)?.showAutoScrollFloatingButton shouldBe true
        // The global default for all other sources stays untouched.
        prefs.showAutoScrollFloatingButton().get() shouldBe false
    }

    @Test
    fun `floating button visibility writes the global pref without an override`() {
        val prefs = createPrefs()
        val writer = NovelReaderAutoScrollPreferenceWriter(prefs, sourceId = 7L, hasSourceOverride = false)

        writer.persistShowFloatingButtonPreference(true)

        prefs.showAutoScrollFloatingButton().get() shouldBe true
        prefs.getSourceOverride(7L) shouldBe null
    }

    @Test
    fun `auto scroll interval keeps honoring the override scope`() {
        val prefs = createPrefs()
        prefs.setSourceOverride(7L, NovelReaderOverride())
        val writer = NovelReaderAutoScrollPreferenceWriter(prefs, sourceId = 7L, hasSourceOverride = true)

        writer.persistAutoScrollIntervalPreference(42)

        prefs.getSourceOverride(7L)?.autoScrollInterval shouldBe 42
        prefs.autoScrollInterval().get() shouldBe 10
    }
}
