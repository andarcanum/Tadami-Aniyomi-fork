package eu.kanade.tachiyomi.ui.browse.novel.extension

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.novel.NovelExtensionScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.more.settings.screen.browse.NovelExtensionReposScreen
import eu.kanade.tachiyomi.ui.browse.novel.extension.details.NovelExtensionDetailsScreen
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.extension.novel.model.NovelPlugin
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun novelExtensionsTab(
    extensionsScreenModel: NovelExtensionsScreenModel,
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val state by extensionsScreenModel.state.collectAsState()
    var pluginToUninstall by remember { mutableStateOf<NovelPlugin.Installed?>(null) }

    return TabContent(
        titleRes = AYMR.strings.label_novel_extensions,
        badgeNumber = state.updates.takeIf { it > 0 },
        searchEnabled = true,
        actions = persistentListOf(
            AppBar.OverflowAction(
                title = stringResource(MR.strings.action_filter),
                onClick = { navigator.push(NovelExtensionFilterScreen()) },
            ),
            AppBar.OverflowAction(
                title = stringResource(MR.strings.label_extension_repos),
                onClick = { navigator.push(NovelExtensionReposScreen()) },
            ),
        ),
        content = { contentPadding, _ ->
            NovelExtensionScreen(
                state = state,
                contentPadding = contentPadding,
                searchQuery = state.searchQuery,
                onInstallExtension = extensionsScreenModel::installExtension,
                onCancelInstall = extensionsScreenModel::cancelInstall,
                onUpdateExtension = extensionsScreenModel::updateExtension,
                onOpenExtension = { navigator.push(novelExtensionDetailsScreen(it.id)) },
                onUninstallExtension = { pluginToUninstall = it },
                onUpdateAll = extensionsScreenModel::updateAllExtensions,
                onRefresh = extensionsScreenModel::refresh,
                onToggleSection = extensionsScreenModel::toggleSection,
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
        },
    )
}

internal fun novelExtensionDetailsScreen(pluginId: String): NovelExtensionDetailsScreen {
    return NovelExtensionDetailsScreen(pluginId)
}
