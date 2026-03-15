package eu.kanade.presentation.more

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal fun auroraPrimaryMenuTitleTextStyle(
    baseStyle: TextStyle,
    useMediumWeight: Boolean,
): TextStyle {
    val resolvedWeight = if (useMediumWeight) {
        FontWeight.Medium
    } else {
        FontWeight.Normal
    }
    return baseStyle.copy(
        fontSize = 16.sp,
        fontWeight = resolvedWeight,
    )
}
