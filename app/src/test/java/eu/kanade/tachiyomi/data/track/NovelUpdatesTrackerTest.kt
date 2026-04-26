package eu.kanade.tachiyomi.data.track

import eu.kanade.tachiyomi.data.track.novelupdates.NovelUpdates
import eu.kanade.tachiyomi.data.track.novelupdates.resolveReadingListId
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelUpdatesTrackerTest {

    @Test
    fun `uses custom list mapping when enabled`() {
        resolveReadingListId(
            status = NovelUpdates.READING,
            customEnabled = true,
            mappingJson = """{"1":"42"}""",
        ) shouldBe "42"
    }

    @Test
    fun `falls back to default mapping when custom mapping is disabled`() {
        resolveReadingListId(
            status = NovelUpdates.COMPLETED,
            customEnabled = false,
            mappingJson = """{"2":"99"}""",
        ) shouldBe "1"
    }
}
