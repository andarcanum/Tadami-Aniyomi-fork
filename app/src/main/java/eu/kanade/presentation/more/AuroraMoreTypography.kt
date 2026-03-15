package eu.kanade.presentation.more

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal fun auroraPrimaryMenuTitleTextStyle(
    baseStyle: TextStyle,
): TextStyle {
    return baseStyle.copy(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
    )
}
