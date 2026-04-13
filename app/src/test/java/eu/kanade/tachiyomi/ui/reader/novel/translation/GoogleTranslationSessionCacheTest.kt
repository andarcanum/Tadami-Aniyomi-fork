package eu.kanade.tachiyomi.ui.reader.novel.translation

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class GoogleTranslationSessionCacheTest {
    @Test
    fun `evicts oldest chapter entry when cache exceeds limit`() {
        val cache = GoogleTranslationSessionCache()

        repeat(5) { index ->
            cache.put(
                chapterId = index.toLong(),
                sourceLang = "en",
                targetLang = "ru",
                backend = ChapterTranslationBackend.GOOGLE,
                translatedByIndex = mapOf(index to "translation-$index"),
            )
        }

        cache.get(
            chapterId = 0L,
            sourceLang = "en",
            targetLang = "ru",
            backend = ChapterTranslationBackend.GOOGLE,
        ) shouldBe null
        cache.get(
            chapterId = 4L,
            sourceLang = "en",
            targetLang = "ru",
            backend = ChapterTranslationBackend.GOOGLE,
        ) shouldBe mapOf(4 to "translation-4")
        cache.snapshotSize() shouldBe 4
    }

    @Test
    fun `ml kit and google backends produce different cache entries`() {
        val cache = GoogleTranslationSessionCache()
        val chapterId = 1L
        val sourceLang = "ja"
        val targetLang = "en"

        cache.put(
            chapterId = chapterId,
            sourceLang = sourceLang,
            targetLang = targetLang,
            backend = ChapterTranslationBackend.ML_KIT,
            translatedByIndex = mapOf(0 to "mlkit-result"),
        )

        cache.put(
            chapterId = chapterId,
            sourceLang = sourceLang,
            targetLang = targetLang,
            backend = ChapterTranslationBackend.GOOGLE,
            translatedByIndex = mapOf(0 to "google-result"),
        )

        cache.get(
            chapterId = chapterId,
            sourceLang = sourceLang,
            targetLang = targetLang,
            backend = ChapterTranslationBackend.ML_KIT,
        ) shouldBe mapOf(0 to "mlkit-result")

        cache.get(
            chapterId = chapterId,
            sourceLang = sourceLang,
            targetLang = targetLang,
            backend = ChapterTranslationBackend.GOOGLE,
        ) shouldBe mapOf(0 to "google-result")
    }

    @Test
    fun `ml kit cache entries work independently`() {
        val cache = GoogleTranslationSessionCache()

        cache.put(
            chapterId = 1L,
            sourceLang = "ja",
            targetLang = "en",
            backend = ChapterTranslationBackend.ML_KIT,
            translatedByIndex = mapOf(0 to "mlkit-translation"),
        )

        cache.get(
            chapterId = 1L,
            sourceLang = "ja",
            targetLang = "en",
            backend = ChapterTranslationBackend.ML_KIT,
        ) shouldBe mapOf(0 to "mlkit-translation")
    }
}
