package eu.kanade.tachiyomi.ui.entries.novel

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.entries.novel.interactor.GetNovelExcludedScanlators
import eu.kanade.domain.entries.novel.interactor.SetNovelExcludedScanlators
import eu.kanade.domain.entries.novel.interactor.UpdateNovel
import eu.kanade.domain.items.novelchapter.interactor.GetAvailableNovelScanlators
import eu.kanade.domain.items.novelchapter.interactor.GetNovelScanlatorChapterCounts
import eu.kanade.domain.items.novelchapter.interactor.SyncNovelChaptersWithSource
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadQueueState
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.novelsource.NovelSource
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
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
import tachiyomi.domain.items.novelchapter.interactor.SetNovelDefaultChapterFlags
import tachiyomi.domain.items.novelchapter.interactor.ShouldUpdateDbNovelChapter
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.domain.track.novel.interactor.GetNovelTracks
import tachiyomi.domain.track.novel.model.NovelTrack

class NovelScreenModelTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupMainDispatcher() {
            Dispatchers.setMain(Dispatchers.Unconfined)
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
                screenModel.getResumeOrNextChapter()?.id shouldBe chapter2.id
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
            val downloadCacheChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
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
                downloadCacheChanges.emit(Unit)
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
        downloadCacheChanges: Flow<Unit> = MutableSharedFlow(extraBufferCapacity = 1),
        downloadQueueState: Flow<NovelDownloadQueueState> = MutableStateFlow(NovelDownloadQueueState()),
        resolveDownloadedChapterIds: (Novel, List<NovelChapter>) -> Set<Long> = { _, _ -> emptySet() },
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
            coEvery { handler.awaitList<String>(any(), any()) } returns emptyList()
            every { handler.subscribeToList<String>(any()) } returns MutableStateFlow(emptyList())
        }
        val getNovelWithChapters = GetNovelWithChapters(novelRepository, chapterRepository)
        val updateNovel = UpdateNovel(novelRepository)
        val syncNovelChaptersWithSource = SyncNovelChaptersWithSource(
            novelChapterRepository = chapterRepository,
            shouldUpdateDbNovelChapter = ShouldUpdateDbNovelChapter(),
            updateNovel = updateNovel,
            libraryPreferences = libraryPreferences,
        )

        return NovelScreenModel(
            lifecycle = FakeLifecycleOwner().lifecycle,
            novelId = novel.id,
            basePreferences = basePreferences,
            libraryPreferences = libraryPreferences,
            getNovelWithChapters = getNovelWithChapters,
            updateNovel = updateNovel,
            syncNovelChaptersWithSource = syncNovelChaptersWithSource,
            novelChapterRepository = chapterRepository,
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
            novelReaderPreferences = eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences(
                preferenceStore = preferenceStore,
                json = Json { encodeDefaults = true },
            ),
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
        override suspend fun getScanlatorsByNovelId(novelId: Long): List<String> = emptyList()
        override fun getScanlatorsByNovelIdAsFlow(novelId: Long): Flow<List<String>> =
            MutableStateFlow(emptyList())
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
