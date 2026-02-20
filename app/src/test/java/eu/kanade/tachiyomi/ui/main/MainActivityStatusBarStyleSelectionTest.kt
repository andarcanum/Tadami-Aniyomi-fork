package eu.kanade.tachiyomi.ui.main

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MainActivityStatusBarStyleSelectionTest {

    @Test
    fun `aurora home screen always uses dark status bar style`() {
        resolveMainStatusBarStyleMode(
            isHomeScreen = true,
            isAurora = true,
            isLightStatusBarBackground = true,
        ) shouldBe MainStatusBarStyleMode.DARK

        resolveMainStatusBarStyleMode(
            isHomeScreen = true,
            isAurora = true,
            isLightStatusBarBackground = false,
        ) shouldBe MainStatusBarStyleMode.DARK
    }

    @Test
    fun `non aurora home screen keeps transparent light style on light background`() {
        resolveMainStatusBarStyleMode(
            isHomeScreen = true,
            isAurora = false,
            isLightStatusBarBackground = true,
        ) shouldBe MainStatusBarStyleMode.TRANSPARENT_LIGHT
    }

    @Test
    fun `non aurora home screen uses dark style on dark background`() {
        resolveMainStatusBarStyleMode(
            isHomeScreen = true,
            isAurora = false,
            isLightStatusBarBackground = false,
        ) shouldBe MainStatusBarStyleMode.DARK
    }

    @Test
    fun `non home screen uses light style on light background`() {
        resolveMainStatusBarStyleMode(
            isHomeScreen = false,
            isAurora = true,
            isLightStatusBarBackground = true,
        ) shouldBe MainStatusBarStyleMode.LIGHT
    }

    @Test
    fun `non home screen uses dark style on dark background`() {
        resolveMainStatusBarStyleMode(
            isHomeScreen = false,
            isAurora = false,
            isLightStatusBarBackground = false,
        ) shouldBe MainStatusBarStyleMode.DARK
    }
}
