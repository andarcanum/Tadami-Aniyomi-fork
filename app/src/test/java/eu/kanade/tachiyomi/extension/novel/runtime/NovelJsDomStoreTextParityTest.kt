package eu.kanade.tachiyomi.extension.novel.runtime

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelJsDomStoreTextParityTest {

    @Test
    fun `getText concatenates raw text nodes without inserting spaces like cheerio`() {
        val store = NovelJsDomStore()
        val doc = store.loadDocument(
            "<html><body><div class=\"wrap\"><span>301/1197</span><span>896 chapters locked</span></div></body></html>",
        )
        val div = store.select(doc, "div.wrap").first()
        store.getText(div) shouldBe "301/1197896 chapters locked"
    }

    @Test
    fun `raw text keeps lnreader chapter-count regex behavior on poisoned fallbacks`() {
        val store = NovelJsDomStore()
        val doc = store.loadDocument(
            "<html><body><div><span>301/1197</span><span>896 chapters locked</span></div></body></html>",
        )
        val handle = store.select(doc, "div").first()
        val match = Regex("(\\d+)\\s+[Cc]hapters?").find(store.getText(handle))
        match?.groupValues?.get(1) shouldBe "1197896"
    }

    @Test
    fun `getText still returns script data for script elements`() {
        val store = NovelJsDomStore()
        val doc = store.loadDocument(
            "<html><body><script id=\"__NEXT_DATA__\">{\"a\":1}</script></body></html>",
        )
        val script = store.select(doc, "#__NEXT_DATA__").first()
        store.getText(script) shouldBe "{\"a\":1}"
    }
}
