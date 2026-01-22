package eu.kanade.tachiyomi.ui.home

import android.content.Context
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UserProfilePreferences
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.domain.history.manga.interactor.GetMangaHistory
import tachiyomi.domain.history.manga.interactor.GetNextChapters
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
    private val getNextChapters: GetNextChapters = Injekt.get(),
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
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
        val userName: String = "Читатель",
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
        val coverUrl: String?,
        val coverLastModified: Long,
        val totalCount: Long,
        val unreadCount: Long,
    )

    init {
        val cached = fastCache.load()
        val lastOpened = userProfilePreferences.lastOpenedTime().get()
        val greeting = GreetingProvider.getGreeting(lastOpened)

        userProfilePreferences.lastOpenedTime().set(System.currentTimeMillis())

        mutableState.update {
            it.copy(
                hero = cached.hero?.toHeroData(),
                history = cached.history.map { h -> h.toHistoryData() },
                recommendations = cached.recommendations.map { r -> r.toRecommendationData() },
                userName = cached.userName,
                userAvatar = cached.userAvatar,
                greeting = greeting,
                isInitialized = cached.isInitialized,
                isLoading = false,
            )
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
                getMangaHistory.subscribe("").map { it.take(7) },
                getLibraryManga.subscribe().map { list ->
                    list.filter { it.manga.favorite }
                        .take(10)
                },
            ) { name, avatar, historyList, mangaList ->
                LiveData(name, avatar, historyList, mangaList)
            }.collectLatest { data ->
                val hero = data.historyList.firstOrNull()
                val history = if (data.historyList.size > 1) data.historyList.drop(1) else emptyList()

                val hasData = hero != null || history.isNotEmpty() || data.mangaList.isNotEmpty()
                if (hasData && !state.value.isInitialized) {
                    fastCache.markInitialized()
                }

                val previousHeroId = mutableState.value.hero?.mangaId

                mutableState.update {
                    it.copy(
                        hero = hero?.toHeroData(),
                        history = history.map { h -> h.toHistoryData() },
                        recommendations = data.mangaList.map { m -> m.toRecommendationData() },
                        userName = data.name,
                        userAvatar = data.avatar,
                        isInitialized = hasData || it.isInitialized,
                        isLoading = false,
                    )
                }

                if (hero != null && hero.mangaId != previousHeroId) {
                    loadHeroChapter(hero.mangaId, hero.chapterId)
                }

                saveCache()
            }
        }
    }

    private suspend fun loadHeroChapter(mangaId: Long, chapterId: Long) {
        val nextChapters = getNextChapters.await(mangaId, chapterId, onlyUnread = true)
        val heroChapter = nextChapters.firstOrNull()
            ?: getNextChapters.await(mangaId, chapterId, onlyUnread = false).firstOrNull()
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
        userProfilePreferences.name().set(name)
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
        coverUrl = coverUrl,
        coverLastModified = coverLastModified,
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
        coverUrl = manga.thumbnailUrl,
        coverLastModified = manga.coverLastModified,
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
        coverUrl = coverUrl,
        coverLastModified = coverLastModified,
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
