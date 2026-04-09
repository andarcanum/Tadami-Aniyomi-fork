package eu.kanade.tachiyomi.ui.reader.novel.tts

import io.kotest.matchers.shouldBe
import org.junit.Test

class NovelPageReaderTtsPagingTest {

    @Test
    fun `page slice mapping assigns later utterances to the later page of the same block`() {
        val model = NovelTtsChapterModel(
            chapterId = 1L,
            chapterTitle = "Chapter 1",
            segments = listOf(
                NovelTtsSegment(
                    id = "segment-0",
                    chapterId = 1L,
                    text = "Alpha beta gamma. Delta epsilon zeta.",
                    sourceBlockIndex = 0,
                    firstUtteranceIndex = 0,
                    lastUtteranceIndex = 1,
                    wordRangeCount = 6,
                ),
            ),
            utterances = listOf(
                NovelTtsUtterance(
                    id = "utterance-0",
                    segmentId = "segment-0",
                    text = "Alpha beta gamma.",
                    sourceBlockIndex = 0,
                    wordRanges = NovelTtsWordTokenizer.tokenize("Alpha beta gamma."),
                ),
                NovelTtsUtterance(
                    id = "utterance-1",
                    segmentId = "segment-0",
                    text = "Delta epsilon zeta.",
                    sourceBlockIndex = 0,
                    wordRanges = NovelTtsWordTokenizer.tokenize("Delta epsilon zeta."),
                ),
            ),
        )

        val pages = listOf(
            listOf(
                NovelTtsPageSlice(
                    blockIndex = 0,
                    start = 0,
                    endExclusive = "Alpha beta gamma.".length,
                ),
            ),
            listOf(
                NovelTtsPageSlice(
                    blockIndex = 0,
                    start = "Alpha beta gamma. ".length,
                    endExclusive = "Alpha beta gamma. Delta epsilon zeta.".length,
                ),
            ),
        )

        val anchors = resolvePlainPageReaderTtsAnchors(
            textBlocks = listOf("Alpha beta gamma. Delta epsilon zeta."),
            pages = pages,
            chapterModel = model,
        )

        anchors.mapValues { (_, anchor) -> anchor.pageIndex } shouldBe mapOf(
            "utterance-0" to 0,
            "utterance-1" to 1,
        )
    }

    @Test
    fun `start utterance resolver respects current page`() {
        val anchors = mapOf(
            "utterance-0" to NovelTtsPageAnchor(pageIndex = 0),
            "utterance-1" to NovelTtsPageAnchor(pageIndex = 1),
        )
        val model = NovelTtsChapterModel(
            chapterId = 1L,
            chapterTitle = "Chapter 1",
            segments = listOf(
                NovelTtsSegment(
                    id = "segment-0",
                    chapterId = 1L,
                    text = "Alpha beta gamma. Delta epsilon zeta.",
                    sourceBlockIndex = 0,
                    pageCandidates = listOf(0, 1),
                    firstUtteranceIndex = 0,
                    lastUtteranceIndex = 1,
                    wordRangeCount = 6,
                ),
            ),
            utterances = listOf(
                NovelTtsUtterance(
                    id = "utterance-0",
                    segmentId = "segment-0",
                    text = "Alpha beta gamma.",
                    sourceBlockIndex = 0,
                    pageCandidate = 0,
                    wordRanges = NovelTtsWordTokenizer.tokenize("Alpha beta gamma."),
                ),
                NovelTtsUtterance(
                    id = "utterance-1",
                    segmentId = "segment-0",
                    text = "Delta epsilon zeta.",
                    sourceBlockIndex = 0,
                    pageCandidate = 1,
                    wordRanges = NovelTtsWordTokenizer.tokenize("Delta epsilon zeta."),
                ),
            ),
        )

        resolvePageReaderTtsStartUtteranceId(
            pageIndex = 1,
            fallbackBlockIndex = 0,
            chapterModel = model,
            utteranceAnchors = anchors,
        ) shouldBe "utterance-1"
    }
}
