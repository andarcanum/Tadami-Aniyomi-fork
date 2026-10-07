package eu.kanade.tachiyomi.ui.home

import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.novel.LocalNovelVisibility
import eu.kanade.domain.source.novel.interactor.GetEnabledNovelSources
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UserProfilePreferences
import eu.kanade.tachiyomi.ui.novel.resolveNovelResumeChapter
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import tachiyomi.domain.entries.novel.interactor.GetLibraryNovel
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.entries.novel.interactor.GetNovelWithChapters
import tachiyomi.domain.entries.novel.model.NovelCover
import tachiyomi.domain.history.novel.model.NovelHistoryWithRelations
import tachiyomi.domain.history.novel.repository.NovelHistoryRepository
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.source.local.io.novel.LocalNovelSourceFileSystem
import tachiyomi.source.local.io.novel.hasSupportedLocalNovelContent
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import tachiyomi.core.common.i18n.stringResource as contextStringResource

internal class NovelHomeHubScreenModel(
    context: android.content.Context = Injekt.get<android.app.Application>(),
    userProfilePreferences: UserProfilePreferences = Injekt.get(),
) : BaseHomeHubScreenModel(
    context = context,
    initialState = run {
        val tempCache = HomeHubFastCache(context, HomeHubSection.Novel)
        val cached = tempCache.loadCachedState()
        val hadCache = !cached.isEmpty || cached.isInitialized
        if (hadCache) {
            HomeHubUiState(
                hero = cached.hero
                    ?.takeUnless {
                        LocalNovelVisibility.shouldHideLocalHistoryByCover(it.sourceId, it.coverUrl)
                    }
                    ?.let { h ->
                        HomeHubHero(
                            entryId = h.entryId,
                            title = h.title,
                            progressNumber = h.progressNumber,
                            coverData = NovelCover(h.entryId, h.sourceId, h.favorite, h.coverUrl, h.coverLastModified),
                        )
                    },
                history = cached.history
                    .filterNot {
                        LocalNovelVisibility.shouldHideLocalHistoryByCover(it.sourceId, it.coverUrl)
                    }
                    .map { h ->
                        HomeHubHistory(
                            entryId = h.entryId,
                            title = h.title,
                            progressNumber = h.progressNumber,
                            coverData = NovelCover(h.entryId, h.sourceId, h.favorite, h.coverUrl, h.coverLastModified),
                            section = HomeHubSection.Novel,
                        )
                    },
                recommendations = cached.recommendations
                    .filterNot {
                        LocalNovelVisibility.shouldHideLocalHistoryByCover(it.sourceId, it.coverUrl)
                    }
                    .map { r ->
                        HomeHubRecommendation(
                            entryId = r.entryId,
                            title = r.title,
                            coverData = NovelCover(r.entryId, r.sourceId, r.favorite, r.coverUrl, r.coverLastModified),
                            section = HomeHubSection.Novel,
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

    private val historyRepository: NovelHistoryRepository by injectLazy()
    private val getLibraryNovel: GetLibraryNovel by injectLazy()
    private val getNovel: GetNovel by injectLazy()
    private val getNovelWithChapters: GetNovelWithChapters by injectLazy()
    private val getNovelBookState: tachiyomi.domain.book.novel.interactor.GetNovelBookState by injectLazy()
    private val getNovelCategories: GetNovelCategories by injectLazy()
    private val getEnabledNovelSources: GetEnabledNovelSources by injectLazy()
    private val sourcePreferences: SourcePreferences by injectLazy()
    private val sourceManager: NovelSourceManager by injectLazy()
    private val localNovelSourceFileSystem: LocalNovelSourceFileSystem by injectLazy()
    private val discoveryRepository: tachiyomi.domain.discovery.repository.DiscoveryRepository by injectLazy()
    private val discoveryPreferences: eu.kanade.domain.discovery.service.DiscoveryPreferences by injectLazy()

    override val avatarFileName: String = "user_avatar_novel.jpg"

    private val fastCache = HomeHubFastCache(context, HomeHubSection.Novel)

    @Volatile
    private var liveUpdatesStarted = false

    private var lastResolvedHeroChapterId: Long? = null
    private var originalHeroChapterId: Long? = null

    override fun updateCacheUserName(name: String) {
        fastCache.updateUserName(name)
    }

    override fun updateCacheUserAvatar(path: String) {
        fastCache.updateUserAvatar(path)
    }

    init {
        val cached = fastCache.load()
        val hadCache = !cached.isEmpty || cached.isInitialized

        if (hadCache) {
            originalHeroChapterId = cached.hero?.subId
            mutableState.update {
                it.copy(
                    hero = cached.hero
                        ?.takeUnless {
                            LocalNovelVisibility.shouldHideLocalHistoryByCover(it.sourceId, it.coverUrl)
                        }
                        ?.let { h ->
                            HomeHubHero(
                                entryId = h.entryId,
                                title = h.title,
                                progressNumber = h.progressNumber,
                                coverData = NovelCover(
                                    h.entryId,
                                    h.sourceId,
                                    h.favorite,
                                    h.coverUrl,
                                    h.coverLastModified,
                                ),
                            )
                        },
                    history = cached.history
                        .filterNot {
                            LocalNovelVisibility.shouldHideLocalHistoryByCover(it.sourceId, it.coverUrl)
                        }
                        .map { h ->
                            HomeHubHistory(
                                entryId = h.entryId,
                                title = h.title,
                                progressNumber = h.progressNumber,
                                coverData = NovelCover(
                                    h.entryId,
                                    h.sourceId,
                                    h.favorite,
                                    h.coverUrl,
                                    h.coverLastModified,
                                ),
                                section = HomeHubSection.Novel,
                            )
                        },
                    recommendations = cached.recommendations
                        .filterNot {
                            LocalNovelVisibility.shouldHideLocalHistoryByCover(it.sourceId, it.coverUrl)
                        }
                        .map { r ->
                            HomeHubRecommendation(
                                entryId = r.entryId,
                                title = r.title,
                                coverData = NovelCover(
                                    r.entryId,
                                    r.sourceId,
                                    r.favorite,
                                    r.coverUrl,
                                    r.coverLastModified,
                                ),
                                section = HomeHubSection.Novel,
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

        // PERF: Defer expensive DB work until after first frame
        initializeGreetingDeferred()

        cached.hero?.let { hero ->
            screenModelScope.launchIO {
                loadHeroChapterId(hero.entryId, hero.subId)
            }
        }

        // Enabled (installed) novel sources for the Home source picker — mirrors Browse's logic
        screenModelScope.launchIO {
            getEnabledNovelSources.subscribe().collectLatest { sources ->
                mutableState.update {
                    it.copy(
                        availableSources = sources.distinctBy { it.id }.map { s ->
                            HomeSourceItem(
                                id = s.id,
                                name = s.name,
                                lang = s.lang,
                                isLocal = s.id == tachiyomi.source.local.entries.novel.LocalNovelSource.ID,
                                iconUrl = novelExtensionManager.getPluginIconUrlForSource(s.id),
                            )
                        },
                    )
                }
            }
        }

        // Тизер ленты «Для тебя» — чтение только из БД (ноль сети на рендер Home).
        screenModelScope.launchIO {
            combine(
                discoveryRepository.subscribe(tachiyomi.domain.discovery.model.DiscoveryMediaType.NOVEL),
                discoveryPreferences.discoveryEnabled().changes(),
                discoveryPreferences.teaserCount().changes(),
                // «Просмотрено/прочитано»: реактивно исчезает из пула/тизера сразу
                // после открытия читалки (в т.ч. в инкогнито) или ручной отметки.
                combine(
                    discoveryRepository.subscribeHidden(tachiyomi.domain.discovery.model.DiscoveryMediaType.NOVEL),
                    discoveryRepository.subscribeConsumed(tachiyomi.domain.discovery.model.DiscoveryMediaType.NOVEL),
                ) { hidden, consumed -> hidden to consumed },
                discoveryRepository.subscribeBlacklist(tachiyomi.domain.discovery.model.DiscoveryMediaType.NOVEL),
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
                            tachiyomi.domain.discovery.model.DiscoveryMediaType.NOVEL,
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
                        updateDiscoveryTeaser(advanceOffset = false)
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
    ) {
        if (!discoveryPreferences.discoveryEnabled().get()) {
            mutableState.update {
                it.copy(discovery = emptyList(), discoveryEnabled = false, discoveryPool = emptyList())
            }
            return
        }
        val count = discoveryPreferences.teaserCount().get().coerceIn(3, 20)
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
                    tachiyomi.domain.discovery.model.DiscoveryMediaType.NOVEL,
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
                        mediaType = tachiyomi.domain.discovery.model.DiscoveryMediaType.NOVEL,
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
            discoveryRepository.getShownTitlesWithTimestamp(tachiyomi.domain.discovery.model.DiscoveryMediaType.NOVEL)
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
            val hasOverlap = teaser.any { it.cleanTitle in currentTitles }
            if (hasOverlap || distinctPoolSize <= count) {
                context.toast(context.contextStringResource(AYMR.strings.for_you_limited_pool))
            }
        }

        val cleanTitlesToMark = teaser.map { it.cleanTitle }
        if (cleanTitlesToMark.isNotEmpty()) {
            runCatching {
                discoveryRepository.markShown(
                    mediaType = tachiyomi.domain.discovery.model.DiscoveryMediaType.NOVEL,
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
        if (state.value.isDiscoveryRefreshing) return

        val now = System.currentTimeMillis()
        if (now - lastRefreshClickTime < 500L) return
        lastRefreshClickTime = now

        val mediaType = tachiyomi.domain.discovery.model.DiscoveryMediaType.NOVEL
        val lastManual = discoveryPreferences.homeManualRefreshAt(mediaType).get().takeIf { it > 0L }
        val cooldown = eu.kanade.tachiyomi.ui.discovery.remainingCooldownSeconds(
            lastManual,
            now,
            cooldownMs = eu.kanade.tachiyomi.ui.discovery.HOME_DISCOVERY_COOLDOWN_MS,
        )
        if (cooldown > 0L) {
            screenModelScope.launchIO {
                updateDiscoveryTeaser(advanceOffset = true, forceUpdate = true, notifyIfLimited = true)
            }
            return
        }

        discoveryPreferences.homeManualRefreshAt(mediaType).set(now)
        discoveryPreferences.manualRefreshAt().set(now)
        mutableState.update { it.copy(isDiscoveryRefreshing = true) }
        screenModelScope.launchIO {
            try {
                Injekt.get<eu.kanade.tachiyomi.data.discovery.DiscoveryRunner>().run(
                    listOf(mediaType),
                    isManualRefresh = true,
                )
            } finally {
                mutableState.update { it.copy(isDiscoveryRefreshing = false) }
                updateDiscoveryTeaser(advanceOffset = false, forceUpdate = true, notifyIfLimited = true)
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
                getNovelCategories.subscribe(),
                historyRepository.getNovelHistory(""),
                getLibraryNovel.subscribe(),
            ) { name, avatar, categories, historyList, novelList ->
                LiveData(name, avatar, categories, historyList, novelList)
            }.collectLatest { data ->
                val hiddenCategoryIds = hiddenHomeHubCategoryIds(
                    categories = data.categories,
                    isHiddenFromHomeHub = { it.hiddenFromHomeHub },
                    idSelector = { it.id },
                )
                val novelCategoryIdsByNovelId = homeHubCategoryIdsByEntryId(
                    items = data.novelList,
                    entryIdSelector = { it.novel.id },
                    categoryIdSelector = { it.category },
                )

                val categoryFilteredHistory = filterHomeHubEntriesBy(
                    items = data.historyList,
                    keySelector = { it.novelId },
                    entryCategoryIds = novelCategoryIdsByNovelId,
                    hiddenCategoryIds = hiddenCategoryIds,
                )
                val filteredHistory = filterGhostLocalNovelHistory(categoryFilteredHistory)

                val categoryFilteredNovel = filterHomeHubEntriesByDistinct(
                    items = data.novelList,
                    keySelector = { it.novel.id },
                    entryCategoryIds = novelCategoryIdsByNovelId,
                    hiddenCategoryIds = hiddenCategoryIds,
                )
                val filteredNovel = categoryFilteredNovel.filter { item ->
                    LocalNovelVisibility.shouldShowLocalNovelEntry(
                        sourceId = item.novel.source,
                        url = item.novel.url,
                        coverUrl = item.novel.thumbnailUrl,
                        hasSupportedContent = localNovelSourceFileSystem::hasSupportedLocalNovelContent,
                    )
                }

                val hero = filteredHistory.firstOrNull()
                val history = takeHomeHubHistoryExcluding(
                    items = filteredHistory,
                    limit = 6,
                    excludedEntryId = hero?.novelId,
                    entryIdSelector = { it.novelId },
                )

                val hasData = hero != null || history.isNotEmpty() || filteredNovel.isNotEmpty()
                val isInitialized = hasData ||
                    (
                        state.value.showFilteredEmpty ||
                            (state.value.showWelcome.not() && state.value.isLoading.not())
                        )

                val novelRecommendations = filteredNovel
                    .sortedByDescending { it.novel.dateAdded }
                    .take(10)

                val previousHero = state.value.hero
                val previousHeroChapterId = originalHeroChapterId

                originalHeroChapterId = hero?.chapterId

                val isEmpty = hero == null && history.isEmpty() && novelRecommendations.isEmpty()
                val showWelcome = !isInitialized && isEmpty
                val showFilteredEmpty = isInitialized && isEmpty

                mutableState.update {
                    it.copy(
                        hero = hero?.let { h ->
                            HomeHubHero(
                                entryId = h.novelId,
                                title = h.title,
                                progressNumber = h.chapterNumber,
                                coverData = h.coverData,
                            )
                        },
                        history = history.map { h ->
                            HomeHubHistory(
                                entryId = h.novelId,
                                title = h.title,
                                progressNumber = h.chapterNumber,
                                coverData = h.coverData,
                                section = HomeHubSection.Novel,
                            )
                        },
                        recommendations = novelRecommendations.map { n ->
                            HomeHubRecommendation(
                                entryId = n.novel.id,
                                title = n.novel.title,
                                coverData = NovelCover(
                                    novelId = n.novel.id,
                                    sourceId = n.novel.source,
                                    isNovelFavorite = n.novel.favorite,
                                    url = n.novel.thumbnailUrl,
                                    lastModified = n.novel.coverLastModified,
                                ),
                                section = HomeHubSection.Novel,
                                progressNumerator = n.readCount,
                                progressDenominator = n.totalChapters,
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

                if (
                    hero != null &&
                    shouldReloadNovelHomeHeroChapterId(
                        previousHeroNovelId = previousHero?.entryId,
                        previousHeroChapterId = previousHeroChapterId,
                        currentHeroNovelId = hero.novelId,
                        currentHeroChapterId = hero.chapterId,
                    )
                ) {
                    loadHeroChapterId(hero.novelId, hero.chapterId)
                }

                saveCache()
            }
        }
    }

    private suspend fun loadHeroChapterId(novelId: Long, fromChapterId: Long) {
        val chapters = getNovelWithChapters.awaitChapters(novelId, applyScanlatorFilter = true)
        // The hero card must resume a book-mode title at its stored book position, not at the
        // per-chapter heuristic result.
        val bookState = getNovelBookState.await(novelId)
        lastResolvedHeroChapterId = resolveNovelHomeHeroChapterId(chapters, fromChapterId, bookState)
    }

    fun getHeroChapterId(): Long? {
        return lastResolvedHeroChapterId ?: originalHeroChapterId
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
                        coverUrl = (hero.coverData as? NovelCover)?.url,
                        coverLastModified = (hero.coverData as? NovelCover)?.lastModified ?: 0L,
                        sourceId = (hero.coverData as? NovelCover)?.sourceId ?: -1L,
                        favorite = (hero.coverData as? NovelCover)?.isNovelFavorite ?: false,
                        subId = originalHeroChapterId ?: 0L,
                    )
                },
                history = currentState.history.map { h ->
                    CachedHistoryItem(
                        entryId = h.entryId,
                        title = h.title,
                        progressNumber = h.progressNumber,
                        coverUrl = (h.coverData as? NovelCover)?.url,
                        coverLastModified = (h.coverData as? NovelCover)?.lastModified ?: 0L,
                        sourceId = (h.coverData as? NovelCover)?.sourceId ?: -1L,
                        favorite = (h.coverData as? NovelCover)?.isNovelFavorite ?: false,
                    )
                },
                recommendations = currentState.recommendations.map { r ->
                    CachedRecommendationItem(
                        entryId = r.entryId,
                        title = r.title,
                        coverUrl = (r.coverData as? NovelCover)?.url,
                        coverLastModified = (r.coverData as? NovelCover)?.lastModified ?: 0L,
                        sourceId = (r.coverData as? NovelCover)?.sourceId ?: -1L,
                        favorite = (r.coverData as? NovelCover)?.isNovelFavorite ?: false,
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

    private val novelExtensionManager: eu.kanade.tachiyomi.extension.novel.NovelExtensionManager by injectLazy()

    fun getLastUsedNovelSourceId(): Long = sourcePreferences.lastUsedNovelSource().get()

    fun setLastUsedNovelSourceId(sourceId: Long) {
        sourcePreferences.lastUsedNovelSource().set(sourceId)
    }

    fun getLastUsedNovelSourceName(): String? {
        val sourceId = sourcePreferences.lastUsedNovelSource().get()
        if (sourceId == -1L) return null
        return sourceManager.get(sourceId)?.name
    }

    private suspend fun filterGhostLocalNovelHistory(
        items: List<NovelHistoryWithRelations>,
    ): List<NovelHistoryWithRelations> {
        if (items.isEmpty()) return items
        val localItems = items.filter { LocalNovelVisibility.isLocalSource(it.coverData.sourceId) }
        if (localItems.isEmpty()) return items

        val urlByNovelId = HashMap<Long, String?>(localItems.size)
        for (item in localItems) {
            if (item.novelId in urlByNovelId) continue
            if (LocalNovelVisibility.shouldHideLocalHistoryByCover(item.coverData.sourceId, item.coverData.url)) {
                urlByNovelId[item.novelId] = null
                continue
            }
            urlByNovelId[item.novelId] = getNovel.await(item.novelId)?.url
        }

        return items.filter { item ->
            if (!LocalNovelVisibility.isLocalSource(item.coverData.sourceId)) return@filter true
            LocalNovelVisibility.shouldShowLocalNovelEntry(
                sourceId = item.coverData.sourceId,
                url = urlByNovelId[item.novelId],
                coverUrl = item.coverData.url,
                hasSupportedContent = localNovelSourceFileSystem::hasSupportedLocalNovelContent,
            )
        }
    }

    private data class LiveData(
        val name: String,
        val avatar: String,
        val categories: List<tachiyomi.domain.category.novel.model.NovelCategory>,
        val historyList: List<NovelHistoryWithRelations>,
        val novelList: List<LibraryNovel>,
    )

    companion object {
        @Volatile
        private var instance: NovelHomeHubScreenModel? = null

        fun saveOnExit() {
            instance?.saveCache()
        }

        internal fun setInstance(model: NovelHomeHubScreenModel) {
            instance = model
        }
    }
}

internal fun resolveNovelHomeHeroChapterId(
    chapters: List<tachiyomi.domain.items.novelchapter.model.NovelChapter>,
    fromChapterId: Long,
    bookState: tachiyomi.domain.book.novel.model.NovelBookState? = null,
): Long? {
    return resolveNovelResumeChapter(chapters, fromChapterId, bookState)?.id
}

internal fun shouldReloadNovelHomeHeroChapterId(
    previousHeroNovelId: Long?,
    previousHeroChapterId: Long?,
    currentHeroNovelId: Long,
    currentHeroChapterId: Long,
): Boolean {
    return previousHeroNovelId != currentHeroNovelId ||
        previousHeroChapterId != currentHeroChapterId
}
