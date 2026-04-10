package eu.kanade.tachiyomi.ui.reader.novel.tts

import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreenModel
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichContentBlock
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichTextSegment
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test

class NovelTtsChapterModelBuilderTest {

    @Test
    fun `buildFromContentBlocks skips decorative blocks and obeys chapter title option`() {
        val model = NovelTtsChapterModelBuilder(
            tokenizer = NovelTtsWordTokenizer,
        ).build(
            chapterId = 7L,
            chapterTitle = "Chapter 7",
            contentBlocks = listOf(
                NovelReaderScreenModel.ContentBlock.Text("  Opening line  "),
                NovelReaderScreenModel.ContentBlock.Image(url = "https://example.org/cover.jpg", alt = "cover"),
                NovelReaderScreenModel.ContentBlock.Text(""),
                NovelReaderScreenModel.ContentBlock.Text("Closing line"),
            ),
            richContentBlocks = emptyList(),
            options = NovelTtsChapterModelBuildOptions(
                includeChapterTitle = false,
                maxUtteranceLength = 80,
            ),
        )

        model.chapterId shouldBe 7L
        model.segments.shouldHaveSize(2)
        model.segments.map { it.text } shouldBe listOf("Opening line", "Closing line")
        model.segments.map { it.sourceBlockIndex } shouldBe listOf(0, 3)
        model.utterances.shouldHaveSize(2)
    }

    @Test
    fun `buildFromRichContent chunks long speech and preserves word ranges`() {
        val longSentence = listOf(
            "This is a deliberately long sentence for utterance chunking.",
            "The builder should split it into multiple utterances before it reaches the TTS engine.",
            "Each utterance still needs stable word ranges for follow-along highlighting.",
        ).joinToString(" ")

        val model = NovelTtsChapterModelBuilder(
            tokenizer = NovelTtsWordTokenizer,
        ).build(
            chapterId = 11L,
            chapterTitle = "Long Chapter",
            contentBlocks = emptyList(),
            richContentBlocks = listOf(
                NovelRichContentBlock.Heading(
                    level = 2,
                    segments = listOf(NovelRichTextSegment("Long Chapter")),
                ),
                NovelRichContentBlock.Paragraph(
                    segments = listOf(NovelRichTextSegment(longSentence)),
                ),
                NovelRichContentBlock.HorizontalRule,
                NovelRichContentBlock.Image(url = "https://example.org/scene.jpg"),
            ),
            options = NovelTtsChapterModelBuildOptions(
                includeChapterTitle = true,
                maxUtteranceLength = 70,
            ),
        )

        model.segments.shouldHaveSize(2)
        model.segments[0].text shouldBe "Long Chapter"
        model.segments[0].sourceBlockIndex shouldBe -1
        model.segments[1].sourceBlockIndex shouldBe 1
        (model.utterances.size > 2) shouldBe true
        model.utterances.drop(1).all { it.text.length <= 70 } shouldBe true
        model.utterances[1].wordRanges.isNotEmpty() shouldBe true
        model.utterances[1].wordRanges.first().text.isNotBlank() shouldBe true
        model.utterances[1].pageCandidate.shouldBeNull()
        model.utterances[1].sourceBlockIndex shouldBe 1

        val paragraphSegment = model.segments[1]
        paragraphSegment.firstUtteranceIndex shouldBe 1
        paragraphSegment.lastUtteranceIndex shouldBe model.utterances.lastIndex
        paragraphSegment.pageCandidates shouldBe emptyList()
        paragraphSegment.wordRangeCount shouldBe model.utterances.drop(1).sumOf { it.wordRanges.size }
    }

    @Test
    fun `buildFromContentBlocks creates utterance level block metadata`() {
        val model = NovelTtsChapterModelBuilder(
            tokenizer = NovelTtsWordTokenizer,
        ).build(
            chapterId = 15L,
            chapterTitle = "Metadata Chapter",
            contentBlocks = listOf(
                NovelReaderScreenModel.ContentBlock.Text("One two three four five six seven eight."),
            ),
            richContentBlocks = emptyList(),
            options = NovelTtsChapterModelBuildOptions(
                includeChapterTitle = true,
                maxUtteranceLength = 18,
            ),
        )

        model.utterances.shouldHaveSize(4)
        model.utterances.map { it.sourceBlockIndex } shouldBe listOf(-1, 0, 0, 0)
        model.utterances[1].segmentId shouldBe model.segments[1].id
        model.utterances[1].wordRanges.first().wordIndex shouldBe 0
        model.utterances[2].wordRanges.last().endChar shouldBe model.utterances[2].text.length
        model.findSegmentForUtterance(model.utterances[2].id).shouldNotBeNull().id shouldBe model.segments[1].id
    }

    @Test
    fun `buildFromContentBlocks preserves raw block offsets for each utterance`() {
        val rawBlockText = "Alpha beta. Gamma delta."
        val model = NovelTtsChapterModelBuilder(
            tokenizer = NovelTtsWordTokenizer,
        ).build(
            chapterId = 21L,
            chapterTitle = "Offsets",
            contentBlocks = listOf(
                NovelReaderScreenModel.ContentBlock.Text(rawBlockText),
            ),
            richContentBlocks = emptyList(),
            options = NovelTtsChapterModelBuildOptions(
                includeChapterTitle = false,
                maxUtteranceLength = 12,
            ),
        )

        model.utterances.shouldHaveSize(2)
        model.utterances.forEach { utterance ->
            val start = utterance.blockTextStart.shouldNotBeNull()
            val endExclusive = utterance.blockTextEndExclusive.shouldNotBeNull()
            rawBlockText.substring(start, endExclusive) shouldBe utterance.text
        }
    }
}
