package eu.kanade.tachiyomi.ui.reader.novel

import eu.kanade.tachiyomi.data.book.novel.NovelBookNativeAlign
import eu.kanade.tachiyomi.data.book.novel.NovelBookNativeBlock
import eu.kanade.tachiyomi.data.book.novel.NovelBookNativeBlockKind
import eu.kanade.tachiyomi.data.book.novel.NovelBookNativeSegment

/**
 * Maps the on-disk block DTO to the renderer model.
 *
 * Keeping the two models apart is what allows the renderer to evolve (new styling, bionic reading,
 * TTS highlighting) without breaking books that were compiled by an older version: an unknown field
 * is ignored by the codec and an unmapped one simply falls back to the renderer default.
 */
internal fun NovelBookNativeBlock.toRichContentBlock(): NovelRichContentBlock? {
    val range = if (charStart >= 0 && charLength > 0) {
        NovelBookSourceRange(charStart, charLength)
    } else {
        null
    }
    return when (kind) {
        NovelBookNativeBlockKind.RULE -> NovelRichContentBlock.HorizontalRule(bookRange = range)
        NovelBookNativeBlockKind.IMAGE -> imageUrl?.let { url ->
            NovelRichContentBlock.Image(url = url, alt = imageAlt, bookRange = range)
        }
        NovelBookNativeBlockKind.HEADING -> NovelRichContentBlock.Heading(
            level = level.coerceIn(1, 6),
            segments = segments.map { it.toRichSegment() },
            textAlign = align.toRichAlign(),
            bookRange = range,
            isBookChapterHeading = isChapterHeading,
        )
        NovelBookNativeBlockKind.QUOTE -> NovelRichContentBlock.BlockQuote(
            segments = segments.map { it.toRichSegment() },
            textAlign = align.toRichAlign(),
            bookRange = range,
        )
        NovelBookNativeBlockKind.PARAGRAPH -> NovelRichContentBlock.Paragraph(
            segments = segments.map { it.toRichSegment() },
            textAlign = align.toRichAlign(),
            firstLineIndentEm = indentEm,
            bookRange = range,
        )
    }
}

/**
 * A stored block together with the position it has inside its own chapter.
 *
 * The compiled book is read as windows over one continuous stream, so the position of a block in
 * the window says nothing about the chapter it belongs to. TTS addresses blocks by
 * `(chapterId, blockIndex)`, so the index has to travel with the block instead of being recovered
 * from where it happens to be rendered.
 */
data class NovelBookAnchoredNativeBlock(
    val block: NovelBookNativeBlock,
    val blockIndex: Int,
)

/**
 * Numbers every block inside its chapter, restarting at each chapter boundary.
 *
 * This is the same numbering `annotateNovelBlockAnchors` writes into the book DOM as `data-an-b`
 * and the TTS model reports as `sourceBlockIndex`: every parsed block counts, including images and
 * rules, so the three sides address the same paragraph by the same name.
 */
internal fun List<NovelBookNativeBlock>.withChapterBlockIndices(): List<NovelBookAnchoredNativeBlock> {
    val nextIndexByChapter = mutableMapOf<Long, Int>()
    return map { block ->
        val blockIndex = nextIndexByChapter.getOrElse(block.chapterId) { 0 }
        nextIndexByChapter[block.chapterId] = blockIndex + 1
        NovelBookAnchoredNativeBlock(block = block, blockIndex = blockIndex)
    }
}

/**
 * Maps a window of stored blocks to renderer blocks.
 *
 * [includeChapterHeadings] only controls rendering. The heading block itself always exists in the
 * stored stream because its characters are part of the book offset domain: hiding it here can never
 * shift a saved reading position.
 */
internal fun List<NovelBookNativeBlock>.toRichContentBlocks(
    includeChapterHeadings: Boolean = true,
): List<NovelRichContentBlock> = withChapterBlockIndices().toAnchoredRichContentBlocks(includeChapterHeadings)

/**
 * Maps already numbered stored blocks to renderer blocks that carry their book address.
 *
 * Without the anchor the native renderer had no address at all for a block of a compiled book:
 * `annotateNovelBlockAnchors` only ever ran on the HTML path, so follow-along could neither paint
 * the spoken paragraph nor scroll to it, and the highlight fell back to comparing the chapter-local
 * TTS index against the position of the block inside the whole section - a different chapter's
 * paragraph, or nothing at all.
 *
 * A hidden chapter heading is dropped only after the numbering, so what the reader shows can never
 * shift the addresses the voice speaks by.
 */
internal fun List<NovelBookAnchoredNativeBlock>.toAnchoredRichContentBlocks(
    includeChapterHeadings: Boolean = true,
): List<NovelRichContentBlock> = asSequence()
    .filter { includeChapterHeadings || !it.block.isChapterHeading }
    .mapNotNull { anchored ->
        anchored.block.toRichContentBlock()?.withAnchor(
            NovelBlockAnchor(
                chapterId = anchored.block.chapterId,
                blockIndex = anchored.blockIndex,
            ),
        )
    }
    .toList()

private fun NovelBookNativeSegment.toRichSegment(): NovelRichTextSegment = NovelRichTextSegment(
    text = t,
    style = NovelRichTextStyle(
        bold = b,
        italic = i,
        underline = u,
        strikeThrough = s,
        colorCss = color,
        backgroundColorCss = background,
    ),
    linkUrl = href,
)

private fun NovelBookNativeAlign?.toRichAlign(): NovelRichBlockTextAlign? = when (this) {
    null -> null
    NovelBookNativeAlign.LEFT -> NovelRichBlockTextAlign.LEFT
    NovelBookNativeAlign.CENTER -> NovelRichBlockTextAlign.CENTER
    NovelBookNativeAlign.JUSTIFY -> NovelRichBlockTextAlign.JUSTIFY
    NovelBookNativeAlign.RIGHT -> NovelRichBlockTextAlign.RIGHT
}
