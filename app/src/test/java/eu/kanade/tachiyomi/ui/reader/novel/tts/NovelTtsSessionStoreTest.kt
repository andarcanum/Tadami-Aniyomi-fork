package eu.kanade.tachiyomi.ui.reader.novel.tts

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class NovelTtsSessionStoreTest {

    @AfterEach
    fun tearDown() {
        runBlocking {
            SharedNovelTtsSessionStore.clearCheckpoint()
        }
    }

    @Test
    fun `shared session store retains checkpoint across consumers`() {
        runBlocking {
            SharedNovelTtsSessionStore.saveCheckpoint(
                NovelTtsSessionCheckpoint(
                    chapterId = 7L,
                    utteranceId = "chapter-7-utterance-1",
                    segmentId = "chapter-7-segment-1",
                    wordIndex = 3,
                    textSource = NovelTtsTextSource.ORIGINAL,
                    autoAdvanceChapter = true,
                ),
            )

            SharedNovelTtsSessionStore.loadCheckpoint() shouldBe NovelTtsSessionCheckpoint(
                chapterId = 7L,
                utteranceId = "chapter-7-utterance-1",
                segmentId = "chapter-7-segment-1",
                wordIndex = 3,
                textSource = NovelTtsTextSource.ORIGINAL,
                autoAdvanceChapter = true,
            )
        }
    }
}
