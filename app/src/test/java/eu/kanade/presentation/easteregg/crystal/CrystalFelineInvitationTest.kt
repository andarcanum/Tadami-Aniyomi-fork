package eu.kanade.presentation.easteregg.crystal

import org.junit.jupiter.api.Test

class CrystalFelineInvitationTest {
    @Test fun pressFeedbackHonorsDisabledInkAndSystemMotion() {
        for (pressed in listOf(false, true)) {
            for (enabled in listOf(false, true)) {
                for (ink in listOf(false, true)) {
                    for (motion in listOf(false, true)) {
                        val expected = if (pressed && enabled && !ink && motion) .985f else 1f
                        check(crystalFelineInvitationPressScale(pressed, enabled, ink, motion) == expected)
                    }
                }
            }
        }
    }

    @Test fun threadInvitationRetains48DpTargetWhenPressed() {
        check(CRYSTAL_FELINE_INVITATION_MIN_HEIGHT_DP == 58f)
        check(CRYSTAL_FELINE_INVITATION_MIN_HEIGHT_DP * .985f >= 48f)
    }
}
