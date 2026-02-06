package eu.kanade.tachiyomi.ui.home

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ExtensionUpdateCountsTest {

    @Test
    fun `sum combines all extension update counts`() {
        ExtensionUpdateCounts.sum(1, 2, 3) shouldBe 6
    }

    @Test
    fun `sum handles zeros`() {
        ExtensionUpdateCounts.sum(0, 0, 0) shouldBe 0
    }
}
