package eu.kanade.tachiyomi.ui.entries.novel

import android.app.Application
import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.entries.novel.interactor.GetNovelExcludedScanlators
import eu.kanade.domain.entries.novel.interactor.NovelRatingFetcher
import eu.kanade.domain.entries.novel.interactor.SetNovelExcludedScanlators
import eu.kanade.domain.entries.novel.interactor.UpdateNovel
import eu.kanade.domain.items.novelchapter.interactor.GetAvailableNovelScanlators
import eu.kanade.domain.items.novelchapter.interactor.GetNovelScanlatorChapterCounts
import eu.kanade.domain.items.novelchapter.interactor.SyncNovelChaptersWithSource
import eu.kanade.domain.track.model.AutoTrackState
import eu.kanade.domain.track.novel.interactor.TrackNovelChapter
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadCacheEvent
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadQueueState
import eu.kanade.tachiyomi.data.download.novel.NovelTranslatedDownloadManager
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.translation.TranslationQueueItem
import eu.kanade.tachiyomi.data.translation.TranslationQueueManager
import eu.kanade.tachiyomi.data.translation.TranslationStatus
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiTranslationCacheEntry
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelReaderTranslationDiskCacheStore
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import tachiyomi.domain.category.novel.interactor.SetNovelCategories
import tachiyomi.domain.entries.novel.interactor.GetNovelFavorites
import tachiyomi.domain.entries.novel.interactor.GetNovelWithChapters
import tachiyomi.domain.entries.novel.interactor.SetNovelChapterFlags
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.history.novel.model.NovelHistory
import tachiyomi.domain.history.novel.model.NovelHistoryUpdate
import tachiyomi.domain.history.novel.model.NovelHistoryWithRelations
import tachiyomi.domain.history.novel.repository.NovelHistoryRepository
import tachiyomi.domain.items.novelchapter.interactor.SetNovelDefaultChapterFlags
import tachiyomi.domain.items.novelchapter.interactor.ShouldUpdateDbNovelChapter
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.domain.track.novel.interactor.GetNovelTracks
import tachiyomi.domain.track.novel.model.NovelTrack
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.fullType
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.Date

class NovelScreenModelTest {
    private class TestApplication : Application()

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupInjektBindings() {
            runCatching { Injekt.get<Application>() }
                .getOrElse {
                    val filesDir = File(System.getProperty("java.io.tmpdir"), "novel-screen-model-test-files")
                        .apply { mkdirs() }
                    val cacheDir = File(System.getProperty("java.io.tmpdir"), "novel-screen-model-test-cache")
                        .apply { mkdirs() }
                    val application = mockk<Application>(relaxed = true)
                    every { application.filesDir } returns filesDir
                    every { application.cacheDir } returns cacheDir
                    Injekt.addSingleton(fullType<Application>(), application)
                }
            runCatching { Injekt.get<Json>() }
                .getOrElse {
                    Injekt.addSingleton(fullType<Json>(), Json { encodeDefaults = true })
                }
            runCatching { Injekt.get<NovelRatingFetcher>() }
                .getOrElse {
                    Injekt.addSingleton(fullType<NovelRatingFetcher>(), mockk<NovelRatingFetcher>(relaxed = true))
                }
            runCatching { Injekt.get<TranslationQueueManager>() }
                .getOrElse {
                    val translationQueueManager = mockk<TranslationQueueManager>(relaxed = true)
                    every {
                        translationQueueManager.queue
                    } returns MutableStateFlow<List<TranslationQueueItem>>(emptyList())
                    every {
                        translationQueueManager.activeTranslation
                    } returns MutableStateFlow<TranslationQueueItem?>(null)
                    Injekt.addSingleton(fullType<TranslationQueueManager>(), translationQueueManager)
                }
            runCatching { Injekt.get<TrackNovelChapter>() }
                .getOrElse {
                    Injekt.addSingleton(fullType<TrackNovelChapter>(), mockk<TrackNovelChapter>(relaxed = true))
                }
            runCatching { Injekt.get<TrackPreferences>() }
                .getOrElse {
                    val trackPreferences = mockk<TrackPreferences>(relaxed = true)
                    every { trackPreferences.autoUpdateTrackOnMarkRead() } returns object :
                        tachiyomi.core.common.preference.Preference<AutoTrackState> {
                        override fun get() = AutoTrackState.ALWAYS
                        override fun set(value: AutoTrackState) {}
                        override fun isSet() = true
                        override fun defaultValue() = AutoTrackState.ALWAYS
                        override fun key() = "pref_auto_update_manga_on_mark_read"
                        override fun changes() = kotlinx.coroutines.flow.MutableStateFlow(get())
                        override fun stateIn(
                            scope: kotlinx.coroutines.CoroutineScope,
                        ) = kotlinx.coroutines.flow.MutableStateFlow(get())
                        override fun delete() {}
                    }
                    every { trackPreferences.autoUpdateTrack() } returns object :
                        tachiyomi.core.common.preference.Preference<Boolean> {
                        override fun get() = true
                        override fun set(value: Boolean) {}
                        override fun isSet() = true
                        override fun defaultValue() = true
                        override fun key() = "pref_auto_update_manga_sync_key"
                        override fun changes() = kotlinx.coroutines.flow.MutableStateFlow(get())
                        override fun stateIn(
                            scope: kotlinx.coroutines.CoroutineScope,
                        ) = kotlinx.coroutines.flow.MutableStateFlow(get())
                        override fun delete() {}
                    }
                    Injekt.addSingleton(fullType<TrackPreferences>(), trackPreferences)
                }
        }

        @JvmStatic
        @BeforeAll
        fun setupMainDispatcher() {
            Dispatchers.setMain(Dispatchers.Unconfined)
        }

        @JvmStatic
        @AfterAll
        fun resetMainDispatcher() {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `toggleFavorite updates repository`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, favorite = false, title = "Novel", initialized = true)
            val novelRepository = FakeNovelRepository(novel)
            val getNovelWithChapters = GetNovelWithChapters(
                novelRepository = novelRepository,
                novelChapterRepository = object :
                    tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository {
                    override suspend fun addAllChapters(
                        chapters: List<NovelChapter>,
                    ): List<NovelChapter> = chapters
                    override suspend fun updateChapter(
                        chapterUpdate: tachiyomi.domain.items.novelchapter.model.NovelChapterUpdate,
                    ) = Unit
                    override suspend fun updateAllChapters(
                        chapterUpdates: List<tachiyomi.domain.items.novelchapter.model.NovelChapterUpdate>,
                    ) = Unit
                    override suspend fun removeChaptersWithIds(chapterIds: List<Long>) = Unit
                    override suspend fun getChapterByNovelId(
                        novelId: Long,
                        applyScanlatorFilter: Boolean,
                    ): List<NovelChapter> = emptyList()
                    override suspend fun getScanlatorsByNovelId(novelId: Long): List<String> = emptyList()
                    override fun getScanlatorsByNovelIdAsFlow(novelId: Long): Flow<List<String>> =
                        MutableStateFlow(emptyList())
                    override suspend fun getBookmarkedChaptersByNovelId(novelId: Long): List<NovelChapter> = emptyList()
                    override suspend fun getChapterById(id: Long): NovelChapter? = null
                    override suspend fun getChapterByNovelIdAsFlow(
                        novelId: Long,
                        applyScanlatorFilter: Boolean,
                    ): Flow<List<NovelChapter>> = MutableStateFlow(emptyList())
                    override suspend fun getChapterByUrlAndNovelId(url: String, novelId: Long): NovelChapter? = null
                },
            )
            val updateNovel = UpdateNovel(novelRepository)
            val sourceManager = FakeNovelSourceManager()
            val preferenceStore = object : tachiyomi.core.common.preference.PreferenceStore {
                override fun getString(key: String, defaultValue: String) = FakePreference(defaultValue)
                override fun getLong(key: String, defaultValue: Long) = FakePreference(defaultValue)
                override fun getInt(key: String, defaultValue: Int) = FakePreference(defaultValue)
                override fun getFloat(key: String, defaultValue: Float) = FakePreference(defaultValue)
                override fun getBoolean(key: String, defaultValue: Boolean) = FakePreference(defaultValue)
                override fun getStringSet(key: String, defaultValue: Set<String>) = FakePreference(defaultValue)
                override fun <T> getObject(
                    key: String,
                    defaultValue: T,
                    serializer: (T) -> String,
                    deserializer: (String) -> T,
                ) = FakePreference(defaultValue)
                override fun getAll(): Map<String, *> = emptyMap<String, Any>()
            }
            val basePreferences = BasePreferences(
                context = mockk<Context>(relaxed = true),
                preferenceStore = preferenceStore,
            )
            val chapterRepository = object : tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository {
                override suspend fun addAllChapters(chapters: List<NovelChapter>): List<NovelChapter> = chapters
                override suspend fun updateChapter(
                    chapterUpdate: tachiyomi.domain.items.novelchapter.model.NovelChapterUpdate,
                ) = Unit
                override suspend fun updateAllChapters(
                    chapterUpdates: List<tachiyomi.domain.items.novelchapter.model.NovelChapterUpdate>,
                ) = Unit
                override suspend fun removeChaptersWithIds(chapterIds: List<Long>) = Unit
                override suspend fun getChapterByNovelId(
                    novelId: Long,
                    applyScanlatorFilter: Boolean,
                ): List<NovelChapter> = emptyList()
                override suspend fun getScanlatorsByNovelId(novelId: Long): List<String> = emptyList()
                override fun getScanlatorsByNovelIdAsFlow(novelId: Long): Flow<List<String>> =
                    MutableStateFlow(emptyList())
                override suspend fun getBookmarkedChaptersByNovelId(novelId: Long): List<NovelChapter> = emptyList()
                override suspend fun getChapterById(id: Long): NovelChapter? = null
                override suspend fun getChapterByNovelIdAsFlow(
                    novelId: Long,
                    applyScanlatorFilter: Boolean,
                ): Flow<List<NovelChapter>> = MutableStateFlow(emptyList())
                override suspend fun getChapterByUrlAndNovelId(url: String, novelId: Long): NovelChapter? = null
            }
            val sync = SyncNovelChaptersWithSource(
                novelChapterRepository = object :
                    tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository {
                    override suspend fun addAllChapters(
                        chapters: List<NovelChapter>,
                    ): List<NovelChapter> = chapters
                    override suspend fun updateChapter(
                        chapterUpdate: tachiyomi.domain.items.novelchapter.model.NovelChapterUpdate,
                    ) = Unit
                    override suspend fun updateAllChapters(
                        chapterUpdates: List<tachiyomi.domain.items.novelchapter.model.NovelChapterUpdate>,
                    ) = Unit
                    override suspend fun removeChaptersWithIds(chapterIds: List<Long>) = Unit
                    override suspend fun getChapterByNovelId(
                        novelId: Long,
                        applyScanlatorFilter: Boolean,
                    ): List<NovelChapter> = emptyList()
                    override suspend fun getScanlatorsByNovelId(novelId: Long): List<String> = emptyList()
                    override fun getScanlatorsByNovelIdAsFlow(novelId: Long): Flow<List<String>> =
                        MutableStateFlow(emptyList())
                    override suspend fun getBookmarkedChaptersByNovelId(novelId: Long): List<NovelChapter> = emptyList()
                    override suspend fun getChapterById(id: Long): NovelChapter? = null
                    override suspend fun getChapterByNovelIdAsFlow(
                        novelId: Long,
                        applyScanlatorFilter: Boolean,
                    ): Flow<List<NovelChapter>> = MutableStateFlow(emptyList())
                    override suspend fun getChapterByUrlAndNovelId(url: String, novelId: Long): NovelChapter? = null
                },
                shouldUpdateDbNovelChapter =
                tachiyomi.domain.items.novelchapter.interactor.ShouldUpdateDbNovelChapter(),
                updateNovel = UpdateNovel(
                    novelRepository = object : tachiyomi.domain.entries.novel.repository.NovelRepository {
                        override suspend fun getNovelById(id: Long): Novel = Novel.create()
                        override suspend fun getNovelByIdAsFlow(id: Long) = MutableStateFlow(Novel.create())
                        override suspend fun getNovelByUrlAndSourceId(url: String, sourceId: Long): Novel? = null
                        override fun getNovelByUrlAndSourceIdAsFlow(url: String, sourceId: Long) =
                            MutableStateFlow<Novel?>(null)
                        override suspend fun getNovelFavorites(): List<Novel> = emptyList()
                        override suspend fun getReadNovelNotInLibrary(): List<Novel> = emptyList()
                        override suspend fun getLibraryNovel() =
                            emptyList<tachiyomi.domain.library.novel.LibraryNovel>()
                        override fun getLibraryNovelAsFlow() =
                            MutableStateFlow(emptyList<tachiyomi.domain.library.novel.LibraryNovel>())
                        override fun getNovelFavoritesBySourceId(sourceId: Long) =
                            MutableStateFlow(emptyList<Novel>())
                        override suspend fun insertNovel(novel: Novel): Long? = null
                        override suspend fun updateNovel(update: NovelUpdate): Boolean = true
                        override suspend fun updateAllNovel(novelUpdates: List<NovelUpdate>): Boolean = true
                        override suspend fun resetNovelViewerFlags(): Boolean = true
                    },
                ),
                libraryPreferences = tachiyomi.domain.library.service.LibraryPreferences(
                    preferenceStore = preferenceStore,
                ),
            )
            val lifecycleOwner = FakeLifecycleOwner()
            val libraryPreferences = tachiyomi.domain.library.service.LibraryPreferences(
                preferenceStore = preferenceStore,
            )
            val trackerManager = mockk<TrackerManager>().also { manager ->
                every { manager.loggedInTrackersFlow() } returns MutableStateFlow(emptyList())
                every { manager.loggedInNovelTrackersFlow() } returns MutableStateFlow(emptyList())
            }
            val getNovelTracks = mockk<GetNovelTracks>().also { tracks ->
                every { tracks.subscribe(any()) } returns MutableStateFlow(
                    emptyList<tachiyomi.domain.track.novel.model.NovelTrack>(),
                )
            }
            val novelCategoryRepository = object : tachiyomi.domain.category.novel.repository.NovelCategoryRepository {
                override suspend fun getCategory(id: Long) = null
                override suspend fun getCategories() = emptyList<tachiyomi.domain.category.novel.model.NovelCategory>()
                override suspend fun getVisibleCategories() =
                    emptyList<tachiyomi.domain.category.novel.model.NovelCategory>()
                override suspend fun getCategoriesByNovelId(novelId: Long) =
                    emptyList<tachiyomi.domain.category.novel.model.NovelCategory>()
                override suspend fun getVisibleCategoriesByNovelId(novelId: Long) =
                    emptyList<tachiyomi.domain.category.novel.model.NovelCategory>()
                override fun getCategoriesAsFlow() = MutableStateFlow(
                    emptyList<tachiyomi.domain.category.novel.model.NovelCategory>(),
                )
                override fun getVisibleCategoriesAsFlow() = MutableStateFlow(
                    emptyList<tachiyomi.domain.category.novel.model.NovelCategory>(),
                )
                override suspend fun insertCategory(
                    category: tachiyomi.domain.category.novel.model.NovelCategory,
                ) = null
                override suspend fun updatePartialCategory(
                    update: tachiyomi.domain.category.novel.model.NovelCategoryUpdate,
                ) {
                }
                override suspend fun updateAllFlags(flags: Long) {}
                override suspend fun deleteCategory(categoryId: Long) {}
                override suspend fun setNovelCategories(novelId: Long, categoryIds: List<Long>) {}
            }

            val screenModel = NovelScreenModel(
                lifecycle = lifecycleOwner.lifecycle,
                novelId = 1L,
                basePreferences = basePreferences,
                libraryPreferences = libraryPreferences,
                getNovelWithChapters = getNovelWithChapters,
                updateNovel = updateNovel,
                syncNovelChaptersWithSource = sync,
                novelChapterRepository = chapterRepository,
                novelHistoryRepository = FakeNovelHistoryRepository(),
                setNovelChapterFlags = SetNovelChapterFlags(novelRepository),
                setNovelDefaultChapterFlags = SetNovelDefaultChapterFlags(
                    libraryPreferences = libraryPreferences,
                    setNovelChapterFlags = SetNovelChapterFlags(novelRepository),
                    getFavorites = GetNovelFavorites(novelRepository),
                ),
                getAvailableNovelScanlators = GetAvailableNovelScanlators(chapterRepository),
                getNovelScanlatorChapterCounts = GetNovelScanlatorChapterCounts(chapterRepository),
                getNovelExcludedScanlators = GetNovelExcludedScanlators(
                    mockk<NovelDatabaseHandler>().also { handler ->
                        coEvery { handler.awaitList<String>(any(), any()) } returns emptyList()
                        every { handler.subscribeToList<String>(any()) } returns MutableStateFlow(emptyList())
                    },
                ),
                setNovelExcludedScanlators = SetNovelExcludedScanlators(
                    mockk<NovelDatabaseHandler>(relaxed = true),
                ),
                getNovelCategories = GetNovelCategories(novelCategoryRepository),
                setNovelCategories = SetNovelCategories(novelCategoryRepository),
                sourceManager = sourceManager,
                trackerManager = trackerManager,
                getTracks = getNovelTracks,
                novelReaderPreferences = eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences(
                    preferenceStore = preferenceStore,
                    json = Json { encodeDefaults = true },
                ),
            )

            try {
                withTimeout(1_000) {
                    while (screenModel.state.value is NovelScreenModel.State.Loading) {
                        yield()
                    }
                }

                novelRepository.allUpdates.clear()
                screenModel.toggleFavorite()

                withTimeout(1_000) {
                    while (novelRepository.allUpdates.none { it.favorite == true }) {
                        yield()
                    }
                }

                novelRepository.allUpdates.any { it.favorite == true } shouldBe true
            } finally {
                screenModel.onDispose()
                repeat(5) { yield() }
            }
            Unit
        }
    }

    @Test
    fun `in progress unread chapter wins over earlier read chapters`() {
        runBlocking {
            val novel = novelForResumeTests(101L)
            val chapter1 = novelChapter(id = 1L, novelId = novel.id, chapterNumber = 1.0, read = true)
            val chapter2 = novelChapter(
                id = 2L,
                novelId = novel.id,
                chapterNumber = 2.0,
                read = false,
                lastPageRead = 8L,
            )
            val chapter3 = novelChapter(id = 3L, novelId = novel.id, chapterNumber = 3.0, read = false)
            val screenModel = createResumeScreenModel(novel, listOf(chapter1, chapter2, chapter3))

            try {
                awaitResumeScreenModel(screenModel)
                screenModel.getContinueChapter()?.id shouldBe chapter2.id
            } finally {
                screenModel.onDispose()
            }
        }
    }

    @Test
    fun `next unread chapter follows the last touched chapter`() {
        runBlocking {
            val novel = novelForResumeTests(102L)
            val chapter1 = novelChapter(id = 1L, novelId = novel.id, chapterNumber = 1.0, read = true)
            val chapter2 = novelChapter(id = 2L, novelId = novel.id, chapterNumber = 2.0, read = true)
            val chapter3 = novelChapter(id = 3L, novelId = novel.id, chapterNumber = 3.0, read = false)
            val screenModel = createResumeScreenModel(novel, listOf(chapter1, chapter2, chapter3))

            try {
                awaitResumeScreenModel(screenModel)
                screenModel.getResumeOrNextChapter()?.id shouldBe chapter3.id
            } finally {
                screenModel.onDispose()
            }
        }
    }

    @Test
    fun `fully read novel resumes the last touched chapter`() {
        runBlocking {
            val novel = novelForResumeTests(103L)
            val chapter1 = novelChapter(id = 1L, novelId = novel.id, chapterNumber = 1.0, read = true)
            val chapter2 = novelChapter(id = 2L, novelId = novel.id, chapterNumber = 2.0, read = true)
            val screenModel = createResumeScreenModel(novel, listOf(chapter1, chapter2))

            try {
                awaitResumeScreenModel(screenModel)
                screenModel.getResumeOrNextChapter()?.id shouldBe chapter2.id
            } finally {
                screenModel.onDispose()
            }
        }
    }

    @Test
    fun `history chapter id wins even when later unread chapters exist`() {
        runBlocking {
            val novel = novelForResumeTests(108L)
            val chapter1 = novelChapter(id = 1L, novelId = novel.id, chapterNumber = 1.0, read = false)
            val chapter2 = novelChapter(id = 2L, novelId = novel.id, chapterNumber = 2.0, read = true)
            val chapter3 = novelChapter(id = 3L, novelId = novel.id, chapterNumber = 3.0, read = false)
            val historyRepository = FakeNovelHistoryRepository(
                mapOf(
                    novel.id to listOf(
                        NovelHistory.create().copy(
                            chapterId = chapter2.id,
                            readAt = Date(2_000L),
                            readDuration = 0L,
                        ),
                    ),
                ),
            )
            val screenModel = createResumeScreenModel(
                novel = novel,
                chapters = listOf(chapter1, chapter2, chapter3),
                novelHistoryRepository = historyRepository,
            )

            try {
                awaitResumeScreenModel(screenModel)
                screenModel.getContinueChapter()?.id shouldBe chapter2.id
            } finally {
                screenModel.onDispose()
            }
        }
    }

    @Test
    fun `chapter action states appear before translated download checks finish`() {
        runBlocking {
            val novel = novelForResumeTests(105L)
            val chapters = listOf(
                novelChapter(id = 1L, novelId = novel.id, chapterNumber = 1.0, read = false),
            )
            val blocker = CompletableDeferred<Unit>()
            val translatedDownloadManager = mockk<NovelTranslatedDownloadManager>()
            every {
                translatedDownloadManager.isTranslatedChapterDownloaded(any(), any(), any())
            } answers {
                runBlocking { blocker.await() }
                false
            }

            val screenModel = createResumeScreenModel(
                novel = novel,
                chapters = chapters,
                novelTranslatedDownloadManager = translatedDownloadManager,
                geminiEnabled = true,
            )

            try {
                withTimeout(500) {
                    while (screenModel.state.value is NovelScreenModel.State.Loading) {
                        yield()
                    }
                }

                val state = screenModel.state.value as NovelScreenModel.State.Success
                state.chapterActionStates[1L]?.showGeminiRow shouldBe true
                state.chapterActionStates[1L]?.translateState shouldBe NovelChapterActionIconState.Neutral
                state.chapterActionStates[1L]?.downloadTranslatedState shouldBe NovelChapterActionIconState.Neutral
            } finally {
                blocker.complete(Unit)
                screenModel.onDispose()
            }
        }
    }

    @Test
    fun `chapter action states refresh when chapter ids change`() {
        runBlocking {
            val novel = novelForResumeTests(106L)
            val initialChapters = listOf(
                novelChapter(id = 1L, novelId = novel.id, chapterNumber = 1.0, read = false),
            )
            val updatedChapters = listOf(
                novelChapter(id = 11L, novelId = novel.id, chapterNumber = 1.0, read = false),
            )
            val chapterRepository = FakeNovelChapterRepository(initialChapters)
            val screenModel = createResumeScreenModel(
                novel = novel,
                chapters = initialChapters,
                chapterRepository = chapterRepository,
                geminiEnabled = true,
            )

            try {
                awaitResumeScreenModel(screenModel)
                chapterRepository.updateChapters(updatedChapters)

                withTimeout(1_000) {
                    while ((screenModel.state.value as? NovelScreenModel.State.Success)
                            ?.chapterActionStates
                            ?.get(updatedChapters.first().id)
                            ?.showGeminiRow != true
                    ) {
                        yield()
                    }
                }

                val state = screenModel.state.value as NovelScreenModel.State.Success
                state.chapterActionStates[updatedChapters.first().id]?.showGeminiRow shouldBe true
                state.chapterActionStates[updatedChapters.first().id]?.translateState shouldBe
                    NovelChapterActionIconState.Neutral
                state.chapterActionStates[updatedChapters.first().id]?.downloadTranslatedState shouldBe
                    NovelChapterActionIconState.Neutral
            } finally {
                screenModel.onDispose()
            }
        }
    }

    @Test
    fun `queued translation updates icon immediately`() {
        runBlocking {
            val novel = novelForResumeTests(108L)
            val chapters = listOf(
                novelChapter(id = 1L, novelId = novel.id, chapterNumber = 1.0, read = false),
            )
            val translatedDownloadManager = mockk<NovelTranslatedDownloadManager>()
            every {
                translatedDownloadManager.isTranslatedChapterDownloaded(any(), any(), any())
            } returns false

            val queueFlow = MutableStateFlow<List<TranslationQueueItem>>(emptyList())
            val activeTranslationFlow = MutableStateFlow<TranslationQueueItem?>(null)
            val translationQueueManager = mockk<TranslationQueueManager>(relaxed = true).also { manager ->
                every { manager.queue } returns queueFlow
                every { manager.activeTranslation } returns activeTranslationFlow
            }

            val screenModel = createResumeScreenModel(
                novel = novel,
                chapters = chapters,
                novelTranslatedDownloadManager = translatedDownloadManager,
                geminiEnabled = true,
                translationQueueManager = translationQueueManager,
            )

            try {
                awaitResumeScreenModel(screenModel)

                queueFlow.value = listOf(
                    TranslationQueueItem(
                        id = 1L,
                        chapterId = 1L,
                        novelId = novel.id,
                        status = TranslationStatus.PENDING,
                        progress = 0,
                        errorMessage = null,
                        retryCount = 0,
                        createdAt = 1L,
                        updatedAt = 1L,
                    ),
                )

                withTimeout(5_000) {
                    while ((screenModel.state.value as? NovelScreenModel.State.Success)
                            ?.chapterActionStates
                            ?.get(1L)
                            ?.translateState != NovelChapterActionIconState.InProgress
                    ) {
                        yield()
                    }
                }

                val state = screenModel.state.value as NovelScreenModel.State.Success
                state.chapterActionStates[1L]?.translateState shouldBe NovelChapterActionIconState.InProgress
            } finally {
                screenModel.onDispose()
            }
        }
    }

    @Test
    fun `active translation keeps icon in progress while queue clears`() {
        runBlocking {
            val novel = novelForResumeTests(109L)
            val chapter = novelChapter(id = 1L, novelId = novel.id, chapterNumber = 1.0, read = false)
            val blocker = CompletableDeferred<Unit>()
            val translatedDownloadManager = mockk<NovelTranslatedDownloadManager>()
            every {
                translatedDownloadManager.isTranslatedChapterDownloaded(any(), any(), any())
            } answers {
                runBlocking { blocker.await() }
                false
            }

            val queueFlow = MutableStateFlow<List<TranslationQueueItem>>(emptyList())
            val activeTranslationFlow = MutableStateFlow<TranslationQueueItem?>(null)
            val translationQueueManager = mockk<TranslationQueueManager>(relaxed = true).also { manager ->
                every { manager.queue } returns queueFlow
                every { manager.activeTranslation } returns activeTranslationFlow
            }

            val screenModel = createResumeScreenModel(
                novel = novel,
                chapters = listOf(chapter),
                novelTranslatedDownloadManager = translatedDownloadManager,
                geminiEnabled = true,
                translationQueueManager = translationQueueManager,
            )

            try {
                awaitResumeScreenModel(screenModel)

                val queuedItem = TranslationQueueItem(
                    id = 1L,
                    chapterId = chapter.id,
                    novelId = novel.id,
                    status = TranslationStatus.PENDING,
                    progress = 0,
                    errorMessage = null,
                    retryCount = 0,
                    createdAt = 1L,
                    updatedAt = 1L,
                )
                queueFlow.value = listOf(queuedItem)

                withTimeout(5_000) {
                    while ((screenModel.state.value as? NovelScreenModel.State.Success)
                            ?.chapterActionStates
                            ?.get(chapter.id)
                            ?.translateState != NovelChapterActionIconState.InProgress
                    ) {
                        yield()
                    }
                }

                activeTranslationFlow.value = queuedItem
                queueFlow.value = emptyList()

                withTimeout(5_000) {
                    while ((screenModel.state.value as? NovelScreenModel.State.Success)
                            ?.chapterActionStates
                            ?.get(chapter.id)
                            ?.translateState != NovelChapterActionIconState.InProgress
                    ) {
                        yield()
                    }
                }

                val state = screenModel.state.value as NovelScreenModel.State.Success
                state.chapterActionStates[chapter.id]?.translateState shouldBe NovelChapterActionIconState.InProgress
            } finally {
                blocker.complete(Unit)
                screenModel.onDispose()
            }
        }
    }

    @Test
    fun `translation cache chapter ids are loaded in bulk`() {
        NovelReaderTranslationDiskCacheStore.clear()
        try {
            NovelReaderTranslationDiskCacheStore.put(
                GeminiTranslationCacheEntry(
                    chapterId = 11L,
                    translatedByIndex = mapOf(0 to "hello"),
                    model = "gemini-3.1-flash-lite-preview",
                    sourceLang = "English",
                    targetLang = "Russian",
                    promptMode = eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode.ADULT_18,
                    stylePreset = eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationStylePreset.PROFESSIONAL,
                ),
            )
            NovelReaderTranslationDiskCacheStore.put(
                GeminiTranslationCacheEntry(
                    chapterId = 42L,
                    translatedByIndex = mapOf(0 to "world"),
                    model = "gemini-3.1-flash-lite-preview",
                    sourceLang = "English",
                    targetLang = "Russian",
                    promptMode = eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode.ADULT_18,
                    stylePreset = eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationStylePreset.PROFESSIONAL,
                ),
            )

            NovelReaderTranslationDiskCacheStore.chapterIds() shouldBe setOf(11L, 42L)
        } finally {
            NovelReaderTranslationDiskCacheStore.clear()
        }
    }

    @Test
    fun `chapter action states refresh after scanlator selection changes chapters`() {
        runBlocking {
            val novel = novelForResumeTests(107L)
            val initialChapters = listOf(
                novelChapter(id = 1L, novelId = novel.id, chapterNumber = 1.0, read = false).copy(
                    scanlator = "Alpha",
                ),
                novelChapter(id = 2L, novelId = novel.id, chapterNumber = 1.0, read = false).copy(
                    scanlator = "Beta",
                ),
            )
            val updatedChapters = listOf(
                novelChapter(id = 11L, novelId = novel.id, chapterNumber = 1.0, read = false).copy(
                    scanlator = "Alpha",
                ),
            )
            val currentChapters = MutableStateFlow(initialChapters)
            val getNovelWithChapters = mockk<GetNovelWithChapters>()
            coEvery { getNovelWithChapters.awaitNovel(novel.id) } returns novel
            coEvery { getNovelWithChapters.awaitChapters(novel.id, any()) } answers {
                currentChapters.value
            }
            coEvery { getNovelWithChapters.subscribe(novel.id, any()) } returns
                MutableStateFlow(novel to initialChapters)

            val screenModel = createResumeScreenModel(
                novel = novel,
                chapters = initialChapters,
                getNovelWithChaptersOverride = getNovelWithChapters,
                geminiEnabled = true,
                excludedScanlators = setOf("Beta"),
            )

            try {
                awaitResumeScreenModel(screenModel)
                withTimeout(1_000) {
                    while ((screenModel.state.value as? NovelScreenModel.State.Success)
                            ?.availableScanlators
                            ?.containsAll(setOf("Alpha", "Beta")) != true
                    ) {
                        yield()
                    }
                }

                currentChapters.value = updatedChapters
                screenModel.selectScanlator("Beta")

                withTimeout(1_000) {
                    while ((screenModel.state.value as? NovelScreenModel.State.Success)
                            ?.chapterActionStates
                            ?.get(updatedChapters.first().id)
                            ?.showGeminiRow != true
                    ) {
                        yield()
                    }
                }

                val state = screenModel.state.value as NovelScreenModel.State.Success
                state.chapterActionStates[updatedChapters.first().id]?.showGeminiRow shouldBe true
                state.chapterActionStates[updatedChapters.first().id]?.translateState shouldBe
                    NovelChapterActionIconState.Neutral
                state.chapterActionStates[updatedChapters.first().id]?.downloadTranslatedState shouldBe
                    NovelChapterActionIconState.Neutral
            } finally {
                screenModel.onDispose()
            }
        }
    }

    @Test
    fun `resume selection follows source order when chapter numbers are out of order`() {
        runBlocking {
            val novel = novelForResumeTests(104L)
            val chapter1 = novelChapter(
                id = 1L,
                novelId = novel.id,
                sourceOrder = 2L,
                chapterNumber = 1.0,
                read = true,
            )
            val chapter2 = novelChapter(
                id = 2L,
                novelId = novel.id,
                sourceOrder = 0L,
                chapterNumber = 10.0,
                read = true,
            )
            val chapter3 = novelChapter(
                id = 3L,
                novelId = novel.id,
                sourceOrder = 1L,
                chapterNumber = 20.0,
                read = false,
            )
            val screenModel = createResumeScreenModel(novel, listOf(chapter1, chapter2, chapter3))

            try {
                awaitResumeScreenModel(screenModel)
                screenModel.getResumeOrNextChapter()?.id shouldBe chapter1.id
            } finally {
                screenModel.onDispose()
            }
        }
    }

    @Test
    fun `cached novel state does not restore saved chapter list scroll position`() {
        runBlocking {
            val novel = novelForResumeTests(104L)
            val chapters = listOf(
                novelChapter(id = 1L, novelId = novel.id, chapterNumber = 1.0, read = false),
                novelChapter(id = 2L, novelId = novel.id, chapterNumber = 2.0, read = false),
            )
            val firstModel = createResumeScreenModel(novel, chapters)

            try {
                awaitResumeScreenModel(firstModel)
                firstModel.saveScrollPosition(index = 12, offset = 34)
            } finally {
                firstModel.onDispose()
            }

            val restoredModel = createResumeScreenModel(novel, chapters)

            try {
                awaitResumeScreenModel(restoredModel)
                val restoredState = restoredModel.state.value as NovelScreenModel.State.Success
                restoredState.scrollIndex shouldBe 0
                restoredState.scrollOffset shouldBe 0
            } finally {
                restoredModel.onDispose()
            }
        }
    }

    @Test
    fun `chapter status updates do not rescan downloaded ids when chapter ids are unchanged`() {
        runBlocking {
            val novel = novelForResumeTests(105L)
            val initialChapters = listOf(
                novelChapter(id = 1L, novelId = novel.id, chapterNumber = 1.0, read = false),
                novelChapter(id = 2L, novelId = novel.id, chapterNumber = 2.0, read = false),
            )
            val chapterRepository = FakeNovelChapterRepository(initialChapters)
            val downloadCacheChanges = MutableSharedFlow<NovelDownloadCacheEvent>(replay = 1, extraBufferCapacity = 1)
            var resolveDownloadedIdsCalls = 0

            val screenModel = createResumeScreenModel(
                novel = novel,
                chapters = initialChapters,
                chapterRepository = chapterRepository,
                downloadCacheChanges = downloadCacheChanges,
                downloadQueueState = MutableStateFlow(NovelDownloadQueueState()),
                resolveDownloadedChapterIds = { _, chapters ->
                    resolveDownloadedIdsCalls++
                    chapters
                        .asSequence()
                        .map { it.id }
                        .filter { it == 1L }
                        .toSet()
                },
            )

            try {
                awaitResumeScreenModel(screenModel)
                downloadCacheChanges.emit(NovelDownloadCacheEvent.InvalidateAll)
                withTimeout(1_000) {
                    while ((screenModel.state.value as? NovelScreenModel.State.Success)?.downloadedChapterIds !=
                        setOf(1L)
                    ) {
                        yield()
                    }
                }

                val callsAfterInit = resolveDownloadedIdsCalls
                chapterRepository.updateChapters(
                    initialChapters.map { chapter ->
                        if (chapter.id == 2L) {
                            chapter.copy(read = true)
                        } else {
                            chapter
                        }
                    },
                )

                withTimeout(1_000) {
                    while ((screenModel.state.value as? NovelScreenModel.State.Success)
                            ?.chapters
                            ?.firstOrNull { it.id == 2L }
                            ?.read != true
                    ) {
                        yield()
                    }
                }

                resolveDownloadedIdsCalls shouldBe callsAfterInit
            } finally {
                screenModel.onDispose()
            }
        }
    }

    @Test
    fun `toggle chapter download returns before enqueue finishes`() {
        runBlocking {
            val novel = novelForResumeTests(201L)
            val chapters = listOf(
                novelChapter(id = 1L, novelId = novel.id, chapterNumber = 1.0, read = false),
            )
            val enqueueStarted = CompletableDeferred<Unit>()
            val allowEnqueueToFinish = CompletableDeferred<Unit>()
            val methodReturned = CompletableDeferred<Unit>()
            val screenModel = createResumeScreenModel(
                novel = novel,
                chapters = chapters,
                enqueueOriginal = { _, _ ->
                    enqueueStarted.complete(Unit)
                    runBlocking {
                        allowEnqueueToFinish.await()
                    }
                    1
                },
            )

            try {
                awaitResumeScreenModel(screenModel)

                val worker = Thread {
                    screenModel.toggleChapterDownload(1L)
                    methodReturned.complete(Unit)
                }
                worker.start()

                withTimeout(1_000) {
                    enqueueStarted.await()
                }
                methodReturned.isCompleted shouldBe true

                allowEnqueueToFinish.complete(Unit)
                worker.join(1_000)
            } finally {
                screenModel.onDispose()
            }
        }
    }

    @Test
    fun `toggle chapter download does not show snackbar queue status`() {
        runBlocking {
            val novel = novelForResumeTests(2011L)
            val chapters = listOf(
                novelChapter(id = 1L, novelId = novel.id, chapterNumber = 1.0, read = false),
            )
            val snackbarHostState = SnackbarHostState()
            val screenModel = createResumeScreenModel(
                novel = novel,
                chapters = chapters,
                enqueueOriginal = { _, _ -> 1 },
                snackbarHostState = snackbarHostState,
            )

            try {
                awaitResumeScreenModel(screenModel)

                screenModel.toggleChapterDownload(1L)
                delay(250)

                snackbarHostState.currentSnackbarData shouldBe null
            } finally {
                screenModel.onDispose()
            }
        }
    }

    @Test
    fun `download selected chapters returns before enqueue finishes`() {
        runBlocking {
            val novel = novelForResumeTests(202L)
            val chapters = listOf(
                novelChapter(id = 1L, novelId = novel.id, chapterNumber = 1.0, read = false),
                novelChapter(id = 2L, novelId = novel.id, chapterNumber = 2.0, read = false),
            )
            val enqueueStarted = CompletableDeferred<Unit>()
            val allowEnqueueToFinish = CompletableDeferred<Unit>()
            val methodReturned = CompletableDeferred<Unit>()
            val screenModel = createResumeScreenModel(
                novel = novel,
                chapters = chapters,
                enqueueOriginal = { _, queuedChapters ->
                    queuedChapters.map { it.id }.toSet() shouldBe setOf(1L, 2L)
                    enqueueStarted.complete(Unit)
                    runBlocking {
                        allowEnqueueToFinish.await()
                    }
                    queuedChapters.size
                },
            )

            try {
                awaitResumeScreenModel(screenModel)
                screenModel.toggleSelection(1L)
                screenModel.toggleSelection(2L)

                val worker = Thread {
                    screenModel.downloadSelectedChapters()
                    methodReturned.complete(Unit)
                }
                worker.start()

                withTimeout(1_000) {
                    enqueueStarted.await()
                }
                methodReturned.isCompleted shouldBe true

                allowEnqueueToFinish.complete(Unit)
                worker.join(1_000)
            } finally {
                screenModel.onDispose()
            }
        }
    }

    @Test
    fun `run download action returns before enqueue finishes`() {
        runBlocking {
            val novel = novelForResumeTests(203L)
            val chapters = listOf(
                novelChapter(id = 1L, novelId = novel.id, chapterNumber = 1.0, read = false),
                novelChapter(id = 2L, novelId = novel.id, chapterNumber = 2.0, read = false),
                novelChapter(id = 3L, novelId = novel.id, chapterNumber = 3.0, read = false),
            )
            val enqueueStarted = CompletableDeferred<Unit>()
            val allowEnqueueToFinish = CompletableDeferred<Unit>()
            val methodReturned = CompletableDeferred<Unit>()
            val screenModel = createResumeScreenModel(
                novel = novel,
                chapters = chapters,
                enqueueOriginal = { _, queuedChapters ->
                    queuedChapters.map { it.id }.toSet() shouldBe setOf(1L, 2L, 3L)
                    enqueueStarted.complete(Unit)
                    runBlocking {
                        allowEnqueueToFinish.await()
                    }
                    queuedChapters.size
                },
            )

            try {
                awaitResumeScreenModel(screenModel)

                val worker = Thread {
                    screenModel.runDownloadAction(NovelDownloadAction.ALL)
                    methodReturned.complete(Unit)
                }
                worker.start()

                withTimeout(1_000) {
                    enqueueStarted.await()
                }
                methodReturned.isCompleted shouldBe true

                allowEnqueueToFinish.complete(Unit)
                worker.join(1_000)
            } finally {
                screenModel.onDispose()
            }
        }
    }

    @Test
    fun `targeted download cache update changes downloaded ids without full rescan`() {
        runBlocking {
            val novel = novelForResumeTests(301L)
            val chapters = listOf(
                novelChapter(id = 1L, novelId = novel.id, chapterNumber = 1.0, read = false),
                novelChapter(id = 2L, novelId = novel.id, chapterNumber = 2.0, read = false),
            )
            val downloadCacheChanges = MutableSharedFlow<NovelDownloadCacheEvent>(replay = 1, extraBufferCapacity = 1)
            var resolveDownloadedIdsCalls = 0
            val screenModel = createResumeScreenModel(
                novel = novel,
                chapters = chapters,
                downloadCacheChanges = downloadCacheChanges,
                resolveDownloadedChapterIds = { _, resolvedChapters ->
                    resolveDownloadedIdsCalls++
                    resolvedChapters
                        .asSequence()
                        .map { it.id }
                        .filter { it == 1L }
                        .toSet()
                },
            )

            try {
                awaitResumeScreenModel(screenModel)
                downloadCacheChanges.emit(NovelDownloadCacheEvent.InvalidateAll)
                withTimeout(1_000) {
                    while ((screenModel.state.value as? NovelScreenModel.State.Success)?.downloadedChapterIds !=
                        setOf(1L)
                    ) {
                        yield()
                    }
                }

                val callsAfterInitialRescan = resolveDownloadedIdsCalls
                screenModel.handleDownloadCacheEvent(
                    NovelDownloadCacheEvent.ChaptersChanged(
                        novelId = novel.id,
                        chapterIds = setOf(2L),
                        downloaded = true,
                    ),
                )

                withTimeout(1_000) {
                    while ((screenModel.state.value as? NovelScreenModel.State.Success)?.downloadedChapterIds !=
                        setOf(1L, 2L)
                    ) {
                        yield()
                    }
                }

                resolveDownloadedIdsCalls shouldBe callsAfterInitialRescan
            } finally {
                screenModel.onDispose()
            }
        }
    }

    @Test
    fun `invalidate all download cache event still triggers full rescan`() {
        runBlocking {
            val novel = novelForResumeTests(302L)
            val chapters = listOf(
                novelChapter(id = 1L, novelId = novel.id, chapterNumber = 1.0, read = false),
            )
            val downloadCacheChanges = MutableSharedFlow<NovelDownloadCacheEvent>(replay = 1, extraBufferCapacity = 1)
            var resolveDownloadedIdsCalls = 0
            val screenModel = createResumeScreenModel(
                novel = novel,
                chapters = chapters,
                downloadCacheChanges = downloadCacheChanges,
                resolveDownloadedChapterIds = { _, resolvedChapters ->
                    resolveDownloadedIdsCalls++
                    resolvedChapters.map { it.id }.toSet()
                },
            )

            try {
                awaitResumeScreenModel(screenModel)
                val initialCalls = resolveDownloadedIdsCalls

                screenModel.handleDownloadCacheEvent(NovelDownloadCacheEvent.InvalidateAll)

                withTimeout(1_000) {
                    while (resolveDownloadedIdsCalls <= initialCalls) {
                        yield()
                    }
                }
            } finally {
                screenModel.onDispose()
            }
        }
    }

    private class FakeLifecycleOwner : LifecycleOwner {
        private class NoopStartedLifecycle : Lifecycle() {
            override val currentState: State
                get() = State.STARTED

            override fun addObserver(observer: androidx.lifecycle.LifecycleObserver) = Unit

            override fun removeObserver(observer: androidx.lifecycle.LifecycleObserver) = Unit
        }

        override val lifecycle: Lifecycle = NoopStartedLifecycle()
    }

    private fun createResumeScreenModel(
        novel: Novel,
        chapters: List<NovelChapter>,
        chapterRepository: FakeNovelChapterRepository = FakeNovelChapterRepository(chapters),
        novelHistoryRepository: NovelHistoryRepository = FakeNovelHistoryRepository(),
        downloadCacheChanges: Flow<NovelDownloadCacheEvent> = MutableSharedFlow(replay = 1, extraBufferCapacity = 1),
        downloadQueueState: Flow<NovelDownloadQueueState> = MutableStateFlow(NovelDownloadQueueState()),
        getNovelWithChaptersOverride: GetNovelWithChapters? = null,
        novelTranslatedDownloadManager: NovelTranslatedDownloadManager = NovelTranslatedDownloadManager(),
        translationQueueManager: TranslationQueueManager = Injekt.get(),
        geminiEnabled: Boolean = false,
        excludedScanlators: Set<String> = emptySet(),
        resolveDownloadedChapterIds: (Novel, List<NovelChapter>) -> Set<Long> = { _, _ -> emptySet() },
        enqueueOriginal: (Novel, List<NovelChapter>) -> Int = { novel, queuedChapters ->
            eu.kanade.tachiyomi.data.download.novel.NovelDownloadQueueManager.enqueueOriginal(novel, queuedChapters)
        },
        snackbarHostState: SnackbarHostState = SnackbarHostState(),
    ): NovelScreenModel {
        val novelRepository = FakeNovelRepository(novel)
        val preferenceStore = FakePreferenceStore()
        val basePreferences = BasePreferences(
            context = mockk<Context>(relaxed = true),
            preferenceStore = preferenceStore,
        )
        val libraryPreferences = LibraryPreferences(preferenceStore)
        val sourceManager = FakeNovelSourceManager()
        val trackerManager = mockk<TrackerManager>().also { manager ->
            every { manager.loggedInTrackersFlow() } returns MutableStateFlow(emptyList())
            every { manager.loggedInNovelTrackersFlow() } returns MutableStateFlow(emptyList())
        }
        val getNovelTracks = mockk<GetNovelTracks>().also { tracks ->
            every { tracks.subscribe(any()) } returns MutableStateFlow(emptyList<NovelTrack>())
        }
        val categoryRepository = object : tachiyomi.domain.category.novel.repository.NovelCategoryRepository {
            override suspend fun getCategory(id: Long) = null
            override suspend fun getCategories() = emptyList<tachiyomi.domain.category.novel.model.NovelCategory>()
            override suspend fun getVisibleCategories() =
                emptyList<tachiyomi.domain.category.novel.model.NovelCategory>()
            override suspend fun getCategoriesByNovelId(novelId: Long) =
                emptyList<tachiyomi.domain.category.novel.model.NovelCategory>()
            override suspend fun getVisibleCategoriesByNovelId(novelId: Long) =
                emptyList<tachiyomi.domain.category.novel.model.NovelCategory>()
            override fun getCategoriesAsFlow() = MutableStateFlow(
                emptyList<tachiyomi.domain.category.novel.model.NovelCategory>(),
            )
            override fun getVisibleCategoriesAsFlow() = MutableStateFlow(
                emptyList<tachiyomi.domain.category.novel.model.NovelCategory>(),
            )
            override suspend fun insertCategory(
                category: tachiyomi.domain.category.novel.model.NovelCategory,
            ) = null
            override suspend fun updatePartialCategory(
                update: tachiyomi.domain.category.novel.model.NovelCategoryUpdate,
            ) = Unit
            override suspend fun updateAllFlags(flags: Long) = Unit
            override suspend fun deleteCategory(categoryId: Long) = Unit
            override suspend fun setNovelCategories(novelId: Long, categoryIds: List<Long>) = Unit
        }
        val databaseHandler = mockk<NovelDatabaseHandler>().also { handler ->
            coEvery { handler.awaitList<String>(any(), any()) } returns excludedScanlators.toList()
            every { handler.subscribeToList<String>(any()) } returns MutableStateFlow(excludedScanlators.toList())
        }
        val getNovelWithChapters = getNovelWithChaptersOverride
            ?: GetNovelWithChapters(novelRepository, chapterRepository)
        val updateNovel = UpdateNovel(novelRepository)
        val syncNovelChaptersWithSource = SyncNovelChaptersWithSource(
            novelChapterRepository = chapterRepository,
            shouldUpdateDbNovelChapter = ShouldUpdateDbNovelChapter(),
            updateNovel = updateNovel,
            libraryPreferences = libraryPreferences,
        )
        val novelReaderPreferences = eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences(
            preferenceStore = preferenceStore,
            json = Json { encodeDefaults = true },
        ).also { preferences ->
            preferences.geminiEnabled().set(geminiEnabled)
        }

        return NovelScreenModel(
            lifecycle = FakeLifecycleOwner().lifecycle,
            novelId = novel.id,
            basePreferences = basePreferences,
            libraryPreferences = libraryPreferences,
            getNovelWithChapters = getNovelWithChapters,
            updateNovel = updateNovel,
            syncNovelChaptersWithSource = syncNovelChaptersWithSource,
            novelChapterRepository = chapterRepository,
            novelHistoryRepository = novelHistoryRepository,
            setNovelChapterFlags = SetNovelChapterFlags(novelRepository),
            setNovelDefaultChapterFlags = SetNovelDefaultChapterFlags(
                libraryPreferences = libraryPreferences,
                setNovelChapterFlags = SetNovelChapterFlags(novelRepository),
                getFavorites = GetNovelFavorites(novelRepository),
            ),
            getAvailableNovelScanlators = GetAvailableNovelScanlators(chapterRepository),
            getNovelScanlatorChapterCounts = GetNovelScanlatorChapterCounts(chapterRepository),
            getNovelExcludedScanlators = GetNovelExcludedScanlators(databaseHandler),
            setNovelExcludedScanlators = SetNovelExcludedScanlators(
                mockk<NovelDatabaseHandler>(relaxed = true),
            ),
            getNovelCategories = GetNovelCategories(categoryRepository),
            setNovelCategories = SetNovelCategories(categoryRepository),
            sourceManager = sourceManager,
            trackerManager = trackerManager,
            getTracks = getNovelTracks,
            downloadCacheChanges = downloadCacheChanges,
            downloadQueueState = downloadQueueState,
            resolveDownloadedChapterIds = resolveDownloadedChapterIds,
            enqueueOriginal = enqueueOriginal,
            snackbarHostState = snackbarHostState,
            novelReaderPreferences = novelReaderPreferences,
            novelTranslatedDownloadManager = novelTranslatedDownloadManager,
            translationQueueManager = translationQueueManager,
        )
    }

    private suspend fun awaitResumeScreenModel(screenModel: NovelScreenModel) {
        withTimeout(1_000) {
            while (screenModel.state.value is NovelScreenModel.State.Loading) {
                yield()
            }
        }
    }

    private fun novelForResumeTests(id: Long): Novel {
        return Novel.create().copy(
            id = id,
            source = 10L,
            title = "Novel",
            chapterFlags = Novel.CHAPTER_SORTING_NUMBER or Novel.CHAPTER_SORT_ASC,
            initialized = true,
        )
    }

    private fun novelChapter(
        id: Long,
        novelId: Long,
        sourceOrder: Long = 0L,
        chapterNumber: Double,
        read: Boolean,
        lastPageRead: Long = 0L,
    ): NovelChapter {
        return NovelChapter.create().copy(
            id = id,
            novelId = novelId,
            sourceOrder = sourceOrder,
            chapterNumber = chapterNumber,
            read = read,
            lastPageRead = lastPageRead,
            url = "https://example.org/ch$id",
            name = "Chapter $id",
        )
    }

    private class FakeNovelChapterRepository(
        chapters: List<NovelChapter>,
    ) : NovelChapterRepository {
        private val chapterFlow = MutableStateFlow(chapters)

        fun updateChapters(chapters: List<NovelChapter>) {
            chapterFlow.value = chapters
        }

        override suspend fun addAllChapters(chapters: List<NovelChapter>): List<NovelChapter> = chapters
        override suspend fun updateChapter(
            chapterUpdate: tachiyomi.domain.items.novelchapter.model.NovelChapterUpdate,
        ) = Unit
        override suspend fun updateAllChapters(
            chapterUpdates: List<tachiyomi.domain.items.novelchapter.model.NovelChapterUpdate>,
        ) = Unit
        override suspend fun removeChaptersWithIds(chapterIds: List<Long>) = Unit
        override suspend fun getChapterByNovelId(
            novelId: Long,
            applyScanlatorFilter: Boolean,
        ): List<NovelChapter> = chapterFlow.value
        override suspend fun getScanlatorsByNovelId(novelId: Long): List<String> =
            chapterFlow.value.mapNotNull { it.scanlator }.distinct()
        override fun getScanlatorsByNovelIdAsFlow(novelId: Long): Flow<List<String>> =
            chapterFlow.map { chapters -> chapters.mapNotNull { it.scanlator }.distinct() }
        override suspend fun getBookmarkedChaptersByNovelId(novelId: Long): List<NovelChapter> =
            chapterFlow.value.filter { it.bookmark }
        override suspend fun getChapterById(id: Long): NovelChapter? =
            chapterFlow.value.firstOrNull { it.id == id }
        override suspend fun getChapterByNovelIdAsFlow(
            novelId: Long,
            applyScanlatorFilter: Boolean,
        ): Flow<List<NovelChapter>> = chapterFlow
        override suspend fun getChapterByUrlAndNovelId(url: String, novelId: Long): NovelChapter? =
            chapterFlow.value.firstOrNull { it.url == url }
    }

    private class FakePreferenceStore : PreferenceStore {
        private val strings = mutableMapOf<String, Preference<String>>()
        private val longs = mutableMapOf<String, Preference<Long>>()
        private val ints = mutableMapOf<String, Preference<Int>>()
        private val floats = mutableMapOf<String, Preference<Float>>()
        private val booleans = mutableMapOf<String, Preference<Boolean>>()
        private val stringSets = mutableMapOf<String, Preference<Set<String>>>()
        private val objects = mutableMapOf<String, Preference<Any>>()

        override fun getString(key: String, defaultValue: String): Preference<String> =
            strings.getOrPut(key) { FakePreference(defaultValue) }

        override fun getLong(key: String, defaultValue: Long): Preference<Long> =
            longs.getOrPut(key) { FakePreference(defaultValue) }

        override fun getInt(key: String, defaultValue: Int): Preference<Int> =
            ints.getOrPut(key) { FakePreference(defaultValue) }

        override fun getFloat(key: String, defaultValue: Float): Preference<Float> =
            floats.getOrPut(key) { FakePreference(defaultValue) }

        override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> =
            booleans.getOrPut(key) { FakePreference(defaultValue) }

        override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
            stringSets.getOrPut(key) { FakePreference(defaultValue) }

        override fun <T> getObject(
            key: String,
            defaultValue: T,
            serializer: (T) -> String,
            deserializer: (String) -> T,
        ): Preference<T> {
            @Suppress("UNCHECKED_CAST")
            return objects.getOrPut(key) { FakePreference(defaultValue as Any) as Preference<Any> } as Preference<T>
        }

        override fun getAll(): Map<String, *> = emptyMap<String, Any>()
    }

    private class FakeNovelRepository(
        private val novel: Novel,
    ) : tachiyomi.domain.entries.novel.repository.NovelRepository {
        var lastUpdate: NovelUpdate? = null
        val allUpdates = mutableListOf<NovelUpdate>()

        override suspend fun getNovelById(id: Long): Novel = novel
        override suspend fun getNovelByIdAsFlow(id: Long) = MutableStateFlow(novel)
        override suspend fun getNovelByUrlAndSourceId(url: String, sourceId: Long): Novel? = null
        override fun getNovelByUrlAndSourceIdAsFlow(url: String, sourceId: Long) = MutableStateFlow<Novel?>(null)
        override suspend fun getNovelFavorites(): List<Novel> = emptyList()
        override suspend fun getReadNovelNotInLibrary(): List<Novel> = emptyList()
        override suspend fun getLibraryNovel() = emptyList<tachiyomi.domain.library.novel.LibraryNovel>()
        override fun getLibraryNovelAsFlow() =
            MutableStateFlow(emptyList<tachiyomi.domain.library.novel.LibraryNovel>())
        override fun getNovelFavoritesBySourceId(sourceId: Long) = MutableStateFlow(emptyList<Novel>())
        override suspend fun insertNovel(novel: Novel): Long? = null
        override suspend fun updateNovel(update: NovelUpdate): Boolean {
            lastUpdate = update
            allUpdates += update
            return true
        }
        override suspend fun updateAllNovel(novelUpdates: List<NovelUpdate>): Boolean = true
        override suspend fun resetNovelViewerFlags(): Boolean = true
    }

    private class FakeNovelHistoryRepository(
        private val historyByNovelId: Map<Long, List<NovelHistory>> = emptyMap(),
    ) : NovelHistoryRepository {
        override fun getNovelHistory(query: String): Flow<List<NovelHistoryWithRelations>> =
            MutableStateFlow(emptyList())

        override suspend fun getLastNovelHistory(): NovelHistoryWithRelations? =
            historyByNovelId.values
                .flatten()
                .maxByOrNull { it.readAt?.time ?: Long.MIN_VALUE }
                ?.let { history ->
                    NovelHistoryWithRelations(
                        id = history.id,
                        chapterId = history.chapterId,
                        novelId = -1L,
                        title = "",
                        chapterNumber = 0.0,
                        readAt = history.readAt,
                        readDuration = history.readDuration,
                        coverData = tachiyomi.domain.entries.novel.model.NovelCover(
                            novelId = -1L,
                            sourceId = -1L,
                            isNovelFavorite = false,
                            url = "",
                            lastModified = 0L,
                        ),
                    )
                }

        override suspend fun getTotalReadDuration(): Long =
            historyByNovelId.values.flatten().sumOf { it.readDuration }

        override suspend fun getHistoryByNovelId(novelId: Long): List<NovelHistory> =
            historyByNovelId[novelId].orEmpty()

        override suspend fun resetNovelHistory(historyId: Long) = Unit

        override suspend fun resetHistoryByNovelId(novelId: Long) = Unit

        override suspend fun deleteAllNovelHistory(): Boolean = true

        override suspend fun upsertNovelHistory(historyUpdate: NovelHistoryUpdate) = Unit
    }

    private class FakeNovelSourceManager : NovelSourceManager {
        override val isInitialized = MutableStateFlow(true)
        override val catalogueSources =
            MutableStateFlow(emptyList<eu.kanade.tachiyomi.novelsource.NovelCatalogueSource>())
        override fun get(sourceKey: Long): NovelSource? = null
        override fun getOrStub(sourceKey: Long): NovelSource =
            object : NovelSource {
                override val id: Long = sourceKey
                override val name: String = "Stub"
            }
        override fun getOnlineSources() = emptyList<eu.kanade.tachiyomi.novelsource.online.NovelHttpSource>()
        override fun getCatalogueSources() = emptyList<eu.kanade.tachiyomi.novelsource.NovelCatalogueSource>()
        override fun getStubSources() = emptyList<tachiyomi.domain.source.novel.model.StubNovelSource>()
    }

    private class FakePreference<T>(
        initial: T,
    ) : tachiyomi.core.common.preference.Preference<T> {
        private val state = MutableStateFlow(initial)
        override fun key(): String = "fake"
        override fun get(): T = state.value
        override fun set(value: T) {
            state.value = value
        }
        override fun isSet(): Boolean = true
        override fun delete() = Unit
        override fun defaultValue(): T = state.value
        override fun changes() = state
        override fun stateIn(scope: kotlinx.coroutines.CoroutineScope) = state
    }
}
