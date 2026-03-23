package eu.kanade.presentation.entries.components.aurora

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import eu.kanade.presentation.theme.AuroraTheme

@Composable
internal fun rememberAuroraPosterColorFilter(): ColorFilter? {
    if (!AuroraTheme.colors.isEInk) {
        return null
    }
    return remember {
        ColorFilter.colorMatrix(
            ColorMatrix().apply {
                setToSaturation(0f)
            },
        )
    }
}
