package eu.kanade.tachiyomi.ui.reader.novel.translation

import com.google.mlkit.nl.translate.TranslateLanguage
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.Locale

class MlKitLanguageCatalogTest {

    @Test
    fun `supported language options are sorted by display name`() {
        val options = MlKitLanguageCatalog.supportedLanguageOptions(
            languageCodes = listOf(
                TranslateLanguage.RUSSIAN,
                TranslateLanguage.ENGLISH,
                TranslateLanguage.JAPANESE,
            ),
            locale = Locale.ENGLISH,
        )

        options.map { it.languageCode } shouldBe listOf(
            TranslateLanguage.ENGLISH,
            TranslateLanguage.JAPANESE,
            TranslateLanguage.RUSSIAN,
        )
        options.first().isBuiltIn shouldBe true
        options.first().displayName shouldBe "English"
    }

    @Test
    fun `required model language codes skip english`() {
        MlKitLanguageCatalog.requiredModelLanguageCodes(
            sourceLanguage = TranslateLanguage.JAPANESE,
            targetLanguage = TranslateLanguage.RUSSIAN,
        ) shouldBe setOf(
            TranslateLanguage.JAPANESE,
            TranslateLanguage.RUSSIAN,
        )

        MlKitLanguageCatalog.requiredModelLanguageCodes(
            sourceLanguage = TranslateLanguage.ENGLISH,
            targetLanguage = TranslateLanguage.RUSSIAN,
        ) shouldBe setOf(TranslateLanguage.RUSSIAN)

        MlKitLanguageCatalog.requiredModelLanguageCodes(
            sourceLanguage = TranslateLanguage.JAPANESE,
            targetLanguage = TranslateLanguage.ENGLISH,
        ) shouldBe setOf(TranslateLanguage.JAPANESE)
    }
}
