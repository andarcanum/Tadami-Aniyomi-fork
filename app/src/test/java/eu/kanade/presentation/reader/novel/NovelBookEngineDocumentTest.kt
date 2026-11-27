package eu.kanade.presentation.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.NovelBookDocument
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookEngineFlow
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

class NovelBookEngineDocumentTest {

    @Test
    fun `paginated document owns a fixed viewport and columnizes only its section content`() {
        val html = buildNovelBookEngineDocumentHtml(
            document = NovelBookDocument(
                sectionIndex = 3,
                chapterId = 44L,
                html = "<p>Visible page text</p><img src=\"cover.jpg\">",
            ),
            flow = NovelBookEngineFlow.PAGINATED,
        )

        html shouldContain "Visible page text"
        html shouldContain "id=\"an-book-viewport\""
        html shouldContain "id=\"an-book-content\""
        html shouldContain "#an-book-viewport {"
        html shouldContain "overflow: hidden;"
        html shouldContain "#an-book-content {"
        // The column box itself is sized from JS with inline !important styles, because the reader
        // stylesheet forces width, height and padding and would otherwise win.
        html shouldContain "column-fill: auto !important;"
        html shouldContain "applyPagedGeometry"
        html shouldContain "#an-book-content img"
        html shouldContain "object-fit: contain;"
        html shouldContain "break-inside: avoid;"
        html shouldNotContain "body { column-width:"
        // Both flows wrap their content in a section element: it is what lets a position be
        // expressed as (section, offset) and what the stitching addresses.
        html shouldContain "class=\"an-book-section\""
    }

    @Test
    fun `paginated document exposes renderer owned page turns instead of browser panning`() {
        val html = buildNovelBookEngineDocumentHtml(
            document = NovelBookDocument(
                sectionIndex = 0,
                chapterId = 1L,
                html = "<p>Page one</p><p>Page two</p>",
            ),
            flow = NovelBookEngineFlow.PAGINATED,
        )

        html shouldContain "touch-action: none;"
        html shouldContain "window.__anBookEngine = Object.freeze({"
        html shouldContain "next: function(styleName)"
        html shouldContain "kind: 'end'"
        // Page turns are animated from JS, so the page turn style reaches the actual transform
        // instead of losing against the inline !important geometry.
        html shouldContain "const goToPage = function(page, styleName)"
        html shouldContain "goToPage(targetPage, styleName);"
        html shouldContain "if (style === 'curl')"
        html shouldNotContain "document.body.scrollLeft"
    }

    @Test
    fun `book document stabilizes images before exposing renderer readiness`() {
        val html = buildNovelBookEngineDocumentHtml(
            document = NovelBookDocument(
                sectionIndex = 0,
                chapterId = 1L,
                html = "<p>Text before image</p><img src=\"novelimg://source/cover\">",
            ),
            flow = NovelBookEngineFlow.PAGINATED,
        )

        html shouldContain "const images = Array.from(content.querySelectorAll('img'));"
        html shouldContain "image.decode()"
        html shouldContain "Promise.all(images.map(waitForImage))"
        html shouldContain "requestAnimationFrame"
        html shouldContain "charCount: totalCharCount(textNodes())"
        html shouldContain "ready: ready"
    }

    @Test
    fun `book document exposes text offset relocation and restoration`() {
        val html = buildNovelBookEngineDocumentHtml(
            document = NovelBookDocument(
                sectionIndex = 2,
                chapterId = 3L,
                html = "<p>First paragraph</p><p>Second paragraph</p>",
            ),
            flow = NovelBookEngineFlow.PAGINATED,
        )

        html shouldContain "document.createTreeWalker"
        html shouldContain "const charOffsetAtViewportStart = function()"
        html shouldContain "goToCharOffset = function(charOffset"
        // Restoring by fraction is what makes reopening a chapter that was never measured land on
        // the position the reader actually left.
        html shouldContain "goToFraction = function(fraction"
        // The ready payload uses the cheap geometry-based offset: the exact caret resolution would
        // force a full layout at open time, which made page mode appear to load forever.
        html shouldContain "charOffset: cheapCharOffsetAtViewportStart()"
        html shouldContain "goTo: goToCharOffset"
        html shouldContain "goToFraction: goToFraction"
        html shouldContain "relocate: relocate"
    }

    @Test
    fun `scrolled document uses the same exact location api`() {
        val html = buildNovelBookEngineDocumentHtml(
            document = NovelBookDocument(
                sectionIndex = 0,
                chapterId = 1L,
                html = "<p>Long chapter text</p>",
            ),
            flow = NovelBookEngineFlow.SCROLLED,
        )

        html shouldContain "window.__anBookEngine = Object.freeze({"
        html shouldContain "next: function(styleName)"
        html shouldContain "previous: function(styleName)"
        html shouldContain "goTo: goToCharOffset"
        html shouldContain "relocate: relocate"
        html shouldContain "viewport.scrollTop"
    }

    @Test
    fun `book document applies the active reader appearance css`() {
        val html = buildNovelBookEngineDocumentHtml(
            document = NovelBookDocument(
                sectionIndex = 0,
                chapterId = 1L,
                html = "<p>Styled chapter text</p>",
            ),
            flow = NovelBookEngineFlow.PAGINATED,
            readerCss = """
                :root { --an-reader-fg: #e8e1d5; --an-reader-bg: #171411; }
                #an-book-content { padding: 18px 28px; font-size: 21px; line-height: 1.65; }
            """.trimIndent(),
        )

        html shouldContain "--an-reader-fg: #e8e1d5"
        html shouldContain "--an-reader-bg: #171411"
        html shouldContain "#an-book-content { padding: 18px 28px; font-size: 21px; line-height: 1.65; }"
    }

    @Test
    fun `document pushes readiness and coalesced relocation events to its native renderer`() {
        val html = buildNovelBookEngineDocumentHtml(
            document = NovelBookDocument(
                sectionIndex = 4,
                chapterId = 55L,
                html = "<p>Push renderer events</p>",
            ),
            flow = NovelBookEngineFlow.SCROLLED,
            documentGeneration = 27L,
        )

        html shouldContain "window.AnBookNative"
        html shouldContain "onReady(27"
        html shouldContain "viewport.addEventListener('scroll'"
        html shouldContain "requestAnimationFrame"
        html shouldContain "onRelocated(27"
    }

    @Test
    fun `scrolled document wraps its chapter in a section the renderer can stitch`() {
        val html = buildNovelBookEngineDocumentHtml(
            document = NovelBookDocument(
                sectionIndex = 7,
                chapterId = 91L,
                html = "<p>Stitched chapter body</p>",
            ),
            flow = NovelBookEngineFlow.SCROLLED,
        )

        html shouldContain
            "<section class=\"an-book-section\" data-an-index=\"7\" data-an-chapter=\"91\">"
        html shouldContain ".an-book-section {"
        html shouldContain "appendSection: appendSection"
        html shouldContain "prependSection: prependSection"
        html shouldContain "removeSection: removeSection"
    }

    @Test
    fun `scrolled document asks for the next chapter before the reader reaches the edge`() {
        val html = buildNovelBookEngineDocumentHtml(
            document = NovelBookDocument(
                sectionIndex = 0,
                chapterId = 1L,
                html = "<p>Long chapter text</p>",
            ),
            flow = NovelBookEngineFlow.SCROLLED,
        )

        // Stitching is a prefetch request, not an edge event: the neighbouring chapter has to be in
        // the document before the reader gets there, otherwise the crossing is not seamless.
        html shouldContain "pushStitchRequests"
        html shouldContain "stitchForwardRequested"
        html shouldContain "stitchBackwardRequested"
        // Every resident chapter reports its real text length, so whole book progress does not stay
        // on estimated chapter weights.
        html shouldContain "onSectionMeasured"
    }

    @Test
    fun `book document guards transient resize frames and preserves text anchor across resize and book flip`() {
        val paginatedHtml = buildNovelBookEngineDocumentHtml(
            document = NovelBookDocument(
                sectionIndex = 0,
                chapterId = 1L,
                html = "<p>Chapter paragraph one</p><p>Chapter paragraph two</p>",
            ),
            flow = NovelBookEngineFlow.PAGINATED,
        )

        paginatedHtml shouldContain "isValidViewportBox"
        paginatedHtml shouldContain "hasPendingViewportResize"
        paginatedHtml shouldContain "applyViewportResizeAnchor"
        paginatedHtml shouldContain "getPropertyValue('--an-reader-bg')"

        val scrolledHtml = buildNovelBookEngineDocumentHtml(
            document = NovelBookDocument(
                sectionIndex = 0,
                chapterId = 1L,
                html = "<p>Scrolled paragraph</p>",
            ),
            flow = NovelBookEngineFlow.SCROLLED,
        )

        scrolledHtml shouldContain "applyViewportResizeAnchor"
        scrolledHtml shouldContain "settledScrollFraction"
    }
}
