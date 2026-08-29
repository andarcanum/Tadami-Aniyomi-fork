package eu.kanade.tachiyomi.ui.browse.novel.extension.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.RepoPickerDialog
import eu.kanade.presentation.browse.novel.NovelExtensionDetailsScreen
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.domain.extension.novel.model.NovelPlugin
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.screens.LoadingScreen

data class NovelExtensionDetailsScreen(
    internal val pluginId: String,
) : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val screenModel = rememberScreenModel {
            NovelExtensionDetailsScreenModel(
                pluginId = pluginId,
                context = context,
            )
        }
        val state by screenModel.state.collectAsStateWithLifecycle()

        if (state.isLoading) {
            LoadingScreen()
            return
        }

        val navigator = LocalNavigator.currentOrThrow

        var showReinstallPicker by remember { mutableStateOf(false) }

        NovelExtensionDetailsScreen(
            navigateUp = navigator::pop,
            state = state,
            onClickSourcePreferences = { navigator.push(novelSourcePreferencesScreen(it)) },
            onClickEnableAll = { screenModel.toggleSources(true) },
            onClickDisableAll = { screenModel.toggleSources(false) },
            onClickClearCookies = screenModel::clearCookies,
            onClickUninstall = screenModel::uninstallExtension,
            onClickUpdate = screenModel::updateExtension,
            onClickReinstall = { showReinstallPicker = true },
            onClickSource = screenModel::toggleSource,
            onClickIncognito = screenModel::toggleIncognito,
        )

        if (showReinstallPicker) {
            RepoPickerDialog(
                titleRes = MR.strings.ext_repo_update_dialog_title,
                newestContentDescriptionRes = AYMR.strings.novel_repo_picker_newest,
                itemName = state.extension?.name.orEmpty(),
                options = screenModel.getReinstallCandidates(),
                onSelectOption = {
                    screenModel.reinstallFromRepo(it)
                    showReinstallPicker = false
                },
                onDismiss = { showReinstallPicker = false },
                optionLabel = { it.repoName.ifBlank { it.repoUrl } },
                optionVersionText = { "v${it.versionName}" },
                comparator = compareBy<NovelPlugin.Available> { it.versionCode },
            )
        }

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                if (event is NovelExtensionDetailsEvent.Uninstalled) {
                    navigator.pop()
                }
            }
        }
    }
}

internal fun novelSourcePreferencesScreen(sourceId: Long): NovelSourcePreferencesScreen {
    return NovelSourcePreferencesScreen(sourceId)
}
