package eu.kanade.tachiyomi.ui.reader.novel.tts

import eu.kanade.tachiyomi.ui.reader.novel.mapTtsEngineRangeStartToWordIndex
import io.kotest.matchers.shouldBe
import org.junit.Test

/**
 * Regression: after a mid-utterance resume the engine speaks only a suffix of the utterance and
 * reports suffix-relative `onRangeStart` offsets. Mapping them without the resume base offset
 * snapped the word highlight back to the start of the utterance and rewound the TTS checkpoint,
 * re-speaking already-heard text on the next resume.
 */
class NovelTtsRangeOffsetMappingTest {

    // "Hello brave new world": Hello=0, brave=6, new=12, world=16
    private val utterance = NovelTtsUtterance(
        id = "utterance-0-0",
        segmentId = "segment-0",
        text = "Hello brave new world",
        sourceBlockIndex = 0,
        wordRanges = NovelTtsWordTokenizer.tokenize("Hello brave new world"),
    )

    @Test
    fun `resume offsets are shifted by the spoken-text base before mapping`() {
        // Resume from word 2 ("new"): the engine speaks "new world" and reports offsets from 0.
        val spokenTextStartChar = 12
        mapTtsEngineRangeStartToWordIndex(utterance, engineStartChar = 0, spokenTextStartChar) shouldBe 2
        mapTtsEngineRangeStartToWordIndex(utterance, engineStartChar = 4, spokenTextStartChar) shouldBe 3
    }

    @Test
    fun `fresh utterances keep the zero base offset mapping`() {
        mapTtsEngineRangeStartToWordIndex(utterance, engineStartChar = 0, spokenTextStartChar = 0) shouldBe 0
        mapTtsEngineRangeStartToWordIndex(utterance, engineStartChar = 6, spokenTextStartChar = 0) shouldBe 1
    }
}
