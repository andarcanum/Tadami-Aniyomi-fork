package eu.kanade.presentation.entries.components.aurora

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import tachiyomi.presentation.core.util.clickableNoIndication

@Composable
internal fun AuroraZoomablePoster(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickableNoIndication(onClick = onDismissRequest),
        content = content,
    )
}
