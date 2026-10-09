package eu.kanade.presentation.entries.manga

import eu.kanade.presentation.theme.aurora.adaptive.AuroraDeviceClass
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MangaScreenAuroraLayoutTest {

    @Test
    fun `two pane aurora layout is enabled only for tablet expanded`() {
        shouldUseMangaAuroraTwoPane(AuroraDeviceClass.Phone) shouldBe false
        shouldUseMangaAuroraTwoPane(AuroraDeviceClass.TabletCompact) shouldBe false
        shouldUseMangaAuroraTwoPane(AuroraDeviceClass.TabletExpanded) shouldBe true
    }

    @Test
    fun `fast scroller uses pane scoped placement only in two pane manga layout`() {
        shouldUseMangaAuroraPaneScopedFastScroller(useTwoPaneLayout = false) shouldBe false
        shouldUseMangaAuroraPaneScopedFastScroller(useTwoPaneLayout = true) shouldBe true
    }

    @Test
    fun `aurora manga overflow actions include migrate when callback is available`() {
        resolveMangaAuroraOverflowActions(
            hasGlobalSearch = true,
            hasShare = true,
            hasSettings = true,
            hasNotes = false,
            hasMigrate = true,
        ).contains(AuroraMangaOverflowAction.Migrate) shouldBe true
    }

    @Test
    fun `resolver includes delete actions only when downloads exist`() {
        val withDownloads = resolveMangaAuroraOverflowActions(
            hasGlobalSearch = false,
            hasShare = false,
            hasSettings = false,
            hasNotes = false,
            hasMigrate = false,
            hasDeleteDownloads = true,
            hasDeleteRead = true,
        )
        withDownloads shouldContain AuroraMangaOverflowAction.DeleteDownloads
        withDownloads shouldContain AuroraMangaOverflowAction.DeleteRead

        val without = resolveMangaAuroraOverflowActions(
            hasGlobalSearch = false,
            hasShare = false,
            hasSettings = false,
            hasNotes = false,
            hasMigrate = false,
        )
        without shouldNotContain AuroraMangaOverflowAction.DeleteDownloads
        without shouldNotContain AuroraMangaOverflowAction.DeleteRead
    }
}
