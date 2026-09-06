package eu.kanade.tachiyomi.ui.browse.manga.extension.details

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
import eu.kanade.presentation.browse.manga.MangaExtensionDetailsScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.screens.LoadingScreen

data class MangaExtensionDetailsScreen(
    private val pkgName: String,
) : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val screenModel = rememberScreenModel {
            MangaExtensionDetailsScreenModel(
                pkgName = pkgName,
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

        MangaExtensionDetailsScreen(
            navigateUp = navigator::pop,
            state = state,
            onClickSourcePreferences = { navigator.push(MangaSourcePreferencesScreen(it)) },
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
                newestContentDescriptionRes = AYMR.strings.repo_picker_newest,
                itemName = state.extension?.name.orEmpty(),
                options = screenModel.getReinstallCandidates(),
                onSelectOption = {
                    screenModel.reinstallFromRepo(it)
                    showReinstallPicker = false
                },
                onDismiss = { showReinstallPicker = false },
                optionLabel = { it.repoName.ifBlank { it.repoUrl } },
                optionVersionText = { "v${it.versionName}" },
                comparator = compareBy<MangaExtension.Available> { it.versionCode }
                    .thenBy { it.libVersion },
            )
        }

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                if (event is MangaExtensionDetailsEvent.Uninstalled) {
                    navigator.pop()
                }
            }
        }
    }
}
