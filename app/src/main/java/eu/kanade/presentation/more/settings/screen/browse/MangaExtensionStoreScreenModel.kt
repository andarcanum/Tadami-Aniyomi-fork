package eu.kanade.presentation.more.settings.screen.browse

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import mihon.domain.extensionrepo.manga.interactor.CreateMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.DeleteMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.GetMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.ReplaceMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.UpdateMangaExtensionRepo
import mihon.domain.extensionrepo.model.ExtensionRepo
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaExtensionStoreScreenModel(
    private val getExtensionRepo: GetMangaExtensionRepo = Injekt.get(),
    private val createExtensionRepo: CreateMangaExtensionRepo = Injekt.get(),
    private val deleteExtensionRepo: DeleteMangaExtensionRepo = Injekt.get(),
    private val replaceExtensionRepo: ReplaceMangaExtensionRepo = Injekt.get(),
    private val updateExtensionRepo: UpdateMangaExtensionRepo = Injekt.get(),
    private val extensionManager: MangaExtensionManager = Injekt.get(),
) : StateScreenModel<RepoScreenState>(RepoScreenState.Loading) {

    private val _events: Channel<RepoEvent> = Channel(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    init {
        screenModelScope.launchIO {
            // Trigger legacy port on first open of extension store screen (after Home launch)
            getExtensionRepo.getAll() // this will call ensureLegacyMigrated inside
            getExtensionRepo.subscribeAll()
                .collectLatest { repos ->
                    mutableState.update {
                        RepoScreenState.Success(
                            repos = repos.toImmutableSet(),
                        )
                    }
                }
        }
    }

    /**
     * Creates and adds a new repo to the database.
     *
     * @param baseUrl The baseUrl of the repo to create.
     */
    fun createRepo(baseUrl: String, displayName: String? = null) {
        screenModelScope.launchIO {
            try {
                when (val result = createExtensionRepo.await(baseUrl, displayName)) {
                    CreateMangaExtensionRepo.Result.InvalidUrl -> _events.send(RepoEvent.InvalidUrl)
                    CreateMangaExtensionRepo.Result.RepoAlreadyExists -> _events.send(RepoEvent.RepoAlreadyExists)
                    is CreateMangaExtensionRepo.Result.DuplicateFingerprint -> {
                        showDialog(RepoDialog.Conflict(result.oldRepo, result.newRepo))
                    }
                    CreateMangaExtensionRepo.Result.Success -> refreshAvailablePlugins()
                    CreateMangaExtensionRepo.Result.Error -> _events.send(RepoEvent.UnknownError)
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to create repository" }
                _events.send(RepoEvent.UnknownError)
            }
        }
    }

    /**
     * Inserts a repo to the database, replace a matching repo with the same signing key fingerprint if found.
     *
     * @param newRepo The repo to insert
     */
    fun replaceRepo(newRepo: ExtensionRepo) {
        screenModelScope.launchIO {
            try {
                replaceExtensionRepo.await(newRepo)
                refreshAvailablePlugins()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to replace repository" }
                _events.send(RepoEvent.UnknownError)
            }
        }
    }

    /**
     * Updates the stored display name for an existing repo.
     */
    fun renameRepo(repo: ExtensionRepo, displayName: String) {
        screenModelScope.launchIO {
            try {
                replaceExtensionRepo.await(repo.copy(name = displayName))
                refreshAvailablePlugins()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to rename repository" }
                _events.send(RepoEvent.UnknownError)
            }
        }
    }

    /**
     * Refreshes information for each repository.
     */
    fun refreshRepos() {
        val status = state.value

        if (status is RepoScreenState.Success) {
            screenModelScope.launchIO {
                try {
                    updateExtensionRepo.awaitAll()
                    refreshAvailablePlugins()
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to refresh repositories" }
                    _events.send(RepoEvent.UnknownError)
                }
            }
        }
    }

    /**
     * Deletes the given repo from the database
     */
    fun deleteRepo(baseUrl: String) {
        screenModelScope.launchIO {
            try {
                deleteExtensionRepo.await(baseUrl)
                refreshAvailablePlugins()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to delete repository" }
                _events.send(RepoEvent.UnknownError)
            }
        }
    }

    private suspend fun refreshAvailablePlugins() {
        runCatching { extensionManager.findAvailableExtensions() }
            .onFailure { error ->
                logcat(LogPriority.WARN, error) { "Failed to refresh available manga plugins" }
            }
    }

    fun showDialog(dialog: RepoDialog) {
        mutableState.update {
            when (it) {
                RepoScreenState.Loading -> it
                is RepoScreenState.Success -> it.copy(dialog = dialog)
            }
        }
    }

    fun dismissDialog() {
        mutableState.update {
            when (it) {
                RepoScreenState.Loading -> it
                is RepoScreenState.Success -> it.copy(dialog = null)
            }
        }
    }
}

sealed class RepoEvent {
    sealed class LocalizedMessage(val stringRes: StringResource) : RepoEvent()
    data object InvalidUrl : LocalizedMessage(MR.strings.invalid_store_name)
    data object RepoAlreadyExists : LocalizedMessage(MR.strings.error_store_exists)
    data object UnknownError : LocalizedMessage(MR.strings.unknown_error)
}

sealed class RepoDialog {
    data object Create : RepoDialog()
    data class Delete(val repo: String) : RepoDialog()
    data class Rename(val repo: ExtensionRepo) : RepoDialog()
    data class Conflict(val oldRepo: ExtensionRepo, val newRepo: ExtensionRepo) : RepoDialog()
    data class Confirm(val url: String) : RepoDialog()
}

sealed class RepoScreenState {

    @Immutable
    data object Loading : RepoScreenState()

    @Immutable
    data class Success(
        val repos: ImmutableSet<ExtensionRepo>,
        val oldRepos: ImmutableSet<String>? = null,
        val dialog: RepoDialog? = null,
    ) : RepoScreenState() {

        val isEmpty: Boolean
            get() = repos.isEmpty()
    }
}
