package eu.kanade.tachiyomi.ui.home

import android.content.Context
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.anime.interactor.GetEnabledAnimeSources
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UserProfilePreferences
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.ui.discovery.ManualRefreshAction
import eu.kanade.tachiyomi.ui.discovery.manualRefreshFlow
import eu.kanade.tachiyomi.ui.discovery.resolveManualRefreshAction
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.workManager
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
        }
    },
    userProfilePreferences = userProfilePreferences,
) {

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
    private val discoveryPreferences: eu.kanade.domain.discovery.service.DiscoveryPreferences by injectLazy()

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

        // Enabled (installed) anime sources for the Home source picker — mirrors Browse's logic.
        // Feed (Reels) sources are excluded: they are not catalogue sources and cannot be
        // browsed/searched from the Home picker; they are opened via the dedicated Reels screen.
        screenModelScope.launchIO {
            getEnabledAnimeSources.subscribe().collectLatest { sources ->
                mutableState.update {
                    it.copy(
                        availableSources = sources.filterNot { s -> s.isFeedSource }.distinctBy { it.id }.map { s ->
                            HomeSourceItem(
                                id = s.id,
                                name = s.name,
                                lang = s.lang,
                                isLocal = s.id == tachiyomi.source.local.entries.anime.LocalAnimeSource.ID,
                                iconBitmap = animeExtensionManager.getAppIconForSource(
                                    s.id,
                                )?.toBitmap()?.asImageBitmap(),
                            )
                        },
                    )
                }
            }
        }

        observeDiscoveryRefreshFailure(
            discoveryPreferences,
            tachiyomi.domain.discovery.model.DiscoveryMediaType.ANIME,
        )
        screenModelScope.launchIO {
            var wasRefreshing = false
            manualRefreshFlow(
                context.workManager,
                tachiyomi.domain.discovery.model.DiscoveryMediaType.ANIME,
            ).collectLatest { refreshing ->
                mutableState.update { it.copy(isDiscoveryRefreshing = refreshing) }
                if (wasRefreshing && !refreshing) {
                    updateDiscoveryTeaser(advanceOffset = false, forceUpdate = true, notifyIfLimited = true)
                }
                wasRefreshing = refreshing
            }
        }

        // Тизер ленты «Для тебя» — чтение только из БД (ноль сети на рендер Home).
        screenModelScope.launchIO {
            combine(
                discoveryRepository.subscribe(tachiyomi.domain.discovery.model.DiscoveryMediaType.ANIME),
                discoveryPreferences.discoveryEnabled().changes(),
                discoveryPreferences.teaserCount().changes(),
                // «Просмотрено/прочитано»: реактивно исчезает из пула/тизера сразу
                // после открытия читалки (в т.ч. в инкогнито) или ручной отметки.
                combine(
                    discoveryRepository.subscribeHidden(tachiyomi.domain.discovery.model.DiscoveryMediaType.ANIME),
                    discoveryRepository.subscribeConsumed(tachiyomi.domain.discovery.model.DiscoveryMediaType.ANIME),
                ) { hidden, consumed -> hidden to consumed },
                discoveryRepository.subscribeBlacklist(tachiyomi.domain.discovery.model.DiscoveryMediaType.ANIME),
            ) { items, enabled, count, hiddenAndConsumed, blacklist ->
                val hidden = hiddenAndConsumed.first
                val consumed = hiddenAndConsumed.second
                items.filterNot { it.cleanTitle in hidden || it.cleanTitle in consumed } to Triple(
                    enabled,
                    count,
                    eu.kanade.tachiyomi.data.discovery.expandGenreSet(blacklist.toList()),
                )
            }
                .collectLatest { (visible, prefs) ->
                    val (enabled, count, expandedBlacklist) = prefs
                    val filtered = visible.filterNot {
                        eu.kanade.tachiyomi.data.discovery.isBlacklisted(it, expandedBlacklist)
                    }
                    cachedDiscoveryPool = eu.kanade.tachiyomi.data.discovery.dedupeCrossRow(filtered)
                    // 48h-метки показа: hero-карусель Stage заказывает непоказанное вперёд.
                    val shownAtByTitle = runCatching {
                        discoveryRepository.getShownTitlesWithTimestamp(
                            tachiyomi.domain.discovery.model.DiscoveryMediaType.ANIME,
                        )
                    }.getOrDefault(emptyMap())
                    // Полный пул для hero-карусели: без тизерного окна, чтобы листать всю подборку.
                    mutableState.update {
                        it.copy(
                            discoveryPool = cachedDiscoveryPool.map { item ->
                                item.toHomeHubDiscoveryItem(shownAtByTitle[item.cleanTitle])
                            },
                        )
                    }
                    if (enabled) {
                        updateDiscoveryTeaser(advanceOffset = false, teaserCount = count)
                    } else {
                        mutableState.update {
                            it.copy(discovery = emptyList(), discoveryEnabled = false, discoveryPool = emptyList())
                        }
                    }
                }
        }
    }

    private var cachedDiscoveryPool: List<tachiyomi.domain.discovery.model.DiscoverySuggestion> = emptyList()
    private var discoveryOffset: Int = 0
    private var lastReentryTime: Long = 0L

    private var lastRefreshClickTime = 0L

    private suspend fun updateDiscoveryTeaser(
        advanceOffset: Boolean = false,
        forceUpdate: Boolean = false,
        notifyIfLimited: Boolean = false,
        teaserCount: Int? = null,
    ) {
        if (!discoveryPreferences.discoveryEnabled().get()) {
            mutableState.update {
                it.copy(discovery = emptyList(), discoveryEnabled = false, discoveryPool = emptyList())
            }
            return
        }
        val count = teaserCount ?: discoveryPreferences.teaserCount().get().coerceIn(3, 20)
        val pool = cachedDiscoveryPool
        if (pool.isEmpty()) {
            mutableState.update {
                it.copy(discovery = emptyList(), discoveryEnabled = true, discoveryPool = emptyList())
            }
            return
        }

        if (!forceUpdate && !advanceOffset && state.value.discovery.isNotEmpty()) {
            val validCleanTitles = pool.mapTo(HashSet()) { it.cleanTitle }
            val currentValid = state.value.discovery.filter { it.cleanTitle in validCleanTitles }
            if (currentValid.size == count) {
                return
            }
            val shownMap = runCatching {
                discoveryRepository.getShownTitlesWithTimestamp(
                    tachiyomi.domain.discovery.model.DiscoveryMediaType.ANIME,
                )
            }.getOrDefault(emptyMap())
            val patched = patchTeaserItems(
                currentItems = state.value.discovery,
                pool = pool,
                validCleanTitles = validCleanTitles,
                targetCount = count,
                shownTitles = shownMap.keys,
                shownCutoffMap = shownMap,
            )
            val existingValidSet = currentValid.mapTo(HashSet()) { it.cleanTitle }
            val newTitles = patched.map { it.cleanTitle }.filterNot { it in existingValidSet }
            if (newTitles.isNotEmpty()) {
                runCatching {
                    discoveryRepository.markShown(
                        mediaType = tachiyomi.domain.discovery.model.DiscoveryMediaType.ANIME,
                        cleanTitles = newTitles,
                    )
                }
            }
            val updatedPool = pool.filter { it.cleanTitle in validCleanTitles }.map { p ->
                p.toHomeHubDiscoveryItem(shownMap[p.cleanTitle])
            }
            mutableState.update {
                it.copy(
                    discovery = patched,
                    discoveryEnabled = true,
                    discoveryPool = updatedPool,
                )
            }
            return
        }

        if (advanceOffset) {
            val step = if (pool.size > count) count else 1
            discoveryOffset = (discoveryOffset + step) % pool.size
        }

        val shownMap = runCatching {
            discoveryRepository.getShownTitlesWithTimestamp(tachiyomi.domain.discovery.model.DiscoveryMediaType.ANIME)
        }.getOrDefault(emptyMap())
        val shownTitles = shownMap.keys
        val currentTitles = state.value.discovery.mapTo(HashSet()) { it.cleanTitle }

        val teaser = selectFreshTeaserItems(
            pool = pool,
            shownTitles = shownTitles,
            count = count,
            offset = discoveryOffset,
            shownCutoffMap = shownMap,
            currentTitles = currentTitles,
        )

        if (notifyIfLimited && currentTitles.isNotEmpty()) {
            val distinctPoolSize = pool.distinctBy { it.cleanTitle }.size
            if (shouldNotifyDiscoveryPoolLimited(teaser, currentTitles, distinctPoolSize, count)) {
                emitEffect(HomeHubEffect.DiscoveryPoolLimited)
            }
        }

        val cleanTitlesToMark = teaser.map { it.cleanTitle }
        if (cleanTitlesToMark.isNotEmpty()) {
            runCatching {
                discoveryRepository.markShown(
                    mediaType = tachiyomi.domain.discovery.model.DiscoveryMediaType.ANIME,
                    cleanTitles = cleanTitlesToMark,
                )
            }
        }

        val updatedPool = pool.map { p ->
            p.toHomeHubDiscoveryItem(shownMap[p.cleanTitle])
        }
        mutableState.update {
            it.copy(
                discovery = teaser,
                discoveryEnabled = true,
                discoveryPool = updatedPool,
            )
        }
    }

    override fun onScreenReentry() {
        val now = System.currentTimeMillis()
        if (!shouldAdvanceDiscoveryTeaserOnReentry(now, lastReentryTime)) return
        lastReentryTime = now
        screenModelScope.launchIO {
            updateDiscoveryTeaser(advanceOffset = true)
        }
    }

    override fun rotateOrRefreshDiscovery() {
        if (!discoveryPreferences.discoveryEnabled().get()) return
        val now = System.currentTimeMillis()
        if (now - lastRefreshClickTime < 500L) return
        lastRefreshClickTime = now

        val mediaType = tachiyomi.domain.discovery.model.DiscoveryMediaType.ANIME
        val cooldown = eu.kanade.tachiyomi.ui.discovery.remainingCooldownSeconds(
            discoveryPreferences.manualRefreshAt(mediaType).get().takeIf { it > 0L },
            now,
        )
        val action = resolveManualRefreshAction(state.value.isDiscoveryRefreshing, cooldown)
        if (action == ManualRefreshAction.CANCEL) {
            eu.kanade.tachiyomi.data.discovery.DiscoveryUpdateJob.stop(context, mediaType)
            mutableState.update { it.copy(isDiscoveryRefreshing = false) }
            return
        }

        if (action == ManualRefreshAction.ROTATE || !discoveryPreferences.tryStartManualRefresh(mediaType, now)) {
            screenModelScope.launchIO {
                updateDiscoveryTeaser(advanceOffset = true, forceUpdate = true, notifyIfLimited = true)
            }
            return
        }

        mutableState.update { it.copy(isDiscoveryRefreshing = true) }
        eu.kanade.tachiyomi.data.discovery.DiscoveryUpdateJob.refreshNow(context, mediaType)
    }

    fun markDiscoveryItemShown(item: HomeHubDiscoveryItem) {
        if (item.cleanTitle.isBlank()) return
        screenModelScope.launchIO {
            runCatching {
                discoveryRepository.markShown(item.mediaType, listOf(item.cleanTitle))
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
        prefetchHomeHubCovers(
            buildList {
                add(currentState.hero?.coverData)
                currentState.history.forEach { add(it.coverData) }
                currentState.recommendations.forEach { add(it.coverData) }
            },
        )
        fastCache.save(
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
                userName = currentState.userName,
                userAvatar = currentState.userAvatar,
                isInitialized =
                currentState.showFilteredEmpty || (currentState.showWelcome.not() && currentState.isLoading.not()),
            ),
        )
    }

    private val animeExtensionManager: AnimeExtensionManager by injectLazy()

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
