package eu.kanade.tachiyomi.ui.home

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType

class HomeHubStageHeroOrderTest {

    private fun item(title: String, shownAt: Long? = null) = HomeHubDiscoveryItem(
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
    fun `unseen titles come before shown titles`() {
        val shown = listOf(item("shown a", shownAt = 100L), item("shown b", shownAt = 200L))
        val fresh = listOf(item("fresh a"), item("fresh b"))
        val ordered = stageHeroOrder(shown + fresh, seed = 7)
        ordered.map { it.shownAt == null } shouldBe listOf(true, true, false, false)
    }

    @Test
    fun `order is deterministic for the same seed`() {
        val items = (1..20).map { item("t$it", shownAt = if (it % 2 == 0) it * 10L else null) }
        stageHeroOrder(items, seed = 3) shouldBe stageHeroOrder(items, seed = 3)
    }

    @Test
    fun `reroll seed changes the order`() {
        val items = (1..20).map { item("t$it", shownAt = if (it % 2 == 0) it * 10L else null) }
        val same = stageHeroOrder(items, seed = 5)
        val other = stageHeroOrder(items, seed = 6)
        (same == other) shouldBe false
    }

    @Test
    fun `all-fresh pool keeps legacy shuffle behaviour`() {
        val items = (1..10).map { item("t$it") }
        val ordered = stageHeroOrder(items, seed = 11)
        ordered.size shouldBe items.size
        ordered.containsAll(items) shouldBe true
        // Детерминизм прежнего контракта: тот же seed — тот же shuffle.
        ordered shouldBe items.shuffled(kotlin.random.Random(11))
    }

    @Test
    fun `single item list passes through untouched`() {
        val single = listOf(item("only"))
        stageHeroOrder(single, seed = 1) shouldBe single
    }

    @Test
    fun `no content is dropped by grouping`() {
        val items = (1..30).map { item("t$it", shownAt = if (it % 3 == 0) it.toLong() else null) }
        val ordered = stageHeroOrder(items, seed = 2)
        ordered.size shouldBe 30
        ordered.toSet() shouldBe items.toSet()
    }

    @Test
    fun `stageHeroOrder with currentVisibleTitles prioritizes non-visible items`() {
        val items = (1..20).map { item("t$it") }
        val visible = setOf("t1", "t2", "t3", "t4", "t5")
        val ordered = stageHeroOrder(items, seed = 42, currentVisibleTitles = visible)
        // Non-visible items (15 items) should come before the visible ones
        val first15Titles = ordered.take(15).map { it.cleanTitle }.toSet()
        first15Titles.intersect(visible).isEmpty() shouldBe true
    }

    @Test
    fun `stageRerollTargetCenter produces disjoint visible slots when pool has 10 or more items`() {
        val items = (1..20).map { item("t$it") }
        val currentCenter = 0
        val ordered = stageHeroOrder(items, seed = 0)
        val initialVisible = (-2..2).map { stageItemIndex(currentCenter, it, ordered.size) }
            .map { ordered[it].cleanTitle }
            .toSet()

        val nextOrdered = stageHeroOrder(items, seed = 1, currentVisibleTitles = initialVisible)
        val targetCenter = stageRerollTargetCenter(currentCenter, initialVisible, nextOrdered)

        val newVisible = (-2..2).map { stageItemIndex(targetCenter, it, nextOrdered.size) }
            .map { nextOrdered[it].cleanTitle }
            .toSet()

        newVisible.intersect(initialVisible).isEmpty() shouldBe true
    }

    @Test
    fun `stageRerollTargetCenter smoothly advances for small pools`() {
        val items = (1..4).map { item("t$it") }
        val currentCenter = 2
        val visible = items.map { it.cleanTitle }.toSet()
        val targetCenter = stageRerollTargetCenter(currentCenter, visible, items)
        targetCenter shouldBe 3
    }

    @Test
    fun `patchStageOrder retains surviving card positions and replaces consumed item in place`() {
        val current = listOf(item("a"), item("b"), item("c"), item("d"), item("e"))
        // "c" was consumed/removed from pool, "x" is fresh replacement candidate
        val newPool = listOf(item("a"), item("b"), item("d"), item("e"), item("x"))
        val patched = patchStageOrder(current, newPool)

        patched.size shouldBe 5
        patched[0].cleanTitle shouldBe "a"
        patched[1].cleanTitle shouldBe "b"
        patched[2].cleanTitle shouldBe "x" // replaced in slot 2 where "c" was
        patched[3].cleanTitle shouldBe "d"
        patched[4].cleanTitle shouldBe "e"
    }
}
