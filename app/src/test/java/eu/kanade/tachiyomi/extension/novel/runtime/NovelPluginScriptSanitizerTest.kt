package eu.kanade.tachiyomi.extension.novel.runtime

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelPluginScriptSanitizerTest {

    @Test
    fun `removes BOM character`() {
        val input = "\uFEFFmodule.exports = {};"
        NovelPluginScriptSanitizer.sanitize(input) shouldBe "module.exports = {};"
    }

    @Test
    fun `removes null characters`() {
        val input = "module\u0000.exports = {};"
        val result = NovelPluginScriptSanitizer.sanitize(input)
        result.contains('\u0000') shouldBe false
    }

    @Test
    fun `normalizes CRLF to LF`() {
        val input = "line1\r\nline2\r\nline3"
        NovelPluginScriptSanitizer.sanitize(input) shouldBe "line1\nline2\nline3"
    }

    @Test
    fun `preserves normal code unchanged`() {
        val input = "module.exports = { foo: 1 };"
        NovelPluginScriptSanitizer.sanitize(input) shouldBe input
    }

    @Test
    fun `trims trailing whitespace`() {
        val input = "module.exports = {};   \n  "
        NovelPluginScriptSanitizer.sanitize(input) shouldBe "module.exports = {};"
    }
}
