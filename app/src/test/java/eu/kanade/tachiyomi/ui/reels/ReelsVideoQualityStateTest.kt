package eu.kanade.tachiyomi.ui.reels

import eu.kanade.tachiyomi.ui.reels.components.ReelsQualityDialogMode
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.reels.anime.model.ReelsQualityPreference

class ReelsVideoQualityStateTest {

    @Test
    fun `initial quality state has no owner and defaults to source`() {
        val state = ReelsQualityState()

        state.owner shouldBe null
        state.global shouldBe ReelsQualityPreference.Source
        state.items shouldBe emptyMap()
        state.selectionRevision shouldBe 0L
    }

    @Test
    fun `item state keeps the requested preference separate from the actual binding`() {
        val owner = ReelsQualityOwner("token", 1L, "42")
        val state = ReelsQualityItemState(
            owner = owner,
            itemId = "tdlib:-1/2",
            requested = ReelsQualityPreference.Resolution(720),
            usingGlobal = false,
            catalogStatus = ReelsQualityCatalogStatus.LOADING,
            options = emptyList(),
            actual = null,
            switching = true,
            failure = null,
            revision = 3L,
        )

        state.requested shouldBe ReelsQualityPreference.Resolution(720)
        state.actual shouldBe null
        state.switching shouldBe true
        state.revision shouldBe 3L
    }

    @Test
    fun `dialog exposes only the approved current and global modes`() {
        ReelsQualityDialogMode.values().toSet() shouldBe
            setOf(ReelsQualityDialogMode.VIDEO, ReelsQualityDialogMode.GLOBAL)
    }
}
