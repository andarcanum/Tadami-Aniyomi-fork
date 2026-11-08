package eu.kanade.presentation.easteregg.crystal

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CrystalLivingLabVisibilityTest {
    @Test fun hiddenBackgroundOrObscuredSceneCannotRunItsClockOrObserveRain() {
        assertTrue(isCrystalLivingViewActive(true, true, true, true, false))
        assertFalse(isCrystalLivingViewActive(false, true, true, true, false))
        assertFalse(isCrystalLivingViewActive(true, false, true, true, false))
        assertFalse(isCrystalLivingViewActive(true, true, false, true, false))
        assertFalse(isCrystalLivingViewActive(true, true, true, false, false))
        assertFalse(isCrystalLivingViewActive(true, true, true, true, true))
    }

    @Test fun reducedPausedOrUnloadedSceneHasNoFrameSource() {
        assertTrue(isCrystalLivingMotionActive(true, false, false, true))
        assertFalse(isCrystalLivingMotionActive(false, false, false, true))
        assertFalse(isCrystalLivingMotionActive(true, true, false, true))
        assertFalse(isCrystalLivingMotionActive(true, false, true, true))
        assertFalse(isCrystalLivingMotionActive(true, false, false, false))
    }

    @Test fun onlyNormalDebugBuildHasLab() {
        assertTrue(isCrystalLivingLabAvailable(true, "debug"))
        assertFalse(isCrystalLivingLabAvailable(false, "release"))
        assertFalse(isCrystalLivingLabAvailable(true, "benchmark"))
        assertFalse(isCrystalLivingLabAvailable(false, "debug"))
    }
}
