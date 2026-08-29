package eu.kanade.tachiyomi.ui.reels

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.reels.anime.model.ReelsFollow
import tachiyomi.domain.reels.anime.repository.ReelsFollowRepository
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ReelsFollowsScreenModel(
    private val repository: ReelsFollowRepository = Injekt.get(),
    private val sourceManager: AnimeSourceManager = Injekt.get(),
) : StateScreenModel<ReelsFollowsScreenModel.State>(State()) {

    @Immutable
    data class State(
        val follows: List<ReelsFollow> = emptyList(),
        val sourceNames: Map<Long, String> = emptyMap(),
    )

    init {
        screenModelScope.launch {
            repository.subscribeAll().collectLatest { follows ->
                val names = follows
                    .map { it.sourceId }
                    .distinct()
                    .mapNotNull { id -> sourceManager.get(id)?.let { id to it.name } }
                    .toMap()
                mutableState.update { it.copy(follows = follows, sourceNames = names) }
            }
        }
    }

    fun removeFollow(follow: ReelsFollow) {
        screenModelScope.launch {
            repository.delete(follow.sourceId, follow.creator)
        }
    }

    // Undo path for the remove snackbar: INSERT OR REPLACE restores the row with its
    // original addedAt, so the follow keeps its sort position (favorites pattern).
    fun restoreFollow(follow: ReelsFollow) {
        screenModelScope.launch {
            repository.insert(follow)
        }
    }
}
