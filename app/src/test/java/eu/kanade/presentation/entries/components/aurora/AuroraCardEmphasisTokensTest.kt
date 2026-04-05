package eu.kanade.presentation.entries.components.aurora

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AuroraCardEmphasisTokensTest {

    @Test
    fun `new item highlight uses a subtle accent tint`() {
        AURORA_NEW_ITEM_HIGHLIGHT_ALPHA shouldBe 0.08f
    }

    @Test
    fun `dimmed cards stay readable`() {
        AURORA_DIMMED_ITEM_ALPHA shouldBe 0.72f
    }
}
