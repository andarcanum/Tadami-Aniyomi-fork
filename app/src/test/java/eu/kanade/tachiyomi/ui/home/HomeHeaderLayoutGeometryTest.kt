package eu.kanade.tachiyomi.ui.home

import eu.kanade.domain.ui.model.HomeHeaderLayoutElement
import eu.kanade.domain.ui.model.HomeHeaderLayoutSpec
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class HomeHeaderLayoutGeometryTest {

    @Test
    fun `clampHomeHeaderPixelPoint keeps element inside canvas`() {
        clampHomeHeaderPixelPoint(
            point = HomeHeaderPixelPoint(x = 400f, y = 60f),
            elementSize = HomeHeaderPixelSize(width = 68f, height = 22f),
            canvasWidth = 360f,
            canvasHeight = 72f,
        ) shouldBe HomeHeaderPixelPoint(x = 292f, y = 50f)
    }

    @Test
    fun `wouldPlaceHomeHeaderElementOverlap returns true when rectangles intersect`() {
        val layout = HomeHeaderLayoutSpec.default()
        val sizes = defaultHomeHeaderElementPixelSizes().toMutableMap().apply {
            put(HomeHeaderLayoutElement.Nickname, HomeHeaderPixelSize(width = 120f, height = 30f))
            put(HomeHeaderLayoutElement.Avatar, HomeHeaderPixelSize(width = 48f, height = 48f))
        }

        wouldPlaceHomeHeaderElementOverlap(
            element = HomeHeaderLayoutElement.Avatar,
            candidate = HomeHeaderPixelPoint(x = 80f, y = 18f),
            layout = layout,
            elementSizes = sizes,
        ) shouldBe true
    }

    @Test
    fun `wouldPlaceHomeHeaderElementOverlap ignores hidden elements`() {
        val layout = HomeHeaderLayoutSpec.default()
        val sizes = defaultHomeHeaderElementPixelSizes()

        wouldPlaceHomeHeaderElementOverlap(
            element = HomeHeaderLayoutElement.Streak,
            candidate = HomeHeaderPixelPoint(x = 0f, y = 0f),
            layout = layout,
            elementSizes = sizes,
            hiddenElements = setOf(
                HomeHeaderLayoutElement.Greeting,
                HomeHeaderLayoutElement.Nickname,
            ),
        ) shouldBe false
    }
}
