package eu.kanade.presentation.more.settings.screen.browse

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import mihon.domain.extensionrepo.novel.interactor.CreateNovelExtensionRepo
import mihon.domain.extensionrepo.novel.interactor.DeleteNovelExtensionRepo
import mihon.domain.extensionrepo.novel.interactor.GetNovelExtensionRepo
import mihon.domain.extensionrepo.novel.interactor.ReplaceNovelExtensionRepo
import mihon.domain.extensionrepo.novel.interactor.UpdateNovelExtensionRepo
import tachiyomi.core.common.util.lang.launchIO
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelExtensionReposScreenModel(
    private val getExtensionRepo: GetNovelExtensionRepo = Injekt.get(),
    private val createExtensionRepo: CreateNovelExtensionRepo = Injekt.get(),
    private val deleteExtensionRepo: DeleteNovelExtensionRepo = Injekt.get(),
    private val replaceExtensionRepo: ReplaceNovelExtensionRepo = Injekt.get(),
    private val updateExtensionRepo: UpdateNovelExtensionRepo = Injekt.get(),
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

    fun createRepo(baseUrl: String) {
        screenModelScope.launchIO {
            when (val result = createExtensionRepo.await(baseUrl)) {
                CreateNovelExtensionRepo.Result.InvalidUrl -> _events.send(RepoEvent.InvalidUrl)
                CreateNovelExtensionRepo.Result.RepoAlreadyExists -> _events.send(RepoEvent.RepoAlreadyExists)
                is CreateNovelExtensionRepo.Result.DuplicateFingerprint -> {
                    showDialog(RepoDialog.Conflict(result.oldRepo, result.newRepo))
                }
                else -> {}
            }
        }
    }

    fun replaceRepo(newRepo: mihon.domain.extensionrepo.model.ExtensionRepo) {
        screenModelScope.launchIO {
            replaceExtensionRepo.await(newRepo)
        }
    }

    fun refreshRepos() {
        val status = state.value

        if (status is RepoScreenState.Success) {
            screenModelScope.launchIO {
                updateExtensionRepo.awaitAll()
            }
        }
    }

    fun deleteRepo(baseUrl: String) {
        screenModelScope.launchIO {
            deleteExtensionRepo.await(baseUrl)
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
