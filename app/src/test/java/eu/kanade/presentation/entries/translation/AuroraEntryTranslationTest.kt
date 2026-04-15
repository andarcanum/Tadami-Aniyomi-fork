package eu.kanade.presentation.entries.translation

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.Locale

class AuroraEntryTranslationTest {

    @Test
    fun `normalizes chinese source language family to zh`() {
        resolveGoogleTranslationSourceLanguageFamily("zh-CN") shouldBe "zh"
        resolveGoogleTranslationSourceLanguageFamily("Chinese") shouldBe "zh"
        resolveGoogleTranslationSourceLanguageFamily("zh-TW") shouldBe "zh"
    }

    @Test
    fun `skips unknown or all source languages`() {
        resolveGoogleTranslationSourceLanguageFamily("all") shouldBe null
        resolveGoogleTranslationSourceLanguageFamily("unknown") shouldBe null
    }

    @Test
    fun `uses app locale before system locale for target language`() {
        resolveGoogleTranslationTargetLanguage(
            appLocale = Locale.forLanguageTag("pt-BR"),
            systemLocale = Locale.JAPAN,
        ) shouldBe "pt-br"

        resolveGoogleTranslationTargetLanguage(
            appLocale = null,
            systemLocale = Locale.JAPAN,
        ) shouldBe "ja-jp"
    }

    @Test
    fun `requires enabled whitelist and different language family`() {
        shouldTranslateAuroraEntry(
            enabled = true,
            sourceLanguage = "zh-CN",
            targetLanguage = "en-US",
            allowedSourceFamilies = setOf("zh"),
        ).shouldBeTrue()

        shouldTranslateAuroraEntry(
            enabled = true,
            sourceLanguage = "ja",
            targetLanguage = "en-US",
            allowedSourceFamilies = setOf("zh"),
        ).shouldBeFalse()

        shouldTranslateAuroraEntry(
            enabled = true,
            sourceLanguage = "zh-CN",
            targetLanguage = "zh-Hant",
            allowedSourceFamilies = setOf("zh"),
        ).shouldBeFalse()
    }
}
