package eu.kanade.presentation.browse.novel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.presentation.browse.BaseBrowseItem
import eu.kanade.presentation.browse.manga.ExtensionHeader
import eu.kanade.presentation.more.settings.screen.browse.NovelExtensionReposScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.ui.browse.novel.extension.NovelExtensionItem
import eu.kanade.tachiyomi.ui.browse.novel.extension.NovelExtensionsScreenModel
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.domain.extension.novel.model.NovelPlugin
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.plus

@Composable
fun NovelExtensionScreen(
    state: NovelExtensionsScreenModel.State,
    contentPadding: PaddingValues,
    searchQuery: String?,
    onInstallExtension: (NovelPlugin.Available) -> Unit,
    onCancelInstall: (NovelPlugin.Available) -> Unit,
    onUpdateExtension: (NovelPlugin.Installed) -> Unit,
    onUpdateAll: () -> Unit,
    onRefresh: () -> Unit,
) {
    val navigator = LocalNavigator.currentOrThrow
    PullRefresh(
        refreshing = state.isRefreshing,
        onRefresh = onRefresh,
        enabled = !state.isLoading,
    ) {
        when {
            state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
            state.items.isEmpty() -> {
                val msg = if (!searchQuery.isNullOrEmpty()) {
                    MR.strings.no_results_found
                } else {
                    MR.strings.empty_screen
                }
                EmptyScreen(
                    stringRes = msg,
                    modifier = Modifier.padding(contentPadding),
                    actions = kotlinx.collections.immutable.persistentListOf(
                        EmptyScreenAction(
                            stringRes = MR.strings.label_extension_repos,
                            icon = Icons.Outlined.Public,
                            onClick = { navigator.push(NovelExtensionReposScreen()) },
                        ),
                    ),
                )
            }
            else -> {
                NovelExtensionContent(
                    state = state,
                    contentPadding = contentPadding,
                    onInstallExtension = onInstallExtension,
                    onCancelInstall = onCancelInstall,
                    onUpdateExtension = onUpdateExtension,
                    onUpdateAll = onUpdateAll,
                )
            }
        }
    }
}

@Composable
private fun NovelExtensionContent(
    state: NovelExtensionsScreenModel.State,
    contentPadding: PaddingValues,
    onInstallExtension: (NovelPlugin.Available) -> Unit,
    onCancelInstall: (NovelPlugin.Available) -> Unit,
    onUpdateExtension: (NovelPlugin.Installed) -> Unit,
    onUpdateAll: () -> Unit,
) {
    val grouped = state.items.groupBy { it.status }

    FastScrollLazyColumn(
        contentPadding = contentPadding + topSmallPaddingValues,
    ) {
        val updates = grouped[NovelExtensionItem.Status.UpdateAvailable].orEmpty()
        if (updates.isNotEmpty()) {
            item(key = "novel-ext-updates-header") {
                ExtensionHeader(
                    textRes = MR.strings.ext_updates_pending,
                    action = {
                        IconButton(onClick = onUpdateAll) {
                            Icon(
                                imageVector = Icons.Outlined.GetApp,
                                contentDescription = stringResource(MR.strings.ext_update_all),
                            )
                        }
                    },
                )
            }
            items(
                items = updates,
                key = { item -> "novel-ext-update-${item.plugin.id}" },
            ) { item ->
                NovelExtensionItemRow(
                    item = item,
                    onCancelInstall = onCancelInstall,
                    onUpdateExtension = onUpdateExtension,
                )
            }
        }

        val installed = grouped[NovelExtensionItem.Status.Installed].orEmpty()
        if (installed.isNotEmpty()) {
            item(key = "novel-ext-installed-header") {
                ExtensionHeader(textRes = MR.strings.ext_installed)
            }
            items(
                items = installed,
                key = { item -> "novel-ext-installed-${item.plugin.id}" },
            ) { item ->
                NovelExtensionItemRow(
                    item = item,
                    onCancelInstall = onCancelInstall,
                )
            }
        }

        val available = grouped[NovelExtensionItem.Status.Available].orEmpty()
        if (available.isNotEmpty()) {
            item(key = "novel-ext-available-header") {
                ExtensionHeader(textRes = MR.strings.ext_available)
            }
            items(
                items = available,
                key = { item -> "novel-ext-available-${item.plugin.id}" },
            ) { item ->
                NovelExtensionItemRow(
                    item = item,
                    onInstallExtension = onInstallExtension,
                    onCancelInstall = onCancelInstall,
                )
            }
        }
    }
}

@Composable
private fun NovelExtensionItemRow(
    item: NovelExtensionItem,
    onInstallExtension: ((NovelPlugin.Available) -> Unit)? = null,
    onCancelInstall: ((NovelPlugin.Available) -> Unit)? = null,
    onUpdateExtension: ((NovelPlugin.Installed) -> Unit)? = null,
) {
    val plugin = item.plugin
    val onItemClick: () -> Unit = {
        when (item.installStep) {
            InstallStep.Pending, InstallStep.Downloading, InstallStep.Installing -> Unit
            InstallStep.Error -> {
                val availablePlugin = plugin as? NovelPlugin.Available
                if (availablePlugin != null && onInstallExtension != null) {
                    onInstallExtension(availablePlugin)
                }
            }
            else -> when {
                plugin is NovelPlugin.Available && onInstallExtension != null -> onInstallExtension(plugin)
                plugin is NovelPlugin.Installed && onUpdateExtension != null -> onUpdateExtension(plugin)
            }
        }
    }
    BaseBrowseItem(
        onClickItem = onItemClick,
        icon = {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                val idle = item.installStep.isCompleted()
                if (!idle) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 2.dp,
                    )
                }
                AsyncImage(
                    model = plugin.iconUrl,
                    contentDescription = null,
                    placeholder = ColorPainter(Color(0x1F888888)),
                    error = painterResource(R.mipmap.ic_default_source),
                    modifier = Modifier.size(34.dp),
                )
            }
        },
        action = {
            when (item.installStep) {
                InstallStep.Pending, InstallStep.Downloading, InstallStep.Installing -> {
                    val availablePlugin = plugin as? NovelPlugin.Available
                    if (availablePlugin != null && onCancelInstall != null) {
                        IconButton(onClick = { onCancelInstall(availablePlugin) }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(MR.strings.action_cancel),
                            )
                        }
                    }
                }
                InstallStep.Error -> {
                    val availablePlugin = plugin as? NovelPlugin.Available
                    if (availablePlugin != null && onInstallExtension != null) {
                        IconButton(onClick = { onInstallExtension(availablePlugin) }) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = stringResource(MR.strings.action_retry),
                            )
                        }
                    }
                }
                else -> when {
                    onInstallExtension != null && plugin is NovelPlugin.Available -> {
                        IconButton(onClick = { onInstallExtension(plugin) }) {
                            Icon(
                                imageVector = Icons.Outlined.GetApp,
                                contentDescription = stringResource(MR.strings.ext_install),
                            )
                        }
                    }
                    onUpdateExtension != null && plugin is NovelPlugin.Installed -> {
                        IconButton(onClick = { onUpdateExtension(plugin) }) {
                            Icon(
                                imageVector = Icons.Outlined.GetApp,
                                contentDescription = stringResource(MR.strings.ext_update),
                            )
                        }
                    }
                }
            }
        },
    ) {
        val context = LocalContext.current
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = plugin.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowRow(
                modifier = Modifier.padding(top = 2.dp),
            ) {
                Text(
                    text = LocaleHelper.getSourceDisplayName(plugin.lang, context),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        text = "v${plugin.version}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            val statusText = when (item.installStep) {
                InstallStep.Pending -> stringResource(MR.strings.ext_pending)
                InstallStep.Downloading -> stringResource(MR.strings.ext_downloading)
                InstallStep.Installing -> stringResource(MR.strings.ext_installing)
                InstallStep.Error -> stringResource(MR.strings.action_retry)
                else -> null
            }
            if (statusText != null) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}
