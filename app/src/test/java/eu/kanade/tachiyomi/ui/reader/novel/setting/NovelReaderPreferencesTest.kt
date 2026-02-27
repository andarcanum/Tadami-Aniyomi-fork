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

        prefs.textAlign().get() shouldBe TextAlign.SOURCE
        prefs.preferWebViewRenderer().get() shouldBe false
        prefs.richNativeRendererExperimental().get() shouldBe true
        prefs.forceParagraphIndent().get() shouldBe true
        prefs.preserveSourceTextAlignInNative().get() shouldBe true
        prefs.showScrollPercentage().get() shouldBe true
        prefs.showBatteryAndTime().get() shouldBe false
        prefs.verticalSeekbar().get() shouldBe true
        prefs.swipeToNextChapter().get() shouldBe false
        prefs.swipeToPrevChapter().get() shouldBe false
        prefs.tapToScroll().get() shouldBe false
        prefs.autoScroll().get() shouldBe false
        prefs.autoScrollInterval().get() shouldBe 10
        prefs.autoScrollOffset().get() shouldBe 0
        prefs.prefetchNextChapter().get() shouldBe false
        prefs.cacheReadChapters().get() shouldBe true
        prefs.cacheReadChaptersUnlimited().get() shouldBe false
        prefs.bionicReading().get() shouldBe false
        prefs.swipeGestures().get() shouldBe false
        prefs.customThemes().get() shouldBe emptyList()
        prefs.geminiEnabled().get() shouldBe false
        prefs.geminiPromptMode().get() shouldBe GeminiPromptMode.ADULT_18
        prefs.geminiModel().get() shouldBe "gemini-2.5-flash"
        prefs.geminiTemperature().get() shouldBe 0.7f
        prefs.geminiReasoningEffort().get() shouldBe "minimal"
        prefs.geminiBudgetTokens().get() shouldBe 8192
        prefs.geminiTopP().get() shouldBe 0.95f
        prefs.geminiTopK().get() shouldBe 40
        prefs.geminiAutoTranslateEnglishSource().get() shouldBe false
        prefs.geminiPrefetchNextChapterTranslation().get() shouldBe false
        prefs.translationProvider().get() shouldBe NovelTranslationProvider.GEMINI
        prefs.airforceBaseUrl().get() shouldBe "https://api.airforce"
        prefs.airforceApiKey().get() shouldBe ""
        prefs.airforceModel().get() shouldBe ""
        prefs.openRouterBaseUrl().get() shouldBe "https://openrouter.ai/api/v1"
        prefs.openRouterApiKey().get() shouldBe ""
        prefs.openRouterModel().get() shouldBe ""
    }

    @Test
    fun `enable source override copies new settings`() {
        val prefs = createPrefs()
        val sourceId = 123L

        prefs.showScrollPercentage().set(false)
        prefs.showBatteryAndTime().set(true)
        prefs.preferWebViewRenderer().set(false)
        prefs.richNativeRendererExperimental().set(true)
        prefs.forceParagraphIndent().set(true)
        prefs.preserveSourceTextAlignInNative().set(false)
        prefs.verticalSeekbar().set(false)
        prefs.swipeToNextChapter().set(true)
        prefs.swipeToPrevChapter().set(true)
        prefs.tapToScroll().set(true)
        prefs.autoScroll().set(true)
        prefs.autoScrollInterval().set(7)
        prefs.autoScrollOffset().set(480)
        prefs.prefetchNextChapter().set(true)
        prefs.bionicReading().set(true)
        prefs.geminiApiKey().set("test-key")
        prefs.geminiModel().set("gemini-2.5-pro")
        prefs.geminiBatchSize().set(30)
        prefs.geminiConcurrency().set(2)
        prefs.geminiDisableCache().set(true)
        prefs.geminiRelaxedMode().set(false)
        prefs.geminiReasoningEffort().set("high")
        prefs.geminiBudgetTokens().set(2048)
        prefs.geminiTemperature().set(0.7f)
        prefs.geminiTopP().set(0.85f)
        prefs.geminiTopK().set(64)
        prefs.geminiSourceLang().set("English")
        prefs.geminiTargetLang().set("Russian")
        prefs.geminiPromptMode().set(GeminiPromptMode.ADULT_18)
        prefs.geminiPromptModifiers().set("modifiers")
        prefs.geminiAutoTranslateEnglishSource().set(true)
        prefs.geminiPrefetchNextChapterTranslation().set(true)
        prefs.translationProvider().set(NovelTranslationProvider.AIRFORCE)
        prefs.airforceBaseUrl().set("https://api.airforce")
        prefs.airforceApiKey().set("airforce-key")
        prefs.airforceModel().set("openai/gpt-4.1-mini")
        prefs.openRouterBaseUrl().set("https://openrouter.ai/api/v1")
        prefs.openRouterApiKey().set("openrouter-key")
        prefs.openRouterModel().set("google/gemma-3-27b-it:free")
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
        override?.richNativeRendererExperimental shouldBe true
        override?.forceParagraphIndent shouldBe true
        override?.preserveSourceTextAlignInNative shouldBe false
        override?.verticalSeekbar shouldBe false
        override?.swipeToNextChapter shouldBe true
        override?.swipeToPrevChapter shouldBe true
        override?.tapToScroll shouldBe true
        override?.autoScroll shouldBe true
        override?.autoScrollInterval shouldBe 7
        override?.autoScrollOffset shouldBe 480
        override?.prefetchNextChapter shouldBe true
        override?.bionicReading shouldBe true
        override?.geminiApiKey shouldBe "test-key"
        override?.geminiModel shouldBe "gemini-2.5-pro"
        override?.geminiBatchSize shouldBe 30
        override?.geminiConcurrency shouldBe 2
        override?.geminiDisableCache shouldBe true
        override?.geminiRelaxedMode shouldBe false
        override?.geminiReasoningEffort shouldBe "high"
        override?.geminiBudgetTokens shouldBe 2048
        override?.geminiTemperature shouldBe 0.7f
        override?.geminiTopP shouldBe 0.85f
        override?.geminiTopK shouldBe 64
        override?.geminiSourceLang shouldBe "English"
        override?.geminiTargetLang shouldBe "Russian"
        override?.geminiPromptMode shouldBe GeminiPromptMode.ADULT_18
        override?.geminiPromptModifiers shouldBe "modifiers"
        override?.geminiAutoTranslateEnglishSource shouldBe true
        override?.geminiPrefetchNextChapterTranslation shouldBe true
        override?.translationProvider shouldBe NovelTranslationProvider.AIRFORCE
        override?.airforceBaseUrl shouldBe "https://api.airforce"
        override?.airforceApiKey shouldBe "airforce-key"
        override?.airforceModel shouldBe "openai/gpt-4.1-mini"
        override?.openRouterBaseUrl shouldBe "https://openrouter.ai/api/v1"
        override?.openRouterApiKey shouldBe "openrouter-key"
        override?.openRouterModel shouldBe "google/gemma-3-27b-it:free"
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
        prefs.richNativeRendererExperimental().set(false)
        prefs.forceParagraphIndent().set(true)
        prefs.preserveSourceTextAlignInNative().set(true)
        prefs.verticalSeekbar().set(true)
        prefs.swipeToNextChapter().set(false)
        prefs.swipeToPrevChapter().set(false)
        prefs.tapToScroll().set(false)
        prefs.autoScroll().set(false)
        prefs.autoScrollInterval().set(10)
        prefs.autoScrollOffset().set(0)
        prefs.prefetchNextChapter().set(false)
        prefs.bionicReading().set(false)
        prefs.geminiApiKey().set("")
        prefs.geminiModel().set("gemini-2.5-flash")
        prefs.geminiBatchSize().set(40)
        prefs.geminiConcurrency().set(2)
        prefs.geminiDisableCache().set(false)
        prefs.geminiRelaxedMode().set(true)
        prefs.geminiReasoningEffort().set("low")
        prefs.geminiBudgetTokens().set(4096)
        prefs.geminiTemperature().set(0.9f)
        prefs.geminiTopP().set(0.95f)
        prefs.geminiTopK().set(40)
        prefs.geminiSourceLang().set("English")
        prefs.geminiTargetLang().set("Russian")
        prefs.geminiPromptMode().set(GeminiPromptMode.CLASSIC)
        prefs.geminiPromptModifiers().set("")
        prefs.geminiAutoTranslateEnglishSource().set(false)
        prefs.geminiPrefetchNextChapterTranslation().set(false)
        prefs.translationProvider().set(NovelTranslationProvider.GEMINI)
        prefs.airforceBaseUrl().set("https://api.airforce")
        prefs.airforceApiKey().set("")
        prefs.airforceModel().set("")
        prefs.openRouterBaseUrl().set("https://openrouter.ai/api/v1")
        prefs.openRouterApiKey().set("")
        prefs.openRouterModel().set("")
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
                richNativeRendererExperimental = true,
                forceParagraphIndent = false,
                preserveSourceTextAlignInNative = false,
                verticalSeekbar = false,
                swipeToNextChapter = true,
                swipeToPrevChapter = true,
                tapToScroll = true,
                autoScroll = true,
                autoScrollInterval = 3,
                autoScrollOffset = 240,
                prefetchNextChapter = true,
                bionicReading = true,
                geminiApiKey = "override-key",
                geminiModel = "gemini-2.5-pro",
                geminiBatchSize = 20,
                geminiConcurrency = 1,
                geminiDisableCache = true,
                geminiRelaxedMode = false,
                geminiReasoningEffort = "medium",
                geminiBudgetTokens = 1024,
                geminiTemperature = 0.6f,
                geminiTopP = 0.8f,
                geminiTopK = 50,
                geminiSourceLang = "Japanese",
                geminiTargetLang = "Russian",
                geminiPromptMode = GeminiPromptMode.ADULT_18,
                geminiPromptModifiers = "override-mod",
                geminiAutoTranslateEnglishSource = true,
                geminiPrefetchNextChapterTranslation = true,
                translationProvider = NovelTranslationProvider.AIRFORCE,
                airforceBaseUrl = "https://api.airforce",
                airforceApiKey = "airforce-key",
                airforceModel = "openai/gpt-4.1-mini",
                openRouterBaseUrl = "https://openrouter.ai/api/v1",
                openRouterApiKey = "openrouter-key",
                openRouterModel = "google/gemma-3-27b-it:free",
                customThemes = listOf(
                    NovelReaderColorTheme(backgroundColor = "#000000", textColor = "#ffffff"),
                ),
            ),
        )

        val settings = prefs.resolveSettings(sourceId)

        settings.showScrollPercentage shouldBe false
        settings.showBatteryAndTime shouldBe true
        settings.preferWebViewRenderer shouldBe false
        settings.richNativeRendererExperimental shouldBe true
        settings.forceParagraphIndent shouldBe false
        settings.preserveSourceTextAlignInNative shouldBe false
        settings.verticalSeekbar shouldBe false
        settings.swipeToNextChapter shouldBe true
        settings.swipeToPrevChapter shouldBe true
        settings.tapToScroll shouldBe true
        settings.autoScroll shouldBe true
        settings.autoScrollInterval shouldBe 3
        settings.autoScrollOffset shouldBe 240
        settings.prefetchNextChapter shouldBe true
        settings.bionicReading shouldBe true
        settings.geminiApiKey shouldBe "override-key"
        settings.geminiModel shouldBe "gemini-2.5-pro"
        settings.geminiBatchSize shouldBe 20
        settings.geminiConcurrency shouldBe 1
        settings.geminiDisableCache shouldBe true
        settings.geminiRelaxedMode shouldBe false
        settings.geminiReasoningEffort shouldBe "medium"
        settings.geminiBudgetTokens shouldBe 1024
        settings.geminiTemperature shouldBe 0.6f
        settings.geminiTopP shouldBe 0.8f
        settings.geminiTopK shouldBe 50
        settings.geminiSourceLang shouldBe "Japanese"
        settings.geminiTargetLang shouldBe "Russian"
        settings.geminiPromptMode shouldBe GeminiPromptMode.ADULT_18
        settings.geminiPromptModifiers shouldBe "override-mod"
        settings.geminiAutoTranslateEnglishSource shouldBe true
        settings.geminiPrefetchNextChapterTranslation shouldBe true
        settings.translationProvider shouldBe NovelTranslationProvider.AIRFORCE
        settings.airforceBaseUrl shouldBe "https://api.airforce"
        settings.airforceApiKey shouldBe "airforce-key"
        settings.airforceModel shouldBe "openai/gpt-4.1-mini"
        settings.openRouterBaseUrl shouldBe "https://openrouter.ai/api/v1"
        settings.openRouterApiKey shouldBe "openrouter-key"
        settings.openRouterModel shouldBe "google/gemma-3-27b-it:free"
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
