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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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

    @Test
    fun `complete means exact nonblank source index coverage not map size`() {
        val entry = cacheEntry(chapterId = 41L).copy(sourceSegmentCount = 2)
        entry.copy(translatedByIndex = mapOf(0 to "a", 2 to "b")).isTranslationComplete shouldBe false
        entry.copy(translatedByIndex = mapOf(0 to "a", 1 to " ")).isTranslationComplete shouldBe false
        entry.copy(translatedByIndex = mapOf(0 to "a", 1 to "b")).isTranslationComplete shouldBe true
    }

    @Test
    fun `cache write failure propagates instead of pretending publication succeeded`() {
        val blocked = File(tempDir, "blocked").apply { writeText("not a directory") }
        val cache = NovelReaderTranslationDiskCache(blocked, json)
        runCatching { cache.put(cacheEntry(chapterId = 42L)) }.isFailure shouldBe true
    }

    @Test
    fun `partial replacement never overwrites the last complete artifact`() {
        val cache = NovelReaderTranslationDiskCache(tempDir, json)
        cache.put(cacheEntry(chapterId = 43L))
        cache.put(cacheEntry(chapterId = 43L).copy(translatedByIndex = mapOf(0 to "partial"), sourceSegmentCount = 2))
        cache.get(43L)?.translatedByIndex shouldBe mapOf(0 to "hello")
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
            sourceSegmentCount = translatedByIndex.size,
        )
    }

    @Test
    fun `has by targetLang and chapterIds use rebuilt index and meta sidecar`() {
        val writer = NovelReaderTranslationDiskCache(tempDir, json)
        writer.put(cacheEntry(chapterId = 11L, translatedByIndex = mapOf(0 to "hello")))
        writer.put(cacheEntry(chapterId = 12L, translatedByIndex = mapOf(0 to "partial")).copy(sourceSegmentCount = 2))
        writer.put(cacheEntry(chapterId = 13L, translatedByIndex = mapOf(0 to "   ")))
        writer.put(cacheEntry(chapterId = 14L, translatedByIndex = mapOf(0 to "hola")).copy(targetLang = "Spanish"))
        writer.put(cacheEntry(chapterId = 99L, translatedByIndex = mapOf(0 to "sentinel")))

        val warmed = NovelReaderTranslationDiskCache(tempDir, json)
        val meta11 = File(tempDir, "11.meta")
        val deadline1 = System.nanoTime() + 5_000_000_000L
        while (!meta11.isFile && System.nanoTime() < deadline1) {
            warmed.has(11L, "Russian")
            Thread.sleep(10)
        }
        meta11.isFile shouldBe true

        warmed.has(11L, "Russian") shouldBe true
        warmed.has(12L, "Russian") shouldBe false
        warmed.has(13L, "Russian") shouldBe false
        warmed.has(14L, "Russian") shouldBe false
        warmed.chapterIds(listOf(11L, 12L, 13L, 14L), "Russian") shouldBe setOf(11L)

        // Corrupt 11.json while keeping 11.meta intact; after 99.meta proves rebuildIndex completed,
        // a new cache must answer has/chapterIds from 11.meta without re-parsing 11.json.
        File(tempDir, "99.meta").delete()
        File(tempDir, "11.json").writeText("{corrupted-json", Charsets.UTF_8)
        val fromMeta = NovelReaderTranslationDiskCache(tempDir, json)
        val meta99 = File(tempDir, "99.meta")
        val deadline2 = System.nanoTime() + 5_000_000_000L
        while (!meta99.isFile && System.nanoTime() < deadline2) {
            fromMeta.has(99L, "Russian")
            Thread.sleep(10)
        }
        meta99.isFile shouldBe true

        fromMeta.has(11L, "Russian") shouldBe true
        fromMeta.chapterIds(listOf(11L, 12L, 13L, 14L), "Russian") shouldBe setOf(11L)
        File(tempDir, "11.json").exists() shouldBe true
    }

    @Test
    fun `legacy meta without current schemaVersion refreshes from json`() {
        val writer = NovelReaderTranslationDiskCache(tempDir, json)
        writer.put(cacheEntry(chapterId = 15L, translatedByIndex = mapOf(0 to "privet")))
        val meta15 = File(tempDir, "15.meta")
        meta15.writeText(
            """
            {
              "targetLang": "Russian",
              "provider": "GEMINI",
              "model": "gemini-3.1-flash-lite-preview",
              "sourceLang": "English",
              "promptMode": "ADULT_18",
              "stylePreset": "PROFESSIONAL",
              "hasTranslatedContent": false,
              "extractorVersion": 0,
              "promptModifiersFingerprint": "",
              "replaceRulesFingerprint": "",
              "sourceSegmentCount": 0,
              "translatedCount": 0
            }
            """.trimIndent(),
            Charsets.UTF_8,
        )

        val reader = NovelReaderTranslationDiskCache(tempDir, json)
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!meta15.readText(Charsets.UTF_8).contains("\"schemaVersion\":1") && System.nanoTime() < deadline) {
            reader.has(15L, "Russian")
            Thread.sleep(10)
        }

        meta15.readText(Charsets.UTF_8).contains("\"schemaVersion\":1") shouldBe true
        reader.has(15L, "Russian") shouldBe true
    }

    @Test
    fun `concurrent put during rebuildIndex does not drop newly added chapterId`() {
        val writer = NovelReaderTranslationDiskCache(tempDir, json)
        writer.put(cacheEntry(chapterId = 1L, translatedByIndex = mapOf(0 to "one")))

        val scanFinished = CountDownLatch(1)
        val putFinished = CountDownLatch(1)
        val cache = NovelReaderTranslationDiskCache(tempDir, json)
        cache.onRebuildScanForTest = {
            scanFinished.countDown()
            putFinished.await(5, TimeUnit.SECONDS)
        }

        // Start background rebuildIndex (sees only chapter 1).
        cache.has(1L) shouldBe true
        scanFinished.await(5, TimeUnit.SECONDS) shouldBe true

        // Publish chapter 2 while rebuildIndex is between scan and commit.
        cache.onRebuildScanForTest = null
        cache.put(cacheEntry(chapterId = 2L, translatedByIndex = mapOf(0 to "two")))
        putFinished.countDown()

        // Allow the subsequent rebuild to finish (1.meta is materialized once indexBuilt=true)
        // and verify chapter 2 is visible both via has() and chapterIds().
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!File(tempDir, "1.meta").isFile && System.nanoTime() < deadline) {
            cache.has(2L) shouldBe true
            cache.chapterIds() shouldContainExactly setOf(1L, 2L)
            Thread.sleep(10)
        }
        File(tempDir, "1.meta").isFile shouldBe true

        cache.has(2L) shouldBe true
        cache.chapterIds() shouldContainExactly setOf(1L, 2L)
    }

    @Test
    fun `trimToTargetBytes preserves tmp files and removes evicted chapters from index`() {
        val writer = NovelReaderTranslationDiskCache(tempDir, json)
        writer.put(cacheEntry(chapterId = 1L, translatedByIndex = mapOf(0 to "oldest")))
        writer.put(cacheEntry(chapterId = 2L, translatedByIndex = mapOf(0 to "newest")))

        // Warm index + sidecar meta files from disk in a fresh cache instance.
        val cache = NovelReaderTranslationDiskCache(tempDir, json)
        val deadline = System.nanoTime() + 5_000_000_000L
        while ((!File(tempDir, "1.meta").isFile || !File(tempDir, "2.meta").isFile) && System.nanoTime() < deadline) {
            cache.chapterIds()
            Thread.sleep(10)
        }
        File(tempDir, "1.meta").isFile shouldBe true
        File(tempDir, "2.meta").isFile shouldBe true

        val json1 = File(tempDir, "1.json")
        val meta1 = File(tempDir, "1.meta")
        val json2 = File(tempDir, "2.json")
        val meta2 = File(tempDir, "2.meta")
        json1.setLastModified(1_000L)
        meta1.setLastModified(1_000L)
        json2.setLastModified(10_000L)
        meta2.setLastModified(10_000L)

        // Simulate an in-flight temporary file with oldest mtime; trim must never delete .tmp files.
        val tmpFile = File(tempDir, ".99-in-flight.tmp").apply {
            writeText("partial", Charsets.UTF_8)
            setLastModified(500L)
        }

        cache.trimToTargetBytes(targetBytes = json2.length() + meta2.length() + 10L)

        tmpFile.exists() shouldBe true
        json1.exists() shouldBe false
        meta1.exists() shouldBe false
        json2.exists() shouldBe true
        cache.has(1L) shouldBe false
        cache.has(2L) shouldBe true
        cache.chapterIds() shouldContainExactly setOf(2L)
    }
}
