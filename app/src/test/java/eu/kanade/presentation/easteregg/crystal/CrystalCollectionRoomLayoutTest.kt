package eu.kanade.presentation.easteregg.crystal

import androidx.compose.material3.lightColorScheme
import eu.kanade.domain.easteregg.crystal.CrystalDayPhase
import eu.kanade.domain.easteregg.crystal.CrystalWeather
import eu.kanade.presentation.theme.AuroraColors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CrystalCollectionRoomLayoutTest {
    @Test
    fun roomViewportHasNoLetterboxAtPhoneAndTabletWidths() {
        for (width in listOf(297f, 317f, 360f, 390f, 600f, 720f)) {
            val height = crystalLivingArtworkHeight(width)
            assertEquals(CRYSTAL_LIVING_ASPECT_RATIO, width / height, .0001f)
            assertEquals(width / 640f, height / 530f, .0001f)
        }
    }

    @Test
    fun ordinaryCollectionNeverShowsPlaybackButtonsEvenInDebugBuild() {
        assertFalse(crystalLivingVisibleControls(false))
        assertTrue(crystalLivingVisibleControls(true))
    }

    @Test
    fun roomColorCopyDoesNotMutateOrReplaceTheChosenAppTheme() {
        val original = AuroraColors.fromColorScheme(lightColorScheme(), isDark = false)
        val background = original.background
        val scene = resolveCrystalLivingColors(
            CrystalDayPhase.NIGHT,
            CrystalWeather.CLEAR,
            CrystalPalette.Moon,
            false,
            false,
            false,
        )
        val room = crystalCollectionRoomColors(original, scene)
        assertEquals(background, original.background)
        assertNotEquals(original.background, room.background)
        assertEquals(original.eInkProfile, room.eInkProfile)
        assertTrue(room.isDark)
    }

    @Test fun negativeCeilingCameraRoundTripsToyAndCrystalHitCoordinates() {
        val viewport = LivingViewport(androidx.compose.ui.unit.IntSize(640, 530))
        val toy = viewport.pixel(542f, 336f)
        val returned = viewport.world(toy)
        assertEquals(542f, returned.x, .0001f)
        assertEquals(336f, returned.y, .0001f)
        assertEquals(48f, viewport.pixel(354f, -62f).y, .0001f)
    }

    @Test fun localTrailHasNoCosmeticDependencyAndStopsWithMotionPolicy() {
        assertTrue(crystalLocalTrailCanRun(true, false, false))
        assertFalse(crystalLocalTrailCanRun(false, false, false))
        assertFalse(crystalLocalTrailCanRun(true, true, false))
        assertFalse(crystalLocalTrailCanRun(true, false, true))
        for (time in listOf(-100f, 0f, .5f, 12f, 18f, 1000f)) {
            for (lane in 0..2) {
                for (step in 0..5) {
                    assertTrue(crystalLocalTrailAlpha(time, lane, step) in 0f..0.13f)
                    assertEquals(
                        crystalLocalTrailAlpha(time, lane, step),
                        crystalLocalTrailAlpha(time + 18f, lane, step),
                        .0001f,
                    )
                }
            }
        }
    }
}
