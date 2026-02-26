package eu.kanade.tachiyomi.ui.reader.novel.translation

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class GeminiXmlSegmentParserTest {

    @Test
    fun `parses ordered xml segments by explicit index`() {
        val raw = "<s i='1'>Beta</s><s i='0'>Alpha</s><s i='2'>Gamma</s>"

        val parsed = GeminiXmlSegmentParser.parse(raw, expectedCount = 3)

        parsed.size shouldBe 3
        parsed[0] shouldBe "Alpha"
        parsed[1] shouldBe "Beta"
        parsed[2] shouldBe "Gamma"
    }

    @Test
    fun `fills missing indexes with null`() {
        val raw = "<s i='0'>First</s><s i='2'>Third</s>"

        val parsed = GeminiXmlSegmentParser.parse(raw, expectedCount = 3)

        parsed shouldBe listOf("First", null, "Third")
    }

    @Test
    fun `strips markdown fences around xml payload`() {
        val raw = "```xml\n<s i='0'>One</s>\n<s i='1'>Two</s>\n```"

        val parsed = GeminiXmlSegmentParser.parse(raw, expectedCount = 2)

        parsed shouldBe listOf("One", "Two")
    }
}
