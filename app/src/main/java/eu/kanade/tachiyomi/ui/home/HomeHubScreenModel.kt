package eu.kanade.tachiyomi.ui.home

import android.content.Context
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UserProfilePreferences
import eu.kanade.tachiyomi.ui.main.MainActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.history.anime.interactor.GetAnimeHistory
import tachiyomi.domain.history.anime.interactor.GetNextEpisodes
import tachiyomi.domain.history.anime.model.AnimeHistoryWithRelations
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

class HomeHubScreenModel(
    private val getAnimeHistory: GetAnimeHistory = Injekt.get(),
    private val getNextEpisodes: GetNextEpisodes = Injekt.get(),
    private val getLibraryAnime: GetLibraryAnime = Injekt.get(),
    private val userProfilePreferences: UserProfilePreferences = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: AnimeSourceManager = Injekt.get(),
) : StateScreenModel<HomeHubScreenModel.State>(State()) {

    private val fastCache = HomeHubFastCache(Injekt.get<android.app.Application>())

    @Volatile
    private var liveUpdatesStarted = false

    data class State(
        val hero: HeroData? = null,
        val history: List<HistoryData> = emptyList(),
        val recommendations: List<RecommendationData> = emptyList(),
        val heroEpisode: Episode? = null,
        val userName: String = "Зритель",
        val userAvatar: String = "",
        val greeting: StringResource = AYMR.strings.aurora_welcome_back,
        val isInitialized: Boolean = false,
        val isLoading: Boolean = true
    ) {
        val isEmpty: Boolean
            get() = hero == null && history.isEmpty() && recommendations.isEmpty()

        val showWelcome: Boolean
            get() = !isInitialized && isEmpty && !isLoading
    }

    data class HeroData(
        val animeId: Long,
        val title: String,
        val episodeNumber: Double,
        val coverData: AnimeCover,
        val episodeId: Long
    )

    data class HistoryData(
        val animeId: Long,
        val title: String,
        val episodeNumber: Double,
        val coverData: AnimeCover
    )

    data class RecommendationData(
        val animeId: Long,
        val title: String,
        val coverUrl: String?,
        val coverLastModified: Long
    )

    init {
        val cached = fastCache.load()
        val lastOpened = userProfilePreferences.lastOpenedTime().get()
        val greeting = GreetingProvider.getGreeting(lastOpened)
        
        userProfilePreferences.lastOpenedTime().set(System.currentTimeMillis())
        
        mutableState.update {
            it.copy(
                hero = cached.hero?.toHeroData(),
                history = cached.history.map { h -> h.toHistoryData() },
                recommendations = cached.recommendations.map { r -> r.toRecommendationData() },
                userName = cached.userName,
                userAvatar = cached.userAvatar,
                greeting = greeting,
                isInitialized = cached.isInitialized,
                isLoading = false
            )
        }

        cached.hero?.let { hero ->
            screenModelScope.launchIO {
                loadHeroEpisode(hero.animeId, hero.episodeId)
            }
        }
    }

    fun startLiveUpdates() {
        if (liveUpdatesStarted) return
        liveUpdatesStarted = true

        screenModelScope.launchIO {
            combine(
                userProfilePreferences.name().changes(),
                userProfilePreferences.avatarUrl().changes(),
                getAnimeHistory.subscribeRecent(limit = 7),
                getLibraryAnime.subscribeRecentFavorites(10),
            ) { name, avatar, historyList, animeList ->
                LiveData(name, avatar, historyList, animeList)
            }.collectLatest { data ->
                val hero = data.historyList.firstOrNull()
                val history = if (data.historyList.size > 1) data.historyList.drop(1) else emptyList()

                val hasData = hero != null || history.isNotEmpty() || data.animeList.isNotEmpty()
                if (hasData && !state.value.isInitialized) {
                    fastCache.markInitialized()
                }

                val previousHeroId = mutableState.value.hero?.animeId

                mutableState.update {
                    it.copy(
                        hero = hero?.toHeroData(),
                        history = history.map { h -> h.toHistoryData() },
                        recommendations = data.animeList.map { a -> a.toRecommendationData() },
                        userName = data.name,
                        userAvatar = data.avatar,
                        isInitialized = hasData || it.isInitialized,
                        isLoading = false
                    )
                }

                if (hero != null && hero.animeId != previousHeroId) {
                    loadHeroEpisode(hero.animeId, hero.episodeId)
                }

                saveCache()
            }
        }
    }

    private suspend fun loadHeroEpisode(animeId: Long, episodeId: Long) {
        val nextEpisodes = getNextEpisodes.await(animeId, episodeId, onlyUnseen = true)
        val heroEp = nextEpisodes.firstOrNull()
            ?: getNextEpisodes.await(animeId, episodeId, onlyUnseen = false).firstOrNull()
        mutableState.update { it.copy(heroEpisode = heroEp) }
    }

    fun saveCache() {
        val currentState = state.value
        fastCache.save(
            CachedHomeState(
                hero = currentState.hero?.toCached(),
                history = currentState.history.map { it.toCached() },
                recommendations = currentState.recommendations.map { it.toCached() },
                userName = currentState.userName,
                userAvatar = currentState.userAvatar,
                isInitialized = currentState.isInitialized
            )
        )
    }

    fun playHeroEpisode(context: Context) {
        val hero = state.value.hero ?: return
        val episode = state.value.heroEpisode ?: return

        screenModelScope.launchIO {
            MainActivity.startPlayerActivity(context, hero.animeId, episode.id, false)
        }
    }

    fun updateUserName(name: String) {
        userProfilePreferences.name().set(name)
        fastCache.updateUserName(name)
        mutableState.update { it.copy(userName = name) }
    }

    fun updateUserAvatar(uriString: String) {
        val context = Injekt.get<android.app.Application>()
        try {
            val uri = android.net.Uri.parse(uriString)
            val inputStream = context.contentResolver.openInputStream(uri) ?: return
            val file = File(context.filesDir, "user_avatar.jpg")
            file.outputStream().use { output ->
                inputStream.use { input -> input.copyTo(output) }
            }
            val path = file.absolutePath
            userProfilePreferences.avatarUrl().set(path)
            fastCache.updateUserAvatar(path)
            mutableState.update { it.copy(userAvatar = path) }
        } catch (_: Exception) { }
    }

    fun getLastUsedAnimeSourceId(): Long = sourcePreferences.lastUsedAnimeSource().get()

    fun getLastUsedAnimeSourceName(): String? {
        val sourceId = sourcePreferences.lastUsedAnimeSource().get()
        if (sourceId == -1L) return null
        return sourceManager.get(sourceId)?.name
    }

    private data class LiveData(
        val name: String,
        val avatar: String,
        val historyList: List<AnimeHistoryWithRelations>,
        val animeList: List<Anime>
    )

    private fun CachedHeroItem.toHeroData() = HeroData(
        animeId = animeId,
        title = title,
        episodeNumber = episodeNumber,
        coverData = AnimeCover(animeId, -1, true, coverUrl, coverLastModified),
        episodeId = episodeId
    )

    private fun CachedHistoryItem.toHistoryData() = HistoryData(
        animeId = animeId,
        title = title,
        episodeNumber = episodeNumber,
        coverData = AnimeCover(animeId, -1, true, coverUrl, coverLastModified)
    )

    private fun CachedRecommendationItem.toRecommendationData() = RecommendationData(
        animeId = animeId,
        title = title,
        coverUrl = coverUrl,
        coverLastModified = coverLastModified
    )

    private fun AnimeHistoryWithRelations.toHeroData() = HeroData(
        animeId = animeId,
        title = title,
        episodeNumber = episodeNumber,
        coverData = coverData,
        episodeId = episodeId
    )

    private fun AnimeHistoryWithRelations.toHistoryData() = HistoryData(
        animeId = animeId,
        title = title,
        episodeNumber = episodeNumber,
        coverData = coverData
    )

    private fun Anime.toRecommendationData() = RecommendationData(
        animeId = id,
        title = title,
        coverUrl = thumbnailUrl,
        coverLastModified = coverLastModified
    )

    private fun HeroData.toCached() = CachedHeroItem(
        animeId = animeId,
        title = title,
        episodeNumber = episodeNumber,
        coverUrl = coverData.url,
        coverLastModified = coverData.lastModified,
        episodeId = episodeId
    )

    private fun HistoryData.toCached() = CachedHistoryItem(
        animeId = animeId,
        title = title,
        episodeNumber = episodeNumber,
        coverUrl = coverData.url,
        coverLastModified = coverData.lastModified
    )

    private fun RecommendationData.toCached() = CachedRecommendationItem(
        animeId = animeId,
        title = title,
        coverUrl = coverUrl,
        coverLastModified = coverLastModified
    )

    companion object {
        @Volatile
        private var instance: HomeHubScreenModel? = null

        fun saveOnExit() {
            instance?.saveCache()
        }

        internal fun setInstance(model: HomeHubScreenModel) {
            instance = model
        }
    }
}
