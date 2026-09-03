package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationStylePreset
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class NovelReaderTranslationDiskCacheTest {
    @TempDir
    lateinit var tempDir: File

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun `has and chapter ids ignore invalid cache files`() {
        File(tempDir, "7.json").writeText("{invalid", Charsets.UTF_8)
        val cache = NovelReaderTranslationDiskCache(tempDir, json)

        cache.has(7L) shouldBe false
        cache.chapterIds() shouldBe emptySet()
        File(tempDir, "7.json").exists() shouldBe false
    }

    @Test
    fun `has and chapter ids ignore empty translation cache entries`() {
        val cache = NovelReaderTranslationDiskCache(tempDir, json)
        cache.put(cacheEntry(chapterId = 8L, translatedByIndex = emptyMap()))

        cache.has(8L) shouldBe false
        cache.chapterIds() shouldBe emptySet()
    }

    @Test
    fun `chapter ids only include entries matching current translation requirements`() {
        val cache = NovelReaderTranslationDiskCache(tempDir, json)
        val requirements = requirements()
        cache.put(cacheEntry(chapterId = 9L))
        cache.put(cacheEntry(chapterId = 10L, model = "different"))

        cache.has(9L, requirements) shouldBe true
        cache.has(10L, requirements) shouldBe false
        cache.chapterIds(requirements) shouldContainExactly setOf(9L)
    }

    @Test
    fun `has with requirements rejects incomplete and legacy entries`() {
        val cache = NovelReaderTranslationDiskCache(tempDir, json)
        val requirements = requirements()

        // Relaxed-mode partial result: fewer translations than source segments - a later batch
        // must be allowed to heal it, so skip-checks treat it as not-cached.
        cache.put(
            cacheEntry(chapterId = 20L).copy(
                translatedByIndex = mapOf(0 to "a", 1 to "b"),
                sourceSegmentCount = 5,
            ),
        )
        // Legacy extractor version.
        cache.put(cacheEntry(chapterId = 21L).copy(extractorVersion = 0))
        // Complete entry with the current version.
        cache.put(cacheEntry(chapterId = 22L).copy(sourceSegmentCount = 1))

        cache.has(20L, requirements) shouldBe false
        cache.has(21L, requirements) shouldBe false
        cache.has(22L, requirements) shouldBe true
    }

    @Test
    fun `chapter ids fallback ignores entries without translated content`() {
        val cache = NovelReaderTranslationDiskCache(tempDir, json)
        cache.put(cacheEntry(chapterId = 30L, translatedByIndex = emptyMap()))
        cache.put(cacheEntry(chapterId = 31L))

        // The background index is not ready right after put(), so this exercises the file-scan
        // fallback, which used to light up badges for content-less entries (targetLang matched).
        cache.chapterIds(listOf(30L, 31L), "Russian") shouldBe setOf(31L)
    }

    @Test
    fun `has with requirements rejects entries from different replace rules`() {
        val cache = NovelReaderTranslationDiskCache(tempDir, json)
        cache.put(cacheEntry(chapterId = 40L).copy(replaceRulesFingerprint = "rule-a"))

        cache.has(40L, requirements()) shouldBe false
    }

    private fun requirements(): NovelReaderTranslationCacheRequirements {
        return NovelReaderTranslationCacheRequirements(
            geminiEnabled = true,
            geminiDisableCache = false,
            translationProvider = NovelTranslationProvider.GEMINI,
            modelId = "gemini-3.1-flash-lite-preview",
            sourceLang = "English",
            targetLang = "Russian",
            promptMode = GeminiPromptMode.ADULT_18,
            stylePreset = NovelTranslationStylePreset.PROFESSIONAL,
            extractorVersion = NOVEL_TRANSLATION_EXTRACTOR_VERSION,
        )
    }

    private fun cacheEntry(
        chapterId: Long = 9L,
        translatedByIndex: Map<Int, String> = mapOf(0 to "hello"),
        model: String = "gemini-3.1-flash-lite-preview",
    ): GeminiTranslationCacheEntry {
        return GeminiTranslationCacheEntry(
            chapterId = chapterId,
            translatedByIndex = translatedByIndex,
            provider = NovelTranslationProvider.GEMINI,
            model = model,
            sourceLang = "English",
            targetLang = "Russian",
            promptMode = GeminiPromptMode.ADULT_18,
            stylePreset = NovelTranslationStylePreset.PROFESSIONAL,
            extractorVersion = NOVEL_TRANSLATION_EXTRACTOR_VERSION,
        )
    }
}
