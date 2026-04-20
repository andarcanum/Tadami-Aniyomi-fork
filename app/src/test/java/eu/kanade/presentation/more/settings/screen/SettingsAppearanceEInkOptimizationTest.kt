package eu.kanade.presentation.more.settings.screen

import eu.kanade.domain.ui.model.EInkProfile
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import kotlinx.serialization.json.Json

class SettingsAppearanceEInkOptimizationTest {

    @Test
    fun `monochrome profile seeds e ink friendly reader defaults`() {
        val store = FakePreferenceStore()
        val readerPreferences = ReaderPreferences(store)
        val novelReaderPreferences = NovelReaderPreferences(
            store,
            Json {},
        )

        seedEInkAutoOptimizationDefaults(
            eInkProfile = EInkProfile.MONOCHROME,
            readerPreferences = readerPreferences,
            novelReaderPreferences = novelReaderPreferences,
        )

        readerPreferences.pageTransitions().get() shouldBe false
        readerPreferences.flashOnPageChange().get() shouldBe true
        readerPreferences.grayscale().get() shouldBe true
        readerPreferences.invertedColors().get() shouldBe false
        readerPreferences.keepScreenOn().get() shouldBe true
        novelReaderPreferences.fullScreenMode().get() shouldBe true
        novelReaderPreferences.keepScreenOn().get() shouldBe true
    }

    @Test
    fun `color profile keeps grayscale off while seeding low motion defaults`() {
        val store = FakePreferenceStore()
        val readerPreferences = ReaderPreferences(store)
        val novelReaderPreferences = NovelReaderPreferences(
            store,
            Json {},
        )

        seedEInkAutoOptimizationDefaults(
            eInkProfile = EInkProfile.COLOR,
            readerPreferences = readerPreferences,
            novelReaderPreferences = novelReaderPreferences,
        )

        readerPreferences.pageTransitions().get() shouldBe false
        readerPreferences.flashOnPageChange().get() shouldBe true
        readerPreferences.grayscale().get() shouldBe false
        novelReaderPreferences.keepScreenOn().get() shouldBe true
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

        override fun getAll(): Map<String, *> = emptyMap<String, Any>()
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

        override fun stateIn(scope: CoroutineScope): StateFlow<T> = state
    }
}
