package eu.kanade.tachiyomi.ui.reader.novel.tts

import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTtsHighlightMode

data class NovelTtsChapterModel(
    val chapterId: Long,
    val chapterTitle: String,
    val segments: List<NovelTtsSegment>,
    val utterances: List<NovelTtsUtterance>,
) {
    private val segmentsById = segments.associateBy { it.id }

    fun findSegmentForUtterance(utteranceId: String): NovelTtsSegment? {
        val utterance = utterances.firstOrNull { it.id == utteranceId } ?: return null
        return segmentsById[utterance.segmentId]
    }
}

data class NovelTtsSegment(
    val id: String,
    val chapterId: Long,
    val text: String,
    val sourceBlockIndex: Int,
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
)

data class NovelTtsWordRange(
    val wordIndex: Int,
    val text: String,
    val startChar: Int,
    val endChar: Int,
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
