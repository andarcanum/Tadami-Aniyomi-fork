package eu.kanade.tachiyomi.ui.entries.novel

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import eu.kanade.domain.entries.novel.interactor.GetNovelExcludedScanlators
import eu.kanade.domain.entries.novel.interactor.SetNovelExcludedScanlators
import eu.kanade.domain.entries.novel.interactor.UpdateNovel
import eu.kanade.domain.items.novelchapter.interactor.GetAvailableNovelScanlators
import eu.kanade.domain.items.novelchapter.interactor.GetNovelScanlatorChapterCounts
import eu.kanade.domain.items.novelchapter.interactor.SyncNovelChaptersWithSource
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.novelsource.NovelSource
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import tachiyomi.domain.category.novel.interactor.SetNovelCategories
import tachiyomi.domain.entries.novel.interactor.GetNovelFavorites
import tachiyomi.domain.entries.novel.interactor.GetNovelWithChapters
import tachiyomi.domain.entries.novel.interactor.SetNovelChapterFlags
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.items.novelchapter.interactor.SetNovelDefaultChapterFlags
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.domain.track.novel.interactor.GetNovelTracks

class NovelScreenModelTest {

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
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

            withTimeout(1_000) {
                while (screenModel.state.value is NovelScreenModel.State.Loading) {
                    yield()
                }
            }

            screenModel.toggleFavorite()

            withTimeout(1_000) {
                while (novelRepository.allUpdates.isEmpty()) {
                    yield()
                }
            }

            novelRepository.allUpdates.any { it.favorite == true } shouldBe true
            Unit
        }
    }

    private class FakeLifecycleOwner : LifecycleOwner {
        override val lifecycle: Lifecycle = LifecycleRegistry.createUnsafe(this).apply {
            currentState = Lifecycle.State.RESUMED
        }
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
