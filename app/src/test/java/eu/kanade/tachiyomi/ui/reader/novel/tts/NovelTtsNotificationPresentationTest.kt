package eu.kanade.tachiyomi.ui.reader.novel.tts

import io.kotest.matchers.shouldBe
import org.junit.Test

class NovelTtsNotificationPresentationTest {

    @Test
    fun `notification layout snapshot delegates layout to the system media template`() {
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
    }
}
