package eu.kanade.tachiyomi.ui.home

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType

class HomeHubStageReanchorTest {

    private fun stageItem(title: String, shownAt: Long? = null) = HomeHubDiscoveryItem(
        title = title,
        cleanTitle = title.lowercase(),
        coverUrl = null,
        seedTitle = null,
        reasonPayload = null,
        provider = "test",
        rowType = DiscoveryRowType.TREND,
        mediaType = DiscoveryMediaType.NOVEL,
        shownAt = shownAt,
    )

    @Test
    fun `composition without items change keeps center`() {
        resolveStageReanchorCenter(
            itemsChanged = false,
            previousFocusedTitle = "solo leveling",
            newOrderedTitles = listOf("a", "solo leveling", "b"),
            currentCenter = 7,
        ) shouldBe 7
    }

    @Test
    fun `items change moves center to focused title new index`() {
        resolveStageReanchorCenter(
            itemsChanged = true,
            previousFocusedTitle = "solo leveling",
            newOrderedTitles = listOf("a", "b", "c", "solo leveling", "d"),
            currentCenter = 9,
        ) shouldBe 3
    }

    @Test
    fun `items change with same index keeps center`() {
        resolveStageReanchorCenter(
            itemsChanged = true,
            previousFocusedTitle = "b",
            newOrderedTitles = listOf("a", "b", "c"),
            currentCenter = 1,
        ) shouldBe 1
    }

    @Test
    fun `focused title removed by refresh keeps scene at current slot modulo size`() {
        resolveStageReanchorCenter(
            itemsChanged = true,
            previousFocusedTitle = "hidden title",
            newOrderedTitles = listOf("a", "b", "c"),
            currentCenter = 5,
        ) shouldBe 2
    }

    @Test
    fun `no previous focus keeps center`() {
        resolveStageReanchorCenter(
            itemsChanged = true,
            previousFocusedTitle = null,
            newOrderedTitles = listOf("a", "b"),
            currentCenter = 4,
        ) shouldBe 4
    }

    @Test
    fun `defensive empty new list keeps center`() {
        resolveStageReanchorCenter(
            itemsChanged = true,
            previousFocusedTitle = "a",
            newOrderedTitles = emptyList(),
            currentCenter = 2,
        ) shouldBe 2
    }

    @Test
    fun `restoreOrPatchStageOrder preserves positions when shownAt changes or item replaced at same offset`() {
        val savedTitles = listOf("a", "b", "c", "d")
        val updatedPool = listOf(
            stageItem("a", shownAt = 100L),
            stageItem("c"),
            stageItem("d"),
            stageItem("x"),
        )
        val restored = restoreOrPatchStageOrder(
            items = updatedPool,
            offset = 3,
            savedCleanTitles = savedTitles,
            savedOffset = 3,
        )
        restored.map { it.cleanTitle } shouldBe listOf("a", "x", "c", "d")
        restored.first().shownAt shouldBe 100L
    }
}
