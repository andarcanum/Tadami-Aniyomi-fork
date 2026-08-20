package tachiyomi.presentation.core.components.material

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun NavigationBar(
    modifier: Modifier = Modifier,
    containerColor: Color = NavigationBarDefaults.containerColor,
    contentColor: Color = MaterialTheme.colorScheme.contentColorFor(containerColor),
    shadowElevation: Dp = 0.dp,
    tonalElevation: Dp = 0.dp,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
    shape: Shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    height: Dp = 80.dp,
    content: @Composable RowScope.() -> Unit,
) {
    androidx.compose.material3.Surface(
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = shadowElevation,
        tonalElevation = tonalElevation,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(windowInsets)
                .padding(contentPadding)
                .height(height)
                .selectableGroup(),
            content = content,
        )
    }
}
