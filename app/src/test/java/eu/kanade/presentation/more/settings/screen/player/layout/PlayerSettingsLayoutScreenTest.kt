package eu.kanade.presentation.more.settings.screen.player.layout

import eu.kanade.tachiyomi.ui.player.layout.PlayerLayoutConfig
import eu.kanade.tachiyomi.ui.player.layout.PlayerLayoutOrientation
import eu.kanade.tachiyomi.ui.player.layout.PlayerLayoutRegion
import eu.kanade.tachiyomi.ui.player.layout.PlayerLayoutSlot
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

class PlayerSettingsLayoutScreenTest {

    @Test
    fun `layout main screen is a registered searchable settings destination`() {
        val screen: eu.kanade.presentation.more.settings.screen.SearchableSettings = PlayerSettingsLayoutMainScreen
        screen shouldNotBe null
    }

    @Test
    fun `layout screen constructs for portrait orientation`() {
        val screen = PlayerSettingsLayoutScreen(PlayerLayoutOrientation.Portrait)
        screen shouldNotBe null
    }

    @Test
    fun `layout screen constructs for landscape orientation`() {
        val screen = PlayerSettingsLayoutScreen(PlayerLayoutOrientation.Landscape)
        screen shouldNotBe null
    }

    @Test
    fun `default portrait layout maps core controls to bottom left and optional chrome to bottom right`() {
        val config = PlayerLayoutConfig()

        config.regionFor(PlayerLayoutOrientation.Portrait, PlayerLayoutSlot.PlaybackSpeed) shouldBe
            PlayerLayoutRegion.BottomLeft
        config.regionFor(PlayerLayoutOrientation.Portrait, PlayerLayoutSlot.LockControls) shouldBe
            PlayerLayoutRegion.BottomLeft
        config.regionFor(PlayerLayoutOrientation.Portrait, PlayerLayoutSlot.RotateScreen) shouldBe
            PlayerLayoutRegion.BottomLeft
        config.regionFor(PlayerLayoutOrientation.Portrait, PlayerLayoutSlot.SkipIntro) shouldBe
            PlayerLayoutRegion.BottomRight
        config.regionFor(PlayerLayoutOrientation.Portrait, PlayerLayoutSlot.CustomButton) shouldBe
            PlayerLayoutRegion.BottomRight
        config.regionFor(PlayerLayoutOrientation.Portrait, PlayerLayoutSlot.PictureInPicture) shouldBe
            PlayerLayoutRegion.BottomRight
        config.regionFor(PlayerLayoutOrientation.Portrait, PlayerLayoutSlot.AspectRatio) shouldBe
            PlayerLayoutRegion.BottomRight
    }

    @Test
    fun `default landscape layout maps rotate screen to bottom right`() {
        val config = PlayerLayoutConfig()

        config.regionFor(PlayerLayoutOrientation.Landscape, PlayerLayoutSlot.RotateScreen) shouldBe
            PlayerLayoutRegion.BottomRight
    }

    @Test
    fun `hidden slots are excluded from region queries`() {
        val config = PlayerLayoutConfig()
            .withRegion(PlayerLayoutOrientation.Portrait, PlayerLayoutSlot.CustomButton, PlayerLayoutRegion.Hidden)

        config.slotsForRegion(PlayerLayoutOrientation.Portrait, PlayerLayoutRegion.BottomRight) shouldBe
            listOf(
                PlayerLayoutSlot.SkipIntro,
                PlayerLayoutSlot.PictureInPicture,
                PlayerLayoutSlot.AspectRatio,
            )
    }

    @Test
    fun `canHide slots have three allowed regions, core slots have two`() {
        PlayerLayoutSlot.SkipIntro.allowedRegions() shouldContainExactly
            listOf(PlayerLayoutRegion.BottomLeft, PlayerLayoutRegion.BottomRight, PlayerLayoutRegion.Hidden)
        PlayerLayoutSlot.PlaybackSpeed.allowedRegions() shouldContainExactly
            listOf(PlayerLayoutRegion.BottomLeft, PlayerLayoutRegion.BottomRight)
    }

    @Test
    fun `layout config round trips through the preference string`() {
        val config = PlayerLayoutConfig()
            .withRegion(PlayerLayoutOrientation.Landscape, PlayerLayoutSlot.CustomButton, PlayerLayoutRegion.Hidden)

        val encoded = config.toPreferenceValue()
        PlayerLayoutConfig.fromPreferenceValue(encoded) shouldBe config
    }

    @Test
    fun `layout config slots for region matches region for round trip for all orientations`() {
        for (orientation in PlayerLayoutOrientation.entries) {
            val config = PlayerLayoutConfig()
            for (slot in PlayerLayoutSlot.entries) {
                val region = config.regionFor(orientation, slot)
                config.slotsForRegion(orientation, region).contains(slot) shouldBe true
            }
        }
    }
}
