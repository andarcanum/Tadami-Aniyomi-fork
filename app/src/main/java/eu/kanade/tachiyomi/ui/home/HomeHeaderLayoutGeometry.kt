package eu.kanade.tachiyomi.ui.home

import eu.kanade.domain.ui.model.HomeHeaderLayoutElement
import eu.kanade.domain.ui.model.HomeHeaderLayoutSpec

internal const val HOME_HEADER_LAYOUT_DESIGN_WIDTH_PX = 360f
internal const val HOME_HEADER_LAYOUT_DESIGN_HEIGHT_PX = 72f

internal data class HomeHeaderPixelPoint(
    val x: Float,
    val y: Float,
)

internal data class HomeHeaderPixelSize(
    val width: Float,
    val height: Float,
)

private data class HomeHeaderPixelRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

internal fun clampHomeHeaderPixelPoint(
    point: HomeHeaderPixelPoint,
    elementSize: HomeHeaderPixelSize,
    canvasWidth: Float,
    canvasHeight: Float,
): HomeHeaderPixelPoint {
    val safeWidth = canvasWidth.coerceAtLeast(1f)
    val safeHeight = canvasHeight.coerceAtLeast(1f)
    val maxX = (safeWidth - elementSize.width).coerceAtLeast(0f)
    val maxY = (safeHeight - elementSize.height).coerceAtLeast(0f)
    return HomeHeaderPixelPoint(
        x = point.x.coerceIn(0f, maxX),
        y = point.y.coerceIn(0f, maxY),
    )
}

internal fun defaultHomeHeaderElementPixelSizes(): Map<HomeHeaderLayoutElement, HomeHeaderPixelSize> {
    return mapOf(
        // Expanded to reach the streak/counter area so longer greetings fit without early ellipsis.
        HomeHeaderLayoutElement.Greeting to HomeHeaderPixelSize(width = 280f, height = 32f),
        HomeHeaderLayoutElement.Nickname to HomeHeaderPixelSize(width = 248f, height = 30f),
        HomeHeaderLayoutElement.Avatar to HomeHeaderPixelSize(width = 48f, height = 48f),
        HomeHeaderLayoutElement.Streak to HomeHeaderPixelSize(width = 68f, height = 22f),
    )
}

internal fun wouldPlaceHomeHeaderElementOverlap(
    element: HomeHeaderLayoutElement,
    candidate: HomeHeaderPixelPoint,
    layout: HomeHeaderLayoutSpec,
    elementSizes: Map<HomeHeaderLayoutElement, HomeHeaderPixelSize>,
    hiddenElements: Set<HomeHeaderLayoutElement> = emptySet(),
): Boolean {
    if (element in hiddenElements) return false
    val candidateSize = elementSizes[element] ?: return false
    val candidateRect = HomeHeaderPixelRect(
        x = candidate.x,
        y = candidate.y,
        width = candidateSize.width,
        height = candidateSize.height,
    )

    return HomeHeaderLayoutElement.entries
        .asSequence()
        .filter { it != element }
        .filterNot { it in hiddenElements }
        .mapNotNull { other ->
            val size = elementSizes[other] ?: return@mapNotNull null
            val pos = layout.positionOf(other)
            HomeHeaderPixelRect(
                x = pos.x,
                y = pos.y,
                width = size.width,
                height = size.height,
            )
        }
        .any { rectsOverlap(candidateRect, it) }
}

private fun rectsOverlap(a: HomeHeaderPixelRect, b: HomeHeaderPixelRect): Boolean {
    return a.x < b.x + b.width &&
        a.x + a.width > b.x &&
        a.y < b.y + b.height &&
        a.y + a.height > b.y
}
