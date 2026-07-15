package eu.kanade.tachiyomi.ui.home

import android.content.Context
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UserProfilePreferences
import eu.kanade.tachiyomi.ui.main.MainActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.history.anime.interactor.GetAnimeHistory
import tachiyomi.domain.history.anime.interactor.GetNextEpisodes
import tachiyomi.domain.history.anime.model.AnimeHistoryWithRelations
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

internal class HomeHubScreenModel(
    context: android.content.Context = Injekt.get<android.app.Application>(),
    userProfilePreferences: UserProfilePreferences = Injekt.get(),
) : BaseHomeHubScreenModel(
    context = context,
    initialState = run {
        val tempCache = HomeHubFastCache(context, HomeHubSection.Anime)
        val cached = tempCache.loadCachedState()
        val hadCache = !cached.isEmpty || cached.isInitialized
        if (hadCache) {
            HomeHubUiState(
                hero = cached.hero?.let { h ->
                    HomeHubHero(
                        entryId = h.entryId,
                        title = h.title,
                        progressNumber = h.progressNumber,
                        coverData = AnimeCover(h.entryId, -1, true, h.coverUrl, h.coverLastModified),
                    )
                },
                history = cached.history.map { h ->
                    HomeHubHistory(
                        entryId = h.entryId,
                        title = h.title,
                        progressNumber = h.progressNumber,
                        coverData = AnimeCover(h.entryId, -1, true, h.coverUrl, h.coverLastModified),
                        section = HomeHubSection.Anime,
                    )
                },
                recommendations = cached.recommendations.map { r ->
                    HomeHubRecommendation(
                        entryId = r.entryId,
                        title = r.title,
                        coverData = AnimeCover(r.entryId, -1, true, r.coverUrl, r.coverLastModified),
                        section = HomeHubSection.Anime,
                        progressNumerator = r.progressNumerator,
                        progressDenominator = r.totalCount,
                    )
                },
                userName = cached.userName,
                userAvatar = cached.userAvatar,
                greeting = GreetingProvider.getInitialGreeting(userProfilePreferences),
                greetingReady = true,
                isLoading = false,
                showWelcome = !cached.isInitialized && cached.isEmpty,
                showFilteredEmpty = cached.isInitialized && cached.isEmpty,
            )
        } else {
            HomeHubUiState(
                userName = userProfilePreferences.name().get(),
                userAvatar = userProfilePreferences.avatarUrl().get(),
                greeting = AYMR.strings.aurora_welcome_back,
                greetingReady = false,
                isLoading = true,
                showWelcome = true,
            )
        }
    },
    userProfilePreferences = userProfilePreferences,
) {

    private val getAnimeHistory: GetAnimeHistory by injectLazy()
    private val getNextEpisodes: GetNextEpisodes by injectLazy()
    private val getLibraryAnime: GetLibraryAnime by injectLazy()
    private val getAnimeCategories: GetAnimeCategories by injectLazy()
    private val sourcePreferences: SourcePreferences by injectLazy()
    private val sourceManager: AnimeSourceManager by injectLazy()
    private val userProfileManager: tachiyomi.data.achievement.UserProfileManager by injectLazy()
    private val streakChecker: tachiyomi.data.achievement.handler.checkers.StreakAchievementChecker by injectLazy()
    private val activityDataRepository: tachiyomi.domain.achievement.repository.ActivityDataRepository by injectLazy()

    override val avatarFileName: String = "user_avatar.jpg"

    private val fastCache = HomeHubFastCache(context, HomeHubSection.Anime)

    @Volatile
    private var liveUpdatesStarted = false

    private var heroEpisode: Episode? = null
    private var originalHeroEpisodeId: Long? = null

    override fun updateCacheUserName(name: String) {
        fastCache.updateUserName(name)
    }

    override fun updateCacheUserAvatar(path: String) {
        fastCache.updateUserAvatar(path)
    }

    override suspend fun loadGreetingStats(): HomeGreetingStats {
        val profile = userProfileManager.getCurrentProfile()
        val currentStreak = streakChecker.getCurrentStreak()
        val monthStats = activityDataRepository.getCurrentMonthStats()
        val libraryAnime = getLibraryAnime.await()

        return HomeGreetingStats(
            achievementCount = profile.achievementsUnlocked,
            episodesWatched = monthStats.episodesWatched,
            librarySize = libraryAnime.size,
            currentStreak = currentStreak,
        )
    }

    init {
        val cached = fastCache.load()
        val hadCache = !cached.isEmpty || cached.isInitialized

        if (hadCache) {
            originalHeroEpisodeId = cached.hero?.subId
            mutableState.update {
                it.copy(
                    hero = cached.hero?.let { h ->
                        HomeHubHero(
                            entryId = h.entryId,
                            title = h.title,
                            progressNumber = h.progressNumber,
                            coverData = AnimeCover(h.entryId, -1, true, h.coverUrl, h.coverLastModified),
                        )
                    },
                    history = cached.history.map { h ->
                        HomeHubHistory(
                            entryId = h.entryId,
                            title = h.title,
                            progressNumber = h.progressNumber,
                            coverData = AnimeCover(h.entryId, -1, true, h.coverUrl, h.coverLastModified),
                            section = HomeHubSection.Anime,
                        )
                    },
                    recommendations = cached.recommendations.map { r ->
                        HomeHubRecommendation(
                            entryId = r.entryId,
                            title = r.title,
                            coverData = AnimeCover(r.entryId, -1, true, r.coverUrl, r.coverLastModified),
                            section = HomeHubSection.Anime,
                            progressNumerator = r.progressNumerator,
                            progressDenominator = r.totalCount,
                        )
                    },
                    userName = cached.userName,
                    userAvatar = cached.userAvatar,
                    isLoading = false,
                    showWelcome = !cached.isInitialized && cached.isEmpty,
                    showFilteredEmpty = cached.isInitialized && cached.isEmpty,
                )
            }
        }

        // PERF: Defer expensive DB work (library + history + streaks + greeting) until after first frame.
        // Cached UI is already shown synchronously above.
        initializeGreetingDeferred()

        cached.hero?.let { hero ->
            screenModelScope.launchIO {
                loadHeroEpisode(hero.entryId, hero.subId)
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
                getAnimeCategories.subscribe(),
                getAnimeHistory.subscribe(""),
                getLibraryAnime.subscribe(),
            ) { name, avatar, categories, historyList, animeList ->
                LiveData(name, avatar, categories, historyList, animeList)
            }.collectLatest { data ->
                val hiddenCategoryIds = hiddenHomeHubCategoryIds(
                    categories = data.categories,
                    isHiddenFromHomeHub = { it.hiddenFromHomeHub },
                    idSelector = { it.id },
                )
                val animeCategoryIdsByAnimeId = homeHubCategoryIdsByEntryId(
                    items = data.animeList,
                    entryIdSelector = { it.anime.id },
                    categoryIdSelector = { it.category },
                )

                val filteredHistory = filterHomeHubEntriesBy(
                    items = data.historyList,
                    keySelector = { it.animeId },
                    entryCategoryIds = animeCategoryIdsByAnimeId,
                    hiddenCategoryIds = hiddenCategoryIds,
                )

                val filteredAnime = filterHomeHubEntriesByDistinct(
                    items = data.animeList,
                    keySelector = { it.anime.id },
                    entryCategoryIds = animeCategoryIdsByAnimeId,
                    hiddenCategoryIds = hiddenCategoryIds,
                )

                val hero = filteredHistory.firstOrNull()
                val history = takeHomeHubHistoryExcluding(
                    items = filteredHistory,
                    limit = 6,
                    excludedEntryId = hero?.animeId,
                    entryIdSelector = { it.animeId },
                )

                val hasData = hero != null || history.isNotEmpty() || filteredAnime.isNotEmpty()
                val isInitialized = hasData ||
                    (
                        state.value.showFilteredEmpty ||
                            (state.value.showWelcome.not() && state.value.isLoading.not())
                        )

                val animeRecommendations = filteredAnime
                    .sortedByDescending { it.anime.dateAdded }
                    .take(10)

                val previousHero = state.value.hero
                val previousHeroEpisodeId = originalHeroEpisodeId

                originalHeroEpisodeId = hero?.episodeId

                val isEmpty = hero == null && history.isEmpty() && animeRecommendations.isEmpty()
                val showWelcome = !isInitialized && isEmpty
                val showFilteredEmpty = isInitialized && isEmpty

                mutableState.update {
                    it.copy(
                        hero = hero?.let { h ->
                            HomeHubHero(
                                entryId = h.animeId,
                                title = h.title,
                                progressNumber = h.episodeNumber,
                                coverData = h.coverData,
                            )
                        },
                        history = history.map { h ->
                            HomeHubHistory(
                                entryId = h.animeId,
                                title = h.title,
                                progressNumber = h.episodeNumber,
                                coverData = h.coverData,
                                section = HomeHubSection.Anime,
                            )
                        },
                        recommendations = animeRecommendations.map { a ->
                            HomeHubRecommendation(
                                entryId = a.anime.id,
                                title = a.anime.title,
                                coverData = AnimeCover(
                                    animeId = a.anime.id,
                                    sourceId = a.anime.source,
                                    isAnimeFavorite = a.anime.favorite,
                                    url = a.anime.thumbnailUrl,
                                    lastModified = a.anime.coverLastModified,
                                ),
                                section = HomeHubSection.Anime,
                                progressNumerator = a.seenCount,
                                progressDenominator = a.totalCount,
                            )
                        },
                        userName = data.name,
                        userAvatar = data.avatar,
                        isLoading = false,
                        showWelcome = showWelcome,
                        showFilteredEmpty = showFilteredEmpty,
                    )
                }

                if (hasData && !fastCache.load().isInitialized) {
                    fastCache.markInitialized()
                }

                if (hero != null && hero.animeId != previousHero?.entryId) {
                    loadHeroEpisode(hero.animeId, hero.episodeId)
                }

                saveCache()
            }
        }
    }

    private suspend fun loadHeroEpisode(animeId: Long, episodeId: Long) {
        val nextEpisodes = getNextEpisodes.await(animeId, episodeId, onlyUnseen = true)
        heroEpisode = nextEpisodes.firstOrNull()
            ?: getNextEpisodes.await(animeId, episodeId, onlyUnseen = false).firstOrNull()
    }

    fun playHeroEpisode(context: Context) {
        val hero = state.value.hero ?: return
        val episodeId = heroEpisode?.id ?: originalHeroEpisodeId ?: return

        screenModelScope.launchIO {
            MainActivity.startPlayerActivity(context, hero.entryId, episodeId, false)
        }
    }

    fun saveCache() {
        val currentState = state.value
        fastCache.save(
            CachedHomeState(
                hero = currentState.hero?.let { hero ->
                    CachedHeroItem(
                        entryId = hero.entryId,
                        title = hero.title,
                        progressNumber = hero.progressNumber,
                        coverUrl = (hero.coverData as? AnimeCover)?.url,
                        coverLastModified = (hero.coverData as? AnimeCover)?.lastModified ?: 0L,
                        subId = originalHeroEpisodeId ?: 0L,
                    )
                },
                history = currentState.history.map { h ->
                    CachedHistoryItem(
                        entryId = h.entryId,
                        title = h.title,
                        progressNumber = h.progressNumber,
                        coverUrl = (h.coverData as? AnimeCover)?.url,
                        coverLastModified = (h.coverData as? AnimeCover)?.lastModified ?: 0L,
                    )
                },
                recommendations = currentState.recommendations.map { r ->
                    CachedRecommendationItem(
                        entryId = r.entryId,
                        title = r.title,
                        coverUrl = (r.coverData as? AnimeCover)?.url,
                        coverLastModified = (r.coverData as? AnimeCover)?.lastModified ?: 0L,
                        totalCount = r.progressDenominator,
                        progressCount = r.progressNumerator,
                    )
                },
                userName = currentState.userName,
                userAvatar = currentState.userAvatar,
                isInitialized =
                currentState.showFilteredEmpty || (currentState.showWelcome.not() && currentState.isLoading.not()),
            ),
        )
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
        val categories: List<tachiyomi.domain.category.model.Category>,
        val historyList: List<AnimeHistoryWithRelations>,
        val animeList: List<LibraryAnime>,
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
