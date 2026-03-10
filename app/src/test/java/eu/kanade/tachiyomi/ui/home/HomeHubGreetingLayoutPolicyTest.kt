package eu.kanade.tachiyomi.ui.home

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class HomeHubGreetingLayoutPolicyTest {

    @Test
    fun `resolveGreetingLineLimit follows measured line count and clamps to range`() {
        resolveGreetingLineLimit(measuredLineCount = 1) shouldBe 1
        resolveGreetingLineLimit(measuredLineCount = 2) shouldBe 2
        resolveGreetingLineLimit(measuredLineCount = 3) shouldBe 3
        resolveGreetingLineLimit(measuredLineCount = 99) shouldBe 4
    }

    @Test
    fun `resolveHomeHeaderCanvasHeightDp grows progressively by lines`() {
        resolveHomeHeaderCanvasHeightDp(lineLimit = 1) shouldBe 72
        resolveHomeHeaderCanvasHeightDp(lineLimit = 2) shouldBe 76
        resolveHomeHeaderCanvasHeightDp(lineLimit = 3) shouldBe 80
        resolveHomeHeaderCanvasHeightDp(lineLimit = 4) shouldBe 88
    }

    @Test
    fun `resolveGreetingSlotHeightPx grows progressively by lines`() {
        resolveGreetingSlotHeightPx(lineLimit = 1) shouldBe 24f
        resolveGreetingSlotHeightPx(lineLimit = 2) shouldBe 36f
        resolveGreetingSlotHeightPx(lineLimit = 3) shouldBe 48f
        resolveGreetingSlotHeightPx(lineLimit = 4) shouldBe 56f
    }

    @Test
    fun `resolveNicknameYForGreetingOverlap soft attaches old default nickname for single line greeting`() {
        resolveNicknameYForGreetingOverlap(
            nicknameY = 32f,
            greetingY = 0f,
            greetingHeight = 24f,
            minGap = 2f,
        ) shouldBe 26f
    }

    @Test
    fun `resolveNicknameYForGreetingOverlap keeps minimum gap for taller greeting`() {
        resolveNicknameYForGreetingOverlap(
            nicknameY = 32f,
            greetingY = 0f,
            greetingHeight = 36f,
            minGap = 2f,
        ) shouldBe 38f
    }

    @Test
    fun `resolveNicknameYForGreetingOverlap keeps original nickname y when already safe`() {
        resolveNicknameYForGreetingOverlap(
            nicknameY = 60f,
            greetingY = 0f,
            greetingHeight = 48f,
            minGap = 2f,
        ) shouldBe 60f
    }

    @Test
    fun `resolveNicknameYForGreetingOverlap keeps original nickname y when x ranges do not overlap`() {
        resolveNicknameYForGreetingOverlap(
            nicknameY = 32f,
            greetingY = 0f,
            greetingHeight = 24f,
            minGap = 2f,
            nicknameX = 220f,
            nicknameWidth = 120f,
            greetingX = 10f,
            greetingWidth = 140f,
        ) shouldBe 32f
    }
}
