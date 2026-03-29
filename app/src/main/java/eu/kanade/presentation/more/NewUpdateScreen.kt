package eu.kanade.presentation.more

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material3.RichText
import com.halilibo.richtext.ui.string.RichTextStringStyle
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

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
    var expanded by rememberSaveable { mutableStateOf(false) }
    val hasChangelog = changelogInfo.isNotBlank()

    AlertDialog(
        onDismissRequest = onRejectUpdate,
        icon = {
            Icon(
                imageVector = Icons.Outlined.NewReleases,
                contentDescription = null,
            )
        },
        title = {
            Text(text = stringResource(MR.strings.update_check_notification_update_available))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(
                        modifier = Modifier.padding(MaterialTheme.padding.medium),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                    ) {
                        Text(
                            text = versionName,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (releaseDate.isNotBlank()) {
                            Text(
                                text = releaseDate,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(vertical = MaterialTheme.padding.extraSmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(
                            if (expanded) {
                                MR.strings.update_check_hide_changelog
                            } else {
                                MR.strings.update_check_show_changelog
                            },
                        ),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                    )
                }

                if (expanded) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Column(
                            modifier = Modifier.padding(MaterialTheme.padding.medium),
                            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 280.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
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
                    }
                }

                LabeledCheckbox(
                    label = stringResource(MR.strings.update_check_ignore_version),
                    checked = ignoreThisVersion,
                    onCheckedChange = onToggleIgnoreVersion,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAcceptUpdate) {
                Text(text = stringResource(MR.strings.update_check_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onRejectUpdate) {
                Text(text = stringResource(MR.strings.action_not_now))
            }
        },
    )
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
