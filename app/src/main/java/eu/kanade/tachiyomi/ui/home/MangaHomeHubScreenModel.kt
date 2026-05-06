package eu.kanade.tachiyomi.ui.home

import android.content.Context
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
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
import java.io.File

class MangaHomeHubScreenModel(
    private val getMangaHistory: GetMangaHistory = Injekt.get(),
    private val getMangaWithChapters: GetMangaWithChapters = Injekt.get(),
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val getMangaCategories: GetMangaCategories = Injekt.get(),
    private val userProfilePreferences: UserProfilePreferences = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: MangaSourceManager = Injekt.get(),
) : StateScreenModel<MangaHomeHubScreenModel.State>(State()) {

    private val fastCache = MangaHomeHubFastCache(Injekt.get<android.app.Application>())

    @Volatile
    private var liveUpdatesStarted = false

    data class State(
        val hero: HeroData? = null,
        val history: List<HistoryData> = emptyList(),
        val recommendations: List<RecommendationData> = emptyList(),
        val heroChapter: Chapter? = null,
        val userName: String = "",
        val userAvatar: String = "",
        val greeting: StringResource = AYMR.strings.aurora_welcome_back,
        val greetingReady: Boolean = false,
        val isInitialized: Boolean = false,
        val isLoading: Boolean = true,
    ) {
        val isEmpty: Boolean
            get() = hero == null && history.isEmpty() && recommendations.isEmpty()

        val showWelcome: Boolean
            get() = !isInitialized && isEmpty && !isLoading

        val showFilteredEmpty: Boolean
            get() = isInitialized && isEmpty && !isLoading
    }

    data class HeroData(
        val mangaId: Long,
        val title: String,
        val chapterNumber: Double,
        val coverData: MangaCover,
        val chapterId: Long,
    )

    data class HistoryData(
        val mangaId: Long,
        val title: String,
        val chapterNumber: Double,
        val coverData: MangaCover,
    )

    data class RecommendationData(
        val mangaId: Long,
        val title: String,
        val coverData: MangaCover,
        val totalCount: Long,
        val unreadCount: Long,
    )

    init {
        val cached = fastCache.load()
        mutableState.update {
            it.copy(
                hero = cached.hero?.toHeroData(),
                history = cached.history.map { h -> h.toHistoryData() },
                recommendations = cached.recommendations.map { r -> r.toRecommendationData() },
                userName = cached.userName,
                userAvatar = cached.userAvatar,
                isInitialized = cached.isInitialized,
                isLoading = false,
            )
        }

        screenModelScope.launchIO {
            val greetingSelection = HomeGreetingSession.resolveGreeting(
                userProfilePreferences = userProfilePreferences,
            )
            mutableState.update { it.copy(greeting = greetingSelection.greeting, greetingReady = true) }
        }

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
                val history = if (filteredHistory.size > 1) filteredHistory.drop(1).take(6) else emptyList()

                val hasData = hero != null || history.isNotEmpty() || filteredManga.isNotEmpty()
                if (hasData && !state.value.isInitialized) {
                    fastCache.markInitialized()
                }

                val previousHero = mutableState.value.hero

                val mangaRecommendations = filteredManga
                    .sortedByDescending { it.manga.dateAdded }
                    .take(10)

                mutableState.update {
                    it.copy(
                        hero = hero?.toHeroData(),
                        history = history.map { h -> h.toHistoryData() },
                        recommendations = mangaRecommendations.map { m -> m.toRecommendationData() },
                        userName = data.name,
                        userAvatar = data.avatar,
                        isInitialized = hasData || it.isInitialized,
                        isLoading = false,
                    )
                }

                if (
                    hero != null &&
                    shouldReloadMangaHomeHeroChapter(
                        previousHeroMangaId = previousHero?.mangaId,
                        previousHeroChapterId = previousHero?.chapterId,
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
        val heroChapter = resolveMangaResumeChapter(chapters, chapterId)
        mutableState.update { it.copy(heroChapter = heroChapter) }
    }

    fun readHeroChapter(context: Context) {
        val hero = state.value.hero ?: return
        val chapter = state.value.heroChapter
        val chapterId = chapter?.id ?: hero.chapterId
        context.startActivity(ReaderActivity.newIntent(context, hero.mangaId, chapterId))
    }

    fun saveCache() {
        val currentState = state.value
        fastCache.save(
            CachedMangaHomeState(
                hero = currentState.hero?.toCached(),
                history = currentState.history.map { it.toCached() },
                recommendations = currentState.recommendations.map { it.toCached() },
                userName = currentState.userName,
                userAvatar = currentState.userAvatar,
                isInitialized = currentState.isInitialized,
            ),
        )
    }

    fun updateUserName(name: String) {
        val previousName = userProfilePreferences.name().get()
        userProfilePreferences.name().set(name)
        if (name != previousName) {
            userProfilePreferences.nameEdited().set(true)
        }
        fastCache.updateUserName(name)
        mutableState.update { it.copy(userName = name) }
    }

    fun updateUserAvatar(uriString: String) {
        val context = Injekt.get<android.app.Application>()
        try {
            val uri = android.net.Uri.parse(uriString)
            val inputStream = context.contentResolver.openInputStream(uri) ?: return
            val file = File(context.filesDir, "user_avatar_manga.jpg")
            file.outputStream().use { output ->
                inputStream.use { input -> input.copyTo(output) }
            }
            val path = file.absolutePath
            userProfilePreferences.avatarUrl().set(path)
            fastCache.updateUserAvatar(path)
            mutableState.update { it.copy(userAvatar = path) }
        } catch (_: Exception) {
        }
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

    private fun CachedMangaHeroItem.toHeroData() = HeroData(
        mangaId = mangaId,
        title = title,
        chapterNumber = chapterNumber,
        coverData = MangaCover(mangaId, -1, true, coverUrl, coverLastModified),
        chapterId = chapterId,
    )

    private fun CachedMangaHistoryItem.toHistoryData() = HistoryData(
        mangaId = mangaId,
        title = title,
        chapterNumber = chapterNumber,
        coverData = MangaCover(mangaId, -1, true, coverUrl, coverLastModified),
    )

    private fun CachedMangaRecommendationItem.toRecommendationData() = RecommendationData(
        mangaId = mangaId,
        title = title,
        coverData = MangaCover(mangaId, -1, true, coverUrl, coverLastModified),
        totalCount = totalCount,
        unreadCount = unreadCount,
    )

    private fun MangaHistoryWithRelations.toHeroData() = HeroData(
        mangaId = mangaId,
        title = title,
        chapterNumber = chapterNumber,
        coverData = coverData,
        chapterId = chapterId,
    )

    private fun MangaHistoryWithRelations.toHistoryData() = HistoryData(
        mangaId = mangaId,
        title = title,
        chapterNumber = chapterNumber,
        coverData = coverData,
    )

    private fun LibraryManga.toRecommendationData() = RecommendationData(
        mangaId = manga.id,
        title = manga.title,
        coverData = MangaCover(manga.id, manga.source, manga.favorite, manga.thumbnailUrl, manga.coverLastModified),
        totalCount = totalChapters,
        unreadCount = unreadCount,
    )

    private fun HeroData.toCached() = CachedMangaHeroItem(
        mangaId = mangaId,
        title = title,
        chapterNumber = chapterNumber,
        coverUrl = coverData.url,
        coverLastModified = coverData.lastModified,
        chapterId = chapterId,
    )

    private fun HistoryData.toCached() = CachedMangaHistoryItem(
        mangaId = mangaId,
        title = title,
        chapterNumber = chapterNumber,
        coverUrl = coverData.url,
        coverLastModified = coverData.lastModified,
    )

    private fun RecommendationData.toCached() = CachedMangaRecommendationItem(
        mangaId = mangaId,
        title = title,
        coverUrl = coverData.url,
        coverLastModified = coverData.lastModified,
        totalCount = totalCount,
        unreadCount = unreadCount,
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
