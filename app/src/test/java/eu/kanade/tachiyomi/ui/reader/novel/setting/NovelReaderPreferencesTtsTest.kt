package eu.kanade.tachiyomi.ui.reader.novel.setting

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class NovelReaderPreferencesTtsTest {

    private fun createPrefs(): NovelReaderPreferences {
        return NovelReaderPreferences(
            preferenceStore = FakePreferenceStore(),
            json = Json { encodeDefaults = true },
        )
    }

    @Test
    fun `defaults include tts settings`() {
        val prefs = createPrefs()

        prefs.ttsEnabled().get() shouldBe false
        prefs.ttsEnginePackage().get() shouldBe ""
        prefs.ttsVoiceId().get() shouldBe ""
        prefs.ttsLocaleTag().get() shouldBe ""
        prefs.ttsSpeechRate().get() shouldBe 1f
        prefs.ttsPitch().get() shouldBe 1f
        prefs.ttsHighlightMode().get() shouldBe NovelTtsHighlightMode.AUTO
        prefs.ttsWordHighlightEnabled().get() shouldBe true
        prefs.ttsAutoAdvanceChapter().get() shouldBe false
        prefs.ttsFollowAlong().get() shouldBe true
        prefs.ttsPauseOnManualNavigation().get() shouldBe true
        prefs.ttsKeepScreenOnDuringPlayback().get() shouldBe false
        prefs.ttsPreferTranslatedText().get() shouldBe false
        prefs.ttsReadChapterTitle().get() shouldBe true
    }

    @Test
    fun `resolved settings include tts fields`() {
        val prefs = createPrefs()

        prefs.ttsEnabled().set(true)
        prefs.ttsEnginePackage().set("com.example.tts")
        prefs.ttsVoiceId().set("voice-id")
        prefs.ttsLocaleTag().set("ru-RU")
        prefs.ttsSpeechRate().set(1.15f)
        prefs.ttsPitch().set(0.95f)
        prefs.ttsHighlightMode().set(NovelTtsHighlightMode.ESTIMATED)
        prefs.ttsWordHighlightEnabled().set(false)
        prefs.ttsAutoAdvanceChapter().set(true)
        prefs.ttsFollowAlong().set(false)
        prefs.ttsPauseOnManualNavigation().set(false)
        prefs.ttsKeepScreenOnDuringPlayback().set(true)
        prefs.ttsPreferTranslatedText().set(true)
        prefs.ttsReadChapterTitle().set(false)

        val settings = prefs.resolveSettings(sourceId = 1L)

        settings.ttsEnabled shouldBe true
        settings.ttsEnginePackage shouldBe "com.example.tts"
        settings.ttsVoiceId shouldBe "voice-id"
        settings.ttsLocaleTag shouldBe "ru-RU"
        settings.ttsSpeechRate shouldBe 1.15f
        settings.ttsPitch shouldBe 0.95f
        settings.ttsHighlightMode shouldBe NovelTtsHighlightMode.ESTIMATED
        settings.ttsWordHighlightEnabled shouldBe false
        settings.ttsAutoAdvanceChapter shouldBe true
        settings.ttsFollowAlong shouldBe false
        settings.ttsPauseOnManualNavigation shouldBe false
        settings.ttsKeepScreenOnDuringPlayback shouldBe true
        settings.ttsPreferTranslatedText shouldBe true
        settings.ttsReadChapterTitle shouldBe false
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

        override fun stateIn(scope: CoroutineScope) = state
    }
}
