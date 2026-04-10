package eu.kanade.presentation.reader.novel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTtsHighlightMode
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsWordRange

data class NovelReaderTtsHighlightState(
    val sourceBlockIndex: Int? = null,
    val utteranceText: String? = null,
    val wordRange: NovelTtsWordRange? = null,
    val pageIndex: Int? = null,
    val blockTextStart: Int? = null,
    val blockTextEndExclusive: Int? = null,
    val mode: NovelTtsHighlightMode = NovelTtsHighlightMode.OFF,
) {
    val isEnabled: Boolean
        get() = mode != NovelTtsHighlightMode.OFF &&
            sourceBlockIndex != null &&
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
): AnnotatedString {
    val state = highlightState ?: return text
    if (!state.isEnabled || state.sourceBlockIndex != sourceBlockIndex) return text
    val utteranceText = state.utteranceText?.takeIf { it.isNotBlank() }
    val hasPageContext = (
        pageIndex != null &&
            pageBlockTextStart != null &&
            pageBlockTextEndExclusive != null &&
            state.pageIndex == pageIndex &&
            state.blockTextStart != null &&
            state.blockTextEndExclusive != null
        )

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

    return buildAnnotatedString {
        append(text)
        addStyle(
            style = SpanStyle(background = highlightColor),
            start = blockRange.first,
            end = highlightEndExclusive,
        )
    }
}
