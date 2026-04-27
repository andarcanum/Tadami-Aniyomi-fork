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
        )
    }
}
