package eu.kanade.presentation.util

import eu.kanade.domain.ui.model.NavTransitionMode
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NavigationTransitionResolverTest {

    @Test
    fun `returns none when system animation scale is zero`() {
        resolveNavigationTransitionMode(
            selectedMode = NavTransitionMode.MODERN,
            animatorDurationScale = 0f,
            isPowerSaveMode = false,
        ) shouldBe ResolvedNavigationTransitionMode.NONE
    }

    @Test
    fun `auto mode resolves to modern when power save is off`() {
        resolveNavigationTransitionMode(
            selectedMode = NavTransitionMode.AUTO,
            animatorDurationScale = 1f,
            isPowerSaveMode = false,
        ) shouldBe ResolvedNavigationTransitionMode.MODERN
    }

    @Test
    fun `auto mode resolves to legacy when power save is on`() {
        resolveNavigationTransitionMode(
            selectedMode = NavTransitionMode.AUTO,
            animatorDurationScale = 1f,
            isPowerSaveMode = true,
        ) shouldBe ResolvedNavigationTransitionMode.LEGACY
    }

    @Test
    fun `modern mode ignores power save`() {
        resolveNavigationTransitionMode(
            selectedMode = NavTransitionMode.MODERN,
            animatorDurationScale = 1f,
            isPowerSaveMode = true,
        ) shouldBe ResolvedNavigationTransitionMode.MODERN
    }

    @Test
    fun `legacy mode remains legacy`() {
        resolveNavigationTransitionMode(
            selectedMode = NavTransitionMode.LEGACY,
            animatorDurationScale = 1f,
            isPowerSaveMode = false,
        ) shouldBe ResolvedNavigationTransitionMode.LEGACY
    }
}
