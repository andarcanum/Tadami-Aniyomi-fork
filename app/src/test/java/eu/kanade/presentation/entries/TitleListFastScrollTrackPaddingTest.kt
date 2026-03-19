package eu.kanade.presentation.entries

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.presentation.core.components.resolveFastScrollerTrackTopPadding

class TitleListFastScrollTrackPaddingTest {

    @Test
    fun `track top padding follows live value while thumb is idle`() {
        resolveFastScrollerTrackTopPadding(
            previousTrackTopPadding = 240f,
            liveTrackTopPadding = 180f,
            isThumbDragged = false,
        ) shouldBe 180f
    }

    @Test
    fun `track top padding stays frozen while thumb is actively dragged`() {
        resolveFastScrollerTrackTopPadding(
            previousTrackTopPadding = 240f,
            liveTrackTopPadding = 180f,
            isThumbDragged = true,
        ) shouldBe 240f
    }

    @Test
    fun `track top padding captures live value on first drag frame when no previous value exists`() {
        resolveFastScrollerTrackTopPadding(
            previousTrackTopPadding = null,
            liveTrackTopPadding = 180f,
            isThumbDragged = true,
        ) shouldBe 180f
    }
}
