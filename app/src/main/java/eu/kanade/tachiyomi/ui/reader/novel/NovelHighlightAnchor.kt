package eu.kanade.tachiyomi.ui.reader.novel

import tachiyomi.domain.book.novel.model.NovelHighlight

/**
 * A highlight resolved against the currently rendered blocks: either at its stored offsets
 * (exact) or re-anchored by snippet search after the source content drifted.
 */
sealed interface ResolvedNovelHighlight {
    val highlight: NovelHighlight
    val blockIndex: Int
    val charStart: Int
    val charEndExclusive: Int

    /** True when the coordinates were found via fallback and should be persisted back. */
    val needsPersist: Boolean
}

data class DefaultResolvedNovelHighlight(
    override val highlight: NovelHighlight,
    override val blockIndex: Int,
    override val charStart: Int,
    override val charEndExclusive: Int,
    override val needsPersist: Boolean,
) : ResolvedNovelHighlight

/** Host-side parse of the WebView bridge report (`data-an-b` domId + character offsets). */
fun parseNovelSelectionAnchor(domId: String?, charStart: Int, charEndExclusive: Int): NovelSelectionAnchor? {
    if (charStart < 0 || charEndExclusive <= charStart) return null
    val anchor = NovelBlockAnchor.parse(domId) ?: return null
    return NovelSelectionAnchor(
        chapterId = anchor.chapterId,
        blockIndex = anchor.blockIndex,
        charStart = charStart,
        charEndExclusive = charEndExclusive,
    )
}

object NovelHighlightResolver {

    /**
     * Resolves [highlight] against [blocks] — `(blockIndex, raw text)` pairs of one chapter.
     *
     * 1. Exact path: the stored offsets still cover the stored normalized snippet.
     * 2. Fallback: fuzzy search of the snippet inside the section, skipping [occupied]
     *    ranges; found coordinates are projected back to raw offsets and marked for persist.
     * 3. Miss: `null` — the caller silently skips painting (the row stays in the database).
     */
    fun resolve(
        highlight: NovelHighlight,
        blocks: List<Pair<Int, String>>,
        occupied: Set<Pair<Int, IntRange>> = emptySet(),
    ): ResolvedNovelHighlight? {
        val snippet = highlight.normalizedText
        if (snippet.isBlank()) return null

        val anchoredBlock = blocks.firstOrNull { it.first == highlight.blockIndex }
        if (
            anchoredBlock != null &&
            highlight.charStart >= 0 &&
            highlight.charEndExclusive <= anchoredBlock.second.length &&
            highlight.charStart < highlight.charEndExclusive &&
            normalizeNovelSelectedText(
                anchoredBlock.second.substring(highlight.charStart, highlight.charEndExclusive),
            ) ==
            snippet
        ) {
            return DefaultResolvedNovelHighlight(
                highlight = highlight,
                blockIndex = anchoredBlock.first,
                charStart = highlight.charStart,
                charEndExclusive = highlight.charEndExclusive,
                needsPersist = false,
            )
        }

        for ((blockIndex, text) in blocks) {
            val mapping = buildNormalizedMapping(text)
            var from = 0
            while (true) {
                val hit = mapping.text.indexOf(snippet, from)
                if (hit < 0) break
                val hitEndExclusive = hit + snippet.length
                val rawStart = mapping.starts[hit]
                val rawEndExclusive = mapping.ends[hitEndExclusive - 1]
                val range = rawStart until rawEndExclusive
                val isOccupied = occupied.any { (block, r) -> block == blockIndex && r.intersects(range) }
                if (!isOccupied) {
                    return DefaultResolvedNovelHighlight(
                        highlight = highlight,
                        blockIndex = blockIndex,
                        charStart = rawStart,
                        charEndExclusive = rawEndExclusive,
                        needsPersist = true,
                    )
                }
                from = hit + 1
            }
        }
        return null
    }

    private fun IntRange.intersects(other: IntRange): Boolean = first <= other.last && other.first <= last
}

/**
 * Resolves highlights of one chapter block against a rendered slice of that block and returns
 * ranges in **slice-local** coordinates, ready for painting. Native scroll renders whole blocks
 * (`rangeStart = 0, rangeEndExclusive = text.length`); the page reader renders slices, so stored
 * offsets are clipped to the slice and re-verified against its text. Fallback matches inside the
 * slice keep `needsPersist = false`: painting must stay side-effect free.
 */
fun resolveNovelHighlightsForRange(
    highlights: List<NovelHighlight>,
    chapterId: Long,
    blockIndex: Int,
    rangeStart: Int,
    rangeEndExclusive: Int,
    rangeText: String,
): List<ResolvedNovelHighlight> {
    if (highlights.isEmpty() || rangeText.isEmpty()) return emptyList()
    val mapping = buildNormalizedMapping(rangeText)
    val resolved = ArrayList<ResolvedNovelHighlight>()
    val occupied = ArrayList<IntRange>()
    highlights.forEach { highlight ->
        if (highlight.chapterId != chapterId || highlight.blockIndex != blockIndex) return@forEach
        if (highlight.normalizedText.isBlank()) return@forEach

        // Exact path: clip the stored range into this slice and verify the text.
        val overlapStart = maxOf(highlight.charStart, rangeStart)
        val overlapEnd = minOf(highlight.charEndExclusive, rangeEndExclusive)
        if (overlapStart < overlapEnd &&
            normalizeNovelSelectedText(rangeText.substring(overlapStart - rangeStart, overlapEnd - rangeStart)) ==
            highlight.normalizedText
        ) {
            val localStart = overlapStart - rangeStart
            val localEnd = overlapEnd - rangeStart
            resolved += DefaultResolvedNovelHighlight(
                highlight = highlight,
                blockIndex = blockIndex,
                charStart = localStart,
                charEndExclusive = localEnd,
                needsPersist = false,
            )
            occupied += localStart until localEnd
            return@forEach
        }

        // Fallback: fuzzy-find the snippet inside this slice.
        var from = 0
        while (true) {
            val hit = mapping.text.indexOf(highlight.normalizedText, from)
            if (hit < 0) break
            val hitEndExclusive = hit + highlight.normalizedText.length
            val rawStart = mapping.starts[hit]
            val rawEndExclusive = mapping.ends[hitEndExclusive - 1]
            val range = rawStart until rawEndExclusive
            val isOccupied = occupied.any { it.intersects(range) }
            if (!isOccupied) {
                resolved += DefaultResolvedNovelHighlight(
                    highlight = highlight,
                    blockIndex = blockIndex,
                    charStart = rawStart,
                    charEndExclusive = rawEndExclusive,
                    needsPersist = false,
                )
                occupied += range
                break
            }
            from = hit + 1
        }
    }
    return resolved
}

private fun IntRange.intersects(other: IntRange): Boolean = first <= other.last && other.first <= last

/**
 * Projects a range of the whitespace-normalized form of [raw] back to raw character offsets,
 * so a fallback hit can be painted on the original text. Returns `null` for empty or
 * out-of-bounds ranges.
 */
fun mapNormalizedRangeToRaw(raw: String, normStart: Int, normEndExclusive: Int): IntRange? {
    if (normStart < 0 || normEndExclusive <= normStart) return null
    val mapping = buildNormalizedMapping(raw)
    if (normEndExclusive > mapping.starts.size) return null
    val start = mapping.starts[normStart]
    val endExclusive = mapping.ends[normEndExclusive - 1]
    if (endExclusive <= start) return null
    return start..(endExclusive - 1)
}

private class NormalizedMapping(val text: String, val starts: IntArray, val ends: IntArray)

/**
 * Builds the same normalization as [normalizeNovelSelectedText] (trim + collapse whitespace
 * runs to single spaces) while recording each normalized character's raw span, so normalized
 * offsets can be mapped back onto the raw text.
 */
private fun buildNormalizedMapping(raw: String): NormalizedMapping {
    val chars = StringBuilder(raw.length)
    val starts = ArrayList<Int>()
    val ends = ArrayList<Int>()
    var pendingWhitespaceStart = -1
    var pendingWhitespaceEnd = -1
    var seenNonSpace = false
    for ((index, ch) in raw.withIndex()) {
        if (ch.isWhitespace()) {
            if (seenNonSpace) {
                if (pendingWhitespaceStart < 0) pendingWhitespaceStart = index
                pendingWhitespaceEnd = index
            }
        } else {
            if (pendingWhitespaceStart >= 0) {
                // A collapsed run of whitespace maps to one normalized space spanning the run.
                chars.append(' ')
                starts += pendingWhitespaceStart
                ends += pendingWhitespaceEnd + 1
                pendingWhitespaceStart = -1
            }
            chars.append(ch)
            starts += index
            ends += index + 1
            seenNonSpace = true
        }
    }
    return NormalizedMapping(chars.toString(), starts.toIntArray(), ends.toIntArray())
}
