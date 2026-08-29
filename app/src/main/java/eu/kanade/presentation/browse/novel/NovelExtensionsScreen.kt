package eu.kanade.presentation.browse.novel

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import eu.kanade.presentation.browse.manga.ExtensionTrustDialog
import eu.kanade.presentation.more.settings.screen.browse.NovelExtensionStoreScreen
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.ui.browse.novel.extension.NovelExtensionItem
import eu.kanade.tachiyomi.ui.browse.novel.extension.NovelExtensionsScreenModel
import eu.kanade.tachiyomi.util.system.LocaleHelper
import mihon.domain.extensionstore.model.repoDisplayNameFallback
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

internal fun uniqueNovelExtensionItemKeys(
    section: String,
    items: List<NovelExtensionItem>,
): List<String> {
    val seen = mutableMapOf<String, Int>()
    return items.map { item ->
        val baseKey = novelExtensionItemBaseKey(section, item)
        val duplicateIndex = seen.getOrDefault(baseKey, 0)
        seen[baseKey] = duplicateIndex + 1
        if (duplicateIndex == 0) {
            baseKey
        } else {
            "$baseKey#$duplicateIndex"
        }
    }
}

private fun novelExtensionItemBaseKey(
    section: String,
    item: NovelExtensionItem,
): String {
    val plugin = item.plugin
    val status = when (item.status) {
        NovelExtensionItem.Status.UpdateAvailable -> "update"
        NovelExtensionItem.Status.Installed -> "installed"
        NovelExtensionItem.Status.Untrusted -> "untrusted"
        NovelExtensionItem.Status.Available -> "available"
    }
    val pluginType = when (plugin) {
        is NovelPlugin.Available -> "available"
        is NovelPlugin.Installed -> "installed"
        is NovelPlugin.Untrusted -> "untrusted"
    }
    val packageName = when (plugin) {
        is NovelPlugin.Available -> plugin.pkgName
        is NovelPlugin.Installed -> plugin.pkgName
        is NovelPlugin.Untrusted -> plugin.pkgName
    }.orEmpty()
    val trustSignature = (plugin as? NovelPlugin.Untrusted)?.signatureHash.orEmpty()

    return listOf(
        "novel-ext",
        section,
        status,
        pluginType,
        plugin.id,
        plugin.repoUrl,
        packageName,
        plugin.sha256,
        trustSignature,
    ).joinToString(separator = "|")
}

internal enum class NovelExtensionRowAction {
    None,
    Install,
    Update,
    Reinstall,
    Open,
    Trust,
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
            plugin is NovelPlugin.Untrusted -> NovelExtensionRowAction.Trust
            else -> NovelExtensionRowAction.None
        }
        else -> when {
            plugin is NovelPlugin.Available -> NovelExtensionRowAction.Install
            plugin is NovelPlugin.Installed && item.hasRepoUpdate && !item.hasUpdate -> {
                NovelExtensionRowAction.Reinstall
            }
            plugin is NovelPlugin.Installed && item.hasUpdate -> NovelExtensionRowAction.Update
            plugin is NovelPlugin.Installed -> NovelExtensionRowAction.Open
            plugin is NovelPlugin.Untrusted -> NovelExtensionRowAction.Trust
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
    onOpenExtensionSettings: (Long) -> Unit,
    onUninstallExtension: (NovelPlugin.Installed) -> Unit,
    onUninstallUntrustedExtension: (NovelPlugin.Untrusted) -> Unit,
    onTrustExtension: (NovelPlugin.Untrusted) -> Unit,
    onUpdateAll: () -> Unit,
    onRefresh: () -> Unit,
    onToggleSection: (String) -> Unit,
    onCopyDiagnostic: (NovelPlugin) -> Unit,
    onShareApk: (NovelPlugin) -> Unit,
    onCancelInstall: ((NovelPlugin) -> Unit)?,
    onReinstallAfterSignatureMismatch: () -> Unit,
    onDismissSignatureMismatch: () -> Unit,
) {
    val navigator = LocalNavigator.currentOrThrow

    state.signatureMismatchEvent?.let { event ->
        SignatureMismatchDialog(
            displayName = event.displayName,
            onClickReinstall = onReinstallAfterSignatureMismatch,
            onClickDismiss = onDismissSignatureMismatch,
        )
    }

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
                            stringRes = MR.strings.label_extension_stores,
                            icon = Icons.Outlined.Public,
                            onClick = { navigator.push(NovelExtensionStoreScreen()) },
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
                    onUninstallUntrustedExtension = onUninstallUntrustedExtension,
                    onTrustExtension = onTrustExtension,
                    onUpdateAll = onUpdateAll,
                    onToggleSection = onToggleSection,
                    onCopyDiagnostic = onCopyDiagnostic,
                    onShareApk = onShareApk,
                    onCancelInstall = onCancelInstall,
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
    onOpenExtensionSettings: (Long) -> Unit,
    onUninstallExtension: (NovelPlugin.Installed) -> Unit,
    onUninstallUntrustedExtension: (NovelPlugin.Untrusted) -> Unit,
    onTrustExtension: (NovelPlugin.Untrusted) -> Unit,
    onUpdateAll: () -> Unit,
    onToggleSection: (String) -> Unit,
    onCopyDiagnostic: (NovelPlugin) -> Unit,
    onShareApk: (NovelPlugin) -> Unit,
    onCancelInstall: ((NovelPlugin) -> Unit)?,
) {
    val grouped = state.items.groupBy { it.status }
    val context = LocalContext.current
    var trustState by remember { mutableStateOf<NovelPlugin.Untrusted?>(null) }

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
                            // Parity with the manga/anime extensions header: a labeled
                            // "Update all" button, not a bare download-looking icon.
                            Button(onClick = onUpdateAll) {
                                Text(
                                    text = stringResource(MR.strings.ext_update_all),
                                    style = LocalTextStyle.current.copy(
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                )
                            }
                        }
                    },
                )
            }
            val keyedUpdates = uniqueNovelExtensionItemKeys("update", updates).zip(updates)
            items(
                items = keyedUpdates,
                key = { (key, _) -> key },
            ) { (_, item) ->
                NovelExtensionItemRow(
                    modifier = Modifier.animateItemFastScroll(this),
                    item = item,
                    onUpdateExtension = onUpdateExtension,
                    onReinstallExtension = onReinstallExtension,
                    onOpenExtension = onOpenExtension,
                    onOpenExtensionSettings = onOpenExtensionSettings,
                    onUninstallExtension = onUninstallExtension,
                    onTrustExtension = { trustState = it },
                    onCopyDiagnostic = onCopyDiagnostic,
                    onShareApk = onShareApk,
                    onCancelInstall = onCancelInstall,
                )
            }
        }

        val installed = grouped[NovelExtensionItem.Status.Installed].orEmpty() +
            grouped[NovelExtensionItem.Status.Untrusted].orEmpty()
        if (installed.isNotEmpty()) {
            item(key = "novel-ext-installed-header") {
                ExtensionHeader(textRes = MR.strings.ext_installed)
            }
            val keyedInstalled = uniqueNovelExtensionItemKeys("installed", installed).zip(installed)
            items(
                items = keyedInstalled,
                key = { (key, _) -> key },
            ) { (_, item) ->
                NovelExtensionItemRow(
                    modifier = Modifier.animateItemFastScroll(this),
                    item = item,
                    onOpenExtension = onOpenExtension,
                    onOpenExtensionSettings = onOpenExtensionSettings,
                    onUninstallExtension = onUninstallExtension,
                    onTrustExtension = { trustState = it },
                    onCopyDiagnostic = onCopyDiagnostic,
                    onShareApk = onShareApk,
                    onCancelInstall = onCancelInstall,
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

                val keyedLanguageItems = uniqueNovelExtensionItemKeys("available-$language", languageItems)
                    .zip(languageItems)
                items(
                    items = keyedLanguageItems,
                    key = { (key, _) -> key },
                ) { (_, item) ->
                    NovelExtensionItemRow(
                        modifier = Modifier.animateItemFastScroll(this),
                        item = item,
                        onInstallExtension = onInstallExtension,
                        onCopyDiagnostic = onCopyDiagnostic,
                        onShareApk = onShareApk,
                        onCancelInstall = onCancelInstall,
                    )
                }
            }
        }
    }

    trustState?.let { plugin ->
        ExtensionTrustDialog(
            onClickConfirm = {
                onTrustExtension(plugin)
                trustState = null
            },
            onClickDismiss = {
                onUninstallUntrustedExtension(plugin)
                trustState = null
            },
            onDismissRequest = {
                trustState = null
            },
        )
    }
}

@Composable
private fun NovelExtensionItemRow(
    item: NovelExtensionItem,
    onInstallExtension: ((NovelPlugin.Available) -> Unit)? = null,
    onUpdateExtension: ((NovelPlugin.Installed) -> Unit)? = null,
    onReinstallExtension: ((NovelPlugin.Installed) -> Unit)? = null,
    onOpenExtension: ((NovelPlugin.Installed) -> Unit)? = null,
    onOpenExtensionSettings: ((Long) -> Unit)? = null,
    onUninstallExtension: ((NovelPlugin.Installed) -> Unit)? = null,
    onTrustExtension: ((NovelPlugin.Untrusted) -> Unit)? = null,
    onCopyDiagnostic: ((NovelPlugin) -> Unit)? = null,
    onShareApk: ((NovelPlugin) -> Unit)? = null,
    onCancelInstall: ((NovelPlugin) -> Unit)? = null,
    modifier: Modifier = Modifier,
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
            NovelExtensionRowAction.Trust -> (plugin as? NovelPlugin.Untrusted)?.let { onTrustExtension?.invoke(it) }
        }
    }

    BaseBrowseItem(
        modifier = modifier,
        onClickItem = onItemClick,
        icon = {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                val idle = item.installStep.isCompleted()
                if (!idle) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        strokeWidth = 2.dp,
                    )
                }
                val padding by animateDpAsState(
                    targetValue = if (idle) 0.dp else 8.dp,
                    label = "iconPadding",
                )
                if (shouldLoadNovelPluginIcon(plugin.iconUrl)) {
                    AsyncImage(
                        model = plugin.iconUrl,
                        contentDescription = null,
                        placeholder = ColorPainter(Color(0x1F888888)),
                        error = painterResource(R.mipmap.ic_default_source),
                        modifier = Modifier
                            .matchParentSize()
                            .padding(padding)
                            .clip(MaterialTheme.shapes.extraSmall),
                    )
                } else {
                    Image(
                        painter = painterResource(R.mipmap.ic_default_source),
                        contentDescription = null,
                        modifier = Modifier
                            .matchParentSize()
                            .padding(padding)
                            .clip(MaterialTheme.shapes.extraSmall),
                    )
                }
            }
        },
        action = {
            when (item.installStep) {
                InstallStep.Pending, InstallStep.Downloading, InstallStep.Installing -> {
                    // Cancellation parity with manga/anime rows: a cross while the row is busy.
                    IconButton(onClick = { onCancelInstall?.invoke(plugin) }) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(MR.strings.action_cancel),
                        )
                    }
                }
                InstallStep.Error -> {
                    Row {
                        val retryAction = resolveNovelExtensionRowAction(item)
                        if (retryAction != NovelExtensionRowAction.None) {
                            IconButton(onClick = onItemClick) {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = stringResource(MR.strings.action_retry),
                                )
                            }
                        }
                        if (onShareApk != null) {
                            IconButton(onClick = { onShareApk(plugin) }) {
                                Icon(
                                    imageVector = Icons.Outlined.Share,
                                    contentDescription = stringResource(MR.strings.action_share),
                                )
                            }
                        }
                        if (onCopyDiagnostic != null) {
                            IconButton(onClick = { onCopyDiagnostic(plugin) }) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = stringResource(MR.strings.action_copy_to_clipboard),
                                )
                            }
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
                    plugin is NovelPlugin.Untrusted -> {
                        IconButton(onClick = { onTrustExtension?.invoke(plugin) }) {
                            Icon(
                                imageVector = Icons.Outlined.VerifiedUser,
                                contentDescription = stringResource(MR.strings.ext_trust),
                            )
                        }
                    }
                    plugin is NovelPlugin.Installed -> {
                        Row {
                            if (onOpenExtensionSettings != null && item.settingsSourceId != null) {
                                IconButton(onClick = { onOpenExtensionSettings(item.settingsSourceId) }) {
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
        Column(
            modifier = Modifier
                .padding(start = MaterialTheme.padding.medium)
                .weight(1f),
        ) {
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
                    repoDisplayName != null -> repoDisplayName.truncateLabel()
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
                if (plugin is NovelPlugin.Untrusted) {
                    Row(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            text = stringResource(MR.strings.ext_untrusted).uppercase(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
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
        is NovelPlugin.Available -> repoName.ifBlank { repoUrl.repoDisplayNameFallback() }
        is NovelPlugin.Installed -> repoName?.takeIf { it.isNotBlank() } ?: repoUrl.repoDisplayNameFallback()
        is NovelPlugin.Untrusted -> null
    }

    return rawName?.takeIf { it.isNotBlank() }?.truncateLabel()
}

private fun String.truncateLabel(maxLength: Int = 16): String {
    return if (length <= maxLength) this else take(maxLength - 1).trimEnd() + "…"
}

@Composable
private fun SignatureMismatchDialog(
    displayName: String,
    onClickReinstall: () -> Unit,
    onClickDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClickDismiss,
        title = { Text(text = stringResource(MR.strings.extension_signature_mismatch_title)) },
        text = {
            Text(text = stringResource(MR.strings.extension_signature_mismatch_message, displayName))
        },
        confirmButton = {
            TextButton(onClick = onClickReinstall) {
                Text(text = stringResource(MR.strings.extension_reinstall))
            }
        },
        dismissButton = {
            TextButton(onClick = onClickDismiss) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}
