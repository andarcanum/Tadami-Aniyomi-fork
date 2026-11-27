package eu.kanade.tachiyomi.ui.player.controls.components.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.TextButton
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun PlayerDialog(
    title: String,
    modifier: Modifier = Modifier,
    onConfirmRequest: (() -> Unit)? = null,
    onDismissRequest: () -> Unit,
    content: @Composable (() -> Unit)? = null,
) {
    val onConfirm = {
        onConfirmRequest?.invoke()
        onDismissRequest()
    }
    val aurora = AuroraTheme.colors
    val containerColor = if (aurora.isEInk) {
        MaterialTheme.colorScheme.surface
    } else if (aurora.isDark) {
        aurora.surface.copy(alpha = 0.92f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val rimColor = if (aurora.isDark) {
        Color.White.copy(alpha = 0.12f)
    } else {
        Color.Black.copy(alpha = 0.08f)
    }

    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier.widthIn(max = 960.dp),
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = containerColor,
            border = BorderStroke(1.dp, rimColor),
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 2.dp,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                content?.invoke()

                if (onConfirmRequest != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(onClick = onDismissRequest) {
                            Text(stringResource(MR.strings.action_cancel))
                        }

                        TextButton(onClick = onConfirm) {
                            Text(stringResource(MR.strings.action_ok))
                        }
                    }
                }
            }
        }
    }
}
