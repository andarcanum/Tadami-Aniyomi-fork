package eu.kanade.tachiyomi.ui.reader.novel.tts

import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTtsHighlightMode
import io.kotest.matchers.shouldBe
import org.junit.Test

class NovelTtsModelsTest {

    private val utterance = NovelTtsUtterance(
        id = "utterance-0-0",
        segmentId = "segment-0",
        text = "Hello brave new world",
        sourceBlockIndex = 0,
        wordRanges = NovelTtsWordTokenizer.tokenize("Hello brave new world"),
    )

    private val model = NovelTtsChapterModel(
        chapterId = 1L,
        chapterTitle = "Chapter",
        segments = listOf(
            NovelTtsSegment(
                id = "segment-0",
                chapterId = 1L,
                text = utterance.text,
                sourceBlockIndex = 0,
                firstUtteranceIndex = 0,
                lastUtteranceIndex = 0,
                wordRangeCount = utterance.wordRanges.size,
            ),
        ),
        utterances = listOf(utterance),
    )

    @Test
    fun `indexOfUtterance resolves known ids and rejects unknown ids`() {
        model.indexOfUtterance("utterance-0-0") shouldBe 0
        model.indexOfUtterance("missing") shouldBe -1
    }

    @Test
    fun `findSegmentForUtterance resolves the owning segment`() {
        model.findSegmentForUtterance("utterance-0-0")?.id shouldBe "segment-0"
        model.findSegmentForUtterance("missing") shouldBe null
    }

    @Test
    fun `wordIndexForCharOffset maps engine offsets to word indices`() {
        // "Hello brave new world": Hello=0..5, brave=6..11, new=12..15, world=16..21
        utterance.wordIndexForCharOffset(0) shouldBe 0
        utterance.wordIndexForCharOffset(6) shouldBe 1
        utterance.wordIndexForCharOffset(8) shouldBe 1
        utterance.wordIndexForCharOffset(16) shouldBe 3
        utterance.wordIndexForCharOffset(999) shouldBe 3
    }

    @Test
    fun `wordIndexForCharOffset returns null without words`() {
        val empty = utterance.copy(text = "", wordRanges = emptyList())
        empty.wordIndexForCharOffset(0) shouldBe null
    }

    @Test
    fun `word highlight master switch forces OFF regardless of preferred mode`() {
        val capable = NovelTtsEngineCapabilities(
            supportsExactWordOffsets = true,
            supportsReliablePauseResume = true,
            supportsVoiceEnumeration = true,
            supportsLocaleEnumeration = true,
        )

        resolveActiveTtsHighlightMode(
            wordHighlightEnabled = false,
            preferredMode = NovelTtsHighlightMode.AUTO,
            capabilities = capable,
        ) shouldBe NovelTtsHighlightMode.OFF

        resolveActiveTtsHighlightMode(
            wordHighlightEnabled = true,
            preferredMode = NovelTtsHighlightMode.AUTO,
            capabilities = capable,
        ) shouldBe NovelTtsHighlightMode.EXACT
    }

    @Test
    fun `word highlight enabled still downgrades by engine capabilities`() {
        resolveActiveTtsHighlightMode(
            wordHighlightEnabled = true,
            preferredMode = NovelTtsHighlightMode.AUTO,
            capabilities = NovelTtsEngineCapabilities.NONE,
        ) shouldBe NovelTtsHighlightMode.ESTIMATED
    }
}
