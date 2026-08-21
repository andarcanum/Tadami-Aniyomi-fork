package eu.kanade.presentation.reader.novel

import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class NovelHighlightWebViewJsTest {

    @Test
    fun `anchor js addresses blocks by data-an-b and reports offsets`() {
        val js = buildNovelHighlightAnchorJs()

        js shouldContain "data-an-b"
        js shouldContain "charStart"
        js shouldContain "charEnd"
        js shouldContain "domId"
    }

    @Test
    fun `apply js embeds payload and is idempotent`() {
        val payload = """[{"id":7,"domId":"12:3","start":4,"end":9,"color":"#FBC02D59"}]"""
        val js = buildApplyNovelHighlightsJs(payload)

        js shouldContain payload
        // Idempotency: previously painted spans are removed before repainting.
        js shouldContain "span.an-hl[data-hl-id]"
        js shouldContain "data-an-b"
    }

    @Test
    fun `click js reports highlight id through the bridge`() {
        val js = buildNovelHighlightClickJs("__an_reader_selection_bridge__")

        js shouldContain "__an_reader_selection_bridge__"
        js shouldContain "onHighlightClicked"
        js shouldContain "span.an-hl[data-hl-id]"
    }
}
