package eu.kanade.tachiyomi.ui.reels

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.reels.anime.model.ReelsFavorite
import tachiyomi.domain.reels.anime.repository.ReelsFavoriteRepository
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ReelsFavoritesScreenModel(
    private val repository: ReelsFavoriteRepository = Injekt.get(),
    private val sourceManager: AnimeSourceManager = Injekt.get(),
) : StateScreenModel<ReelsFavoritesScreenModel.State>(State()) {

    @Immutable
    data class State(
        val favorites: List<ReelsFavorite> = emptyList(),
        val sourceNames: Map<Long, String> = emptyMap(),
    )

    init {
        screenModelScope.launch {
            repository.subscribeAll().collectLatest { favorites ->
                val names = favorites
                    .map { it.sourceId }
                    .distinct()
                    .mapNotNull { id -> sourceManager.get(id)?.let { id to it.name } }
                    .toMap()
                mutableState.update { it.copy(favorites = favorites, sourceNames = names) }
            }
        }
    }

    fun removeFavorite(favorite: ReelsFavorite) {
        screenModelScope.launch {
            repository.delete(favorite.videoId, favorite.sourceId)
        }
    }
}
