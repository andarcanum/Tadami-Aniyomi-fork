package eu.kanade.presentation.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class AuroraCtaLabelShadowSpec(
    val alpha: Float,
    val offsetY: Float,
    val blurRadius: Float,
)

internal data class AuroraCtaIconShadowSpec(
    val alpha: Float,
    val offsetXDp: Dp,
    val offsetYDp: Dp,
)

internal fun resolveAuroraCtaLabelShadowSpec(enabled: Boolean): AuroraCtaLabelShadowSpec {
    return if (enabled) {
        AuroraCtaLabelShadowSpec(
            alpha = 0.26f,
            offsetY = 1.5f,
            blurRadius = 3.5f,
        )
    } else {
        AuroraCtaLabelShadowSpec(
            alpha = 0f,
            offsetY = 0f,
            blurRadius = 0f,
        )
    }
}

internal fun resolveAuroraHomeIconShadowSpec(enabled: Boolean): AuroraCtaIconShadowSpec {
    return if (enabled) {
        AuroraCtaIconShadowSpec(
            alpha = 0.24f,
            offsetXDp = 0.dp,
            offsetYDp = 1.dp,
        )
    } else {
        AuroraCtaIconShadowSpec(
            alpha = 0f,
            offsetXDp = 0.dp,
            offsetYDp = 0.dp,
        )
    }
}

internal fun AuroraCtaLabelShadowSpec.toComposeShadow(): Shadow {
    return Shadow(
        color = Color.Black.copy(alpha = alpha),
        offset = Offset(0f, offsetY),
        blurRadius = blurRadius,
    )
}
