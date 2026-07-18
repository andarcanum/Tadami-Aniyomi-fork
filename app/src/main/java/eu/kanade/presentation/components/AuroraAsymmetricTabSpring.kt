package eu.kanade.presentation.components

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.math.pow

/**
 * Variant A — asymmetric spring stretch for Aurora capsule / section tabs.
 *
 * Leading edge of the selection pill uses a stiffer spring than the trailing
 * edge, so the indicator elongates while moving and snaps into place with a
 * soft bounce (same language as Browse [TabbedScreenAurora] tab chrome).
 */

/** Stiffer spring on the edge that reaches the target first. */
internal const val AuroraTabLeadingStiffness = 500f

/** Softer spring on the lagging edge → stretch during travel. */
internal const val AuroraTabTrailingStiffness = 250f

/** Slightly underdamped for a short settle bounce. */
internal const val AuroraTabSpringDamping = 0.78f

/**
 * Left/right edge stiffness for a move.
 * Moving right: left trails, right leads. Moving left: mirrored.
 */
internal fun resolveAsymmetricTabEdgeStiffness(isMovingRight: Boolean): Pair<Float, Float> {
    return if (isMovingRight) {
        AuroraTabTrailingStiffness to AuroraTabLeadingStiffness
    } else {
        AuroraTabLeadingStiffness to AuroraTabTrailingStiffness
    }
}

internal fun auroraTabEdgeSpring(stiffness: Float): SpringSpec<Float> {
    return spring(
        dampingRatio = AuroraTabSpringDamping,
        stiffness = stiffness,
    )
}

/**
 * Corner radius factor when the pill is stretched wider than its rest width.
 * Returns 1 at rest; lower values flatten the capsule slightly while elongated.
 */
internal fun resolveAsymmetricTabStretchRadiusFactor(
    drawWidth: Float,
    restWidth: Float,
): Float {
    if (restWidth <= 0f || drawWidth <= 0f) return 1f
    val stretch = (drawWidth / restWidth).coerceIn(0.7f, 2.4f)
    return (1f / stretch.pow(0.35f)).coerceIn(0.55f, 1f)
}

/**
 * Sticky move direction for the whole travel of a selection change.
 *
 * Unlike `prev = selected` in [androidx.compose.runtime.LaunchedEffect] (which
 * flips stiffness mid-spring once prev catches up), this keeps the leading /
 * trailing assignment stable until the next index change.
 */
@Composable
internal fun rememberAsymmetricTabMovingRight(selectedIndex: Int): Boolean {
    val holder = remember {
        object {
            var previous = selectedIndex
            var movingRight = true
        }
    }
    if (selectedIndex != holder.previous) {
        holder.movingRight = selectedIndex > holder.previous
        holder.previous = selectedIndex
    }
    return holder.movingRight
}

/**
 * Same sticky direction as [rememberAsymmetricTabMovingRight], exposed for
 * non-composable tests via a tiny mutable holder.
 */
internal class AsymmetricTabMoveTracker(initialIndex: Int = 0) {
    private var previous = initialIndex
    private var movingRight = true

    fun onSelected(selectedIndex: Int): Boolean {
        if (selectedIndex != previous) {
            movingRight = selectedIndex > previous
            previous = selectedIndex
        }
        return movingRight
    }
}

/** Convenience: both edge springs for the current move. */
@Composable
internal fun rememberAsymmetricTabEdgeSprings(selectedIndex: Int): Pair<SpringSpec<Float>, SpringSpec<Float>> {
    val movingRight = rememberAsymmetricTabMovingRight(selectedIndex)
    return remember(movingRight) {
        val (left, right) = resolveAsymmetricTabEdgeStiffness(movingRight)
        auroraTabEdgeSpring(left) to auroraTabEdgeSpring(right)
    }
}
