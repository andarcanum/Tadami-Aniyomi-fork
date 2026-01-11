package eu.kanade.tachiyomi.ui.home

import android.content.Context
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.anime.interactor.SetAnimeViewerFlags
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UserProfilePreferences
import eu.kanade.tachiyomi.ui.main.MainActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.history.anime.interactor.GetAnimeHistory
import tachiyomi.domain.history.anime.interactor.GetNextEpisodes
import tachiyomi.domain.history.anime.model.AnimeHistoryWithRelations
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.library.service.LibraryPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class HomeHubScreenModel(
    private val getAnimeHistory: GetAnimeHistory = Injekt.get(),
    private val getNextEpisodes: GetNextEpisodes = Injekt.get(),
    private val getAnime: GetAnime = Injekt.get(),
    private val setAnimeViewerFlags: SetAnimeViewerFlags = Injekt.get(),
    private val getLibraryAnime: GetLibraryAnime = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val userProfilePreferences: UserProfilePreferences = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
) : StateScreenModel<HomeHubScreenModel.State>(State()) {

    data class State(
        val history: List<AnimeHistoryWithRelations> = emptyList(),
        val heroItem: AnimeHistoryWithRelations? = null,
        val heroEpisode: Episode? = null,
        val recommendations: List<LibraryAnime> = emptyList(),
        val userName: String = "Guest",
        val userAvatar: String = "",
        val isHistoryLoading: Boolean = true,
        val isRecommendationsLoading: Boolean = true,
    ) {
        val isLoading: Boolean get() = isHistoryLoading || isRecommendationsLoading
    }

    init {
        screenModelScope.launchIO {
            userProfilePreferences.name().changes().collectLatest { name ->
                mutableState.update { it.copy(userName = name) }
            }
        }
        screenModelScope.launchIO {
            userProfilePreferences.avatarUrl().changes().collectLatest { url ->
                mutableState.update { it.copy(userAvatar = url) }
            }
        }

        screenModelScope.launchIO {
            getAnimeHistory.subscribeRecent(limit = 7)
                .collectLatest { historyList ->
                    val hero = historyList.firstOrNull()
                    val history = if (historyList.size > 1) historyList.drop(1) else emptyList()

                    mutableState.update {
                        it.copy(
                            history = history,
                            heroItem = hero,
                            isHistoryLoading = false,
                        )
                    }

                    if (hero != null) {
                        loadHeroEpisode(hero)
                    }
                }
        }

        screenModelScope.launchIO {
            getLibraryAnime.subscribeRecent(10)
                .collectLatest { recommendations ->
                    mutableState.update {
                        it.copy(
                            recommendations = recommendations,
                            isRecommendationsLoading = false,
                        )
                    }
                }
        }
    }

    private fun loadHeroEpisode(hero: AnimeHistoryWithRelations) {
        screenModelScope.launchIO {
            val nextEpisodes = getNextEpisodes.await(hero.animeId, hero.episodeId, onlyUnseen = true)
            val heroEp = nextEpisodes.firstOrNull() 
                ?: getNextEpisodes.await(hero.animeId, hero.episodeId, onlyUnseen = false).firstOrNull()
            mutableState.update { it.copy(heroEpisode = heroEp) }
        }
    }

    fun playHeroEpisode(context: Context) {
        val state = state.value
        val anime = state.heroItem ?: return
        val episode = state.heroEpisode ?: return

        screenModelScope.launchIO {
            withIOContext {
                MainActivity.startPlayerActivity(
                    context,
                    anime.animeId,
                    episode.id,
                    false // TODO: Check preferences for external player
                )
            }
        }
    }

    fun toggleHeroFavorite() {
        // ... (existing logic)
    }

    fun updateUserName(name: String) {
        userProfilePreferences.name().set(name)
    }

    fun updateUserAvatar(uriString: String) {
        val context = Injekt.get<android.app.Application>()
        try {
            val uri = android.net.Uri.parse(uriString)
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val file = java.io.File(context.filesDir, "user_avatar.jpg")
                val outputStream = java.io.FileOutputStream(file)
                inputStream.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                userProfilePreferences.avatarUrl().set(file.absolutePath)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getLastUsedAnimeSourceId(): Long {
        return sourcePreferences.lastUsedAnimeSource().get()
    }
}
