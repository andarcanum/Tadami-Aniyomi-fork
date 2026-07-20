package eu.kanade.tachiyomi.ui.home

import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UserProfilePreferences
import eu.kanade.tachiyomi.ui.novel.resolveNovelResumeChapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import tachiyomi.domain.entries.novel.interactor.GetLibraryNovel
import tachiyomi.domain.entries.novel.interactor.GetNovelWithChapters
import tachiyomi.domain.entries.novel.model.NovelCover
import tachiyomi.domain.history.novel.model.NovelHistoryWithRelations
import tachiyomi.domain.history.novel.repository.NovelHistoryRepository
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

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
                hero = cached.hero?.let { h ->
                    HomeHubHero(
                        entryId = h.entryId,
                        title = h.title,
                        progressNumber = h.progressNumber,
                        coverData = NovelCover(h.entryId, h.sourceId, h.favorite, h.coverUrl, h.coverLastModified),
                    )
                },
                history = cached.history.map { h ->
                    HomeHubHistory(
                        entryId = h.entryId,
                        title = h.title,
                        progressNumber = h.progressNumber,
                        coverData = NovelCover(h.entryId, h.sourceId, h.favorite, h.coverUrl, h.coverLastModified),
                        section = HomeHubSection.Novel,
                    )
                },
                recommendations = cached.recommendations.map { r ->
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
    private val getNovelWithChapters: GetNovelWithChapters by injectLazy()
    private val getNovelCategories: GetNovelCategories by injectLazy()
    private val sourcePreferences: SourcePreferences by injectLazy()
    private val sourceManager: NovelSourceManager by injectLazy()

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
                    hero = cached.hero?.let { h ->
                        HomeHubHero(
                            entryId = h.entryId,
                            title = h.title,
                            progressNumber = h.progressNumber,
                            coverData = NovelCover(h.entryId, h.sourceId, h.favorite, h.coverUrl, h.coverLastModified),
                        )
                    },
                    history = cached.history.map { h ->
                        HomeHubHistory(
                            entryId = h.entryId,
                            title = h.title,
                            progressNumber = h.progressNumber,
                            coverData = NovelCover(h.entryId, h.sourceId, h.favorite, h.coverUrl, h.coverLastModified),
                            section = HomeHubSection.Novel,
                        )
                    },
                    recommendations = cached.recommendations.map { r ->
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

                val filteredHistory = filterHomeHubEntriesBy(
                    items = data.historyList,
                    keySelector = { it.novelId },
                    entryCategoryIds = novelCategoryIdsByNovelId,
                    hiddenCategoryIds = hiddenCategoryIds,
                )

                val filteredNovel = filterHomeHubEntriesByDistinct(
                    items = data.novelList,
                    keySelector = { it.novel.id },
                    entryCategoryIds = novelCategoryIdsByNovelId,
                    hiddenCategoryIds = hiddenCategoryIds,
                )

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
        lastResolvedHeroChapterId = resolveNovelHomeHeroChapterId(chapters, fromChapterId)
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

    fun getLastUsedNovelSourceId(): Long = sourcePreferences.lastUsedNovelSource().get()

    fun getLastUsedNovelSourceName(): String? {
        val sourceId = sourcePreferences.lastUsedNovelSource().get()
        if (sourceId == -1L) return null
        return sourceManager.get(sourceId)?.name
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
): Long? {
    return resolveNovelResumeChapter(chapters, fromChapterId)?.id
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
