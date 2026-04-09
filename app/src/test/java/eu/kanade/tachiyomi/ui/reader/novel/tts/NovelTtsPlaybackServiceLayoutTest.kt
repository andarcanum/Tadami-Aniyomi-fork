package eu.kanade.tachiyomi.ui.reader.novel.tts

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.Test

class NovelTtsPlaybackServiceLayoutTest {

    @Test
    fun `notification layout uses system media style actions`() {
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
}
