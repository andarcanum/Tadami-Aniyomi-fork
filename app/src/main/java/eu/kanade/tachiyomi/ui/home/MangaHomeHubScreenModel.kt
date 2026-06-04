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
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

internal class MangaHomeHubScreenModel(
    context: android.content.Context = Injekt.get<android.app.Application>(),
    private val getMangaHistory: GetMangaHistory = Injekt.get(),
    private val getMangaWithChapters: GetMangaWithChapters = Injekt.get(),
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val getMangaCategories: GetMangaCategories = Injekt.get(),
    userProfilePreferences: UserProfilePreferences = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: MangaSourceManager = Injekt.get(),
) : BaseHomeHubScreenModel(
    context = context,
    initialState = HomeHubUiState(
        userName = userProfilePreferences.name().get(),
        userAvatar = userProfilePreferences.avatarUrl().get(),
        greeting = AYMR.strings.aurora_welcome_back,
        greetingReady = false,
        isLoading = true,
        showWelcome = true,
    ),
    userProfilePreferences = userProfilePreferences,
) {

    override val avatarFileName: String = "user_avatar_manga.jpg"

    private val fastCache = MangaHomeHubFastCache(context)

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
        val cached = fastCache.load()
        if (!cached.isEmpty || cached.isInitialized) {
            originalHeroChapterId = cached.hero?.chapterId
            mutableState.update {
                it.copy(
                    hero = cached.hero?.let { h ->
                        HomeHubHero(
                            entryId = h.mangaId,
                            title = h.title,
                            progressNumber = h.chapterNumber,
                            coverData = MangaCover(h.mangaId, -1, true, h.coverUrl, h.coverLastModified),
                        )
                    },
                    history = cached.history.map { h ->
                        HomeHubHistory(
                            entryId = h.mangaId,
                            title = h.title,
                            progressNumber = h.chapterNumber,
                            coverData = MangaCover(h.mangaId, -1, true, h.coverUrl, h.coverLastModified),
                            section = HomeHubSection.Manga,
                        )
                    },
                    recommendations = cached.recommendations.map { r ->
                        HomeHubRecommendation(
                            entryId = r.mangaId,
                            title = r.title,
                            coverData = MangaCover(r.mangaId, -1, true, r.coverUrl, r.coverLastModified),
                            section = HomeHubSection.Manga,
                            progressNumerator = r.totalCount - r.unreadCount,
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

        initializeGreeting()

        cached.hero?.let { hero ->
            screenModelScope.launchIO {
                loadHeroChapter(hero.mangaId, hero.chapterId)
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
                val hiddenCategoryIds = data.categories
                    .filter { it.hiddenFromHomeHub }
                    .map { it.id }
                    .toSet()
                val mangaCategoryIdsByMangaId = data.mangaList
                    .groupBy { it.manga.id }
                    .mapValues { (_, items) -> items.map { it.category } }

                val filteredHistory = filterHomeHubEntriesBy(
                    items = data.historyList,
                    keySelector = { it.mangaId },
                    entryCategoryIds = mangaCategoryIdsByMangaId,
                    hiddenCategoryIds = hiddenCategoryIds,
                )

                val filteredManga = filterHomeHubEntriesBy(
                    items = data.mangaList,
                    keySelector = { it.manga.id },
                    entryCategoryIds = mangaCategoryIdsByMangaId,
                    hiddenCategoryIds = hiddenCategoryIds,
                ).distinctBy { it.manga.id }

                val hero = filteredHistory.firstOrNull()
                val history = filteredHistory
                    .filter { hero == null || it.mangaId != hero.mangaId }
                    .take(6)

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
        fastCache.save(
            CachedMangaHomeState(
                hero = currentState.hero?.let { hero ->
                    CachedMangaHeroItem(
                        mangaId = hero.entryId,
                        title = hero.title,
                        chapterNumber = hero.progressNumber,
                        coverUrl = (hero.coverData as? MangaCover)?.url,
                        coverLastModified = (hero.coverData as? MangaCover)?.lastModified ?: 0L,
                        chapterId = originalHeroChapterId ?: 0L,
                    )
                },
                history = currentState.history.map { h ->
                    CachedMangaHistoryItem(
                        mangaId = h.entryId,
                        title = h.title,
                        chapterNumber = h.progressNumber,
                        coverUrl = (h.coverData as? MangaCover)?.url,
                        coverLastModified = (h.coverData as? MangaCover)?.lastModified ?: 0L,
                    )
                },
                recommendations = currentState.recommendations.map { r ->
                    CachedMangaRecommendationItem(
                        mangaId = r.entryId,
                        title = r.title,
                        coverUrl = (r.coverData as? MangaCover)?.url,
                        coverLastModified = (r.coverData as? MangaCover)?.lastModified ?: 0L,
                        totalCount = r.progressDenominator,
                        unreadCount = r.progressDenominator - r.progressNumerator,
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
