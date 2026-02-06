package eu.kanade.tachiyomi.ui.entries.novel

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.novel.interactor.GetNovelWithChapters
import tachiyomi.domain.entries.novel.interactor.SetNovelChapterFlags
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import eu.kanade.domain.entries.novel.interactor.UpdateNovel
import eu.kanade.domain.items.novelchapter.interactor.SyncNovelChaptersWithSource
import eu.kanade.tachiyomi.novelsource.NovelSource
import tachiyomi.domain.source.novel.service.NovelSourceManager

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
            val novel = Novel.create().copy(id = 1L, favorite = false, title = "Novel")
            val novelRepository = FakeNovelRepository(novel)
            val getNovelWithChapters = GetNovelWithChapters(
            novelRepository = novelRepository,
            novelChapterRepository = object : tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository {
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
                override suspend fun getBookmarkedChaptersByNovelId(novelId: Long): List<NovelChapter> = emptyList()
                override suspend fun getChapterById(id: Long): NovelChapter? = null
                override suspend fun getChapterByNovelIdAsFlow(
                    novelId: Long,
                    applyScanlatorFilter: Boolean,
                ): Flow<List<NovelChapter>> = MutableStateFlow(emptyList())
                override suspend fun getChapterByUrlAndNovelId(url: String, novelId: Long): NovelChapter? = null
            }
            val sync = SyncNovelChaptersWithSource(
            novelChapterRepository = object : tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository {
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
            override suspend fun getBookmarkedChaptersByNovelId(novelId: Long): List<NovelChapter> = emptyList()
            override suspend fun getChapterById(id: Long): NovelChapter? = null
            override suspend fun getChapterByNovelIdAsFlow(
            novelId: Long,
            applyScanlatorFilter: Boolean,
            ): Flow<List<NovelChapter>> = MutableStateFlow(emptyList())
            override suspend fun getChapterByUrlAndNovelId(url: String, novelId: Long): NovelChapter? = null
            },
            shouldUpdateDbNovelChapter = tachiyomi.domain.items.novelchapter.interactor.ShouldUpdateDbNovelChapter(),
            updateNovel = UpdateNovel(
            novelRepository = object : tachiyomi.domain.entries.novel.repository.NovelRepository {
            override suspend fun getNovelById(id: Long): Novel = Novel.create()
            override suspend fun getNovelByIdAsFlow(id: Long) = MutableStateFlow(Novel.create())
            override suspend fun getNovelByUrlAndSourceId(url: String, sourceId: Long): Novel? = null
            override fun getNovelByUrlAndSourceIdAsFlow(url: String, sourceId: Long) =
            MutableStateFlow<Novel?>(null)
            override suspend fun getNovelFavorites(): List<Novel> = emptyList()
            override suspend fun getReadNovelNotInLibrary(): List<Novel> = emptyList()
            override suspend fun getLibraryNovel() = emptyList<tachiyomi.domain.library.novel.LibraryNovel>()
            override fun getLibraryNovelAsFlow() =
            MutableStateFlow(emptyList<tachiyomi.domain.library.novel.LibraryNovel>())
            override fun getNovelFavoritesBySourceId(sourceId: Long) = MutableStateFlow(emptyList<Novel>())
            override suspend fun insertNovel(novel: Novel): Long? = null
            override suspend fun updateNovel(update: NovelUpdate): Boolean = true
            override suspend fun updateAllNovel(novelUpdates: List<NovelUpdate>): Boolean = true
            override suspend fun resetNovelViewerFlags(): Boolean = true
            },
            ),
            libraryPreferences = tachiyomi.domain.library.service.LibraryPreferences(
            preferenceStore = object : tachiyomi.core.common.preference.PreferenceStore {
            override fun getString(key: String, defaultValue: String) =
            FakePreference(defaultValue)
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
            },
            ),
            )
            val lifecycleOwner = FakeLifecycleOwner()

            val screenModel = NovelScreenModel(
            lifecycle = lifecycleOwner.lifecycle,
            novelId = 1L,
            getNovelWithChapters = getNovelWithChapters,
            updateNovel = updateNovel,
            syncNovelChaptersWithSource = sync,
            novelChapterRepository = chapterRepository,
            setNovelChapterFlags = SetNovelChapterFlags(novelRepository),
            sourceManager = sourceManager,
            )

            withTimeout(1_000) {
            while (screenModel.state.value is NovelScreenModel.State.Loading) {
            yield()
            }
            }

            screenModel.toggleFavorite()

            withTimeout(1_000) {
            while (novelRepository.lastUpdate == null) {
            yield()
            }
            }

            novelRepository.lastUpdate?.favorite shouldBe true
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
            return true
        }
        override suspend fun updateAllNovel(novelUpdates: List<NovelUpdate>): Boolean = true
        override suspend fun resetNovelViewerFlags(): Boolean = true
    }

    private class FakeNovelSourceManager : NovelSourceManager {
        override val isInitialized = MutableStateFlow(true)
        override val catalogueSources = MutableStateFlow(emptyList<eu.kanade.tachiyomi.novelsource.NovelCatalogueSource>())
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
        override fun set(value: T) { state.value = value }
        override fun isSet(): Boolean = true
        override fun delete() = Unit
        override fun defaultValue(): T = state.value
        override fun changes() = state
        override fun stateIn(scope: kotlinx.coroutines.CoroutineScope) = state
    }
}
