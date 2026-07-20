package eu.kanade.tachiyomi.ui.home

import android.content.Context
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UserProfilePreferences
import eu.kanade.presentation.series.manga.resolveMangaResumeChapter
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga
import tachiyomi.domain.entries.manga.interactor.GetMangaWithChapters
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.domain.history.manga.interactor.GetMangaHistory
import tachiyomi.domain.history.manga.model.MangaHistoryWithRelations
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.source.manga.service.MangaSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

internal class MangaHomeHubScreenModel(
    context: android.content.Context = Injekt.get<android.app.Application>(),
    userProfilePreferences: UserProfilePreferences = Injekt.get(),
) : BaseHomeHubScreenModel(
    context = context,
    initialState = run {
        val tempCache = HomeHubFastCache(context, HomeHubSection.Manga)
        val cached = tempCache.loadCachedState()
        val hadCache = !cached.isEmpty || cached.isInitialized
        if (hadCache) {
            HomeHubUiState(
                hero = cached.hero?.let { h ->
                    HomeHubHero(
                        entryId = h.entryId,
                        title = h.title,
                        progressNumber = h.progressNumber,
                        coverData = MangaCover(h.entryId, h.sourceId, h.favorite, h.coverUrl, h.coverLastModified),
                    )
                },
                history = cached.history.map { h ->
                    HomeHubHistory(
                        entryId = h.entryId,
                        title = h.title,
                        progressNumber = h.progressNumber,
                        coverData = MangaCover(h.entryId, h.sourceId, h.favorite, h.coverUrl, h.coverLastModified),
                        section = HomeHubSection.Manga,
                    )
                },
                recommendations = cached.recommendations.map { r ->
                    HomeHubRecommendation(
                        entryId = r.entryId,
                        title = r.title,
                        coverData = MangaCover(r.entryId, r.sourceId, r.favorite, r.coverUrl, r.coverLastModified),
                        section = HomeHubSection.Manga,
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
                greeting = tachiyomi.i18n.aniyomi.AYMR.strings.aurora_welcome_back,
                greetingReady = false,
                isLoading = true,
                showWelcome = true,
            )
        }
    },
    userProfilePreferences = userProfilePreferences,
) {

    private val getMangaHistory: GetMangaHistory by injectLazy()
    private val getMangaWithChapters: GetMangaWithChapters by injectLazy()
    private val getLibraryManga: GetLibraryManga by injectLazy()
    private val getMangaCategories: GetMangaCategories by injectLazy()
    private val sourcePreferences: SourcePreferences by injectLazy()
    private val sourceManager: MangaSourceManager by injectLazy()

    override val avatarFileName: String = "user_avatar_manga.jpg"

    private val fastCache = HomeHubFastCache(context, HomeHubSection.Manga)

    @Volatile
    private var liveUpdatesStarted = false

    private var heroChapter: Chapter? = null
    private var originalHeroChapterId: Long? = null

    override fun updateCacheUserName(name: String) {
        fastCache.updateUserName(name)
    }

    override fun updateCacheUserAvatar(path: String) {
        fastCache.updateUserAvatar(path)
    }

    init {
        // Initial state is now computed from cache in the constructor for instant first frame without skeleton flicker.
        // The live updates will refresh with fresh data.
        // PERF: Defer expensive DB work until after first frame
        initializeGreetingDeferred()

        // Load hero episode if we had one in initial (re-load cache for this, cheap)
        fastCache.load().hero?.let { hero ->
            screenModelScope.launchIO {
                loadHeroChapter(hero.entryId, hero.subId)
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
                getMangaCategories.subscribe(),
                getMangaHistory.subscribe(""),
                getLibraryManga.subscribe(),
            ) { name, avatar, categories, historyList, mangaList ->
                LiveData(name, avatar, categories, historyList, mangaList)
            }.collectLatest { data ->
                val hiddenCategoryIds = hiddenHomeHubCategoryIds(
                    categories = data.categories,
                    isHiddenFromHomeHub = { it.hiddenFromHomeHub },
                    idSelector = { it.id },
                )
                val mangaCategoryIdsByMangaId = homeHubCategoryIdsByEntryId(
                    items = data.mangaList,
                    entryIdSelector = { it.manga.id },
                    categoryIdSelector = { it.category },
                )

                val filteredHistory = filterHomeHubEntriesBy(
                    items = data.historyList,
                    keySelector = { it.mangaId },
                    entryCategoryIds = mangaCategoryIdsByMangaId,
                    hiddenCategoryIds = hiddenCategoryIds,
                )

                val filteredManga = filterHomeHubEntriesByDistinct(
                    items = data.mangaList,
                    keySelector = { it.manga.id },
                    entryCategoryIds = mangaCategoryIdsByMangaId,
                    hiddenCategoryIds = hiddenCategoryIds,
                )

                val hero = filteredHistory.firstOrNull()
                val history = takeHomeHubHistoryExcluding(
                    items = filteredHistory,
                    limit = 6,
                    excludedEntryId = hero?.mangaId,
                    entryIdSelector = { it.mangaId },
                )

                val hasData = hero != null || history.isNotEmpty() || filteredManga.isNotEmpty()
                val isInitialized = hasData ||
                    (
                        state.value.showFilteredEmpty ||
                            (state.value.showWelcome.not() && state.value.isLoading.not())
                        )

                val mangaRecommendations = filteredManga
                    .sortedByDescending { it.manga.dateAdded }
                    .take(10)

                val previousHero = state.value.hero
                val previousHeroChapterId = originalHeroChapterId

                originalHeroChapterId = hero?.chapterId

                val isEmpty = hero == null && history.isEmpty() && mangaRecommendations.isEmpty()
                val showWelcome = !isInitialized && isEmpty
                val showFilteredEmpty = isInitialized && isEmpty

                mutableState.update {
                    it.copy(
                        hero = hero?.let { h ->
                            HomeHubHero(
                                entryId = h.mangaId,
                                title = h.title,
                                progressNumber = h.chapterNumber,
                                coverData = h.coverData,
                            )
                        },
                        history = history.map { h ->
                            HomeHubHistory(
                                entryId = h.mangaId,
                                title = h.title,
                                progressNumber = h.chapterNumber,
                                coverData = h.coverData,
                                section = HomeHubSection.Manga,
                            )
                        },
                        recommendations = mangaRecommendations.map { m ->
                            HomeHubRecommendation(
                                entryId = m.manga.id,
                                title = m.manga.title,
                                coverData = MangaCover(
                                    mangaId = m.manga.id,
                                    sourceId = m.manga.source,
                                    isMangaFavorite = m.manga.favorite,
                                    url = m.manga.thumbnailUrl,
                                    lastModified = m.manga.coverLastModified,
                                ),
                                section = HomeHubSection.Manga,
                                progressNumerator = m.totalChapters - m.unreadCount,
                                progressDenominator = m.totalChapters,
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
                    shouldReloadMangaHomeHeroChapter(
                        previousHeroMangaId = previousHero?.entryId,
                        previousHeroChapterId = previousHeroChapterId,
                        currentHeroMangaId = hero.mangaId,
                        currentHeroChapterId = hero.chapterId,
                    )
                ) {
                    loadHeroChapter(hero.mangaId, hero.chapterId)
                }

                saveCache()
            }
        }
    }

    private suspend fun loadHeroChapter(mangaId: Long, chapterId: Long) {
        val chapters = getMangaWithChapters.awaitChapters(mangaId, applyScanlatorFilter = true)
        heroChapter = resolveMangaResumeChapter(chapters, chapterId)
    }

    fun readHeroChapter(context: Context) {
        val hero = state.value.hero ?: return
        val chapterId = heroChapter?.id ?: originalHeroChapterId ?: return
        context.startActivity(ReaderActivity.newIntent(context, hero.entryId, chapterId))
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
                        coverUrl = (hero.coverData as? MangaCover)?.url,
                        coverLastModified = (hero.coverData as? MangaCover)?.lastModified ?: 0L,
                        sourceId = (hero.coverData as? MangaCover)?.sourceId ?: -1L,
                        favorite = (hero.coverData as? MangaCover)?.isMangaFavorite ?: false,
                        subId = originalHeroChapterId ?: 0L,
                    )
                },
                history = currentState.history.map { h ->
                    CachedHistoryItem(
                        entryId = h.entryId,
                        title = h.title,
                        progressNumber = h.progressNumber,
                        coverUrl = (h.coverData as? MangaCover)?.url,
                        coverLastModified = (h.coverData as? MangaCover)?.lastModified ?: 0L,
                        sourceId = (h.coverData as? MangaCover)?.sourceId ?: -1L,
                        favorite = (h.coverData as? MangaCover)?.isMangaFavorite ?: false,
                    )
                },
                recommendations = currentState.recommendations.map { r ->
                    CachedRecommendationItem(
                        entryId = r.entryId,
                        title = r.title,
                        coverUrl = (r.coverData as? MangaCover)?.url,
                        coverLastModified = (r.coverData as? MangaCover)?.lastModified ?: 0L,
                        sourceId = (r.coverData as? MangaCover)?.sourceId ?: -1L,
                        favorite = (r.coverData as? MangaCover)?.isMangaFavorite ?: false,
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

    fun getLastUsedMangaSourceId(): Long = sourcePreferences.lastUsedMangaSource().get()

    fun getLastUsedMangaSourceName(): String? {
        val sourceId = sourcePreferences.lastUsedMangaSource().get()
        if (sourceId == -1L) return null
        return sourceManager.get(sourceId)?.name
    }

    private data class LiveData(
        val name: String,
        val avatar: String,
        val categories: List<tachiyomi.domain.category.model.Category>,
        val historyList: List<MangaHistoryWithRelations>,
        val mangaList: List<LibraryManga>,
    )

    companion object {
        @Volatile
        private var instance: MangaHomeHubScreenModel? = null

        fun saveOnExit() {
            instance?.saveCache()
        }

        internal fun setInstance(model: MangaHomeHubScreenModel) {
            instance = model
        }
    }
}

internal fun shouldReloadMangaHomeHeroChapter(
    previousHeroMangaId: Long?,
    previousHeroChapterId: Long?,
    currentHeroMangaId: Long,
    currentHeroChapterId: Long,
): Boolean {
    return previousHeroMangaId != currentHeroMangaId ||
        previousHeroChapterId != currentHeroChapterId
}
