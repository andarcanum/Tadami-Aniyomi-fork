package eu.kanade.presentation.more.settings

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AuroraSettingsTopBarChromeTest {

    @Test
    fun `aurora settings top bar offset limit matches measured height`() {
        resolveAuroraSettingsTopBarHeightOffsetLimit(heightPx = 240) shouldBe -240f
    }

    @Test
    fun `aurora settings top bar offset limit stays zero for empty height`() {
        resolveAuroraSettingsTopBarHeightOffsetLimit(heightPx = 0) shouldBe 0f
    }
}
