package eu.kanade.tachiyomi.ui.browse.novel.extension

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.novel.NovelExtensionScreen
import eu.kanade.presentation.browse.novel.NovelRepoPickerDialog
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.more.settings.screen.browse.NovelExtensionStoreScreen
import eu.kanade.tachiyomi.ui.browse.novel.extension.details.NovelExtensionDetailsScreen
import eu.kanade.tachiyomi.ui.browse.novel.extension.details.NovelSourcePreferencesScreen
import eu.kanade.tachiyomi.util.system.copyToClipboard
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.extension.novel.model.NovelPlugin
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun novelExtensionsTab(
    extensionsScreenModel: NovelExtensionsScreenModel,
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    val state by extensionsScreenModel.state.collectAsStateWithLifecycle()
    var pluginToUninstall by remember { mutableStateOf<NovelPlugin.Installed?>(null) }
    var pluginToReinstall by remember { mutableStateOf<NovelPlugin.Installed?>(null) }
    var showInstallerDiagnostics by remember { mutableStateOf(false) }

    return TabContent(
        titleRes = AYMR.strings.label_novel_extensions,
        badgeNumber = state.updates.takeIf { it > 0 },
        searchEnabled = true,
        actions = persistentListOf(
            AppBar.OverflowAction(
                title = stringResource(MR.strings.action_filter),
                icon = Icons.Outlined.FilterList,
                onClick = { navigator.push(NovelExtensionFilterScreen()) },
            ),
            AppBar.OverflowAction(
                title = stringResource(MR.strings.label_extension_stores),
                icon = Icons.Outlined.Inventory2,
                onClick = { navigator.push(NovelExtensionStoreScreen()) },
            ),
            AppBar.OverflowAction(
                title = stringResource(MR.strings.ext_installer_diagnostics),
                icon = Icons.Outlined.BugReport,
                onClick = { showInstallerDiagnostics = true },
            ),
        ),
        content = { contentPadding, _ ->
            NovelExtensionScreen(
                state = state,
                contentPadding = contentPadding,
                searchQuery = state.searchQuery,
                onInstallExtension = extensionsScreenModel::installExtension,
                onUpdateExtension = extensionsScreenModel::updateExtension,
                onReinstallExtension = { pluginToReinstall = it },
                onOpenExtension = { navigator.push(novelExtensionDetailsScreen(it.id)) },
                onOpenExtensionSettings = { navigator.push(novelExtensionSettingsScreen(it)) },
                onUninstallExtension = { pluginToUninstall = it },
                onUninstallUntrustedExtension = extensionsScreenModel::uninstallExtension,
                onTrustExtension = extensionsScreenModel::trust,
                onUpdateAll = extensionsScreenModel::updateAllExtensions,
                onRefresh = extensionsScreenModel::refresh,
                onToggleSection = extensionsScreenModel::toggleSection,
                onCopyDiagnostic = { plugin ->
                    context.copyToClipboard(
                        label = "Novel extension diagnostic",
                        content = extensionsScreenModel.diagnosticFor(plugin),
                    )
                },
                onShareApk = extensionsScreenModel::shareApk,
            )

            pluginToUninstall?.let { plugin ->
                AlertDialog(
                    title = { Text(text = stringResource(MR.strings.ext_confirm_remove)) },
                    text = { Text(text = stringResource(MR.strings.remove_private_extension_message, plugin.name)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                extensionsScreenModel.uninstallExtension(plugin)
                                pluginToUninstall = null
                            },
                        ) {
                            Text(text = stringResource(MR.strings.ext_remove))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pluginToUninstall = null }) {
                            Text(text = stringResource(MR.strings.action_cancel))
                        }
                    },
                    onDismissRequest = { pluginToUninstall = null },
                )
            }

            pluginToReinstall?.let { plugin ->
                NovelRepoReinstallDialog(
                    plugin = plugin,
                    candidates = extensionsScreenModel.getReinstallCandidates(plugin),
                    onClickCandidate = { candidate ->
                        extensionsScreenModel.reinstallFromRepo(plugin, candidate)
                        pluginToReinstall = null
                    },
                    onDismissRequest = { pluginToReinstall = null },
                )
            }

            if (state.repoPickerOptions.isNotEmpty()) {
                NovelRepoPickerDialog(
                    pluginName = state.repoPickerOptions.first().name,
                    options = state.repoPickerOptions,
                    onSelectPlugin = extensionsScreenModel::installFromRepo,
                    onDismiss = extensionsScreenModel::dismissRepoPicker,
                )
            }

            if (showInstallerDiagnostics) {
                val diagnostic = extensionsScreenModel.installerCompatibilityDiagnostic()
                val diagnosticsTitle = stringResource(MR.strings.ext_installer_diagnostics)
                AlertDialog(
                    title = { Text(text = diagnosticsTitle) },
                    text = { Text(text = diagnostic) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                context.copyToClipboard(
                                    label = diagnosticsTitle,
                                    content = diagnostic,
                                )
                            },
                        ) {
                            Text(text = stringResource(MR.strings.action_copy_to_clipboard))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showInstallerDiagnostics = false }) {
                            Text(text = stringResource(MR.strings.action_cancel))
                        }
                    },
                    onDismissRequest = { showInstallerDiagnostics = false },
                )
            }
        },
    )
}

@Composable
private fun NovelRepoReinstallDialog(
    plugin: NovelPlugin.Installed,
    candidates: List<NovelPlugin.Available>,
    onClickCandidate: (NovelPlugin.Available) -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        icon = {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text(text = stringResource(MR.strings.ext_repo_update_dialog_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
            ) {
                Text(
                    text = stringResource(MR.strings.ext_repo_update_dialog_message, plugin.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider()
                if (candidates.isEmpty()) {
                    Text(
                        text = stringResource(MR.strings.ext_repo_update_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    candidates.forEach { candidate ->
                        val repoName = candidate.repoName
                            .ifBlank { candidate.repoUrl.substringAfter("://", candidate.repoUrl).substringBefore('/') }
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onClickCandidate(candidate) },
                        ) {
                            Text(text = stringResource(MR.strings.ext_repo_update_action, repoName))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        onDismissRequest = onDismissRequest,
    )
}

internal fun novelExtensionDetailsScreen(pluginId: String): NovelExtensionDetailsScreen {
    return NovelExtensionDetailsScreen(pluginId)
}

internal fun novelExtensionSettingsScreen(sourceId: Long): NovelSourcePreferencesScreen {
    return NovelSourcePreferencesScreen(sourceId)
}
