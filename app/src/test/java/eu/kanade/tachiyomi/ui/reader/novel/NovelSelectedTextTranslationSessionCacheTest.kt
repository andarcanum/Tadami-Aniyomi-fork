package eu.kanade.tachiyomi.ui.reader.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelSelectedTextTranslationSessionCacheTest {
    @Test
    fun `evicts oldest selected text translation entry when cache exceeds limit`() {
        val cache = NovelSelectedTextTranslationSessionCache()

        repeat(33) { index ->
            cache.put(
                key = NovelSelectedTextTranslationCacheKey(
                    backendFingerprint = "backend-$index",
                    targetLanguage = "ru",
                    normalizedText = "text-$index",
                ),
                value = NovelSelectedTextTranslationResult(
                    translation = "translation-$index",
                    detectedSourceLanguage = "en",
                    providerFingerprint = "provider-$index",
                ),
            )
        }

        cache.get(
            NovelSelectedTextTranslationCacheKey(
                backendFingerprint = "backend-0",
                targetLanguage = "ru",
                normalizedText = "text-0",
            ),
        ) shouldBe null
        cache.get(
            NovelSelectedTextTranslationCacheKey(
                backendFingerprint = "backend-32",
                targetLanguage = "ru",
                normalizedText = "text-32",
            ),
        ) shouldBe NovelSelectedTextTranslationResult(
            translation = "translation-32",
            detectedSourceLanguage = "en",
            providerFingerprint = "provider-32",
        )
        cache.snapshotSize() shouldBe 32
    }
}
