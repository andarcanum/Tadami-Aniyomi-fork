package eu.kanade.presentation.browse

import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class BrowseScreenAuroraLayoutTest {

    @Test
    fun `quick actions use settings style card metrics`() {
        resolveBrowseQuickActionsRenderSpec() shouldBe BrowseQuickActionsRenderSpec(
            horizontalInset = 16.dp,
            verticalInset = 16.dp,
            itemGap = 12.dp,
            primaryBreakGap = 4.dp,
            minCardHeight = 72.dp,
            leadingIconContainerSize = 48.dp,
        )
    }
}
