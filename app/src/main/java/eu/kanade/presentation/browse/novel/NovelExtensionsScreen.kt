package eu.kanade.presentation.browse.novel

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Warning
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
import com.tadami.aurora.R
import eu.kanade.presentation.browse.BaseBrowseItem
import eu.kanade.presentation.browse.manga.ExtensionHeader
import eu.kanade.presentation.more.settings.screen.browse.NovelExtensionReposScreen
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
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.plus

internal fun shouldLoadNovelPluginIcon(iconUrl: String?): Boolean {
    return !iconUrl.isNullOrBlank()
}

internal enum class NovelExtensionRowAction {
    None,
    Install,
    Update,
    Reinstall,
    Open,
}

internal fun resolveNovelExtensionRowAction(item: NovelExtensionItem): NovelExtensionRowAction {
    val plugin = item.plugin
    return when (item.installStep) {
        InstallStep.Pending, InstallStep.Downloading, InstallStep.Installing -> NovelExtensionRowAction.None
        InstallStep.Error -> when {
            plugin is NovelPlugin.Available -> NovelExtensionRowAction.Install
            plugin is NovelPlugin.Installed && item.hasRepoUpdate && !item.hasUpdate -> {
                NovelExtensionRowAction.Reinstall
            }
            plugin is NovelPlugin.Installed && item.hasUpdate -> NovelExtensionRowAction.Update
            else -> NovelExtensionRowAction.None
        }
        else -> when {
            plugin is NovelPlugin.Available -> NovelExtensionRowAction.Install
            plugin is NovelPlugin.Installed && item.hasRepoUpdate && !item.hasUpdate -> {
                NovelExtensionRowAction.Reinstall
            }
            plugin is NovelPlugin.Installed && item.hasUpdate -> NovelExtensionRowAction.Update
            plugin is NovelPlugin.Installed -> NovelExtensionRowAction.Open
            else -> NovelExtensionRowAction.None
        }
    }
}

@Composable
fun NovelExtensionScreen(
    state: NovelExtensionsScreenModel.State,
    contentPadding: PaddingValues,
    searchQuery: String?,
    onInstallExtension: (NovelPlugin.Available) -> Unit,
    onUpdateExtension: (NovelPlugin.Installed) -> Unit,
    onReinstallExtension: (NovelPlugin.Installed) -> Unit,
    onOpenExtension: (NovelPlugin.Installed) -> Unit,
    onOpenExtensionSettings: (NovelPlugin.Installed) -> Unit,
    onUninstallExtension: (NovelPlugin.Installed) -> Unit,
    onUpdateAll: () -> Unit,
    onRefresh: () -> Unit,
    onToggleSection: (String) -> Unit,
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
                    onUpdateExtension = onUpdateExtension,
                    onReinstallExtension = onReinstallExtension,
                    onOpenExtension = onOpenExtension,
                    onOpenExtensionSettings = onOpenExtensionSettings,
                    onUninstallExtension = onUninstallExtension,
                    onUpdateAll = onUpdateAll,
                    onToggleSection = onToggleSection,
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
    onUpdateExtension: (NovelPlugin.Installed) -> Unit,
    onReinstallExtension: (NovelPlugin.Installed) -> Unit,
    onOpenExtension: (NovelPlugin.Installed) -> Unit,
    onOpenExtensionSettings: (NovelPlugin.Installed) -> Unit,
    onUninstallExtension: (NovelPlugin.Installed) -> Unit,
    onUpdateAll: () -> Unit,
    onToggleSection: (String) -> Unit,
) {
    val grouped = state.items.groupBy { it.status }
    val context = LocalContext.current

    FastScrollLazyColumn(
        contentPadding = contentPadding + topSmallPaddingValues,
    ) {
        val updates = grouped[NovelExtensionItem.Status.UpdateAvailable].orEmpty()
        if (updates.isNotEmpty()) {
            item(key = "novel-ext-updates-header") {
                ExtensionHeader(
                    textRes = MR.strings.ext_updates_pending,
                    action = {
                        if (updates.any { it.hasUpdate }) {
                            IconButton(onClick = onUpdateAll) {
                                Icon(
                                    imageVector = Icons.Outlined.GetApp,
                                    contentDescription = stringResource(MR.strings.ext_update_all),
                                )
                            }
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
                    onUpdateExtension = onUpdateExtension,
                    onReinstallExtension = onReinstallExtension,
                    onOpenExtension = onOpenExtension,
                    onOpenExtensionSettings = onOpenExtensionSettings,
                    onUninstallExtension = onUninstallExtension,
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
                    onOpenExtension = onOpenExtension,
                    onOpenExtensionSettings = onOpenExtensionSettings,
                    onUninstallExtension = onUninstallExtension,
                )
            }
        }

        val available = grouped[NovelExtensionItem.Status.Available].orEmpty()
        if (state.availableLanguages.isNotEmpty()) {
            item(key = "novel-ext-available-header") {
                ExtensionHeader(textRes = MR.strings.ext_available)
            }
            state.availableLanguages.forEach { language ->
                val displayName = LocaleHelper.getSourceDisplayName(language, context)
                val isCollapsed = language in state.collapsedLanguages
                val languageItems = if (isCollapsed && state.searchQuery.isNullOrEmpty()) {
                    emptyList()
                } else {
                    available.filter { it.plugin.lang == language }
                }

                item(key = "novel-ext-language-$language") {
                    ExtensionHeader(
                        text = displayName,
                        action = {
                            IconButton(onClick = { onToggleSection(language) }) {
                                Icon(
                                    imageVector = if (isCollapsed) {
                                        Icons.Outlined.ExpandMore
                                    } else {
                                        Icons.Outlined.ExpandLess
                                    },
                                    contentDescription = null,
                                )
                            }
                        },
                    )
                }

                items(
                    items = languageItems,
                    key = { item -> "novel-ext-available-$language-${item.plugin.id}" },
                ) { item ->
                    NovelExtensionItemRow(
                        item = item,
                        onInstallExtension = onInstallExtension,
                    )
                }
            }
        }
    }
}

@Composable
private fun NovelExtensionItemRow(
    item: NovelExtensionItem,
    onInstallExtension: ((NovelPlugin.Available) -> Unit)? = null,
    onUpdateExtension: ((NovelPlugin.Installed) -> Unit)? = null,
    onReinstallExtension: ((NovelPlugin.Installed) -> Unit)? = null,
    onOpenExtension: ((NovelPlugin.Installed) -> Unit)? = null,
    onOpenExtensionSettings: ((NovelPlugin.Installed) -> Unit)? = null,
    onUninstallExtension: ((NovelPlugin.Installed) -> Unit)? = null,
) {
    val plugin = item.plugin
    val onItemClick: () -> Unit = {
        when (resolveNovelExtensionRowAction(item)) {
            NovelExtensionRowAction.None -> Unit
            NovelExtensionRowAction.Install -> (plugin as? NovelPlugin.Available)?.let {
                onInstallExtension?.invoke(it)
            }
            NovelExtensionRowAction.Update -> (plugin as? NovelPlugin.Installed)?.let { onUpdateExtension?.invoke(it) }
            NovelExtensionRowAction.Reinstall -> (plugin as? NovelPlugin.Installed)?.let {
                onReinstallExtension?.invoke(it)
            }
            NovelExtensionRowAction.Open -> (plugin as? NovelPlugin.Installed)?.let { onOpenExtension?.invoke(it) }
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
                if (shouldLoadNovelPluginIcon(plugin.iconUrl)) {
                    AsyncImage(
                        model = plugin.iconUrl,
                        contentDescription = null,
                        placeholder = ColorPainter(Color(0x1F888888)),
                        error = painterResource(R.mipmap.ic_default_source),
                        modifier = Modifier.size(34.dp),
                    )
                } else {
                    Image(
                        painter = painterResource(R.mipmap.ic_default_source),
                        contentDescription = null,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
        },
        action = {
            when (item.installStep) {
                InstallStep.Pending, InstallStep.Downloading, InstallStep.Installing -> Unit
                InstallStep.Error -> {
                    val retryAction = resolveNovelExtensionRowAction(item)
                    if (retryAction != NovelExtensionRowAction.None) {
                        IconButton(onClick = onItemClick) {
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
                    plugin is NovelPlugin.Installed -> {
                        Row {
                            if (onOpenExtensionSettings != null && item.hasSettings) {
                                IconButton(onClick = { onOpenExtensionSettings(plugin) }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Settings,
                                        contentDescription = stringResource(MR.strings.action_settings),
                                    )
                                }
                            }
                            if (onUpdateExtension != null && item.hasUpdate) {
                                IconButton(onClick = { onUpdateExtension(plugin) }) {
                                    Icon(
                                        imageVector = Icons.Outlined.GetApp,
                                        contentDescription = stringResource(MR.strings.ext_update),
                                    )
                                }
                            }
                            if (onReinstallExtension != null && item.hasRepoUpdate && !item.hasUpdate) {
                                IconButton(onClick = { onReinstallExtension(plugin) }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Warning,
                                        contentDescription = stringResource(MR.strings.ext_repo_update_required),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            if (onUninstallExtension != null) {
                                IconButton(onClick = { onUninstallExtension(plugin) }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = stringResource(MR.strings.ext_remove),
                                    )
                                }
                            }
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
                        text = "v${plugin.versionName}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                val repoDisplayName = item.repoDisplayName
                val repoName = when {
                    plugin is NovelPlugin.Available && item.repoSourceCount > 1 -> {
                        pluralStringResource(MR.plurals.num_repos, count = item.repoSourceCount, item.repoSourceCount)
                    }
                    repoDisplayName != null -> repoDisplayName.oneWordRepoName()
                    else -> plugin.repoDisplayName(item.repoSourceCount)
                }
                repoName?.takeIf { it.isNotBlank() }?.let { name ->
                    Row(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            text = "· $name",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (item.hasRepoUpdate) {
                    Row(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            text = stringResource(MR.strings.ext_repo_update_short),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
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

private fun NovelPlugin.repoDisplayName(repoSourceCount: Int): String? {
    if (this is NovelPlugin.Available && repoSourceCount > 1) {
        return null
    }

    val rawName = when (this) {
        is NovelPlugin.Available -> repoName.ifBlank { repoUrl.shortRepoName() }
        is NovelPlugin.Installed -> repoName?.takeIf { it.isNotBlank() } ?: repoUrl.shortRepoName()
    }

    return rawName.takeIf { it.isNotBlank() }?.oneWordRepoName()
}

private fun String.shortRepoName(): String {
    val withoutScheme = substringAfter("://", this)
    val host = withoutScheme.substringBefore('/').removePrefix("www.")
    if (host.equals("github.com", ignoreCase = true) || host.equals("raw.githubusercontent.com", ignoreCase = true)) {
        val owner = withoutScheme.substringAfter('/', "").substringBefore('/')
        if (owner.isNotBlank()) return owner
    }
    return host.ifBlank { this }
}

private fun String.oneWordRepoName(maxLength: Int = 14): String {
    val commonWords = setOf(
        "novel",
        "anime",
        "manga",
        "extension",
        "extensions",
        "plugin",
        "plugins",
        "repo",
        "repos",
        "repository",
        "repositories",
        "source",
        "sources",
    )
    val normalized = trim()
        .removePrefix("http://")
        .removePrefix("https://")
        .substringBefore('/')
        .removePrefix("www.")
        .replace('-', ' ')
        .replace('_', ' ')
        .replace('.', ' ')
    val word = normalized
        .split(' ')
        .firstOrNull { it.isNotBlank() && it.lowercase() !in commonWords }
        ?: trim()

    return if (word.length <= maxLength) word else word.take(maxLength - 1).trimEnd() + "…"
}
