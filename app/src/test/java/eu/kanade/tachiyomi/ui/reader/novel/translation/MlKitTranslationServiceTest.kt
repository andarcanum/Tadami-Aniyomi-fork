package eu.kanade.tachiyomi.ui.reader.novel.translation

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MlKitTranslationServiceTest {

    private fun createService(): MlKitTranslationService {
        return MlKitTranslationService()
    }

    @Test
    fun `returns empty response when texts list is empty`() = runTest {
        val service = createService()
        val result = service.translateBatch(
            texts = emptyList(),
            params = GoogleTranslationParams(sourceLang = "ja", targetLang = "en"),
        )

        result.translatedByText shouldBe emptyMap()
    }

    @Test
    fun `returns empty response when target language is blank`() = runTest {
        val service = createService()
        val result = service.translateBatch(
            texts = listOf("こんにちは"),
            params = GoogleTranslationParams(sourceLang = "ja", targetLang = ""),
        )

        result.translatedByText shouldBe emptyMap()
    }

    @Test
    fun `returns original text when source is auto and text is empty`() = runTest {
        val service = createService()
        val result = service.translateBatch(
            texts = listOf(""),
            params = GoogleTranslationParams(sourceLang = "auto", targetLang = "en"),
        )

        result.translatedByText[""] shouldBe ""
        result.detectedSourceLanguage shouldBe "und"
    }
}
