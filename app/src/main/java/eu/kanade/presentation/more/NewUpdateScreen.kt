package eu.kanade.presentation.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material3.RichText
import com.halilibo.richtext.ui.string.RichTextStringStyle
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewUpdateScreen(
    versionName: String,
    releaseDate: String,
    changelogInfo: String,
    ignoreThisVersion: Boolean,
    onToggleIgnoreVersion: (Boolean) -> Unit,
    onOpenInBrowser: () -> Unit,
    onRejectUpdate: () -> Unit,
    onAcceptUpdate: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val hasChangelog = changelogInfo.isNotBlank()

    fun dismiss() {
        scope.launch {
            sheetState.hide()
            onRejectUpdate()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onRejectUpdate,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.padding.medium),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                Icon(
                    imageVector = Icons.Outlined.NewReleases,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(MR.strings.update_check_notification_update_available),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.size(MaterialTheme.padding.large))

            // Version info & Details
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = MaterialTheme.padding.medium),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
            ) {
                Column {
                    Text(
                        text = versionName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    if (releaseDate.isNotBlank()) {
                        Text(
                            text = releaseDate,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (hasChangelog) {
                    RichText(
                        style = RichTextStyle(
                            stringStyle = RichTextStringStyle(
                                linkStyle = SpanStyle(color = MaterialTheme.colorScheme.primary),
                            ),
                        ),
                    ) {
                        Markdown(content = changelogInfo)
                    }
                } else {
                    Text(
                        text = stringResource(MR.strings.update_check_changelog_unavailable),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                TextButton(
                    onClick = onOpenInBrowser,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(text = stringResource(MR.strings.update_check_open))
                    Spacer(modifier = Modifier.width(MaterialTheme.padding.extraSmall))
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = null,
                    )
                }
            }

            // Footer (Fixed at the bottom)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MaterialTheme.padding.medium),
            ) {
                LabeledCheckbox(
                    label = stringResource(MR.strings.update_check_ignore_version),
                    checked = ignoreThisVersion,
                    onCheckedChange = onToggleIgnoreVersion,
                )

                Spacer(modifier = Modifier.size(MaterialTheme.padding.small))

                Button(
                    onClick = onAcceptUpdate,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(MR.strings.update_check_confirm))
                }
                TextButton(
                    onClick = { dismiss() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(MR.strings.action_not_now))
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun NewUpdateScreenPreview() {
    TachiyomiPreviewTheme {
        NewUpdateScreen(
            versionName = "v0.99.9",
            releaseDate = "2026-03-29",
            changelogInfo = """
                ## Features
                - Cleaner settings styling
                - Improved layout and icon polish

                ### Fixes
                - Better button-only cards
            """.trimIndent(),
            ignoreThisVersion = false,
            onToggleIgnoreVersion = {},
            onOpenInBrowser = {},
            onRejectUpdate = {},
            onAcceptUpdate = {},
        )
    }
}
