package eu.kanade.tachiyomi.ui.reader.novel.tts

import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreenModel
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichContentBlock

class NovelTtsChapterModelBuilder(
    private val tokenizer: NovelTtsTokenizer,
) {

    fun build(
        chapterId: Long,
        chapterTitle: String,
        contentBlocks: List<NovelReaderScreenModel.ContentBlock>,
        richContentBlocks: List<NovelRichContentBlock>,
        options: NovelTtsChapterModelBuildOptions,
    ): NovelTtsChapterModel {
        val segmentInputs = mutableListOf<SegmentInput>()
        val normalizedTitle = chapterTitle.trim()

        if (options.includeChapterTitle && normalizedTitle.isNotBlank()) {
            segmentInputs += SegmentInput(
                text = normalizedTitle,
                rawText = normalizedTitle,
                sourceBlockIndex = -1,
            )
        }

        val sourceSegments = if (richContentBlocks.isNotEmpty()) {
            richContentBlocks.mapIndexedNotNull { index, block ->
                when (block) {
                    is NovelRichContentBlock.Paragraph -> block.toSegmentInput(index)
                    is NovelRichContentBlock.BlockQuote -> block.toSegmentInput(index)
                    is NovelRichContentBlock.Heading -> {
                        val text = block.segments.joinToString(separator = "") { it.text }.trim()
                        if (
                            text.isBlank() ||
                            (options.includeChapterTitle && normalizedTitle.isNotBlank() && text == normalizedTitle)
                        ) {
                            null
                        } else {
                            SegmentInput(
                                text = text,
                                rawText = block.segments.joinToString(separator = "") { it.text },
                                sourceBlockIndex = index,
                            )
                        }
                    }
                    is NovelRichContentBlock.HorizontalRule -> null
                    is NovelRichContentBlock.Image -> null
                }
            }
        } else {
            contentBlocks.mapIndexedNotNull { index, block ->
                when (block) {
                    is NovelReaderScreenModel.ContentBlock.Text -> {
                        val text = block.text.trim()
                        if (text.isBlank()) {
                            null
                        } else {
                            SegmentInput(
                                text = text,
                                rawText = block.text,
                                sourceBlockIndex = index,
                            )
                        }
                    }
                    is NovelReaderScreenModel.ContentBlock.Image -> null
                }
            }
        }
        segmentInputs += sourceSegments

        val segments = mutableListOf<NovelTtsSegment>()
        val utterances = mutableListOf<NovelTtsUtterance>()

        segmentInputs.forEachIndexed { segmentIndex, input ->
            val segmentId = "segment-$segmentIndex"
            val segmentUtteranceTexts = chunkText(
                text = input.text,
                maxUtteranceLength = options.maxUtteranceLength.coerceAtLeast(1),
            )
            if (segmentUtteranceTexts.isEmpty()) return@forEachIndexed
            val firstUtteranceIndex = utterances.size
            var totalWordRanges = 0
            val rawTextIndexMap = buildNormalizedTextWithIndexMap(input.rawText)
            var normalizedSearchStart = 0

            segmentUtteranceTexts.forEachIndexed { utteranceIndex, utteranceText ->
                val wordRanges = tokenizer.tokenize(utteranceText)
                totalWordRanges += wordRanges.size
                val rawRange = findNormalizedUtteranceRange(
                    normalizedBlockText = rawTextIndexMap.text,
                    utteranceText = utteranceText,
                    searchStart = normalizedSearchStart,
                )
                normalizedSearchStart = rawRange?.endExclusive ?: normalizedSearchStart
                utterances += NovelTtsUtterance(
                    id = "utterance-$segmentIndex-$utteranceIndex",
                    segmentId = segmentId,
                    text = utteranceText,
                    sourceBlockIndex = input.sourceBlockIndex,
                    blockTextStart = rawRange?.let { rawTextIndexMap.normalizedToRawIndex[it.start] },
                    blockTextEndExclusive = rawRange?.let { range ->
                        rawTextIndexMap.normalizedToRawIndex
                            .getOrNull(range.endExclusive - 1)
                            ?.plus(1)
                    },
                    pageCandidate = input.pageCandidates.firstOrNull(),
                    wordRanges = wordRanges,
                )
            }

            segments += NovelTtsSegment(
                id = segmentId,
                chapterId = chapterId,
                text = input.text,
                sourceBlockIndex = input.sourceBlockIndex,
                pageCandidates = input.pageCandidates,
                firstUtteranceIndex = firstUtteranceIndex,
                lastUtteranceIndex = utterances.lastIndex,
                wordRangeCount = totalWordRanges,
            )
        }

        return NovelTtsChapterModel(
            chapterId = chapterId,
            chapterTitle = normalizedTitle,
            segments = segments,
            utterances = utterances,
        )
    }

    private fun NovelRichContentBlock.Paragraph.toSegmentInput(index: Int): SegmentInput? {
        val rawText = segments.joinToString(separator = "") { it.text }
        val text = rawText.trim()
        if (text.isBlank()) return null
        return SegmentInput(text = text, rawText = rawText, sourceBlockIndex = index)
    }

    private fun NovelRichContentBlock.BlockQuote.toSegmentInput(index: Int): SegmentInput? {
        val rawText = segments.joinToString(separator = "") { it.text }
        val text = rawText.trim()
        if (text.isBlank()) return null
        return SegmentInput(text = text, rawText = rawText, sourceBlockIndex = index)
    }

    private fun chunkText(text: String, maxUtteranceLength: Int): List<String> {
        val normalized = text.trim().replace(Regex("\\s+"), " ")
        if (normalized.isBlank()) return emptyList()
        if (normalized.length <= maxUtteranceLength) return listOf(normalized)

        val sentences = sentenceRegex.findAll(normalized)
            .map { it.value.trim() }
            .filter { it.isNotBlank() }
            .toList()
            .ifEmpty { listOf(normalized) }

        val utterances = mutableListOf<String>()
        var current = ""

        sentences.forEach { sentence ->
            if (sentence.length > maxUtteranceLength) {
                flushChunk(current, utterances)
                current = ""
                utterances += chunkLongSentence(sentence, maxUtteranceLength)
                return@forEach
            }

            val candidate = if (current.isBlank()) sentence else "$current $sentence"
            if (candidate.length <= maxUtteranceLength) {
                current = candidate
            } else {
                flushChunk(current, utterances)
                current = sentence
            }
        }

        flushChunk(current, utterances)
        return utterances
    }

    private fun chunkLongSentence(sentence: String, maxUtteranceLength: Int): List<String> {
        val words = sentence.split(' ').filter { it.isNotBlank() }
        val chunks = mutableListOf<String>()
        var current = ""

        words.forEach { word ->
            val candidate = if (current.isBlank()) word else "$current $word"
            if (candidate.length <= maxUtteranceLength) {
                current = candidate
            } else {
                flushChunk(current, chunks)
                current = word
            }
        }

        flushChunk(current, chunks)
        return chunks
    }

    private fun flushChunk(chunk: String, target: MutableList<String>) {
        val normalized = chunk.trim()
        if (normalized.isNotBlank()) {
            target += normalized
        }
    }

    private data class SegmentInput(
        val text: String,
        val rawText: String,
        val sourceBlockIndex: Int,
        val pageCandidates: List<Int> = emptyList(),
    )

    private data class NormalizedTextIndexMap(
        val text: String,
        val normalizedToRawIndex: List<Int>,
    )

    private data class NormalizedTextRange(
        val start: Int,
        val endExclusive: Int,
    )

    private fun buildNormalizedTextWithIndexMap(text: String): NormalizedTextIndexMap {
        val normalized = StringBuilder(text.length)
        val normalizedToRawIndex = mutableListOf<Int>()
        var previousWasWhitespace = true

        text.forEachIndexed { rawIndex, char ->
            val isWhitespace = char.isWhitespace()
            if (isWhitespace) {
                if (!previousWasWhitespace) {
                    normalized.append(' ')
                    normalizedToRawIndex += rawIndex
                }
                previousWasWhitespace = true
            } else {
                normalized.append(char)
                normalizedToRawIndex += rawIndex
                previousWasWhitespace = false
            }
        }

        while (normalized.isNotEmpty() && normalized.last().isWhitespace()) {
            normalized.deleteCharAt(normalized.lastIndex)
            normalizedToRawIndex.removeAt(normalizedToRawIndex.lastIndex)
        }

        return NormalizedTextIndexMap(
            text = normalized.toString().trim(),
            normalizedToRawIndex = normalizedToRawIndex,
        )
    }

    private fun findNormalizedUtteranceRange(
        normalizedBlockText: String,
        utteranceText: String,
        searchStart: Int,
    ): NormalizedTextRange? {
        val start = normalizedBlockText.indexOf(utteranceText, startIndex = searchStart)
        if (start < 0) return null
        return NormalizedTextRange(
            start = start,
            endExclusive = start + utteranceText.length,
        )
    }

    private companion object {
        val sentenceRegex = Regex("""[^.!?]+[.!?]?""")
    }
}
