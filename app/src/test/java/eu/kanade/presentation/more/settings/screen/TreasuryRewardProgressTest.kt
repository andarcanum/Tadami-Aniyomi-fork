package eu.kanade.presentation.more.settings.screen

import eu.kanade.domain.ui.model.AppTheme
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TreasuryRewardProgressTest {
    @Test
    fun `progress counts only visible treasury reward ids`() {
        val hiddenTheme = AppTheme.entries.first(AppTheme::isHidden)
        val progress = calculateTreasuryRewardProgress(
            unlockedUnlockables = setOf(
                "title_a",
                "aura_one",
                "theme_${hiddenTheme.name}",
                "legacy_reward_from_050",
                "badge_old_profile_reward",
                "unrelated_debug_unlock",
            ),
            presetIds = listOf("title_a", "title_b"),
            auraIds = listOf("aura_one", "aura_two"),
            hiddenThemes = listOf(hiddenTheme),
        )

        assertEquals(3, progress.unlocked)
        assertEquals(5, progress.total)
    }

    @Test
    fun `progress deduplicates aliases and catalog ids`() {
        val hiddenTheme = AppTheme.entries.first(AppTheme::isHidden)
        val progress = calculateTreasuryRewardProgress(
            unlockedUnlockables = setOf(
                "title_a",
                "theme_${hiddenTheme.name}",
                "theme_${hiddenTheme.name.lowercase()}",
            ),
            presetIds = listOf("title_a", "title_a"),
            auraIds = listOf("aura_one", "aura_one"),
            hiddenThemes = listOf(hiddenTheme, hiddenTheme),
        )

        assertEquals(2, progress.unlocked)
        assertEquals(3, progress.total)
    }
}
