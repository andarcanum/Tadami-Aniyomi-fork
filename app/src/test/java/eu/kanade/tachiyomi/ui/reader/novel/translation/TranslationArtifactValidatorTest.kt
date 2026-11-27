package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationStylePreset
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import tachiyomi.domain.translation.service.TranslationFragmentPlanner

class TranslationArtifactValidatorTest {
    private val source = listOf("One", "Two")
    private val settings = mockk<NovelReaderSettings>(relaxed = true).also {
        every { it.geminiEnabled } returns true
        // A relaxed mock would otherwise invent a profile/connection and take the profile wire path.
        every { it.translationProfile } returns null
        every { it.connectionProfile } returns null
        every { it.translationProvider } returns NovelTranslationProvider.GEMINI
        every { it.geminiModel } returns "gemini-3.5-flash-lite"
        every { it.geminiSourceLang } returns "English"
        every { it.geminiTargetLang } returns "Russian"
        every { it.geminiPromptMode } returns GeminiPromptMode.CLASSIC
        every { it.geminiStylePreset } returns NovelTranslationStylePreset.PROFESSIONAL
        every { it.geminiEnabledPromptModifiers } returns emptyList()
        every { it.geminiReasoningEffort } returns "minimal"
        every { it.geminiFrozenGenerationConfig } returns null
    }
    private fun artifact() = GeminiTranslationCacheEntry(
        chapterId = 1, translatedByIndex = mapOf(0 to "Первый", 1 to "Второй"), model = settings.geminiModel,
        sourceLang = "English", targetLang = "Russian", promptMode = GeminiPromptMode.CLASSIC,
        extractorVersion = NOVEL_TRANSLATION_EXTRACTOR_VERSION, sourceSegmentCount = 2,
        sourceFingerprint = TranslationFragmentPlanner.sourceFingerprint(source),
        generationFingerprint = settings.translationGenerationFingerprint(),
    )

    @Test
    fun `complete artifact requires actual source hash and exact source index coverage`() {
        val full = artifact()
        translationArtifactForSource(full, settings, "", source) shouldBe full
        translationArtifactForSource(
            full.copy(sourceSegmentCount = 1, translatedByIndex = mapOf(0 to "first")),
            settings,
            "",
            source,
        ) shouldBe
            null
        translationArtifactForSource(
            full.copy(translatedByIndex = mapOf(1 to "first", 2 to "second")),
            settings,
            "",
            source,
        ) shouldBe
            null
        translationArtifactForSource(full.copy(sourceFingerprint = ""), settings, "", source) shouldBe null
    }

    @Test
    fun `changed source with the same number of blocks invalidates the artifact`() {
        translationArtifactForSource(artifact(), settings, "", listOf("One", "Different")) shouldBe null
    }

    @Test
    fun `changed generation options invalidate the result without exposing credentials`() {
        val old = artifact()
        every { settings.geminiReasoningEffort } returns "high"
        translationArtifactForSource(old, settings, "", source) shouldBe null
    }

    @Test
    fun `same raw settings but different frozen wire configurations do not reuse an artifact`() {
        val low = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"maxOutputTokens":8192,"thinkingConfig":{"thinkingLevel":"low"}}""",
        ) as kotlinx.serialization.json.JsonObject
        val high = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"thinkingConfig":{"thinkingLevel":"high"},"maxOutputTokens":16384}""",
        ) as kotlinx.serialization.json.JsonObject
        every { settings.geminiFrozenGenerationConfig } returns low
        val old = artifact()
        every { settings.geminiFrozenGenerationConfig } returns high
        (settings.translationGenerationFingerprint() == old.generationFingerprint) shouldBe false
        translationArtifactForSource(old, settings, "", source) shouldBe null
    }

    @Test
    fun `legacy complete geometry can be displayed but cannot become a verified receipt`() {
        val legacy = artifact().copy(sourceFingerprint = "", generationFingerprint = "", sourceSegmentCount = 0)
        val display = translationArtifactForSource(legacy, settings, "", source)
        display?.sourceFingerprint shouldBe ""
        display?.generationFingerprint shouldBe ""
        display?.sourceSegmentCount shouldBe 2
        translationArtifactForSource(legacy, settings, "", source, allowLegacyDisplay = false) shouldBe null
        translationArtifactForSource(
            legacy.copy(translatedByIndex = mapOf(0 to "partial")),
            settings,
            "",
            source,
        ) shouldBe
            null
    }

    @Test
    fun `adopting book B never reads resident A with the same source block count`() = kotlinx.coroutines.test.runTest {
        var residentReads = 0
        val loaded = mutableListOf<Long>()
        val requested = translationSourceForChapter(2, 1, true, {
            residentReads++
            listOf("A one", "A two")
        }) {
            loaded += it
            source
        }
        requested shouldBe source
        residentReads shouldBe 0
        loaded shouldBe listOf(2L)
        translationArtifactForSource(artifact(), settings, "", requested) shouldBe artifact()
        translationArtifactForSource(artifact(), settings, "", listOf("A one", "A two")) shouldBe null
    }

    @Test
    fun `native source shortcut is only valid for the stable content model owner`() = kotlinx.coroutines.test.runTest {
        translationSourceForChapter(2, 1, false, { listOf("A") }, { listOf("B") }) shouldBe listOf("B")
        translationSourceForChapter(1, 1, false, { listOf("A") }, { error("Resident owner should not load") }) shouldBe
            listOf("A")
    }

    @Test
    fun `cache disabled accepts a validated session result not an unusable completion flag`() {
        val result = artifact()
        every { settings.geminiDisableCache } returns true
        translationArtifactForSource(result, settings, "", source) shouldBe result
    }
}
