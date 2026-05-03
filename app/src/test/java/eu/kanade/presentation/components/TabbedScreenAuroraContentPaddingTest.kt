package eu.kanade.presentation.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Regression tests for [resolveTabbedScreenAuroraContentPadding].
 *
 * The host (e.g. `HomeScreen`) intentionally extends the body under the in-app NavigationBar so it
 * can render glassy/edge-to-edge surfaces. Without these calculations, every tab's LazyColumn
 * would scroll its last items behind the bar — which on the Home screen "Recently Added" grid
 * meant the title and chapter count were hidden by the bar even after scrolling all the way down.
 */
class TabbedScreenAuroraContentPaddingTest {

    @Test
    fun `falls back to extra bottom only when no host padding is provided`() {
        val resolved = resolveTabbedScreenAuroraContentPadding(
            hostContentPadding = null,
            layoutDirection = LayoutDirection.Ltr,
            extraBottom = 16.dp,
        )

        assertEquals(0.dp, resolved.calculateTopPadding())
        assertEquals(16.dp, resolved.calculateBottomPadding())
        assertEquals(0.dp, resolved.calculateStartPadding(LayoutDirection.Ltr))
        assertEquals(0.dp, resolved.calculateEndPadding(LayoutDirection.Ltr))
    }

    @Test
    fun `bottom inset stacks the host bottom navigation height on top of extra padding`() {
        val resolved = resolveTabbedScreenAuroraContentPadding(
            hostContentPadding = PaddingValues(top = 56.dp, bottom = 80.dp),
            layoutDirection = LayoutDirection.Ltr,
            extraBottom = 16.dp,
        )

        assertEquals(96.dp, resolved.calculateBottomPadding())
        // Top padding from the host is intentionally NOT propagated; the host applies it itself.
        assertEquals(0.dp, resolved.calculateTopPadding())
    }

    @Test
    fun `start and end insets propagate so navigation rail does not overlap content`() {
        val resolved = resolveTabbedScreenAuroraContentPadding(
            hostContentPadding = PaddingValues(start = 80.dp, end = 0.dp, bottom = 0.dp),
            layoutDirection = LayoutDirection.Ltr,
            extraBottom = 16.dp,
        )

        assertEquals(80.dp, resolved.calculateStartPadding(LayoutDirection.Ltr))
        assertEquals(0.dp, resolved.calculateEndPadding(LayoutDirection.Ltr))
        assertEquals(16.dp, resolved.calculateBottomPadding())
    }

    @Test
    fun `start and end insets respect right-to-left layouts`() {
        val resolved = resolveTabbedScreenAuroraContentPadding(
            hostContentPadding = PaddingValues(start = 80.dp, end = 12.dp, bottom = 0.dp),
            layoutDirection = LayoutDirection.Rtl,
            extraBottom = 16.dp,
        )

        // Under Rtl, "start" of the source PaddingValues is on the right — but we read it back via
        // calculateStartPadding(Rtl), which reports the side that is currently the start. The
        // helper queries with the supplied direction, so the values it captured should round-trip
        // through Rtl as well.
        assertEquals(80.dp, resolved.calculateStartPadding(LayoutDirection.Rtl))
        assertEquals(12.dp, resolved.calculateEndPadding(LayoutDirection.Rtl))
    }

    @Test
    fun `extra bottom defaults to 16 dp matching the legacy hard-coded value`() {
        val resolved = resolveTabbedScreenAuroraContentPadding(
            hostContentPadding = null,
            layoutDirection = LayoutDirection.Ltr,
        )

        assertEquals(16.dp, resolved.calculateBottomPadding())
    }
}
