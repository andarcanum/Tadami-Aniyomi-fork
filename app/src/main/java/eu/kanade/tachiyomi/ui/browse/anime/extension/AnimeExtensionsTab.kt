package eu.kanade.tachiyomi.ui.browse.anime.extension

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
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
import eu.kanade.presentation.browse.RepoPickerDialog
import eu.kanade.presentation.browse.anime.AnimeExtensionScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.more.settings.screen.browse.AnimeExtensionReposScreen
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.ui.browse.anime.extension.details.AnimeExtensionDetailsScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.util.system.isPackageInstalled
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun animeExtensionsTab(
    extensionsScreenModel: AnimeExtensionsScreenModel,
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current

    val state by extensionsScreenModel.state.collectAsStateWithLifecycle()
    var privateExtensionToUninstall by remember { mutableStateOf<AnimeExtension?>(null) }
    var extensionToReinstall by remember { mutableStateOf<AnimeExtension.Installed?>(null) }

    return TabContent(
        titleRes = AYMR.strings.label_anime_extensions,
        badgeNumber = state.updates.takeIf { it > 0 },
        searchEnabled = true,
        actions = persistentListOf(
            AppBar.OverflowAction(
                title = stringResource(MR.strings.action_filter),
                onClick = {
                    navigator.push(
                        AnimeExtensionFilterScreen(),
                    )
                },
            ),
            AppBar.OverflowAction(
                title = stringResource(MR.strings.label_extension_repos),
                onClick = { navigator.push(AnimeExtensionReposScreen()) },
            ),
        ),
        content = { contentPadding, _ ->
            AnimeExtensionScreen(
                state = state,
                contentPadding = contentPadding,
                searchQuery = state.searchQuery,
                onLongClickItem = { extension ->
                    when (extension) {
                        is AnimeExtension.Available -> extensionsScreenModel.installExtension(
                            extension,
                        )
                        else -> {
                            if (context.isPackageInstalled(extension.pkgName)) {
                                extensionsScreenModel.uninstallExtension(extension)
                            } else {
                                privateExtensionToUninstall = extension
                            }
                        }
                    }
                },
                onClickItemCancel = extensionsScreenModel::cancelInstallUpdateExtension,
                onClickUpdateAll = extensionsScreenModel::updateAllExtensions,
                onOpenWebView = { extension ->
                    extension.sources.getOrNull(0)?.let {
                        navigator.push(
                            WebViewScreen(
                                url = it.baseUrl,
                                initialTitle = it.name,
                                sourceId = it.id,
                            ),
                        )
                    }
                },
                onInstallExtension = extensionsScreenModel::installExtension,
                onOpenExtension = { navigator.push(AnimeExtensionDetailsScreen(it.pkgName)) },
                onTrustExtension = { extensionsScreenModel.trustExtension(it) },
                onUninstallExtension = { extensionsScreenModel.uninstallExtension(it) },
                onUpdateExtension = extensionsScreenModel::updateExtension,
                onReinstallExtension = { extensionToReinstall = it },
                onRefresh = extensionsScreenModel::findAvailableExtensions,
                onToggleSection = extensionsScreenModel::toggleSection,
            )

            privateExtensionToUninstall?.let { extension ->
                AnimeExtensionUninstallConfirmation(
                    extensionName = extension.name,
                    onClickConfirm = {
                        extensionsScreenModel.uninstallExtension(extension)
                    },
                    onDismissRequest = {
                        privateExtensionToUninstall = null
                    },
                )
            }

            extensionToReinstall?.let { extension ->
                AnimeExtensionReinstallDialog(
                    extension = extension,
                    candidates = extensionsScreenModel.getReinstallCandidates(extension),
                    onClickCandidate = { candidate ->
                        extensionsScreenModel.reinstallFromRepo(extension, candidate)
                        extensionToReinstall = null
                    },
                    onDismissRequest = { extensionToReinstall = null },
                )
            }

            if (state.repoPickerOptions.isNotEmpty()) {
                RepoPickerDialog(
                    titleRes = AYMR.strings.novel_repo_picker_title,
                    newestContentDescriptionRes = AYMR.strings.novel_repo_picker_newest,
                    itemName = state.repoPickerOptions.first().name,
                    options = state.repoPickerOptions,
                    onSelectOption = extensionsScreenModel::installFromRepo,
                    onDismiss = extensionsScreenModel::dismissRepoPicker,
                    optionLabel = { it.repoName.ifBlank { it.repoUrl } },
                    optionVersionText = { "v${it.versionName}" },
                    comparator = compareBy<AnimeExtension.Available> { it.versionCode }
                        .thenBy { it.libVersion },
                )
            }
        },
    )
}

@Composable
private fun AnimeExtensionReinstallDialog(
    extension: AnimeExtension.Installed,
    candidates: List<AnimeExtension.Available>,
    onClickCandidate: (AnimeExtension.Available) -> Unit,
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
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium)) {
                Text(
                    text = stringResource(MR.strings.ext_repo_update_dialog_message, extension.name),
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
                        val repoName = candidate.repoName.ifBlank {
                            candidate.repoUrl.substringAfter("://", candidate.repoUrl).substringBefore('/')
                        }
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

@Composable
private fun AnimeExtensionUninstallConfirmation(
    extensionName: String,
    onClickConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        title = {
            Text(text = stringResource(MR.strings.ext_confirm_remove))
        },
        text = {
            Text(text = stringResource(MR.strings.remove_private_extension_message, extensionName))
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onClickConfirm()
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.ext_remove))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        onDismissRequest = onDismissRequest,
    )
}
