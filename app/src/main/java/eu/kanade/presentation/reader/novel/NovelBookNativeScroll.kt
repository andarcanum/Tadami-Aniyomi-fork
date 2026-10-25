package eu.kanade.presentation.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.BookSeekRequest
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookWindowState
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichContentBlock
import eu.kanade.tachiyomi.ui.reader.novel.parseNovelRichContent
import kotlin.math.abs

/**
 * Native-renderer side of book mode.
 *
 * The core publishes one window ([NovelBookWindowState]) and serves section markup on demand; the
 * renderer mounts exactly the sections of that window and drops everything else. There is no
 * command queue and no acknowledgement: a renderer that was rebuilt underneath the core simply
 * reads the window again, which is what used to need a document-ready hook and a "forget what is
 * rendered" call on the core side.
 *
 * The section payload is generic so this layer can be unit tested without a parser or Compose.
 */
internal data class NovelBookNativeSection<T>(
    val sectionIndex: Int,
    val blocks: List<T>,
    /** Content revision this section was built from, see [NovelBookWindowState.sectionRevisions]. */
    val revision: Long = 0L,
)

/** Drops the sections that left the window, keeping the rest in spine order. */
internal fun <T> pruneNovelBookNativeSections(
    sections: List<NovelBookNativeSection<T>>,
    window: NovelBookWindowState,
): List<NovelBookNativeSection<T>> {
    if (sections.isEmpty()) return sections
    val resident = window.residentSections.toSet()
    if (sections.all { it.sectionIndex in resident }) return sections
    return sections.filter { it.sectionIndex in resident }
}

/**
 * Sections the renderer still has to pull, nearest to the reading position first.
 *
 * A section already mounted at an older revision is pulled again: that is how a finished
 * translation is swapped in place instead of rebuilding the document around it.
 */
internal fun <T> missingNovelBookNativeSections(
    sections: List<NovelBookNativeSection<T>>,
    window: NovelBookWindowState,
): List<Int> {
    if (window.residentSections.isEmpty()) return emptyList()
    val mounted = sections.associateBy { it.sectionIndex }
    val center = window.residentSections
        .minByOrNull { index -> abs(index - centerOf(window)) }
        ?: return emptyList()
    return window.residentSections
        .filter { index ->
            val section = mounted[index]
            section == null || section.revision != window.revisionOf(index)
        }
        .sortedBy { index -> abs(index - center) }
}

/** Middle of the resident window, i.e. where the reader currently is. */
private fun centerOf(window: NovelBookWindowState): Int {
    val resident = window.residentSections
    if (resident.isEmpty()) return 0
    return resident[resident.size / 2]
}

/** Inserts (or replaces) one pulled section, keeping the list in spine order. */
internal fun <T> withNovelBookNativeSection(
    sections: List<NovelBookNativeSection<T>>,
    section: NovelBookNativeSection<T>,
): List<NovelBookNativeSection<T>> {
    val bySectionIndex = sections.associateByTo(LinkedHashMap()) { it.sectionIndex }
    bySectionIndex[section.sectionIndex] = section
    return bySectionIndex.values.sortedBy { it.sectionIndex }
}

/** A programmatic move the native list still has to perform. */
internal data class NovelBookNativeSeekTarget(
    val seekRequestId: Long,
    val itemIndex: Int,
    val fraction: Float,
)

/**
 * Resolves the seek the native list has to apply, or null when there is nothing to do.
 *
 * Both renderers now take their moves from the same channel: an explicit [BookSeekRequest] that is
 * applied at most once and acknowledged by id. Reported positions never travel back down into the
 * renderer, so an append arriving mid-scroll can no longer throw the reader back.
 *
 * The target is the block whose text range contains the requested char offset; the returned
 * [NovelBookNativeSeekTarget.fraction] is the position inside that block's row.
 */
internal fun resolveNovelBookNativeSeekTarget(
    entries: List<NovelBookNativeEntry>,
    request: BookSeekRequest?,
    lastAppliedSeekId: Long,
    @Suppress("UNUSED_PARAMETER") sectionFraction: Float,
): NovelBookNativeSeekTarget? {
    if (request == null || request.id <= lastAppliedSeekId) return null
    val sectionBlocks = entries.filterIsInstance<NovelBookNativeEntry.Block>()
        .filter { it.sectionIndex == request.location.sectionIndex }
    if (sectionBlocks.isEmpty()) return null
    val sectionCharCount = sectionBlocks.first().sectionCharCount.coerceAtLeast(1)
    // Zero is a valid explicit target (start of section), never an "unknown" sentinel.
    val targetChar = request.location.charOffset.coerceIn(0, sectionCharCount - 1)
    val target = sectionBlocks.lastOrNull { it.charOffsetBefore <= targetChar } ?: sectionBlocks.first()
    val itemIndex = entries.indexOf(target)
    val within = ((targetChar - target.charOffsetBefore).toFloat() / target.blockCharCount.coerceAtLeast(1))
        .coerceIn(0f, 1f)
    return NovelBookNativeSeekTarget(
        seekRequestId = request.id,
        itemIndex = itemIndex,
        fraction = within,
    )
}

/** The resident book sections as the native renderer holds them. */
internal typealias NovelBookNativeSections = List<NovelBookNativeSection<NovelRichContentBlock>>

/**
 * One row of the native book list.
 *
 * A section that failed to load still occupies its place in the book, so the reader sees an inline
 * error with a retry action instead of the book silently skipping a chapter.
 *
 * Every block of a section is its own row: a section is a whole chapter, and rendering it as one
 * LazyColumn item kept hundreds of AndroidView text blocks composed (and drawn) for as long as any
 * part of the chapter was visible, which is what made the native book scroll at ~15fps.
 */
internal sealed interface NovelBookNativeEntry {
    val sectionIndex: Int

    data class Block(
        override val sectionIndex: Int,
        /** Index of the block inside its section; the reader chrome addresses blocks by it. */
        val blockIndex: Int,
        val block: NovelRichContentBlock,
        /** Text length of all blocks before this one inside the section. */
        val charOffsetBefore: Int,
        /** Text length of this block (at least 1, so every block stays addressable). */
        val blockCharCount: Int,
        /** Total text length of the section this block belongs to. */
        val sectionCharCount: Int,
        /** Number of blocks in the section, so the last block can be recognized. */
        val sectionBlockCount: Int,
    ) : NovelBookNativeEntry

    data class Failed(override val sectionIndex: Int) : NovelBookNativeEntry
}

/** Merges resident sections and failed sections into one row list in spine order. */
internal fun buildNovelBookNativeEntries(
    sections: NovelBookNativeSections,
    failedSectionIndices: List<Int>,
): List<NovelBookNativeEntry> {
    val residentIndices = sections.mapTo(mutableSetOf()) { it.sectionIndex }
    val failures = failedSectionIndices
        .distinct()
        .filterNot { it in residentIndices }
        .mapTo(mutableSetOf()) { it }
    val entries = ArrayList<NovelBookNativeEntry>()
    for (section in sections) {
        entries += novelBookBlockEntries(section)
        failures.remove(section.sectionIndex)
    }
    entries += failures.map { NovelBookNativeEntry.Failed(it) }
    return entries.sortedBy { it.sectionIndex }
}

/** One list row per block, with the char offsets the position math is built on. */
private fun novelBookBlockEntries(
    section: NovelBookNativeSection<NovelRichContentBlock>,
): List<NovelBookNativeEntry> {
    val sectionCharCount = section.blocks.sumOf { novelBookBlockCharCount(it) }
    var offsetBefore = 0
    return section.blocks.mapIndexed { blockIndex, block ->
        val blockCharCount = novelBookBlockCharCount(block)
        NovelBookNativeEntry.Block(
            sectionIndex = section.sectionIndex,
            blockIndex = blockIndex,
            block = block,
            charOffsetBefore = offsetBefore,
            blockCharCount = blockCharCount,
            sectionCharCount = sectionCharCount,
            sectionBlockCount = section.blocks.size,
        ).also { offsetBefore += blockCharCount }
    }
}

/** Text length a block contributes to the position math; non-text blocks weigh one char. */
private fun novelBookBlockCharCount(block: NovelRichContentBlock): Int = when (block) {
    is NovelRichContentBlock.Paragraph -> block.segments.sumOf { it.text.length }.coerceAtLeast(1)
    is NovelRichContentBlock.Heading -> block.segments.sumOf { it.text.length }.coerceAtLeast(1)
    is NovelRichContentBlock.BlockQuote -> block.segments.sumOf { it.text.length }.coerceAtLeast(1)
    else -> 1
}

/**
 * Parses one book section for the native renderer.
 *
 * The section HTML is exactly what the WebView renderer mounts in its document, so both renderers
 * show the same content and the same chapter headings.
 */
internal fun parseNovelBookNativeSection(html: String): List<NovelRichContentBlock> =
    parseNovelRichContent(html).blocks

/** A block as the native list currently lays it out, in viewport coordinates. */
internal data class NovelBookNativeViewportItem(
    val sectionIndex: Int,
    /** Text length of the blocks before this one inside the section. */
    val charOffsetBefore: Int,
    /** Text length of this block. */
    val blockCharCount: Int,
    /** Total text length of the section. */
    val sectionCharCount: Int,
    /** Offset of the block top relative to the viewport top; negative once scrolled past. */
    val offsetPx: Int,
    val heightPx: Int,
)

/**
 * Derives the reading position from the native list layout, mirroring what the WebView relocate
 * bridge reports: the block under the top of the viewport, plus how far into its section the
 * reader is. The fraction is char-weighted: the block's own progress is scaled by its share of the
 * section's text, so uneven paragraph lengths map to honest positions.
 */
internal fun resolveNovelBookNativeRelocate(
    items: List<NovelBookNativeViewportItem>,
): NovelBookViewLocation? {
    if (items.isEmpty()) return null
    val current = items.firstOrNull { it.offsetPx <= 0 && it.offsetPx + it.heightPx > 0 }
        ?: items.first()
    val withinBlock = if (current.heightPx <= 0) {
        0f
    } else {
        ((-current.offsetPx).toFloat() / current.heightPx.toFloat()).coerceIn(0f, 1f)
    }
    val sectionSpan = current.sectionCharCount.coerceAtLeast(1)
    val charInSection = current.charOffsetBefore + withinBlock * current.blockCharCount.coerceAtLeast(1)
    val fraction = (charInSection / sectionSpan).coerceIn(0f, 1f)
    return NovelBookViewLocation(sectionIndex = current.sectionIndex, sectionFraction = fraction)
}
