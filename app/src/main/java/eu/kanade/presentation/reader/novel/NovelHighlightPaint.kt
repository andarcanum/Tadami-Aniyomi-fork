package eu.kanade.presentation.reader.novel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import eu.kanade.tachiyomi.ui.reader.novel.NovelBlockAnchor
import eu.kanade.tachiyomi.ui.reader.novel.ResolvedNovelHighlight
import eu.kanade.tachiyomi.ui.reader.novel.resolveNovelHighlightsForRange
import tachiyomi.domain.book.novel.model.NovelHighlight

/** Alpha applied to highlight backgrounds so the body text stays readable underneath. */
const val HIGHLIGHT_PAINT_ALPHA = 0.35f

/** Highlights of the currently open chapter, provided by the reader host. */
val LocalNovelChapterHighlights = staticCompositionLocalOf<List<NovelHighlight>> { emptyList() }

/**
 * Paints saved highlights over a rendered **whole** block. No-op when the block cannot be
 * addressed (no anchor) or nothing is saved yet.
 */
@Composable
fun paintNovelBlockHighlights(
    text: AnnotatedString,
    anchor: NovelBlockAnchor?,
): AnnotatedString {
    val highlights = LocalNovelChapterHighlights.current
    if (anchor == null || highlights.isEmpty()) return text
    return remember(text, highlights, anchor) {
        applyNovelHighlights(
            text = text,
            blockIndex = anchor.blockIndex,
            resolved = resolveNovelHighlightsForRange(
                highlights = highlights,
                chapterId = anchor.chapterId,
                blockIndex = anchor.blockIndex,
                rangeStart = 0,
                rangeEndExclusive = text.length,
                rangeText = text.text,
            ),
        )
    }
}

/**
 * Same as [paintNovelBlockHighlights] for a rendered **slice** of a block (page reader):
 * stored offsets are clipped into `[sliceStart, sliceEndExclusive)` and expressed in
 * slice-local coordinates.
 */
@Composable
fun paintNovelSliceHighlights(
    text: AnnotatedString,
    chapterId: Long?,
    blockIndex: Int,
    sliceStart: Int,
    sliceEndExclusive: Int,
): AnnotatedString {
    val highlights = LocalNovelChapterHighlights.current
    if (chapterId == null || highlights.isEmpty()) return text
    return remember(text, highlights, chapterId, blockIndex, sliceStart, sliceEndExclusive) {
        applyNovelHighlights(
            text = text,
            blockIndex = blockIndex,
            resolved = resolveNovelHighlightsForRange(
                highlights = highlights,
                chapterId = chapterId,
                blockIndex = blockIndex,
                rangeStart = sliceStart,
                rangeEndExclusive = sliceEndExclusive,
                rangeText = text.text,
            ),
        )
    }
}

/**
 * Paints the saved highlights of one block over its rendered [text]. Ranges outside the
 * rendered text are clamped; highlights belonging to other blocks are ignored. TTS painting
 * runs on top of this, so the currently spoken word stays fully visible.
 */
internal fun applyNovelHighlights(
    text: AnnotatedString,
    blockIndex: Int,
    resolved: List<ResolvedNovelHighlight>,
): AnnotatedString {
    val blockHighlights = resolved.filter { it.blockIndex == blockIndex }
    if (blockHighlights.isEmpty()) return text
    return buildAnnotatedString {
        append(text)
        for (item in blockHighlights) {
            val start = item.charStart.coerceIn(0, text.length)
            val endExclusive = item.charEndExclusive.coerceIn(0, text.length)
            if (start >= endExclusive) continue
            addStyle(
                style = SpanStyle(
                    background = androidx.compose.ui.graphics.Color(item.highlight.colorArgb)
                        .copy(alpha = HIGHLIGHT_PAINT_ALPHA),
                ),
                start = start,
                end = endExclusive,
            )
        }
    }
}
