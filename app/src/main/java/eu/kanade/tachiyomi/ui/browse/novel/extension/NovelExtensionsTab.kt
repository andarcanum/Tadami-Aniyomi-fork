package eu.kanade.tachiyomi.ui.browse.novel.extension

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.novel.NovelExtensionScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.more.settings.screen.browse.NovelExtensionReposScreen
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun novelExtensionsTab(
    extensionsScreenModel: NovelExtensionsScreenModel,
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val state by extensionsScreenModel.state.collectAsState()

    return TabContent(
        titleRes = AYMR.strings.label_novel_extensions,
        badgeNumber = state.updates.takeIf { it > 0 },
        searchEnabled = true,
        actions = persistentListOf(
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
                onUpdateAll = extensionsScreenModel::updateAllExtensions,
                onRefresh = extensionsScreenModel::refresh,
            )
        },
    )
}
