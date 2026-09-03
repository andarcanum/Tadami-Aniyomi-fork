package eu.kanade.tachiyomi.ui.reader.novel.tts

import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTtsHighlightMode

data class NovelTtsChapterModel(
    val chapterId: Long,
    val chapterTitle: String,
    val segments: List<NovelTtsSegment>,
    val utterances: List<NovelTtsUtterance>,
) {
    private val segmentsById = segments.associateBy { it.id }
    private val utteranceIndexById = buildMap(utterances.size) {
        utterances.forEachIndexed { index, utterance -> put(utterance.id, index) }
    }

    /** Returns the index of the utterance with [utteranceId], or -1 when unknown. */
    fun indexOfUtterance(utteranceId: String): Int = utteranceIndexById[utteranceId] ?: -1

    fun findSegmentForUtterance(utteranceId: String): NovelTtsSegment? {
        val index = utteranceIndexById[utteranceId] ?: return null
        return segmentsById[utterances[index].segmentId]
    }
}

data class NovelTtsSegment(
    val id: String,
    val chapterId: Long,
    val text: String,
    val sourceBlockIndex: Int,
    /**
     * Address of this segment's block inside its chapter.
     *
     * Identical to [sourceBlockIndex] for a chapter model, and named separately because book mode
     * addresses blocks by the `(chapterId, blockIndex)` pair: over a book the rendered position of
     * a block says nothing about the chapter it belongs to, so the pair is what the DOM anchor and
     * the native renderer both match on.
     */
    val blockIndex: Int = sourceBlockIndex,
    val pageCandidates: List<Int> = emptyList(),
    val firstUtteranceIndex: Int,
    val lastUtteranceIndex: Int,
    val wordRangeCount: Int,
)

data class NovelTtsUtterance(
    val id: String,
    val segmentId: String,
    val text: String,
    val sourceBlockIndex: Int,
    val blockTextStart: Int? = null,
    val blockTextEndExclusive: Int? = null,
    val pageCandidate: Int? = null,
    val wordRanges: List<NovelTtsWordRange>,
) {
    /**
     * Maps a character offset reported by the speech engine (for example from
     * `UtteranceProgressListener.onRangeStart`) to the index of the spoken word.
     * Returns the last word whose start lies at or before [charOffset],
     * or `null` when the utterance has no words at all.
     */
    fun wordIndexForCharOffset(charOffset: Int): Int? {
        if (wordRanges.isEmpty()) return null
        var low = 0
        var high = wordRanges.lastIndex
        var result = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (wordRanges[mid].startChar <= charOffset) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }
}

data class NovelTtsWordRange(
    val wordIndex: Int,
    val text: String,
    val startChar: Int,
    val endChar: Int,
    /** Start offset of this word inside the raw source block text, when known. */
    val blockStartChar: Int? = null,
    /** Exclusive end offset of this word inside the raw source block text, when known. */
    val blockEndCharExclusive: Int? = null,
)

data class NovelTtsChapterModelBuildOptions(
    val includeChapterTitle: Boolean,
    val maxUtteranceLength: Int = 220,
)

data class NovelTtsEngineCapabilities(
    val supportsExactWordOffsets: Boolean,
    val supportsReliablePauseResume: Boolean,
    val supportsVoiceEnumeration: Boolean,
    val supportsLocaleEnumeration: Boolean,
) {
    fun resolveHighlightMode(preferredMode: NovelTtsHighlightMode): NovelTtsHighlightMode {
        return when (preferredMode) {
            NovelTtsHighlightMode.OFF -> NovelTtsHighlightMode.OFF
            NovelTtsHighlightMode.EXACT -> {
                if (supportsExactWordOffsets) NovelTtsHighlightMode.EXACT else NovelTtsHighlightMode.ESTIMATED
            }
            NovelTtsHighlightMode.AUTO -> {
                if (supportsExactWordOffsets) NovelTtsHighlightMode.EXACT else NovelTtsHighlightMode.ESTIMATED
            }
            NovelTtsHighlightMode.ESTIMATED -> NovelTtsHighlightMode.ESTIMATED
        }
    }

    companion object {
        /** Capabilities of an engine that has not been initialized yet. */
        val NONE = NovelTtsEngineCapabilities(
            supportsExactWordOffsets = false,
            supportsReliablePauseResume = false,
            supportsVoiceEnumeration = false,
            supportsLocaleEnumeration = false,
        )
    }
}

data class NovelTtsInstalledEngine(
    val packageName: String,
    val label: String,
)

data class NovelTtsEngineDescriptor(
    val packageName: String,
    val label: String,
    val isSystemDefault: Boolean,
)

data class NovelTtsVoiceDescriptor(
    val id: String,
    val name: String,
    val localeTag: String,
    val requiresNetwork: Boolean = false,
    val isInstalled: Boolean = true,
)

data class NovelTtsHighlightSelection(
    val wordIndex: Int,
    val wordRange: NovelTtsWordRange,
)

fun interface NovelTtsTokenizer {
    fun tokenize(text: String): List<NovelTtsWordRange>
}

/**
 * Resolves the effective word-highlight mode for a TTS session.
 *
 * [wordHighlightEnabled] (the "Word highlight" reader setting) is the master switch: when it is
 * off, highlighting is OFF regardless of the preferred mode. Otherwise the preferred mode is
 * downgraded by the engine capabilities as usual.
 */
fun resolveActiveTtsHighlightMode(
    wordHighlightEnabled: Boolean,
    preferredMode: NovelTtsHighlightMode,
    capabilities: NovelTtsEngineCapabilities,
): NovelTtsHighlightMode {
    if (!wordHighlightEnabled) return NovelTtsHighlightMode.OFF
    return capabilities.resolveHighlightMode(preferredMode)
}
