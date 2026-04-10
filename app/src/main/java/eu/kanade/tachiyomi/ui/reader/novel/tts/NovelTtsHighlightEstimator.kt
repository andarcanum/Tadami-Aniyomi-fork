package eu.kanade.tachiyomi.ui.reader.novel.tts

import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTtsHighlightMode

class NovelTtsHighlightEstimator {

    fun estimateWordRange(
        utterance: NovelTtsUtterance,
        elapsedMs: Long,
        durationMs: Long,
        mode: NovelTtsHighlightMode,
        startWordIndex: Int = 0,
    ): NovelTtsHighlightSelection? {
        if (mode == NovelTtsHighlightMode.OFF) return null
        val wordRanges = utterance.wordRanges
        if (wordRanges.isEmpty()) return null
        val startIndex = startWordIndex.coerceIn(0, wordRanges.lastIndex)
        val activeWordRanges = wordRanges.drop(startIndex)
        if (activeWordRanges.isEmpty()) return null
        if (durationMs <= 0L) {
            val lastWord = activeWordRanges.last()
            return NovelTtsHighlightSelection(
                wordIndex = lastWord.wordIndex,
                wordRange = lastWord,
            )
        }

        val clampedElapsed = elapsedMs.coerceIn(0L, durationMs)
        val weights = activeWordRanges.map { wordRange ->
            wordWeight(
                utteranceText = utterance.text,
                wordRange = wordRange,
            )
        }
        val totalWeight = weights.sum().takeIf { it > 0.0 } ?: return null
        val targetWeight = (clampedElapsed.toDouble() / durationMs.toDouble()) * totalWeight
        var traversedWeight = 0.0

        activeWordRanges.forEachIndexed { index, wordRange ->
            traversedWeight += weights[index]
            if (targetWeight <= traversedWeight || index == activeWordRanges.lastIndex) {
                return NovelTtsHighlightSelection(
                    wordIndex = wordRange.wordIndex,
                    wordRange = wordRange,
                )
            }
        }

        val lastWord = activeWordRanges.last()
        return NovelTtsHighlightSelection(
            wordIndex = lastWord.wordIndex,
            wordRange = lastWord,
        )
    }

    private fun wordWeight(
        utteranceText: String,
        wordRange: NovelTtsWordRange,
    ): Double {
        val wordLength = wordRange.text.length
        var weight = 1.0 + wordLength.coerceAtMost(16) * 0.08
        if (wordLength >= 10) {
            weight += 0.4
        }

        val trailingText = utteranceText
            .substring(wordRange.endChar, utteranceText.length)
            .takeWhile { !it.isLetterOrDigit() && !it.isWhitespace() }

        weight += trailingText.sumOf { punctuationWeight(it) }
        return weight
    }

    private fun punctuationWeight(char: Char): Double {
        return when (char) {
            ',', ';', ':' -> 0.7
            '.', '!', '?', '…' -> 1.1
            '-', '–', '—' -> 0.35
            else -> 0.15
        }
    }
}
