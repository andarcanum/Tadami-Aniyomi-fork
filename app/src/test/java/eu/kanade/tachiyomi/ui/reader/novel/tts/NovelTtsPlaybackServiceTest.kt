package eu.kanade.tachiyomi.ui.reader.novel.tts

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test

class NovelTtsPlaybackServiceTest {

    @Test
    fun `notification state reflects current playback session`() {
        val controller = FakePlaybackController(
            NovelTtsSessionUiState(
                playbackState = NovelTtsPlaybackState.PLAYING,
                session = session(),
            ),
        )
        val runtime = NovelTtsPlaybackServiceRuntime(
            controller = controller,
            audioFocusManager = FakeAudioFocusManager(granted = true),
        )

        val notification = runtime.notificationState()

        notification.isPlaying shouldBe true
        notification.title shouldBe "Chapter 7"
        notification.text shouldBe "Current utterance"
    }

    @Test
    fun `transport actions expose lockscreen controls`() {
        val runtime = NovelTtsPlaybackServiceRuntime(
            controller = FakePlaybackController(
                NovelTtsSessionUiState(
                    playbackState = NovelTtsPlaybackState.PLAYING,
                    session = session(),
                ),
            ),
            audioFocusManager = FakeAudioFocusManager(granted = true),
        )

        runtime.transportActions() shouldContainExactly listOf(
            NovelTtsTransportAction.PREVIOUS,
            NovelTtsTransportAction.PAUSE,
            NovelTtsTransportAction.NEXT,
            NovelTtsTransportAction.STOP,
        )
    }

    @Test
    fun `notification layout exposes full width custom actions`() {
        val snapshot = resolveNovelTtsNotificationLayoutSnapshot(
            actions = listOf(
                NovelTtsTransportAction.PREVIOUS,
                NovelTtsTransportAction.PAUSE,
                NovelTtsTransportAction.NEXT,
                NovelTtsTransportAction.STOP,
            ),
        )

        snapshot.usesSystemMediaStyle shouldBe true
        snapshot.usesCustomRemoteViews shouldBe false
        snapshot.compactActionIndices shouldContainExactly listOf(0, 1, 2)
        snapshot.compactActions shouldContainExactly listOf(
            NovelTtsTransportAction.PREVIOUS,
            NovelTtsTransportAction.PAUSE,
            NovelTtsTransportAction.NEXT,
        )
        snapshot.expandedActions shouldContainExactly listOf(
            NovelTtsTransportAction.PREVIOUS,
            NovelTtsTransportAction.PAUSE,
            NovelTtsTransportAction.NEXT,
            NovelTtsTransportAction.STOP,
        )
    }

    @Test
    fun `runtime delegates transport actions to the injected session controller`() {
        runBlocking {
            val controller = FakePlaybackController(
                NovelTtsSessionUiState(
                    playbackState = NovelTtsPlaybackState.PLAYING,
                    session = session(),
                ),
            )
            val runtime = NovelTtsPlaybackServiceRuntime(
                controller = controller,
                audioFocusManager = FakeAudioFocusManager(granted = true),
            )

            runtime.handleTransportAction(NovelTtsTransportAction.PAUSE)
            runtime.handleTransportAction(NovelTtsTransportAction.NEXT)

            controller.calls shouldContainExactly listOf("pause", "next")
        }
    }

    @Test
    fun `requestPlaybackStart asks audio focus manager before resuming playback`() {
        runBlocking {
            val controller = FakePlaybackController(
                NovelTtsSessionUiState(
                    playbackState = NovelTtsPlaybackState.PAUSED,
                    session = session(),
                ),
            )
            val audioFocusManager = FakeAudioFocusManager(granted = true)
            val runtime = NovelTtsPlaybackServiceRuntime(
                controller = controller,
                audioFocusManager = audioFocusManager,
            )

            val started = runtime.requestPlaybackStart()

            started shouldBe true
            audioFocusManager.requestCalls shouldBe 1
            controller.calls shouldContain "resume"
        }
    }

    private fun session(): NovelTtsSession {
        val utterance = NovelTtsUtterance(
            id = "utterance-0",
            segmentId = "segment-0",
            text = "Current utterance",
            sourceBlockIndex = 0,
            wordRanges = NovelTtsWordTokenizer.tokenize("Current utterance"),
        )
        return NovelTtsSession(
            chapterId = 1L,
            nextChapterId = 2L,
            model = NovelTtsChapterModel(
                chapterId = 1L,
                chapterTitle = "Chapter 7",
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
            ),
            textSource = NovelTtsTextSource.ORIGINAL,
            utteranceIndex = 0,
            wordIndex = 0,
            autoAdvanceChapter = true,
        )
    }

    private class FakeAudioFocusManager(
        private val granted: Boolean,
    ) : NovelTtsAudioFocusController {
        var requestCalls = 0

        override fun requestPlaybackFocus(): Boolean {
            requestCalls += 1
            return granted
        }

        override fun abandonPlaybackFocus() = Unit
    }

    private class FakePlaybackController(
        initialState: NovelTtsSessionUiState,
    ) : NovelTtsPlaybackController {
        private val mutableState = MutableStateFlow(initialState)
        override val state: StateFlow<NovelTtsSessionUiState> = mutableState

        val calls = mutableListOf<String>()

        override suspend fun pause() {
            calls += "pause"
            mutableState.value = mutableState.value.copy(playbackState = NovelTtsPlaybackState.PAUSED)
        }

        override suspend fun resume() {
            calls += "resume"
            mutableState.value = mutableState.value.copy(playbackState = NovelTtsPlaybackState.PLAYING)
        }

        override suspend fun stop() {
            calls += "stop"
        }

        override suspend fun skipNext() {
            calls += "next"
        }

        override suspend fun skipPrevious() {
            calls += "previous"
        }
    }
}
