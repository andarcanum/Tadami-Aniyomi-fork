package eu.kanade.presentation.more.settings.screen.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.screen.browse.components.ExtensionStoreConfirmDialog
import eu.kanade.presentation.more.settings.screen.browse.components.ExtensionStoreConflictDialog
import eu.kanade.presentation.more.settings.screen.browse.components.ExtensionStoreCreateDialog
import eu.kanade.presentation.more.settings.screen.browse.components.ExtensionStoreDeleteDialog
import eu.kanade.presentation.more.settings.screen.browse.components.ExtensionStoreRenameDialog
import eu.kanade.presentation.more.settings.screen.browse.components.ExtensionStoreScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.presentation.core.screens.LoadingScreen

class AnimeExtensionStoreScreen(
    private val url: String? = null,
) : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel { AnimeExtensionStoreScreenModel() }
        val state by screenModel.state.collectAsStateWithLifecycle()

        LaunchedEffect(url) {
            url?.let { screenModel.showDialog(RepoDialog.Confirm(it)) }
        }

        if (state is RepoScreenState.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as RepoScreenState.Success

        ExtensionStoreScreen(
            state = successState,
            onClickCreate = { screenModel.showDialog(RepoDialog.Create) },
            onOpenWebsite = { context.openInBrowser(it.website) },
            onClickDelete = { screenModel.showDialog(RepoDialog.Delete(it)) },
            onClickRename = { screenModel.showDialog(RepoDialog.Rename(it)) },
            onClickRefresh = { screenModel.refreshRepos() },
            navigateUp = navigator::pop,
        )

        when (val dialog = successState.dialog) {
            null -> {}
            is RepoDialog.Create -> {
                ExtensionStoreCreateDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onCreate = { url, name -> screenModel.createRepo(url, name) },
                    repoUrls = successState.repos.map { it.baseUrl }.toImmutableSet(),
                )
            }
            is RepoDialog.Delete -> {
                ExtensionStoreDeleteDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onDelete = { screenModel.deleteRepo(dialog.repo) },
                    repo = dialog.repo,
                )
            }
            is RepoDialog.Rename -> {
                ExtensionStoreRenameDialog(
                    repo = dialog.repo,
                    onDismissRequest = screenModel::dismissDialog,
                    onRename = { newName -> screenModel.renameRepo(dialog.repo, newName) },
                )
            }
            is RepoDialog.Conflict -> {
                ExtensionStoreConflictDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onMigrate = { screenModel.replaceRepo(dialog.newRepo) },
                    oldRepo = dialog.oldRepo,
                    newRepo = dialog.newRepo,
                )
            }
            is RepoDialog.Confirm -> {
                ExtensionStoreConfirmDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onCreate = { screenModel.createRepo(dialog.url) },
                    repo = dialog.url,
                )
            }
        }

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                if (event is RepoEvent.LocalizedMessage) {
                    context.toast(event.stringRes)
                }
            }
        }
    }
}
