package eu.kanade.presentation.reader

import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class DisplayRefreshHostTest {

    @Test
    fun `flash respects configured interval`() {
        val prefs = readerPreferences(flashInterval = 2)

        val host = DisplayRefreshHost(readerPreferences = prefs)

        host.currentDisplayRefresh shouldBe false

        host.flash()
        host.currentDisplayRefresh shouldBe true

        host.currentDisplayRefresh = false
        host.flash()
        host.currentDisplayRefresh shouldBe false

        host.flash()
        host.currentDisplayRefresh shouldBe true
    }

    @Test
    fun `setInterval resets flash cadence`() {
        val prefs = readerPreferences(flashInterval = 3)

        val host = DisplayRefreshHost(readerPreferences = prefs)

        host.flash()
        host.currentDisplayRefresh = false
        host.flash()
        host.currentDisplayRefresh shouldBe false

        host.setInterval(2)
        host.flash()
        host.currentDisplayRefresh shouldBe true
    }

    private fun readerPreferences(flashInterval: Int): ReaderPreferences {
        return ReaderPreferences(
            InMemoryPreferenceStore(
                sequenceOf(
                    InMemoryPreferenceStore.InMemoryPreference(
                        key = "pref_reader_flash_interval",
                        data = flashInterval,
                        defaultValue = 1,
                    ),
                ),
            ),
        )
    }
}
