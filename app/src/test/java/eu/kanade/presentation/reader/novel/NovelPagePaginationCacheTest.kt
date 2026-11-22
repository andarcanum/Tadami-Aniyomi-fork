package eu.kanade.presentation.reader.novel

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.reader.novel.NovelBlockAnchor
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichBlockTextAlign
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichContentBlock
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichTextSegment
import eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign as ReaderTextAlign
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelPagePaginationCacheTest {

    private fun layoutKey(
        fontSize: Int = 18,
        showPageChapterTitle: Boolean = true,
        fontScale: Float = 1f,
    ): PagePaginationLayoutKey {
        return PagePaginationLayoutKey(
            showPageChapterTitle = showPageChapterTitle,
            fontSize = fontSize,
            lineHeight = 1.5f,
            margin = 16,
            textAlign = ReaderTextAlign.LEFT,
            preserveSourceTextAlignInNative = true,
            paragraphSpacing = 8,
            forceParagraphIndent = false,
            bionicReading = false,
            forceBoldText = false,
            forceItalicText = false,
            richNativeRendererExperimental = true,
            hyphenationEnabled = false,
            layoutTextAlign = ReaderTextAlign.LEFT,
            composeTypeface = null,
            chapterTitleTypeface = null,
            viewportSize = IntSize(1080, 2200),
            contentPaddingPx = 8.dp,
            spreadContentPaddingPx = 24,
            statusBarTopPadding = 24.dp,
            navigationBarHeightPx = 48,
            spreadColumns = 1,
            spreadCutoutLeftPx = 0,
            spreadCutoutRightPx = 0,
            densityScale = 3f,
            fontScale = fontScale,
            textSelectable = true,
        )
    }

    @Test
    fun `promotes cached plain pagination when chapter and layout match`() {
        val cache = NovelPagePaginationCache()
        val key = layoutKey()
        val textBlocks = listOf(
            PlainPageReaderTextBlock(sourceBlockIndex = 0, text = "Глава 2"),
            PlainPageReaderTextBlock(sourceBlockIndex = 1, text = "Текст главы."),
        )
        val pages = listOf(
            listOf(PlainPageSlice.Text(blockIndex = 0, range = TextPageRange(0, 7), spacingBeforePx = 0)),
        )
        cache.put(
            CachedChapterPagePagination.Plain(
                chapterId = 20L,
                chapterName = "Глава 2",
                layoutKey = key,
                textBlocks = textBlocks,
                imageBlocks = emptyList(),
                pages = pages,
            ),
        )

        val hit = cache.getPlain(
            chapterId = 20L,
            chapterName = "Глава 2",
            layoutKey = key,
            textBlocks = textBlocks,
            imageBlocks = emptyList(),
        )
        hit.shouldNotBeNull()
        hit.pages shouldBe pages

        // Layout change (fontSize or system fontScale) must invalidate the cached entry.
        cache.getPlain(
            chapterId = 20L,
            chapterName = "Глава 2",
            layoutKey = layoutKey(fontSize = 20),
            textBlocks = textBlocks,
            imageBlocks = emptyList(),
        ).shouldBeNull()
        cache.getPlain(
            chapterId = 20L,
            chapterName = "Глава 2",
            layoutKey = layoutKey(fontScale = 1.3f),
            textBlocks = textBlocks,
            imageBlocks = emptyList(),
        ).shouldBeNull()

        // Translated text differing from preview must miss safely instead of risking range mismatch.
        val translatedBlocks = listOf(
            PlainPageReaderTextBlock(sourceBlockIndex = 0, text = "Chapter 2"),
            PlainPageReaderTextBlock(sourceBlockIndex = 1, text = "Translated chapter text."),
        )
        cache.getPlain(
            chapterId = 20L,
            chapterName = "Глава 2",
            layoutKey = key,
            textBlocks = translatedBlocks,
            imageBlocks = emptyList(),
        ).shouldBeNull()
    }

    @Test
    fun `promotes cached rich pagination even when active blocks carry TTS anchors`() {
        val cache = NovelPagePaginationCache()
        val key = layoutKey()
        val previewBlocks = listOf(
            IndexedValue(
                index = 0,
                value = NovelRichContentBlock.Heading(
                    level = 1,
                    segments = listOf(NovelRichTextSegment(text = "Глава 3")),
                    anchor = null,
                ),
            ),
            IndexedValue(
                index = 1,
                value = NovelRichContentBlock.Paragraph(
                    segments = listOf(NovelRichTextSegment(text = "Абзац текста.")),
                    textAlign = NovelRichBlockTextAlign.JUSTIFY,
                    anchor = null,
                ),
            ),
        )
        val activeAnchoredBlocks = listOf(
            IndexedValue(
                index = 0,
                value = NovelRichContentBlock.Heading(
                    level = 1,
                    segments = listOf(NovelRichTextSegment(text = "Глава 3")),
                    anchor = NovelBlockAnchor(chapterId = 30L, blockIndex = 0),
                ),
            ),
            IndexedValue(
                index = 1,
                value = NovelRichContentBlock.Paragraph(
                    segments = listOf(NovelRichTextSegment(text = "Абзац текста.")),
                    textAlign = NovelRichBlockTextAlign.JUSTIFY,
                    anchor = NovelBlockAnchor(chapterId = 30L, blockIndex = 1),
                ),
            ),
        )
        val pagination = MixedRichPagePagination(
            blockTexts = listOf(
                RichPageBlockText(
                    sourceBlockIndex = 0,
                    text = AnnotatedString("Глава 3"),
                    firstLineIndentEm = null,
                ),
            ),
            pages = listOf(
                listOf(RichPageSlice.Text(blockIndex = 0, range = TextPageRange(0, 7), spacingBeforePx = 0)),
            ),
        )

        cache.put(
            CachedChapterPagePagination.Rich(
                chapterId = 30L,
                chapterName = "Глава 3",
                layoutKey = key,
                richBlocks = previewBlocks,
                pagination = pagination,
            ),
        )

        val hit = cache.getRich(
            chapterId = 30L,
            chapterName = "Глава 3",
            layoutKey = key,
            richBlocks = activeAnchoredBlocks,
        )
        hit.shouldNotBeNull()
        hit.pagination shouldBe pagination
    }

    @Test
    fun `evicts eldest entries beyond cache capacity`() {
        val cache = NovelPagePaginationCache(maxEntries = 2)
        val key = layoutKey()
        fun entry(id: Long) = CachedChapterPagePagination.Plain(
            chapterId = id,
            chapterName = "Глава $id",
            layoutKey = key,
            textBlocks = emptyList(),
            imageBlocks = emptyList(),
            pages = emptyList(),
        )

        cache.put(entry(1L))
        cache.put(entry(2L))
        cache.put(entry(3L))

        cache.getPlain(1L, "Глава 1", key, emptyList(), emptyList()).shouldBeNull()
        cache.getPlain(2L, "Глава 2", key, emptyList(), emptyList()).shouldNotBeNull()
        cache.getPlain(3L, "Глава 3", key, emptyList(), emptyList()).shouldNotBeNull()
    }

    @Test
    fun `page reader auto-scroll stops at final even-page landscape spread`() {
        val target = PageReaderAutoScrollTarget(
            currentPageIndex = { 8 },
            pageCount = { 10 },
            characterCount = { 420 },
            onStepPage = {},
            spreadColumns = { 2 },
        )
        target.canScrollForward() shouldBe false

        val earlierSpread = PageReaderAutoScrollTarget(
            currentPageIndex = { 6 },
            pageCount = { 10 },
            characterCount = { 420 },
            onStepPage = {},
            spreadColumns = { 2 },
        )
        earlierSpread.canScrollForward() shouldBe true
    }

    @Test
    fun `native scroll restore decodes saved WebView progress percent proportionally`() {
        val restored = resolveInitialNativeReaderIndex(
            nativeLastSavedIndex = 0,
            savedPageReaderProgress = null,
            itemCount = 101,
            savedWebProgressPercent = 50,
        )
        restored shouldBe 50
    }
}
