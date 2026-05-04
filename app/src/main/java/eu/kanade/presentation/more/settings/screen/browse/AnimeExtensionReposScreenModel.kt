package eu.kanade.presentation.more.settings.screen.browse

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import mihon.domain.extensionrepo.anime.interactor.CreateAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.DeleteAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.GetAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.ReplaceAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.UpdateAnimeExtensionRepo
import mihon.domain.extensionrepo.model.ExtensionRepo
import tachiyomi.core.common.util.lang.launchIO
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeExtensionReposScreenModel(
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
            when (val result = createExtensionRepo.await(baseUrl, displayName)) {
                CreateAnimeExtensionRepo.Result.InvalidUrl -> _events.send(RepoEvent.InvalidUrl)
                CreateAnimeExtensionRepo.Result.RepoAlreadyExists -> _events.send(RepoEvent.RepoAlreadyExists)
                is CreateAnimeExtensionRepo.Result.DuplicateFingerprint -> {
                    showDialog(RepoDialog.Conflict(result.oldRepo, result.newRepo))
                }
                CreateAnimeExtensionRepo.Result.Success -> refreshAvailablePlugins()
                else -> {}
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
            replaceExtensionRepo.await(newRepo)
            refreshAvailablePlugins()
        }
    }

    /**
     * Updates the stored display name for an existing repo.
     */
    fun renameRepo(repo: ExtensionRepo, displayName: String) {
        screenModelScope.launchIO {
            replaceExtensionRepo.await(repo.copy(name = displayName))
            refreshAvailablePlugins()
        }
    }

    /**
     * Refreshes information for each repository.
     */
    fun refreshRepos() {
        val status = state.value

        if (status is RepoScreenState.Success) {
            screenModelScope.launchIO {
                updateExtensionRepo.awaitAll()
                refreshAvailablePlugins()
            }
        }
    }

    /**
     * Deletes the given repo from the database
     */
    fun deleteRepo(baseUrl: String) {
        screenModelScope.launchIO {
            deleteExtensionRepo.await(baseUrl)
            refreshAvailablePlugins()
        }
    }

    private suspend fun refreshAvailablePlugins() {
        extensionManager.findAvailableExtensions()
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
