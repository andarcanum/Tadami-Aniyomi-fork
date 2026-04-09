package eu.kanade.tachiyomi.ui.main

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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

    @Test
    fun `main activity keeps edge-to-edge updates enabled for novel reader screen`() {
        shouldMainActivityApplyEdgeToEdge(
            eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreen(chapterId = 1L),
        ) shouldBe true
    }

    @Test
    fun `novel reader still preserves the home shell status bar contract for light and dark themes`() {
        shouldMainActivityApplyEdgeToEdge(
            eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreen(chapterId = 1L),
        ) shouldBe true

        resolveMainStatusBarStyleMode(
            isHomeScreen = true,
            isAurora = false,
            isLightStatusBarBackground = true,
        ) shouldBe MainStatusBarStyleMode.TRANSPARENT_LIGHT

        resolveMainStatusBarStyleMode(
            isHomeScreen = true,
            isAurora = false,
            isLightStatusBarBackground = false,
        ) shouldBe MainStatusBarStyleMode.DARK
    }

    @Test
    fun `novel exit path keeps the home shell status bar contract for light and dark themes`() {
        shouldMainActivityApplyEdgeToEdge(
            eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreen(chapterId = 1L),
        ) shouldBe false

        resolveMainStatusBarStyleMode(
            isHomeScreen = true,
            isAurora = false,
            isLightStatusBarBackground = true,
        ) shouldBe MainStatusBarStyleMode.TRANSPARENT_LIGHT

        resolveMainStatusBarStyleMode(
            isHomeScreen = true,
            isAurora = false,
            isLightStatusBarBackground = false,
        ) shouldBe MainStatusBarStyleMode.DARK
    }

    @Test
    fun `main activity edge-to-edge updates stay enabled for regular screens`() {
        shouldMainActivityApplyEdgeToEdge(Any()) shouldBe true
    }

    @Test
    fun `main activity startup window background prefers last novel reader backdrop`() {
        resolveMainActivityWindowBackgroundArgb(
            readerBackdropColor = Color(0xFFE8DDBD),
            fallbackColorArgb = Color.White.toArgb(),
        ) shouldBe Color(0xFFE8DDBD).toArgb()
    }

    @Test
    fun `main activity startup window background falls back to theme when reader backdrop missing`() {
        resolveMainActivityWindowBackgroundArgb(
            readerBackdropColor = null,
            fallbackColorArgb = Color(0xFF121212).toArgb(),
        ) shouldBe Color(0xFF121212).toArgb()
    }
}
