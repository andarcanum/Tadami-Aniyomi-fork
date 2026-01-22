package eu.kanade.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.presentation.theme.AuroraTheme

@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = AuroraTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundGradient),
    ) {
        content()
    }
}

@Composable
fun AuroraHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    // Reusable header component if needed, currently TopBars are custom per screen
}
