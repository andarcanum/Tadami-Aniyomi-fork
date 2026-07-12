package eu.kanade.presentation.more.settings.screen.browse

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import mihon.domain.extensionrepo.anime.interactor.CreateAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.DeleteAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.GetAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.ReplaceAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.UpdateAnimeExtensionRepo
import mihon.domain.extensionrepo.model.ExtensionRepo
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeExtensionStoreScreenModel(
    private val getExtensionRepo: GetAnimeExtensionRepo = Injekt.get(),
    private val createExtensionRepo: CreateAnimeExtensionRepo = Injekt.get(),
    private val deleteExtensionRepo: DeleteAnimeExtensionRepo = Injekt.get(),
    private val replaceExtensionRepo: ReplaceAnimeExtensionRepo = Injekt.get(),
    private val updateExtensionRepo: UpdateAnimeExtensionRepo = Injekt.get(),
    private val extensionManager: AnimeExtensionManager = Injekt.get(),
) : StateScreenModel<RepoScreenState>(RepoScreenState.Loading) {

    private val _events: Channel<RepoEvent> = Channel(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    init {
        screenModelScope.launchIO {
            getExtensionRepo.getAll() // trigger legacy port
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
                    CreateAnimeExtensionRepo.Result.InvalidUrl -> _events.send(RepoEvent.InvalidUrl)
                    CreateAnimeExtensionRepo.Result.RepoAlreadyExists -> _events.send(RepoEvent.RepoAlreadyExists)
                    is CreateAnimeExtensionRepo.Result.DuplicateFingerprint -> {
                        showDialog(RepoDialog.Conflict(result.oldRepo, result.newRepo))
                    }
                    CreateAnimeExtensionRepo.Result.Success -> refreshAvailablePlugins()
                    CreateAnimeExtensionRepo.Result.Error -> _events.send(RepoEvent.UnknownError)
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
                logcat(LogPriority.WARN, error) { "Failed to refresh available anime plugins" }
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
