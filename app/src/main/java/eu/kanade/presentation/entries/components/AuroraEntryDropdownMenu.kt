package eu.kanade.presentation.entries.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme

@Composable
fun AuroraEntryDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AuroraTheme.colors
    val shape = RoundedCornerShape(16.dp)

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = DpOffset(x = 0.dp, y = 8.dp),
        shape = shape,
        containerColor = colors.surface.copy(alpha = 0.92f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = colors.accent.copy(alpha = 0.26f),
        ),
        modifier = modifier.widthIn(min = 168.dp, max = 236.dp),
        content = content,
    )
}

@Composable
fun AuroraEntryDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    leadingIcon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                color = colors.textPrimary,
                fontSize = 14.sp,
                lineHeight = 18.sp,
            )
        },
        leadingIcon = leadingIcon?.let { icon ->
            {
                androidx.compose.material3.Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.textSecondary,
                )
            }
        },
        onClick = onClick,
        modifier = modifier,
        colors = MenuDefaults.itemColors(
            textColor = colors.textPrimary,
        ),
    )
}
