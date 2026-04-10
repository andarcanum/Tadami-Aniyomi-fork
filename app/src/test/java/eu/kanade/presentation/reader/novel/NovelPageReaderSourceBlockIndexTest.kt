package eu.kanade.presentation.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign
import io.kotest.matchers.shouldBe
import org.junit.Test

class NovelPageReaderSourceBlockIndexTest {

    @Test
    fun `plain page pagination preserves original source block index`() {
        val pages = paginatePlainPageBlocks(
            textBlocks = listOf(
                PlainPageReaderTextBlock(
                    sourceBlockIndex = 3,
                    text = "Delta epsilon zeta.",
                ),
            ),
            paragraphSpacingPx = 0,
            widthPx = 1080,
            heightPx = 1920,
            textSizePx = 48f,
            lineHeightMultiplier = 1.3f,
            typeface = null,
            textAlign = TextAlign.LEFT,
            forceParagraphIndent = false,
            chapterTitle = null,
        )

        pages.single().single().blockIndex shouldBe 3

        val renderBlocks = buildPlainPageRenderBlocks(
            page = pages.single(),
            textBlocks = listOf(
                PlainPageReaderTextBlock(
                    sourceBlockIndex = 3,
                    text = "Delta epsilon zeta.",
                ),
            ),
            paragraphSpacingPx = 0,
            forceParagraphIndent = false,
            chapterTitle = null,
        )

        renderBlocks.single().sourceBlockIndex shouldBe 3
    }
}
