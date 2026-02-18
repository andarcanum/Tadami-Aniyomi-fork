package eu.kanade.presentation.achievement.components

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AchievementPopupCompactTokensTest {

    @Test
    fun `unlock banner tokens are compact by 25 percent`() {
        assertEquals(0.75f, AchievementPopupSizeTokens.COMPACT_SCALE)
        assertEquals(12f, AchievementPopupSizeTokens.unlockOuterHorizontalPadding.value)
        assertEquals(6f, AchievementPopupSizeTokens.unlockOuterVerticalPadding.value)
        assertEquals(42f, AchievementPopupSizeTokens.unlockIconSize.value)
        assertEquals(15f, AchievementPopupSizeTokens.unlockContainerCornerRadius.value)
    }

    @Test
    fun `group notification tokens are compact by 25 percent`() {
        assertEquals(12f, AchievementPopupSizeTokens.groupOuterHorizontalPadding.value)
        assertEquals(6f, AchievementPopupSizeTokens.groupOuterVerticalPadding.value)
        assertEquals(42f, AchievementPopupSizeTokens.groupIconSize.value)
        assertEquals(24f, AchievementPopupSizeTokens.groupArrowSize.value)
        assertEquals(15f, AchievementPopupSizeTokens.groupContainerCornerRadius.value)
    }

    @Test
    fun `main activity overlay padding token is compact`() {
        assertEquals(6f, AchievementPopupSizeTokens.overlayTopPadding.value)
    }
}
