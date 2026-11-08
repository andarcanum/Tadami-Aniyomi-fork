package eu.kanade.tachiyomi.ui.more

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.Test

class MoreMenuEntriesTest {

    private val all = MoreEntryId.entries.toSet()

    @Test
    fun `default layout is declaration order with nothing hidden`() {
        val layout = resolveMoreMenuLayout(all, savedOrderRaw = "", hiddenRaw = emptySet())

        layout.visible shouldContainExactly MoreEntryId.entries.toList()
        layout.hidden shouldBe emptyList()
    }

    @Test
    fun `availability follows runtime flags`() {
        val available = availableMoreEntryIds(
            showReelsEntry = false,
            latticeGridAvailable = false,
            isDebugBuild = false,
        )

        available.size shouldBe 17
        available shouldNotContain MoreEntryId.REELS
        available shouldNotContain MoreEntryId.LATTICE_GRID
        available shouldNotContain MoreEntryId.DEBUG_APP_UPDATE
        available shouldNotContain MoreEntryId.DEBUG_CRYSTAL
        available.size shouldBe availableMoreEntryIds(
            showReelsEntry = true,
            latticeGridAvailable = true,
            isDebugBuild = true,
        ).size - 8
    }

    @Test
    fun `saved order wins and unknown entries are appended in default order`() {
        val layout = resolveMoreMenuLayout(
            available = setOf(
                MoreEntryId.HELP,
                MoreEntryId.SETTINGS,
                MoreEntryId.ABOUT,
                MoreEntryId.STATS,
            ),
            savedOrderRaw = "HELP,SETTINGS,ABOUT",
            hiddenRaw = emptySet(),
        )

        layout.visible shouldContainExactly listOf(
            MoreEntryId.HELP,
            MoreEntryId.SETTINGS,
            MoreEntryId.ABOUT,
            MoreEntryId.STATS,
        )
    }

    @Test
    fun `unknown and stale ids are ignored`() {
        val layout = resolveMoreMenuLayout(
            available = setOf(MoreEntryId.SETTINGS, MoreEntryId.HELP),
            savedOrderRaw = "HELP,BOGUS_ENTRY,SETTINGS",
            hiddenRaw = setOf("ALSO_BOGUS"),
        )

        layout.visible shouldContainExactly listOf(MoreEntryId.HELP, MoreEntryId.SETTINGS)
        layout.hidden shouldBe emptyList()
    }

    @Test
    fun `unavailable entries never render even when saved`() {
        val layout = resolveMoreMenuLayout(
            available = setOf(MoreEntryId.SETTINGS, MoreEntryId.HELP),
            savedOrderRaw = "REELS,HELP,SETTINGS",
            hiddenRaw = emptySet(),
        )

        layout.visible shouldContainExactly listOf(MoreEntryId.HELP, MoreEntryId.SETTINGS)
    }

    @Test
    fun `hidden entries leave the visible list and keep the resolved order`() {
        val layout = resolveMoreMenuLayout(
            available = setOf(
                MoreEntryId.SETTINGS,
                MoreEntryId.STATS,
                MoreEntryId.ABOUT,
            ),
            savedOrderRaw = "ABOUT,STATS,SETTINGS",
            hiddenRaw = setOf("STATS", "ABOUT"),
        )

        layout.visible shouldContainExactly listOf(MoreEntryId.SETTINGS)
        layout.hidden shouldContainExactly listOf(MoreEntryId.ABOUT, MoreEntryId.STATS)
    }

    @Test
    fun `moved tab can never be hidden`() {
        val layout = resolveMoreMenuLayout(
            available = setOf(MoreEntryId.MOVED_TAB, MoreEntryId.SETTINGS),
            savedOrderRaw = "",
            hiddenRaw = setOf("MOVED_TAB"),
        )

        layout.visible shouldContainExactly listOf(MoreEntryId.MOVED_TAB, MoreEntryId.SETTINGS)
        layout.hidden shouldBe emptyList()
    }

    @Test
    fun `order serialization round trips and drops unknown ids`() {
        serializeMoreMenuOrder(listOf(MoreEntryId.SETTINGS, MoreEntryId.HELP)) shouldBe "SETTINGS,HELP"
        serializeMoreMenuOrder(emptyList()) shouldBe ""
        parseMoreMenuOrder("SETTINGS,HELP") shouldContainExactly listOf(MoreEntryId.SETTINGS, MoreEntryId.HELP)
        parseMoreMenuOrder("SETTINGS,BOGUS,HELP") shouldContainExactly listOf(
            MoreEntryId.SETTINGS,
            MoreEntryId.HELP,
        )
        parseMoreMenuOrder("") shouldBe emptyList()
    }

    @Test
    fun `move reorders and clamps out of range targets`() {
        val order = listOf(MoreEntryId.SETTINGS, MoreEntryId.STATS, MoreEntryId.HELP)

        moveMoreMenuEntry(order, from = 0, to = 2) shouldContainExactly listOf(
            MoreEntryId.STATS,
            MoreEntryId.HELP,
            MoreEntryId.SETTINGS,
        )
        moveMoreMenuEntry(order, from = 2, to = 0) shouldContainExactly listOf(
            MoreEntryId.HELP,
            MoreEntryId.SETTINGS,
            MoreEntryId.STATS,
        )
        moveMoreMenuEntry(order, from = 0, to = 99) shouldContainExactly listOf(
            MoreEntryId.STATS,
            MoreEntryId.HELP,
            MoreEntryId.SETTINGS,
        )
        moveMoreMenuEntry(order, from = -1, to = 1) shouldContainExactly order
        moveMoreMenuEntry(order, from = 1, to = 1) shouldContainExactly order
    }
}
