package eu.kanade.presentation.reader.novel

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelColorPickerParsingTest {

    @Test
    fun `parses six digit hex with full alpha`() {
        parseNovelHighlightHexColor("#FBC02D") shouldBe 0xFFFBC02D
        parseNovelHighlightHexColor("90CAF9") shouldBe 0xFF90CAF9
    }

    @Test
    fun `parses eight digit hex keeping alpha`() {
        parseNovelHighlightHexColor("#80FBC02D") shouldBe 0x80FBC02D
    }

    @Test
    fun `rejects malformed values`() {
        parseNovelHighlightHexColor("").shouldBeNull()
        parseNovelHighlightHexColor("#FFF").shouldBeNull()
        parseNovelHighlightHexColor("#GGGGGG").shouldBeNull()
        parseNovelHighlightHexColor("#FBC02DFF00").shouldBeNull()
    }

    @Test
    fun `format round trips through parse`() {
        val argb = 0x80CE93D8L
        parseNovelHighlightHexColor(formatNovelHighlightHexColor(argb)) shouldBe argb
    }
}
