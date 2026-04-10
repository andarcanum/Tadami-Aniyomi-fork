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
                translatedByIndex = mapOf(index to "translation-$index"),
            )
        }

        cache.get(
            chapterId = 0L,
            sourceLang = "en",
            targetLang = "ru",
        ) shouldBe null
        cache.get(
            chapterId = 4L,
            sourceLang = "en",
            targetLang = "ru",
        ) shouldBe mapOf(4 to "translation-4")
        cache.snapshotSize() shouldBe 4
    }
}
