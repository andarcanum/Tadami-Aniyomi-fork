package eu.kanade.tachiyomi.ui.reader.novel.tts

import kotlin.math.max
import kotlin.math.min

data class NovelTtsPageSlice(
    val blockIndex: Int,
    val start: Int,
    val endExclusive: Int,
)

data class NovelTtsPageAnchor(
    val pageIndex: Int,
    val pageCandidates: List<Int> = listOf(pageIndex),
    val blockTextStart: Int? = null,
    val blockTextEndExclusive: Int? = null,
    val pageTextStart: Int? = null,
    val pageTextEndExclusive: Int? = null,
)

data class NovelTtsPageReaderPosition(
    val pageIndex: Int,
    val blockTexts: List<String>,
    val pages: List<List<NovelTtsPageSlice>>,
)

data class NovelTtsPlaybackStartRequest(
    val fallbackBlockIndex: Int = 0,
    val pageReaderPosition: NovelTtsPageReaderPosition? = null,
)

fun resolvePlainPageReaderTtsAnchors(
    textBlocks: List<String>,
    pages: List<List<NovelTtsPageSlice>>,
    chapterModel: NovelTtsChapterModel,
): Map<String, NovelTtsPageAnchor> {
    val pageSlicesByBlock = buildMap<Int, List<IndexedValue<NovelTtsPageSlice>>> {
        pages.forEachIndexed { pageIndex, slices ->
            slices.forEach { slice ->
                put(
                    slice.blockIndex,
                    (get(slice.blockIndex).orEmpty() + IndexedValue(pageIndex, slice))
                        .sortedBy { it.value.start },
                )
            }
        }
    }
    val rawCursorByBlock = mutableMapOf<Int, Int>()
    val anchors = linkedMapOf<String, NovelTtsPageAnchor>()

    chapterModel.utterances.forEach { utterance ->
        val blockIndex = utterance.sourceBlockIndex
        if (blockIndex !in textBlocks.indices) return@forEach
        val rawRange = utterance.blockTextStart?.let { start ->
            utterance.blockTextEndExclusive?.let { endExclusive ->
                RawTextRange(start = start, endExclusive = endExclusive)
            }
        } ?: run {
            val blockText = textBlocks[blockIndex]
            val searchStart = rawCursorByBlock[blockIndex] ?: 0
            findUtteranceRangeInBlock(
                blockText = blockText,
                utteranceText = utterance.text,
                searchStart = searchStart,
            )
        } ?: return@forEach
        rawCursorByBlock[blockIndex] = rawRange.endExclusive

        val overlappingSlices = pageSlicesByBlock[blockIndex]
            .orEmpty()
            .filter { rangesOverlap(it.value.start, it.value.endExclusive, rawRange.start, rawRange.endExclusive) }
        if (overlappingSlices.isEmpty()) return@forEach

        val startPageSlice = overlappingSlices.first()
        val pageLocalStart = max(rawRange.start, startPageSlice.value.start) - startPageSlice.value.start
        val pageLocalEnd = min(rawRange.endExclusive, startPageSlice.value.endExclusive) - startPageSlice.value.start
        anchors[utterance.id] = NovelTtsPageAnchor(
            pageIndex = startPageSlice.index,
            pageCandidates = overlappingSlices.map { it.index }.distinct(),
            blockTextStart = rawRange.start,
            blockTextEndExclusive = rawRange.endExclusive,
            pageTextStart = pageLocalStart,
            pageTextEndExclusive = pageLocalEnd,
        )
    }

    return anchors
}

fun resolvePageReaderTtsStartUtteranceId(
    pageIndex: Int,
    fallbackBlockIndex: Int,
    chapterModel: NovelTtsChapterModel,
    utteranceAnchors: Map<String, NovelTtsPageAnchor>,
): String? {
    chapterModel.utterances.firstOrNull { utterance ->
        utteranceAnchors[utterance.id]?.pageCandidates?.contains(pageIndex) == true
    }?.let { return it.id }

    chapterModel.utterances.firstOrNull { utterance ->
        utterance.sourceBlockIndex >= fallbackBlockIndex
    }?.let { return it.id }

    return chapterModel.utterances.firstOrNull()?.id
}

private data class RawTextRange(
    val start: Int,
    val endExclusive: Int,
)

private fun findUtteranceRangeInBlock(
    blockText: String,
    utteranceText: String,
    searchStart: Int,
): RawTextRange? {
    val directMatchIndex = blockText.indexOf(utteranceText, startIndex = searchStart)
    if (directMatchIndex >= 0) {
        return RawTextRange(
            start = directMatchIndex,
            endExclusive = directMatchIndex + utteranceText.length,
        )
    }

    val normalizedSearch = buildNormalizedTextWithIndexMap(blockText)
    val normalizedUtterance = utteranceText.trim().replace(Regex("\\s+"), " ")
    val normalizedSearchStart = normalizedSearch.rawToNormalizedIndex(searchStart)
    val normalizedMatchIndex = normalizedSearch.text.indexOf(normalizedUtterance, startIndex = normalizedSearchStart)
    if (normalizedMatchIndex < 0) return null

    val rawStart = normalizedSearch.normalizedToRawIndex[normalizedMatchIndex]
    val rawEndExclusive = normalizedSearch.normalizedToRawIndex
        .getOrNull(normalizedMatchIndex + normalizedUtterance.lastIndex)
        ?.plus(1)
        ?: blockText.length
    return RawTextRange(start = rawStart, endExclusive = rawEndExclusive)
}

private data class NormalizedTextIndexMap(
    val text: String,
    val normalizedToRawIndex: List<Int>,
    val rawToNormalizedIndexByRawIndex: IntArray,
) {
    fun rawToNormalizedIndex(rawIndex: Int): Int {
        val safeIndex = rawIndex.coerceIn(0, rawToNormalizedIndexByRawIndex.lastIndex)
        return rawToNormalizedIndexByRawIndex[safeIndex]
    }
}

private fun buildNormalizedTextWithIndexMap(text: String): NormalizedTextIndexMap {
    val normalized = StringBuilder(text.length)
    val normalizedToRawIndex = mutableListOf<Int>()
    val rawToNormalized = IntArray(text.length.coerceAtLeast(1))
    var normalizedIndex = 0
    var previousWasWhitespace = true

    text.forEachIndexed { rawIndex, char ->
        val isWhitespace = char.isWhitespace()
        if (isWhitespace) {
            if (!previousWasWhitespace) {
                normalized.append(' ')
                normalizedToRawIndex += rawIndex
                normalizedIndex++
            }
            rawToNormalized[rawIndex] = (normalizedIndex - 1).coerceAtLeast(0)
            previousWasWhitespace = true
        } else {
            normalized.append(char)
            normalizedToRawIndex += rawIndex
            rawToNormalized[rawIndex] = normalizedIndex
            normalizedIndex++
            previousWasWhitespace = false
        }
    }

    while (normalized.isNotEmpty() && normalized.last().isWhitespace()) {
        normalized.deleteCharAt(normalized.lastIndex)
        normalizedToRawIndex.removeAt(normalizedToRawIndex.lastIndex)
    }

    return NormalizedTextIndexMap(
        text = normalized.toString(),
        normalizedToRawIndex = normalizedToRawIndex,
        rawToNormalizedIndexByRawIndex = rawToNormalized,
    )
}

private fun rangesOverlap(
    firstStart: Int,
    firstEndExclusive: Int,
    secondStart: Int,
    secondEndExclusive: Int,
): Boolean {
    return firstStart < secondEndExclusive && secondStart < firstEndExclusive
}
