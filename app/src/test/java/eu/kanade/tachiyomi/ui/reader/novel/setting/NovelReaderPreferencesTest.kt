package eu.kanade.tachiyomi.ui.reader.novel.setting

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class NovelReaderPreferencesTest {

    private fun createPrefs(): NovelReaderPreferences {
        return NovelReaderPreferences(
            preferenceStore = FakePreferenceStore(),
            json = Json { encodeDefaults = true },
        )
    }

    @Test
    fun `defaults include lnreader parity options`() {
        val prefs = createPrefs()

        prefs.preferWebViewRenderer().get() shouldBe true
        prefs.showScrollPercentage().get() shouldBe true
        prefs.showBatteryAndTime().get() shouldBe false
        prefs.verticalSeekbar().get() shouldBe true
        prefs.swipeToNextChapter().get() shouldBe false
        prefs.swipeToPrevChapter().get() shouldBe false
        prefs.tapToScroll().get() shouldBe false
        prefs.autoScroll().get() shouldBe false
        prefs.autoScrollInterval().get() shouldBe 10
        prefs.autoScrollOffset().get() shouldBe 0
        prefs.bionicReading().get() shouldBe false
        prefs.swipeGestures().get() shouldBe false
        prefs.customThemes().get() shouldBe emptyList()
    }

    @Test
    fun `enable source override copies new settings`() {
        val prefs = createPrefs()
        val sourceId = 123L

        prefs.showScrollPercentage().set(false)
        prefs.showBatteryAndTime().set(true)
        prefs.preferWebViewRenderer().set(false)
        prefs.verticalSeekbar().set(false)
        prefs.swipeToNextChapter().set(true)
        prefs.swipeToPrevChapter().set(true)
        prefs.tapToScroll().set(true)
        prefs.autoScroll().set(true)
        prefs.autoScrollInterval().set(7)
        prefs.autoScrollOffset().set(480)
        prefs.bionicReading().set(true)
        prefs.customThemes().set(
            listOf(
                NovelReaderColorTheme(backgroundColor = "#111111", textColor = "#eeeeee"),
            ),
        )

        prefs.enableSourceOverride(sourceId)
        val override = prefs.getSourceOverride(sourceId)

        override?.showScrollPercentage shouldBe false
        override?.showBatteryAndTime shouldBe true
        override?.preferWebViewRenderer shouldBe false
        override?.verticalSeekbar shouldBe false
        override?.swipeToNextChapter shouldBe true
        override?.swipeToPrevChapter shouldBe true
        override?.tapToScroll shouldBe true
        override?.autoScroll shouldBe true
        override?.autoScrollInterval shouldBe 7
        override?.autoScrollOffset shouldBe 480
        override?.bionicReading shouldBe true
        override?.customThemes shouldBe listOf(
            NovelReaderColorTheme(backgroundColor = "#111111", textColor = "#eeeeee"),
        )
    }

    @Test
    fun `resolve settings prioritizes source override for new fields`() {
        val prefs = createPrefs()
        val sourceId = 42L

        prefs.showScrollPercentage().set(true)
        prefs.showBatteryAndTime().set(false)
        prefs.preferWebViewRenderer().set(true)
        prefs.verticalSeekbar().set(true)
        prefs.swipeToNextChapter().set(false)
        prefs.swipeToPrevChapter().set(false)
        prefs.tapToScroll().set(false)
        prefs.autoScroll().set(false)
        prefs.autoScrollInterval().set(10)
        prefs.autoScrollOffset().set(0)
        prefs.bionicReading().set(false)
        prefs.customThemes().set(
            listOf(
                NovelReaderColorTheme(backgroundColor = "#f5f5fa", textColor = "#111111"),
            ),
        )

        prefs.setSourceOverride(
            sourceId,
            NovelReaderOverride(
                showScrollPercentage = false,
                showBatteryAndTime = true,
                preferWebViewRenderer = false,
                verticalSeekbar = false,
                swipeToNextChapter = true,
                swipeToPrevChapter = true,
                tapToScroll = true,
                autoScroll = true,
                autoScrollInterval = 3,
                autoScrollOffset = 240,
                bionicReading = true,
                customThemes = listOf(
                    NovelReaderColorTheme(backgroundColor = "#000000", textColor = "#ffffff"),
                ),
            ),
        )

        val settings = prefs.resolveSettings(sourceId)

        settings.showScrollPercentage shouldBe false
        settings.showBatteryAndTime shouldBe true
        settings.preferWebViewRenderer shouldBe false
        settings.verticalSeekbar shouldBe false
        settings.swipeToNextChapter shouldBe true
        settings.swipeToPrevChapter shouldBe true
        settings.tapToScroll shouldBe true
        settings.autoScroll shouldBe true
        settings.autoScrollInterval shouldBe 3
        settings.autoScrollOffset shouldBe 240
        settings.bionicReading shouldBe true
        settings.customThemes shouldBe listOf(
            NovelReaderColorTheme(backgroundColor = "#000000", textColor = "#ffffff"),
        )
    }

    private class FakePreferenceStore : PreferenceStore {
        private val strings = mutableMapOf<String, Preference<String>>()
        private val longs = mutableMapOf<String, Preference<Long>>()
        private val ints = mutableMapOf<String, Preference<Int>>()
        private val floats = mutableMapOf<String, Preference<Float>>()
        private val booleans = mutableMapOf<String, Preference<Boolean>>()
        private val stringSets = mutableMapOf<String, Preference<Set<String>>>()
        private val objects = mutableMapOf<String, Preference<Any>>()

        override fun getString(key: String, defaultValue: String): Preference<String> =
            strings.getOrPut(key) { FakePreference(key, defaultValue) }

        override fun getLong(key: String, defaultValue: Long): Preference<Long> =
            longs.getOrPut(key) { FakePreference(key, defaultValue) }

        override fun getInt(key: String, defaultValue: Int): Preference<Int> =
            ints.getOrPut(key) { FakePreference(key, defaultValue) }

        override fun getFloat(key: String, defaultValue: Float): Preference<Float> =
            floats.getOrPut(key) { FakePreference(key, defaultValue) }

        override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> =
            booleans.getOrPut(key) { FakePreference(key, defaultValue) }

        override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
            stringSets.getOrPut(key) { FakePreference(key, defaultValue) }

        @Suppress("UNCHECKED_CAST")
        override fun <T> getObject(
            key: String,
            defaultValue: T,
            serializer: (T) -> String,
            deserializer: (String) -> T,
        ): Preference<T> {
            return objects.getOrPut(key) { FakePreference(key, defaultValue as Any) } as Preference<T>
        }

        override fun getAll(): Map<String, *> {
            return emptyMap<String, Any>()
        }
    }

    private class FakePreference<T>(
        private val preferenceKey: String,
        defaultValue: T,
    ) : Preference<T> {
        private val state = MutableStateFlow(defaultValue)

        override fun key(): String = preferenceKey

        override fun get(): T = state.value

        override fun set(value: T) {
            state.value = value
        }

        override fun isSet(): Boolean = true

        override fun delete() = Unit

        override fun defaultValue(): T = state.value

        override fun changes(): Flow<T> = state

        override fun stateIn(scope: CoroutineScope) = state
    }
}
