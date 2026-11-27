package eu.kanade.tachiyomi.ui.home

import android.content.Context
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.anime.interactor.GetEnabledAnimeSources
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UserProfilePreferences
import eu.kanade.tachiyomi.ui.main.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.yield
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

internal class HomeHubScreenModel private constructor(
    context: android.content.Context,
    userProfilePreferences: UserProfilePreferences,
    private val discoveryPreferences: eu.kanade.domain.discovery.service.DiscoveryPreferences,
    private val fastCache: HomeHubFastCache,
    cached: CachedHomeState,
) : BaseHomeHubScreenModel(
    context = context,
    initialState = createAnimeInitialState(cached, userProfilePreferences, discoveryPreferences),
    userProfilePreferences = userProfilePreferences,
) {

    constructor(
        context: android.content.Context = Injekt.get<android.app.Application>(),
        userProfilePreferences: UserProfilePreferences = Injekt.get(),
        discoveryPreferences: eu.kanade.domain.discovery.service.DiscoveryPreferences = Injekt.get(),
    ) : this(
        context = context,
        userProfilePreferences = userProfilePreferences,
        discoveryPreferences = discoveryPreferences,
        fastCache = HomeHubFastCache(context, HomeHubSection.Anime),
    )

    private constructor(
        context: android.content.Context,
        userProfilePreferences: UserProfilePreferences,
        discoveryPreferences: eu.kanade.domain.discovery.service.DiscoveryPreferences,
        fastCache: HomeHubFastCache,
    ) : this(
        context = context,
        userProfilePreferences = userProfilePreferences,
        discoveryPreferences = discoveryPreferences,
        fastCache = fastCache,
        cached = fastCache.loadCachedState(),
    )

    private val getAnimeHistory: GetAnimeHistory by injectLazy()
    private val getNextEpisodes: GetNextEpisodes by injectLazy()
    private val getLibraryAnime: GetLibraryAnime by injectLazy()
    private val getAnimeCategories: GetAnimeCategories by injectLazy()
    private val getEnabledAnimeSources: GetEnabledAnimeSources by injectLazy()
    private val sourcePreferences: SourcePreferences by injectLazy()
    private val sourceManager: AnimeSourceManager by injectLazy()
    private val userProfileManager: tachiyomi.data.achievement.UserProfileManager by injectLazy()
    private val streakChecker: tachiyomi.data.achievement.handler.checkers.StreakAchievementChecker by injectLazy()
    private val activityDataRepository: tachiyomi.domain.achievement.repository.ActivityDataRepository by injectLazy()
    private val discoveryRepository: tachiyomi.domain.discovery.repository.DiscoveryRepository by injectLazy()

    override val discoveryController: HomeDiscoveryController by lazy {
        HomeDiscoveryController(
            mediaType = tachiyomi.domain.discovery.model.DiscoveryMediaType.ANIME,
            repository = discoveryRepository,
            preferences = discoveryPreferences,
            context = context,
            scope = screenModelScope,
            onStateUpdate = { update -> mutableState.update(update) },
            currentState = { state.value },
            onEmitEffect = { emitEffect(it) },
            trendingSource = eu.kanade.tachiyomi.data.discovery.CompositeTrendingSource(),
            onTeaserPersisted = { items ->
                fastCache.updateDiscovery(items.map { it.toCachedDiscoveryItem() })
            },
        )
    }

    override val avatarFileName: String = "user_avatar.jpg"

    @Volatile
    private var liveUpdatesStarted = false

    @Volatile
    private var fastCacheInitialized = false

    @Volatile
    private var heroEpisode: Episode? = null

    @Volatile
    private var originalHeroEpisodeId: Long? = null

    private var heroLoadJob: Job? = null

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
        fastCacheInitialized = cached.isInitialized
        val hadCache = !cached.isEmpty || cached.isInitialized

        if (hadCache) {
            originalHeroEpisodeId = cached.hero?.subId
        }

        // PERF: Defer expensive DB work (library + history + streaks + greeting) until after first frame.
        // Cached UI is already shown synchronously via initialState above.
        initializeGreetingDeferred()

        cached.hero?.let { hero ->
            heroLoadJob?.cancel()
            heroLoadJob = screenModelScope.launchIO {
                loadHeroEpisode(hero.entryId, hero.subId)
            }
        }

        observeDiscoveryRefreshFailure(
            discoveryPreferences,
            tachiyomi.domain.discovery.model.DiscoveryMediaType.ANIME,
        )
        discoveryController.startObserving()
    }

    fun startLiveUpdates() {
        if (liveUpdatesStarted) return
        liveUpdatesStarted = true

        // PERF: Defer extension-backed source enumeration until this section is active and
        // past the first frame so cold start does not load all installed APKs during composeInitial.
        screenModelScope.launchIO {
            yield()
            getEnabledAnimeSources.subscribe().conflate().collectLatest { sources ->
                val mappedSources = sources.filterNot { s -> s.isFeedSource }.distinctBy { it.id }.map { s ->
                    HomeSourceItem(
                        id = s.id,
                        name = s.name,
                        lang = s.lang,
                        isLocal = s.id == tachiyomi.source.local.entries.anime.LocalAnimeSource.ID,
                    )
                }
                mutableState.update { it.copy(availableSources = mappedSources) }
            }
        }

        screenModelScope.launchIO {
            combine(
                userProfilePreferences.name().changes(),
                userProfilePreferences.avatarUrl().changes(),
                getAnimeCategories.subscribe(),
                getAnimeHistory.subscribe(""),
                getLibraryAnime.subscribe(),
            ) { name, avatar, categories, historyList, animeList ->
                LiveData(name, avatar, categories, historyList, animeList)
            }.conflate().collectLatest { data ->
                val hiddenCategoryIds = hiddenHomeHubCategoryIds(
                    categories = data.categories,
                    isHiddenFromHomeHub = { it.hiddenFromHomeHub },
                    idSelector = { it.id },
                )
                val animeCategoryIdsByAnimeId = if (hiddenCategoryIds.isEmpty()) {
                    emptyMap()
                } else {
                    homeHubCategoryIdsByEntryId(
                        items = data.animeList,
                        entryIdSelector = { it.anime.id },
                        categoryIdSelector = { it.category },
                    )
                }

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

                val animeRecommendations = takeTopKDescendingByLong(
                    items = filteredAnime,
                    limit = 10,
                    keySelector = { it.anime.dateAdded },
                )

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

                if (hasData && !fastCacheInitialized) {
                    fastCacheInitialized = true
                    fastCache.markInitialized()
                }

                if (hero == null) {
                    heroLoadJob?.cancel()
                    heroLoadJob = null
                    heroEpisode = null
                } else if (
                    shouldReloadAnimeHomeHeroEpisode(
                        previousHeroAnimeId = previousHero?.entryId,
                        previousHeroEpisodeId = previousHeroEpisodeId,
                        currentHeroAnimeId = hero.animeId,
                        currentHeroEpisodeId = hero.episodeId,
                    )
                ) {
                    if (hero.animeId != previousHero?.entryId) {
                        heroEpisode = null
                    }
                    heroLoadJob?.cancel()
                    heroLoadJob = screenModelScope.launchIO {
                        loadHeroEpisode(hero.animeId, hero.episodeId)
                    }
                }

                saveCache()
            }
        }
    }

    private suspend fun loadHeroEpisode(animeId: Long, episodeId: Long) {
        val nextEpisodes = getNextEpisodes.await(animeId, episodeId, onlyUnseen = true)
        currentCoroutineContext().ensureActive()
        val resolved = nextEpisodes.firstOrNull()
            ?: getNextEpisodes.await(animeId, episodeId, onlyUnseen = false).firstOrNull()
        currentCoroutineContext().ensureActive()
        if (state.value.hero?.entryId == animeId) {
            heroEpisode = resolved
        }
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
        val changed = fastCache.save(
            CachedHomeState(
                hero = currentState.hero?.let { hero ->
                    CachedHeroItem(
                        entryId = hero.entryId,
                        title = hero.title,
                        progressNumber = hero.progressNumber,
                        coverUrl = (hero.coverData as? AnimeCover)?.url,
                        coverLastModified = (hero.coverData as? AnimeCover)?.lastModified ?: 0L,
                        sourceId = (hero.coverData as? AnimeCover)?.sourceId ?: -1L,
                        favorite = (hero.coverData as? AnimeCover)?.isAnimeFavorite ?: false,
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
                        sourceId = (h.coverData as? AnimeCover)?.sourceId ?: -1L,
                        favorite = (h.coverData as? AnimeCover)?.isAnimeFavorite ?: false,
                    )
                },
                recommendations = currentState.recommendations.map { r ->
                    CachedRecommendationItem(
                        entryId = r.entryId,
                        title = r.title,
                        coverUrl = (r.coverData as? AnimeCover)?.url,
                        coverLastModified = (r.coverData as? AnimeCover)?.lastModified ?: 0L,
                        sourceId = (r.coverData as? AnimeCover)?.sourceId ?: -1L,
                        favorite = (r.coverData as? AnimeCover)?.isAnimeFavorite ?: false,
                        totalCount = r.progressDenominator,
                        progressCount = r.progressNumerator,
                    )
                },
                discovery = currentState.discovery.map { it.toCachedDiscoveryItem() },
                userName = currentState.userName,
                userAvatar = currentState.userAvatar,
                isInitialized =
                currentState.showFilteredEmpty || (currentState.showWelcome.not() && currentState.isLoading.not()),
            ),
        )
        if (changed) {
            prefetchHomeHubCovers(
                buildList {
                    add(currentState.hero?.coverData)
                    currentState.history.forEach { add(it.coverData) }
                    currentState.recommendations.forEach { add(it.coverData) }
                },
            )
        }
    }

    fun getLastUsedAnimeSourceId(): Long = sourcePreferences.lastUsedAnimeSource().get()

    fun setLastUsedAnimeSourceId(sourceId: Long) {
        sourcePreferences.lastUsedAnimeSource().set(sourceId)
    }

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

internal fun shouldReloadAnimeHomeHeroEpisode(
    previousHeroAnimeId: Long?,
    previousHeroEpisodeId: Long?,
    currentHeroAnimeId: Long,
    currentHeroEpisodeId: Long,
): Boolean {
    return previousHeroAnimeId != currentHeroAnimeId ||
        previousHeroEpisodeId != currentHeroEpisodeId
}

private fun createAnimeInitialState(
    cached: CachedHomeState,
    userProfilePreferences: UserProfilePreferences,
    discoveryPreferences: eu.kanade.domain.discovery.service.DiscoveryPreferences,
): HomeHubUiState {
    val hadCache = !cached.isEmpty || cached.isInitialized
    return if (hadCache) {
        HomeHubUiState(
            hero = cached.hero?.let { h ->
                HomeHubHero(
                    entryId = h.entryId,
                    title = h.title,
                    progressNumber = h.progressNumber,
                    coverData = AnimeCover(h.entryId, h.sourceId, h.favorite, h.coverUrl, h.coverLastModified),
                )
            },
            history = cached.history.map { h ->
                HomeHubHistory(
                    entryId = h.entryId,
                    title = h.title,
                    progressNumber = h.progressNumber,
                    coverData = AnimeCover(h.entryId, h.sourceId, h.favorite, h.coverUrl, h.coverLastModified),
                    section = HomeHubSection.Anime,
                )
            },
            recommendations = cached.recommendations.map { r ->
                HomeHubRecommendation(
                    entryId = r.entryId,
                    title = r.title,
                    coverData = AnimeCover(r.entryId, r.sourceId, r.favorite, r.coverUrl, r.coverLastModified),
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
    }.withInitialDiscoveryPreferences(
        preferences = discoveryPreferences,
        mediaType = tachiyomi.domain.discovery.model.DiscoveryMediaType.ANIME,
        cachedDiscovery = cached.discovery,
    )
}
