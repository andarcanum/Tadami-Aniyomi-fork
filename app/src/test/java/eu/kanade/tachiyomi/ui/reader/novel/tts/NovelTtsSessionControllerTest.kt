package eu.kanade.tachiyomi.ui.reader.novel.tts

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test

class NovelTtsSessionControllerTest {

    @Test
    fun `startFromCurrentPosition begins playback from requested utterance`() {
        runBlocking {
            val speaker = FakeSpeaker()
            val store = InMemoryNovelTtsSessionStore()
            val controller = NovelTtsSessionController(
                chapterSource = FakeChapterSource(
                    chapters = listOf(chapter(chapterId = 1L)),
                ),
                speaker = speaker,
                sessionStore = store,
            )

            controller.startFromCurrentPosition(
                chapterId = 1L,
                utteranceId = "chapter-1-utterance-1",
                preferTranslatedText = false,
                autoAdvanceChapter = true,
            )

            speaker.spokenUtteranceIds shouldContainExactly listOf("chapter-1-utterance-1")
            controller.state.value.playbackState shouldBe NovelTtsPlaybackState.PLAYING
        }
    }

    @Test
    fun `pause and resume continue from the same utterance`() {
        runBlocking {
            val speaker = FakeSpeaker()
            val controller = NovelTtsSessionController(
                chapterSource = FakeChapterSource(listOf(chapter(chapterId = 1L))),
                speaker = speaker,
                sessionStore = InMemoryNovelTtsSessionStore(),
            )

            controller.startFromCurrentPosition(
                chapterId = 1L,
                utteranceId = "chapter-1-utterance-0",
                preferTranslatedText = false,
                autoAdvanceChapter = true,
            )
            controller.updateWordProgress(1)
            controller.pause()
            controller.resume()

            speaker.stopCalls shouldBe 1
            speaker.spokenUtteranceIds shouldContainExactly listOf(
                "chapter-1-utterance-0",
                "chapter-1-utterance-0",
            )
            speaker.spokenStartWordIndexes shouldContainExactly listOf(0, 1)
            speaker.spokenTexts shouldContainExactly listOf("Original first", "first")
            controller.state.value.playbackState shouldBe NovelTtsPlaybackState.PLAYING
        }
    }

    @Test
    fun `resume restarts with translated text when preference changes while paused`() {
        runBlocking {
            val speaker = FakeSpeaker()
            val controller = NovelTtsSessionController(
                chapterSource = FakeChapterSource(listOf(chapter(chapterId = 1L))),
                speaker = speaker,
                sessionStore = InMemoryNovelTtsSessionStore(),
            )

            controller.startFromCurrentPosition(
                chapterId = 1L,
                utteranceId = "chapter-1-utterance-0",
                preferTranslatedText = false,
                autoAdvanceChapter = true,
            )
            controller.pause()
            controller.setPreferredTranslatedText(true)
            controller.resume()

            speaker.spokenUtteranceIds shouldContainExactly listOf(
                "chapter-1-utterance-0",
                "chapter-1-utterance-0",
            )
            speaker.spokenTexts shouldContainExactly listOf(
                "Original first",
                "Translated first",
            )
            controller.state.value.session.shouldNotBeNull().textSource shouldBe NovelTtsTextSource.TRANSLATED
            controller.state.value.playbackState shouldBe NovelTtsPlaybackState.PLAYING
        }
    }

    @Test
    fun `stop clears the active session and checkpoint`() {
        runBlocking {
            val store = InMemoryNovelTtsSessionStore()
            val controller = NovelTtsSessionController(
                chapterSource = FakeChapterSource(listOf(chapter(chapterId = 1L))),
                speaker = FakeSpeaker(),
                sessionStore = store,
            )

            controller.startFromCurrentPosition(
                chapterId = 1L,
                utteranceId = "chapter-1-utterance-0",
                preferTranslatedText = false,
                autoAdvanceChapter = true,
            )
            controller.stop()

            controller.state.value.session shouldBe null
            controller.state.value.playbackState shouldBe NovelTtsPlaybackState.IDLE
            store.loadCheckpoint() shouldBe null
        }
    }

    @Test
    fun `auto-next chapter loads the next chapter through the repository`() {
        runBlocking {
            val speaker = FakeSpeaker()
            val controller = NovelTtsSessionController(
                chapterSource = FakeChapterSource(
                    listOf(
                        chapter(chapterId = 1L, nextChapterId = 2L),
                        chapter(chapterId = 2L),
                    ),
                ),
                speaker = speaker,
                sessionStore = InMemoryNovelTtsSessionStore(),
            )

            controller.startFromCurrentPosition(
                chapterId = 1L,
                utteranceId = "chapter-1-utterance-1",
                preferTranslatedText = false,
                autoAdvanceChapter = true,
            )
            controller.onUtteranceCompleted("chapter-1-utterance-1")

            speaker.spokenUtteranceIds shouldContainExactly listOf(
                "chapter-1-utterance-1",
                "chapter-2-utterance-0",
            )
            controller.state.value.session.shouldNotBeNull().chapterId shouldBe 2L
        }
    }

    @Test
    fun `translated-text preference falls back to original text when translation is missing`() {
        runBlocking {
            val speaker = FakeSpeaker()
            val controller = NovelTtsSessionController(
                chapterSource = FakeChapterSource(
                    listOf(
                        chapter(
                            chapterId = 1L,
                            translatedModel = null,
                        ),
                    ),
                ),
                speaker = speaker,
                sessionStore = InMemoryNovelTtsSessionStore(),
            )

            controller.startFromCurrentPosition(
                chapterId = 1L,
                utteranceId = "chapter-1-utterance-0",
                preferTranslatedText = true,
                autoAdvanceChapter = true,
            )

            controller.state.value.session.shouldNotBeNull().textSource shouldBe NovelTtsTextSource.ORIGINAL
            speaker.spokenTexts shouldContainExactly listOf("Original first")
        }
    }

    @Test
    fun `persisted checkpoint restore after recreation resumes from stored utterance and word`() {
        runBlocking {
            val store = InMemoryNovelTtsSessionStore()
            val firstSpeaker = FakeSpeaker()
            val chapterSource = FakeChapterSource(listOf(chapter(chapterId = 1L)))
            val firstController = NovelTtsSessionController(
                chapterSource = chapterSource,
                speaker = firstSpeaker,
                sessionStore = store,
            )

            firstController.startFromCurrentPosition(
                chapterId = 1L,
                utteranceId = "chapter-1-utterance-1",
                preferTranslatedText = false,
                autoAdvanceChapter = false,
            )
            firstController.updateWordProgress(3)

            val restoredSpeaker = FakeSpeaker()
            val restoredController = NovelTtsSessionController(
                chapterSource = chapterSource,
                speaker = restoredSpeaker,
                sessionStore = store,
            )

            restoredController.restoreFromCheckpoint()

            restoredController.state.value.session.shouldNotBeNull().wordIndex shouldBe 3
            restoredSpeaker.spokenUtteranceIds shouldContainExactly listOf("chapter-1-utterance-1")
        }
    }

    private fun chapter(
        chapterId: Long,
        nextChapterId: Long? = null,
        translatedModel: NovelTtsChapterModel? = translatedModel(chapterId),
    ): NovelTtsResolvedChapter {
        return NovelTtsResolvedChapter(
            chapterId = chapterId,
            nextChapterId = nextChapterId,
            originalModel = model(
                chapterId = chapterId,
                texts = listOf("Original first", "Original second"),
            ),
            translatedModel = translatedModel,
        )
    }

    private fun translatedModel(chapterId: Long): NovelTtsChapterModel {
        return model(
            chapterId = chapterId,
            texts = listOf("Translated first", "Translated second"),
        )
    }

    private fun model(
        chapterId: Long,
        texts: List<String>,
    ): NovelTtsChapterModel {
        val utterances = texts.mapIndexed { index, text ->
            NovelTtsUtterance(
                id = "chapter-$chapterId-utterance-$index",
                segmentId = "chapter-$chapterId-segment-$index",
                text = text,
                sourceBlockIndex = index,
                wordRanges = NovelTtsWordTokenizer.tokenize(text),
            )
        }
        val segments = utterances.mapIndexed { index, utterance ->
            NovelTtsSegment(
                id = utterance.segmentId,
                chapterId = chapterId,
                text = utterance.text,
                sourceBlockIndex = utterance.sourceBlockIndex,
                firstUtteranceIndex = index,
                lastUtteranceIndex = index,
                wordRangeCount = utterance.wordRanges.size,
            )
        }
        return NovelTtsChapterModel(
            chapterId = chapterId,
            chapterTitle = "Chapter $chapterId",
            segments = segments,
            utterances = utterances,
        )
    }

    private class FakeChapterSource(
        chapters: List<NovelTtsResolvedChapter>,
    ) : NovelTtsChapterSource {
        private val chaptersById = chapters.associateBy { it.chapterId }

        override suspend fun loadChapter(chapterId: Long): NovelTtsResolvedChapter? = chaptersById[chapterId]
    }

    private class FakeSpeaker : NovelTtsPlaybackSpeaker {
        val spokenUtteranceIds = mutableListOf<String>()
        val spokenTexts = mutableListOf<String>()
        val spokenStartWordIndexes = mutableListOf<Int>()
        var stopCalls = 0

        override suspend fun speak(
            utterance: NovelTtsUtterance,
            flushQueue: Boolean,
            startWordIndex: Int,
        ) {
            spokenUtteranceIds += utterance.id
            spokenStartWordIndexes += startWordIndex
            val resumedText = utterance.wordRanges
                .getOrNull(startWordIndex)
                ?.startChar
                ?.let { startChar -> utterance.text.substring(startChar) }
                ?: utterance.text
            spokenTexts += resumedText
        }

        override fun stop() {
            stopCalls += 1
        }
    }
}
