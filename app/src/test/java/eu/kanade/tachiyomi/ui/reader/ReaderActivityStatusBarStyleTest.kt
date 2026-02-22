package eu.kanade.tachiyomi.ui.reader

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ReaderActivityStatusBarStyleTest {

    @Test
    fun `fullscreen with hidden menu always uses light status bar icons`() {
        resolveReaderLightStatusBars(
            menuVisible = false,
            fullscreen = true,
            defaultLightStatusBars = true,
        ) shouldBe false
    }

    @Test
    fun `visible menu uses theme default status bar icon style`() {
        resolveReaderLightStatusBars(
            menuVisible = true,
            fullscreen = true,
            defaultLightStatusBars = true,
        ) shouldBe true

        resolveReaderLightStatusBars(
            menuVisible = true,
            fullscreen = true,
            defaultLightStatusBars = false,
        ) shouldBe false
    }

    @Test
    fun `non fullscreen uses theme default status bar icon style`() {
        resolveReaderLightStatusBars(
            menuVisible = false,
            fullscreen = false,
            defaultLightStatusBars = true,
        ) shouldBe true
    }
}
