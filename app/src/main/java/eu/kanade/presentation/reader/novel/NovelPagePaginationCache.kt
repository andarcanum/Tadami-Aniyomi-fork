package eu.kanade.presentation.reader.novel

import android.graphics.Typeface
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichContentBlock
import eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign as ReaderTextAlign

internal data class PagePaginationLayoutKey(
    val showPageChapterTitle: Boolean,
    val fontSize: Int,
    val lineHeight: Float,
    val margin: Int,
    val textAlign: ReaderTextAlign,
    val preserveSourceTextAlignInNative: Boolean,
    val paragraphSpacing: Int,
    val forceParagraphIndent: Boolean,
    val bionicReading: Boolean,
    val forceBoldText: Boolean,
    val forceItalicText: Boolean,
    val richNativeRendererExperimental: Boolean,
    val hyphenationEnabled: Boolean,
    val layoutTextAlign: ReaderTextAlign,
    val composeTypeface: Typeface?,
    val chapterTitleTypeface: Typeface?,
    val viewportSize: IntSize,
    val contentPaddingPx: Dp,
    val spreadContentPaddingPx: Int,
    val statusBarTopPadding: Dp,
    val navigationBarHeightPx: Int,
    val spreadColumns: Int,
    val spreadCutoutLeftPx: Int,
    val spreadCutoutRightPx: Int,
    val densityScale: Float,
    val fontScale: Float,
    val textSelectable: Boolean,
)

internal sealed interface CachedChapterPagePagination {
    val chapterId: Long
    val chapterName: String
    val layoutKey: PagePaginationLayoutKey

    data class Plain(
        override val chapterId: Long,
        override val chapterName: String,
        override val layoutKey: PagePaginationLayoutKey,
        val textBlocks: List<PlainPageReaderTextBlock>,
        val imageBlocks: List<PlainPageImageBlock>,
        val pages: List<List<PlainPageSlice>>,
    ) : CachedChapterPagePagination

    data class Rich(
        override val chapterId: Long,
        override val chapterName: String,
        override val layoutKey: PagePaginationLayoutKey,
        val richBlocks: List<IndexedValue<NovelRichContentBlock>>,
        val pagination: MixedRichPagePagination,
    ) : CachedChapterPagePagination
}

/**
 * Bounded LRU cache of completed chapter paginations shared between the active chapter
 * and seamless adjacent-chapter previews.
 *
 * When a seamless swipe commits `nextChapterId` / `previousChapterId` as the active chapter,
 * the active reader promotes the already-computed pages synchronously on frame 0 instead of
 * resetting to an empty list and flashing a blank backdrop while repaginating from scratch.
 */
internal class NovelPagePaginationCache(
    private val maxEntries: Int = 4,
) {
    private val entries = LinkedHashMap<Long, CachedChapterPagePagination>(maxEntries + 1, 0.75f, true)

    fun put(entry: CachedChapterPagePagination) {
        entries[entry.chapterId] = entry
        while (entries.size > maxEntries) {
            val eldestKey = entries.entries.firstOrNull()?.key ?: break
            entries.remove(eldestKey)
        }
    }

    fun getPlain(
        chapterId: Long,
        chapterName: String,
        layoutKey: PagePaginationLayoutKey,
        textBlocks: List<PlainPageReaderTextBlock>,
        imageBlocks: List<PlainPageImageBlock>,
    ): CachedChapterPagePagination.Plain? {
        val cached = entries[chapterId] as? CachedChapterPagePagination.Plain ?: return null
        if (cached.chapterName != chapterName || cached.layoutKey != layoutKey) return null
        if (cached.textBlocks != textBlocks || cached.imageBlocks != imageBlocks) return null
        return cached
    }

    fun getRich(
        chapterId: Long,
        chapterName: String,
        layoutKey: PagePaginationLayoutKey,
        richBlocks: List<IndexedValue<NovelRichContentBlock>>,
    ): CachedChapterPagePagination.Rich? {
        val cached = entries[chapterId] as? CachedChapterPagePagination.Rich ?: return null
        if (cached.chapterName != chapterName || cached.layoutKey != layoutKey) return null
        if (!richBlocksMatchForPagination(cached.richBlocks, richBlocks)) return null
        return cached
    }
}

/**
 * Compares two indexed rich-block lists for pagination equivalence, ignoring [NovelRichContentBlock.anchor]
 * and [NovelRichContentBlock.bookRange]. Adjacent previews are parsed without `data-an-b` anchors while
 * active chapters carry block anchors for TTS follow-along, yet both paginate identically.
 */
internal fun richBlocksMatchForPagination(
    first: List<IndexedValue<NovelRichContentBlock>>,
    second: List<IndexedValue<NovelRichContentBlock>>,
): Boolean {
    if (first === second) return true
    if (first.size != second.size) return false
    for (i in first.indices) {
        val a = first[i]
        val b = second[i]
        if (a.index != b.index) return false
        if (!richBlockMatchesForPagination(a.value, b.value)) return false
    }
    return true
}

private fun richBlockMatchesForPagination(
    a: NovelRichContentBlock,
    b: NovelRichContentBlock,
): Boolean {
    if (a === b) return true
    return when (a) {
        is NovelRichContentBlock.Paragraph -> {
            b is NovelRichContentBlock.Paragraph &&
                a.segments == b.segments &&
                a.textAlign == b.textAlign &&
                a.firstLineIndentEm == b.firstLineIndentEm
        }
        is NovelRichContentBlock.Heading -> {
            b is NovelRichContentBlock.Heading &&
                a.level == b.level &&
                a.segments == b.segments &&
                a.textAlign == b.textAlign &&
                a.isBookChapterHeading == b.isBookChapterHeading
        }
        is NovelRichContentBlock.BlockQuote -> {
            b is NovelRichContentBlock.BlockQuote &&
                a.segments == b.segments &&
                a.textAlign == b.textAlign
        }
        is NovelRichContentBlock.HorizontalRule -> b is NovelRichContentBlock.HorizontalRule
        is NovelRichContentBlock.Image -> {
            b is NovelRichContentBlock.Image &&
                a.url == b.url &&
                a.alt == b.alt
        }
    }
}
