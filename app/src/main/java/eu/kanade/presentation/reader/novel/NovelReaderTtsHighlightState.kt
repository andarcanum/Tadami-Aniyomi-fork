package eu.kanade.presentation.reader.novel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import eu.kanade.tachiyomi.ui.reader.novel.NovelBlockAnchor
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichContentBlock
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTtsHighlightMode
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsPageAnchor
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsUtterance
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsWordRange

data class NovelReaderTtsHighlightState(
    val sourceBlockIndex: Int? = null,
    val utteranceText: String? = null,
    val wordRange: NovelTtsWordRange? = null,
    val pageIndex: Int? = null,
    val blockTextStart: Int? = null,
    val blockTextEndExclusive: Int? = null,
    val mode: NovelTtsHighlightMode = NovelTtsHighlightMode.OFF,
    /**
     * Block the voice is reading, addressed per chapter.
     *
     * Over a book the rendered position of a block is a position inside the whole document, so the
     * block index alone matches the wrong paragraph as soon as more than one chapter is resident.
     * When both the state and the rendered block carry an anchor, the anchor decides.
     */
    val blockAnchor: NovelBlockAnchor? = null,
) {
    val isEnabled: Boolean
        get() = mode != NovelTtsHighlightMode.OFF &&
            // A book addresses blocks by `(chapterId, blockIndex)`. Requiring the chapter-local
            // index on top of that made the highlight depend on a second, independently published
            // piece of state: whenever the anchor arrived first, the snapshot in between was
            // disabled and the block was simply never painted.
            (sourceBlockIndex != null || blockAnchor != null) &&
            (
                (blockTextStart != null && blockTextEndExclusive != null) ||
                    !utteranceText.isNullOrBlank()
                )
}

internal fun applyNovelReaderTtsHighlight(
    text: AnnotatedString,
    blockText: String,
    sourceBlockIndex: Int,
    pageIndex: Int? = null,
    pageBlockTextStart: Int? = null,
    pageBlockTextEndExclusive: Int? = null,
    highlightState: NovelReaderTtsHighlightState?,
    highlightColor: Color,
    blockAnchor: NovelBlockAnchor? = null,
): AnnotatedString {
    val state = highlightState ?: return text
    if (!state.isEnabled) return text
    // The anchor wins whenever both sides have one: over a book the chapter-local index repeats in
    // every resident chapter, so matching on it would paint a paragraph of the wrong chapter.
    val addressedByAnchor = state.blockAnchor != null && blockAnchor != null
    if (addressedByAnchor) {
        if (state.blockAnchor != blockAnchor) return text
    } else if (blockAnchor != null) {
        // The block knows its book address but the voice has not published one yet. Falling back to
        // the index here would compare a chapter-local number against the position of this block in
        // a section that holds whole chapters, i.e. paint some other chapter's paragraph.
        return text
    } else if (state.sourceBlockIndex != sourceBlockIndex) {
        return text
    }
    val utteranceText = state.utteranceText?.takeIf { it.isNotBlank() }
    val hasPageContext = (
        pageIndex != null &&
            pageBlockTextStart != null &&
            pageBlockTextEndExclusive != null &&
            state.pageIndex == pageIndex &&
            state.blockTextStart != null &&
            state.blockTextEndExclusive != null
        )

    var utteranceStartInText: Int? = null
    var usesRawBlockCoordinates = false
    val pageAwareFragmentRange = if (hasPageContext) {
        val overlapStart = maxOf(state.blockTextStart, pageBlockTextStart)
        val overlapEnd = minOf(state.blockTextEndExclusive, pageBlockTextEndExclusive)
        if (overlapStart < overlapEnd) {
            val pageLocalStart = overlapStart - pageBlockTextStart
            val pageLocalEnd = overlapEnd - pageBlockTextStart
            pageLocalStart until pageLocalEnd
        } else {
            null
        }
    } else {
        null
    }

    if (hasPageContext && pageAwareFragmentRange == null) return text

    val blockRange = pageAwareFragmentRange
        ?: state.blockTextStart
            ?.let { start ->
                state.blockTextEndExclusive?.let { endExclusive ->
                    val safeStart = start.coerceIn(0, text.length)
                    val safeEnd = endExclusive.coerceIn(0, text.length)
                    if (safeStart < safeEnd) {
                        usesRawBlockCoordinates = true
                        safeStart until safeEnd
                    } else {
                        null
                    }
                }
            }
        ?: utteranceText
            ?.let { snippet ->
                val utteranceStart = blockText.indexOf(snippet)
                if (utteranceStart >= 0) {
                    utteranceStartInText = utteranceStart
                    val start = utteranceStart.coerceAtLeast(0)
                    val end = (utteranceStart + snippet.length).coerceAtMost(blockText.length)
                    start until end
                } else {
                    null
                }
            }
        ?: return text

    if (blockRange.first > blockRange.last || blockRange.first !in 0..text.length) return text
    val highlightEndExclusive = (blockRange.last + 1).coerceAtMost(text.length)
    if (highlightEndExclusive <= blockRange.first) return text

    val wordRangeInText = resolveWordRangeInText(
        word = state.wordRange,
        hasPageContext = hasPageContext,
        pageBlockTextStart = pageBlockTextStart,
        usesRawBlockCoordinates = usesRawBlockCoordinates,
        utteranceStartInText = utteranceStartInText,
    )?.let { candidate ->
        val start = candidate.first.coerceAtLeast(blockRange.first)
        val endExclusive = (candidate.last + 1).coerceAtMost(highlightEndExclusive)
        if (start < endExclusive) start until endExclusive else null
    }

    return buildAnnotatedString {
        append(text)
        if (wordRangeInText != null) {
            // The paragraph keeps its full backdrop while the word chip moves over it (the
            // approved V4 palette); the chip is the solid accent with a contrasting text color.
            addStyle(
                style = SpanStyle(background = highlightColor),
                start = blockRange.first,
                end = highlightEndExclusive,
            )
            val wordSolid = highlightColor.copy(alpha = 1f)
            addStyle(
                style = SpanStyle(
                    background = wordSolid,
                    color = resolveTtsWordChipTextColor(wordSolid),
                ),
                start = wordRangeInText.first,
                end = wordRangeInText.last + 1,
            )
        } else {
            addStyle(
                style = SpanStyle(background = highlightColor),
                start = blockRange.first,
                end = highlightEndExclusive,
            )
        }
    }
}

/**
 * Builds the highlight state the renderers read, one pure projection of the TTS UI state and the
 * mounted renderer surfaces.
 *
 * The block anchor decides which paragraph is painted when blocks are addressed by
 * `(chapterId, blockIndex)`:
 *  - over a book the anchor is published by the voice itself ([bookTtsBlockAnchor]);
 *  - over a chapter the rich blocks have carried anchors since they were annotated for selection
 *    addressing, while the voice publishes none, and the addressing guard refuses to paint an
 *    anchored block from an anchor-less state. The anchor of the rendered block at the spoken index
 *    is therefore adopted here, which restores the highlight in chapter rich-native scrolling.
 */
internal fun buildNovelReaderTtsHighlightState(
    activeUtterance: NovelTtsUtterance?,
    activeSourceBlockIndex: Int?,
    activeUtteranceText: String?,
    activeWordRange: NovelTtsWordRange?,
    activeHighlightMode: NovelTtsHighlightMode,
    isBookMode: Boolean,
    usePageReader: Boolean,
    pageReaderProgressPageIndex: Int,
    activePageReaderTtsAnchors: Map<String, NovelTtsPageAnchor>,
    bookTtsBlockAnchor: NovelBlockAnchor?,
    richScrollBlocks: List<NovelRichContentBlock>,
): NovelReaderTtsHighlightState {
    val activePageAnchor = if (usePageReader) {
        activeUtterance?.id?.let(activePageReaderTtsAnchors::get)
    } else {
        null
    }
    return NovelReaderTtsHighlightState(
        sourceBlockIndex = activeSourceBlockIndex,
        utteranceText = activeUtteranceText,
        wordRange = activeWordRange,
        pageIndex = activePageAnchor?.pageCandidates
            ?.firstOrNull { it == pageReaderProgressPageIndex }
            ?: activePageAnchor?.pageIndex,
        blockTextStart = activePageAnchor?.blockTextStart ?: activeUtterance?.blockTextStart,
        blockTextEndExclusive = activePageAnchor?.blockTextEndExclusive
            ?: activeUtterance?.blockTextEndExclusive,
        mode = activeHighlightMode,
        blockAnchor = bookTtsBlockAnchor
            ?: resolveChapterTtsBlockAnchor(
                isBookMode = isBookMode,
                activeSourceBlockIndex = activeSourceBlockIndex,
                activeUtteranceText = activeUtteranceText,
                richBlocks = richScrollBlocks,
            ),
    )
}

/**
 * The anchor of the rendered chapter block the voice is reading, or `null` when adopting it would
 * be unsafe.
 *
 * The spoken index addresses the same rich block list the renderer mounts, so its block's own
 * anchor is the correct address by construction. Two guards keep the adoption honest:
 *  - book mode never borrows this path (its chapters repeat block indices, which is exactly what
 *    the voice-published anchor exists for);
 *  - the translated TTS model numbers *plain* content blocks instead of rich blocks, so its index
 *    can point at the wrong rich block. The anchor is adopted only when the block really holds the
 *    spoken text; otherwise `null` leaves the highlight on index addressing, which is what plain
 *    rendering always used.
 */
internal fun resolveChapterTtsBlockAnchor(
    isBookMode: Boolean,
    activeSourceBlockIndex: Int?,
    activeUtteranceText: String?,
    richBlocks: List<NovelRichContentBlock>,
): NovelBlockAnchor? {
    if (isBookMode) return null
    val index = activeSourceBlockIndex?.takeIf { it >= 0 } ?: return null
    val block = richBlocks.getOrNull(index) ?: return null
    val anchor = block.anchor ?: return null
    val utterance = activeUtteranceText?.trim().orEmpty()
    if (utterance.isNotEmpty()) {
        val blockText = block.plainTextForTtsAnchorMatch()?.normalizeTtsAnchorWhitespace().orEmpty()
        val needle = utterance.normalizeTtsAnchorWhitespace()
            .take(TTS_ANCHOR_MATCH_PREFIX_LENGTH)
        if (blockText.isEmpty() || !blockText.contains(needle)) return null
    }
    return anchor
}

private fun NovelRichContentBlock.plainTextForTtsAnchorMatch(): String? = when (this) {
    is NovelRichContentBlock.Paragraph -> segments.joinToString(separator = "") { it.text }
    is NovelRichContentBlock.Heading -> segments.joinToString(separator = "") { it.text }
    is NovelRichContentBlock.BlockQuote -> segments.joinToString(separator = "") { it.text }
    is NovelRichContentBlock.HorizontalRule,
    is NovelRichContentBlock.Image,
    -> null
}

private fun String.normalizeTtsAnchorWhitespace(): String =
    replace(TTS_ANCHOR_WHITESPACE_REGEX, " ").trim()

private val TTS_ANCHOR_WHITESPACE_REGEX = Regex("\\s+")

/**
 * How many normalized utterance characters must appear in the block before its anchor is adopted.
 * The utterance is a whitespace-normalized chunk of the block text, so a short prefix is a strong
 * signal while staying immune to trailing chunk differences.
 */
private const val TTS_ANCHOR_MATCH_PREFIX_LENGTH = 60

/**
 * Resolves the currently spoken word into the coordinate space of the rendered text, or `null`
 * when the word cannot be located reliably (falls back to the whole utterance highlight).
 */
private fun resolveWordRangeInText(
    word: NovelTtsWordRange?,
    hasPageContext: Boolean,
    pageBlockTextStart: Int?,
    usesRawBlockCoordinates: Boolean,
    utteranceStartInText: Int?,
): IntRange? {
    word ?: return null
    val rawStart = word.blockStartChar
    val rawEndExclusive = word.blockEndCharExclusive
    return when {
        rawStart != null && rawEndExclusive != null && rawStart < rawEndExclusive -> when {
            hasPageContext && pageBlockTextStart != null ->
                (rawStart - pageBlockTextStart) until (rawEndExclusive - pageBlockTextStart)
            !hasPageContext && usesRawBlockCoordinates ->
                rawStart until rawEndExclusive
            utteranceStartInText != null && word.startChar < word.endChar ->
                (utteranceStartInText + word.startChar) until (utteranceStartInText + word.endChar)
            else -> null
        }
        utteranceStartInText != null && word.startChar < word.endChar ->
            (utteranceStartInText + word.startChar) until (utteranceStartInText + word.endChar)
        else -> null
    }
}
