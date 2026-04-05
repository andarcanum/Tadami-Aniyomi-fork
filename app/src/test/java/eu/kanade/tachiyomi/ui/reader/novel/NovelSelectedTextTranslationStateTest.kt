package eu.kanade.tachiyomi.ui.reader.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelSelectedTextTranslationStateTest {

    @Test
    fun `selection keeps renderer identity and normalized text`() {
        val selection = NovelSelectedTextSelection(
            sessionId = 42L,
            renderer = NovelSelectedTextRenderer.PAGE_READER,
            text = "  Hello\nworld  ",
            anchor = NovelSelectedTextAnchor(
                leftPx = 10,
                topPx = 20,
                rightPx = 30,
                bottomPx = 40,
            ),
        )

        selection.sessionId shouldBe 42L
        selection.renderer shouldBe NovelSelectedTextRenderer.PAGE_READER
        selection.normalizedText shouldBe "Hello world"
        selection.isBlank shouldBe false
    }

    @Test
    fun `cache key trims backend fingerprint target language and selected text`() {
        val key = buildNovelSelectedTextTranslationCacheKey(
            backendFingerprint = " google-v1 ",
            targetLanguage = " Russian ",
            text = "  Hello\nworld  ",
        )

        key shouldBe NovelSelectedTextTranslationCacheKey(
            backendFingerprint = "google-v1",
            targetLanguage = "Russian",
            normalizedText = "Hello world",
        )
    }

    @Test
    fun `stale response is rejected when session id changes`() {
        val activeSelection = NovelSelectedTextSelection(
            sessionId = 7L,
            renderer = NovelSelectedTextRenderer.NATIVE_SCROLL,
            text = "Hello",
            anchor = NovelSelectedTextAnchor(
                leftPx = 0,
                topPx = 0,
                rightPx = 10,
                bottomPx = 10,
            ),
        )

        isNovelSelectedTextTranslationResponseStale(activeSelection, responseSessionId = 7L) shouldBe false
        isNovelSelectedTextTranslationResponseStale(activeSelection, responseSessionId = 8L) shouldBe true
        isNovelSelectedTextTranslationResponseStale(null, responseSessionId = 8L) shouldBe true
    }

    @Test
    fun `session cache clears when chapter session resets`() {
        val cache = NovelSelectedTextTranslationSessionCache()
        val key = buildNovelSelectedTextTranslationCacheKey(
            backendFingerprint = "google-v1",
            targetLanguage = "Russian",
            text = "Hello world",
        )
        val value = NovelSelectedTextTranslationResult(
            translation = "Привет мир",
            detectedSourceLanguage = "en",
            providerFingerprint = "google-v1",
        )

        cache.put(key, value)
        cache.snapshotSize() shouldBe 1
        cache.get(key) shouldBe value

        cache.clear()

        cache.snapshotSize() shouldBe 0
        cache.get(key) shouldBe null
    }
}
