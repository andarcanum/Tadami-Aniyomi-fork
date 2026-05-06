package eu.kanade.tachiyomi.ui.home

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
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
import java.io.File

class NovelHomeHubScreenModel(
    private val historyRepository: NovelHistoryRepository = Injekt.get(),
    private val getLibraryNovel: GetLibraryNovel = Injekt.get(),
    private val getNovelWithChapters: GetNovelWithChapters = Injekt.get(),
    private val getNovelCategories: GetNovelCategories = Injekt.get(),
    private val userProfilePreferences: UserProfilePreferences = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: NovelSourceManager = Injekt.get(),
) : StateScreenModel<NovelHomeHubScreenModel.State>(State()) {

    private val fastCache = NovelHomeHubFastCache(Injekt.get<android.app.Application>())

    @Volatile
    private var liveUpdatesStarted = false

    data class State(
        val hero: HeroData? = null,
        val history: List<HistoryData> = emptyList(),
        val recommendations: List<RecommendationData> = emptyList(),
        val heroChapterId: Long? = null,
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
        val novelId: Long,
        val title: String,
        val chapterNumber: Double,
        val coverData: NovelCover,
        val chapterId: Long,
    )

    data class HistoryData(
        val novelId: Long,
        val title: String,
        val chapterNumber: Double,
        val coverData: NovelCover,
    )

    data class RecommendationData(
        val novelId: Long,
        val title: String,
        val coverData: NovelCover,
        val totalCount: Long,
        val readCount: Long,
    )

    init {
        mutableState.update {
            it.copy(
                userName = userProfilePreferences.name().get(),
                userAvatar = userProfilePreferences.avatarUrl().get(),
            )
        }

        val cached = fastCache.load()
        if (!cached.isEmpty || cached.isInitialized) {
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
        }

        screenModelScope.launchIO {
            val greetingSelection = HomeGreetingSession.resolveGreeting(
                userProfilePreferences = userProfilePreferences,
            )
            mutableState.update { it.copy(greeting = greetingSelection.greeting, greetingReady = true) }
        }

        cached.hero?.let { hero ->
            screenModelScope.launchIO {
                loadHeroChapterId(hero.novelId, hero.chapterId)
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
                val hiddenCategoryIds = data.categories
                    .filter { it.hiddenFromHomeHub }
                    .map { it.id }
                    .toSet()
                val novelCategoryIdsByNovelId = data.novelList
                    .groupBy { it.novel.id }
                    .mapValues { (_, items) -> items.map { it.category } }

                val filteredHistory = filterHomeHubEntriesBy(
                    items = data.historyList,
                    keySelector = { it.novelId },
                    entryCategoryIds = novelCategoryIdsByNovelId,
                    hiddenCategoryIds = hiddenCategoryIds,
                )

                val filteredNovel = filterHomeHubEntriesBy(
                    items = data.novelList,
                    keySelector = { it.novel.id },
                    entryCategoryIds = novelCategoryIdsByNovelId,
                    hiddenCategoryIds = hiddenCategoryIds,
                ).distinctBy { it.novel.id }

                val hero = filteredHistory.firstOrNull()
                val history = if (filteredHistory.size > 1) filteredHistory.drop(1).take(6) else emptyList()

                val hasData = hero != null || history.isNotEmpty() || filteredNovel.isNotEmpty()
                if (hasData && !state.value.isInitialized) {
                    fastCache.markInitialized()
                }
                val previousHero = mutableState.value.hero

                val novelRecommendations = filteredNovel
                    .sortedByDescending { it.novel.dateAdded }
                    .take(10)

                mutableState.update {
                    it.copy(
                        hero = hero?.toHeroData(),
                        history = history.map { h -> h.toHistoryData() },
                        recommendations = novelRecommendations.map { n -> n.toRecommendationData() },
                        userName = data.name,
                        userAvatar = data.avatar,
                        isInitialized = hasData || it.isInitialized,
                        isLoading = false,
                    )
                }

                if (
                    hero != null &&
                    shouldReloadNovelHomeHeroChapterId(
                        previousHeroNovelId = previousHero?.novelId,
                        previousHeroChapterId = previousHero?.chapterId,
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
        mutableState.update {
            it.copy(heroChapterId = resolveNovelHomeHeroChapterId(chapters, fromChapterId))
        }
    }

    fun getHeroChapterId(): Long? {
        return state.value.heroChapterId ?: state.value.hero?.chapterId
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
            val file = File(context.filesDir, "user_avatar_novel.jpg")
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

    fun saveCache() {
        val currentState = state.value
        fastCache.save(
            CachedNovelHomeState(
                hero = currentState.hero?.toCached(),
                history = currentState.history.map { it.toCached() },
                recommendations = currentState.recommendations.map { it.toCached() },
                userName = currentState.userName,
                userAvatar = currentState.userAvatar,
                isInitialized = currentState.isInitialized,
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

    private fun NovelHistoryWithRelations.toHeroData() = HeroData(
        novelId = novelId,
        title = title,
        chapterNumber = chapterNumber,
        coverData = coverData,
        chapterId = chapterId,
    )

    private fun NovelHistoryWithRelations.toHistoryData() = HistoryData(
        novelId = novelId,
        title = title,
        chapterNumber = chapterNumber,
        coverData = coverData,
    )

    private fun LibraryNovel.toRecommendationData() = RecommendationData(
        novelId = novel.id,
        title = novel.title,
        coverData = NovelCover(
            novelId = novel.id,
            sourceId = novel.source,
            isNovelFavorite = novel.favorite,
            url = novel.thumbnailUrl,
            lastModified = novel.coverLastModified,
        ),
        totalCount = totalChapters,
        readCount = readCount,
    )

    private fun HeroData.toCached() = CachedNovelHeroItem(
        novelId = novelId,
        title = title,
        chapterNumber = chapterNumber,
        coverUrl = coverData.url,
        coverLastModified = coverData.lastModified,
        chapterId = chapterId,
    )

    private fun HistoryData.toCached() = CachedNovelHistoryItem(
        novelId = novelId,
        title = title,
        chapterNumber = chapterNumber,
        coverUrl = coverData.url,
        coverLastModified = coverData.lastModified,
    )

    private fun RecommendationData.toCached() = CachedNovelRecommendationItem(
        novelId = novelId,
        title = title,
        coverUrl = coverData.url,
        coverLastModified = coverData.lastModified,
        totalCount = totalCount,
        readCount = readCount,
    )

    private fun CachedNovelHeroItem.toHeroData() = HeroData(
        novelId = novelId,
        title = title,
        chapterNumber = chapterNumber,
        coverData = NovelCover(novelId, -1, true, coverUrl, coverLastModified),
        chapterId = chapterId,
    )

    private fun CachedNovelHistoryItem.toHistoryData() = HistoryData(
        novelId = novelId,
        title = title,
        chapterNumber = chapterNumber,
        coverData = NovelCover(novelId, -1, true, coverUrl, coverLastModified),
    )

    private fun CachedNovelRecommendationItem.toRecommendationData() = RecommendationData(
        novelId = novelId,
        title = title,
        coverData = NovelCover(novelId, -1, true, coverUrl, coverLastModified),
        totalCount = totalCount,
        readCount = readCount,
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
