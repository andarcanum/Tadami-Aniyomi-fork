package eu.kanade.tachiyomi.ui.home

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UserProfilePreferences
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.entries.novel.interactor.GetLibraryNovel
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.entries.novel.model.NovelCover
import tachiyomi.domain.history.novel.model.NovelHistoryWithRelations
import tachiyomi.domain.history.novel.repository.NovelHistoryRepository
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import tachiyomi.domain.items.novelchapter.service.getNovelChapterSort
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import kotlin.math.max

class NovelHomeHubScreenModel(
    private val historyRepository: NovelHistoryRepository = Injekt.get(),
    private val getLibraryNovel: GetLibraryNovel = Injekt.get(),
    private val getNovel: GetNovel = Injekt.get(),
    private val chapterRepository: NovelChapterRepository = Injekt.get(),
    private val userProfilePreferences: UserProfilePreferences = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: NovelSourceManager = Injekt.get(),
) : StateScreenModel<NovelHomeHubScreenModel.State>(State()) {

    @Volatile
    private var liveUpdatesStarted = false

    data class State(
        val hero: HeroData? = null,
        val history: List<HistoryData> = emptyList(),
        val recommendations: List<RecommendationData> = emptyList(),
        val heroChapterId: Long? = null,
        val userName: String = "Reader",
        val userAvatar: String = "",
        val greeting: StringResource = AYMR.strings.aurora_welcome_back,
        val isInitialized: Boolean = false,
        val isLoading: Boolean = true,
    ) {
        val isEmpty: Boolean
            get() = hero == null && history.isEmpty() && recommendations.isEmpty()

        val showWelcome: Boolean
            get() = !isInitialized && isEmpty && !isLoading
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
        val lastOpened = userProfilePreferences.lastOpenedTime().get()
        val totalLaunches = userProfilePreferences.totalLaunches().get()
        val recentGreetingIds = userProfilePreferences.getRecentGreetingHistory()
        val recentScenarioIds = userProfilePreferences.getRecentScenarioHistory()
        val greetingSelection = GreetingProvider.selectGreeting(
            lastOpenedTime = lastOpened,
            isFirstTime = lastOpened == 0L,
            totalLaunches = totalLaunches,
            recentGreetingIds = recentGreetingIds,
            recentScenarioIds = recentScenarioIds,
        )

        userProfilePreferences.lastOpenedTime().set(System.currentTimeMillis())
        userProfilePreferences.totalLaunches().set(totalLaunches + 1)
        userProfilePreferences.appendRecentGreetingId(greetingSelection.greetingId)
        userProfilePreferences.appendRecentScenarioId(greetingSelection.scenarioId)

        mutableState.update {
            it.copy(
                userName = userProfilePreferences.name().get(),
                userAvatar = userProfilePreferences.avatarUrl().get(),
                greeting = greetingSelection.greeting,
            )
        }
    }

    fun startLiveUpdates() {
        if (liveUpdatesStarted) return
        liveUpdatesStarted = true

        screenModelScope.launchIO {
            combine(
                userProfilePreferences.name().changes(),
                userProfilePreferences.avatarUrl().changes(),
                historyRepository.getNovelHistory("").map { it.take(7) },
                getLibraryNovel.subscribe().map { it.take(10) },
            ) { name, avatar, historyList, novelList ->
                LiveData(name, avatar, historyList, novelList)
            }.collectLatest { data ->
                val hero = data.historyList.firstOrNull()
                val history = if (data.historyList.size > 1) data.historyList.drop(1) else emptyList()

                val hasData = hero != null || history.isNotEmpty() || data.novelList.isNotEmpty()
                val previousHeroId = mutableState.value.hero?.novelId

                mutableState.update {
                    it.copy(
                        hero = hero?.toHeroData(),
                        history = history.map { h -> h.toHistoryData() },
                        recommendations = data.novelList.map { n -> n.toRecommendationData() },
                        userName = data.name,
                        userAvatar = data.avatar,
                        isInitialized = hasData || it.isInitialized,
                        isLoading = false,
                    )
                }

                if (hero != null && hero.novelId != previousHeroId) {
                    loadHeroChapterId(hero.novelId, hero.chapterId)
                }
            }
        }
    }

    private suspend fun loadHeroChapterId(novelId: Long, fromChapterId: Long) {
        val novel = getNovel.await(novelId) ?: return
        val chapters = chapterRepository.getChapterByNovelId(novelId, applyScanlatorFilter = true)
            .sortedWith(getNovelChapterSort(novel, sortDescending = false))
        if (chapters.isEmpty()) {
            mutableState.update { it.copy(heroChapterId = null) }
            return
        }

        val currentIndex = chapters.indexOfFirst { it.id == fromChapterId }
        val candidates = chapters.subList(max(0, currentIndex), chapters.size)
        val nextChapter = candidates.firstOrNull { !it.read }
            ?: candidates.firstOrNull()
            ?: chapters.firstOrNull { !it.read }
            ?: chapters.first()

        mutableState.update { it.copy(heroChapterId = nextChapter.id) }
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
            mutableState.update { it.copy(userAvatar = path) }
        } catch (_: Exception) {
        }
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
}
