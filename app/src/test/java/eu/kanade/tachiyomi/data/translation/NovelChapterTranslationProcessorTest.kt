package eu.kanade.tachiyomi.data.translation

import android.app.Application
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.translation.AirforceTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.DeepSeekTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.OpenRouterTranslationService
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import org.junit.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class NovelChapterTranslationProcessorTest {

    private val application = mockk<Application>(relaxed = true)
    private val geminiTranslationService = mockk<GeminiTranslationService>()
    private val airforceTranslationService = mockk<AirforceTranslationService>(relaxed = true)
    private val openRouterTranslationService = mockk<OpenRouterTranslationService>(relaxed = true)
    private val deepSeekTranslationService = mockk<DeepSeekTranslationService>(relaxed = true)

    @Test
    fun `translates segments with configured provider`() {
        runBlocking {
            val processor = NovelChapterTranslationProcessor(
                application = application,
                geminiTranslationService = geminiTranslationService,
                airforceTranslationService = airforceTranslationService,
                openRouterTranslationService = openRouterTranslationService,
                deepSeekTranslationService = deepSeekTranslationService,
            )
            val settings = createNovelReaderPreferences().resolveSettings(sourceId = 1L)

            coEvery {
                geminiTranslationService.translateBatch(
                    segments = listOf("Hello", "World"),
                    params = any(),
                    onLog = any(),
                )
            } returns listOf("Привет", "Мир")

            val translated = processor.translateSegments(
                segments = listOf("Hello", "World"),
                settings = settings,
            )

            translated shouldBe mapOf(
                0 to "Привет",
                1 to "Мир",
            )

            coVerify(exactly = 1) {
                geminiTranslationService.translateBatch(
                    segments = listOf("Hello", "World"),
                    params = any(),
                    onLog = any(),
                )
            }
        }
    }

    private fun createNovelReaderPreferences(): NovelReaderPreferences {
        val prefs = NovelReaderPreferences(
            preferenceStore = FakePreferenceStore(),
            json = Json { encodeDefaults = true },
        )
        prefs.geminiEnabled().set(true)
        prefs.geminiApiKey().set("test-key")
        prefs.translationProvider().set(NovelTranslationProvider.GEMINI)
        prefs.geminiSourceLang().set("English")
        prefs.geminiTargetLang().set("Russian")
        prefs.geminiBatchSize().set(2)
        prefs.geminiConcurrency().set(1)
        return prefs
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
