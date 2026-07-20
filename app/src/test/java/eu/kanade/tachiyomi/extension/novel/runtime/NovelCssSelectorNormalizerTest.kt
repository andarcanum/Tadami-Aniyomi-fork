package eu.kanade.tachiyomi.extension.novel.runtime

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class NovelCssSelectorNormalizerTest {

    @Test
    fun `rewrites double-quoted contains to case-sensitive containsWholeText`() {
        normalizeCssSelector(".detail-line:contains(\"Chapters\")") shouldBe
            ".detail-line:containsWholeText(Chapters)"
    }

    @Test
    fun `rewrites single-quoted contains`() {
        normalizeCssSelector("td:contains('Genre')") shouldBe "td:containsWholeText(Genre)"
    }

    @Test
    fun `rewrites unquoted contains`() {
        normalizeCssSelector("div:contains(Chapters)") shouldBe "div:containsWholeText(Chapters)"
    }

    @Test
    fun `rewrites multiple occurrences in one selector`() {
        normalizeCssSelector("td:contains(\"Author\") + td, td:contains(\"Status\")") shouldBe
            "td:containsWholeText(Author) + td, td:containsWholeText(Status)"
    }

    @Test
    fun `keeps other selectors untouched`() {
        normalizeCssSelector("#__NEXT_DATA__") shouldBe "#__NEXT_DATA__"
        normalizeCssSelector("div:containsOwn(text)") shouldBe "div:containsOwn(text)"
        normalizeCssSelector("a[href]") shouldBe "a[href]"
        normalizeCssSelectorOrNull(null) shouldBe null
    }

    @Test
    fun `bootstrap defines base64 and text codec globals`() {
        val field = NovelJsRuntime::class.java.getDeclaredField("bootstrapScript").apply {
            isAccessible = true
        }
        val script = field.get(null) as String
        script shouldContain "global.atob"
        script shouldContain "global.btoa"
        script shouldContain "global.TextEncoder"
        script shouldContain "global.TextDecoder"
    }
}
