package eu.kanade.presentation.entries.components.aurora

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.entries.manga.components.aurora.GlassmorphismCard
import eu.kanade.presentation.theme.AuroraTheme
import kotlinx.coroutines.delay
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun AuroraNotePreviewCard(
    note: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (note.isBlank()) return

    val colors = AuroraTheme.colors
    val shape = RoundedCornerShape(18.dp)

    GlassmorphismCard(
        modifier = modifier,
        cornerRadius = 18.dp,
        horizontalPadding = 0.dp,
        verticalPadding = 0.dp,
        innerPadding = 14.dp,
        overlayColor = colors.accent.copy(alpha = 0.08f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    },
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .border(
                            width = 1.dp,
                            color = colors.accent.copy(alpha = 0.24f),
                            shape = shape,
                        )
                        .background(colors.accent.copy(alpha = 0.14f), shape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.EditNote,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    text = stringResource(MR.strings.action_notes),
                    color = colors.accent,
                    fontSize = 12.sp,
                )
            }

            Text(
                text = note,
                color = colors.textPrimary,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(start = 2.dp),
                maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun AuroraNoteEditorDialog(
    initialText: String,
    onDismissRequest: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by rememberSaveable(initialText) { mutableStateOf(initialText) }
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(text)
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            Text(text = stringResource(MR.strings.action_notes))
        },
        text = {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                value = text,
                onValueChange = { text = it },
                label = {
                    Text(text = stringResource(MR.strings.action_notes))
                },
                placeholder = {
                    Text(text = stringResource(MR.strings.information_required_plain))
                },
                minLines = 6,
                maxLines = 10,
            )
        },
    )

    LaunchedEffect(focusRequester) {
        delay(100.milliseconds)
        focusRequester.requestFocus()
    }
}
