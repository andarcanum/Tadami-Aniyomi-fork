package eu.kanade.tachiyomi.ui.reader.novel.translation

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.translation.model.TranslationAttemptException
import tachiyomi.domain.translation.model.TranslationFailureKind

class TranslationStrictParserTest {
    @Test
    fun `malformed index markup never falls back to a complete plaintext block`() {
        listOf("-1", "word", "0.5", "999999999999999999999").forEach { id ->
            val error = runCatching {
                GeminiXmlSegmentParser.parseValidated("<s i='$id'>wrong</s>", 1, true)
            }.exceptionOrNull()
            (error is TranslationAttemptException) shouldBe true
            (error as TranslationAttemptException).failure.kind shouldBe TranslationFailureKind.INVALID_RESPONSE
        }
    }

    @Test
    fun `unquoted fractional index is not accepted as numeric prefix zero`() {
        (
            runCatching {
                GeminiXmlSegmentParser.parseValidated("<s i=0.5>wrong</s>", 1, true)
            }.exceptionOrNull() is TranslationAttemptException
            ) shouldBe
            true
    }

    @Test
    fun `plaintext is allowed only without indexed or incomplete structured markup`() {
        GeminiXmlSegmentParser.parseValidated("Привет", 1, true) shouldBe listOf("Привет")
        GeminiXmlSegmentParser.parseValidated("<s i='0'>partial", 1, true) shouldBe listOf(null)
        GeminiXmlSegmentParser.parseValidated("<s i='word", 1, true) shouldBe listOf(null)
        GeminiXmlSegmentParser.parseValidated("<s>wrong</s>", 1, true) shouldBe listOf(null)
    }

    @Test
    fun `duplicate and missing indices stay incomplete instead of silently overwriting`() {
        GeminiXmlSegmentParser.parseValidated("<s i='0'>first</s><s i='0'>second</s>", 1, true) shouldBe listOf(null)
        GeminiXmlSegmentParser.parseValidated("<s i='0'>first</s>", 2, true) shouldBe listOf("first", null)
    }
}
