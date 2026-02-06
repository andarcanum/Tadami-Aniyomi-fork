package eu.kanade.tachiyomi.ui.reader.novel

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
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
import eu.kanade.tachiyomi.extension.novel.repo.NovelPluginPackage
import eu.kanade.tachiyomi.extension.novel.repo.NovelPluginRepoEntry
import eu.kanade.tachiyomi.extension.novel.repo.NovelPluginStorage
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderTheme
import kotlinx.serialization.json.Json
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.entries.novel.repository.NovelRepository
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.items.novelchapter.model.NovelChapterUpdate
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.source.novel.model.StubNovelSource
import tachiyomi.domain.source.novel.service.NovelSourceManager

class NovelReaderScreenModelTest {

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads chapter html from source`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(id = 5L, novelId = 1L, name = "Chapter 1", url = "https://example.org/ch1")

            val screenModel = NovelReaderScreenModel(
            chapterId = chapter.id,
            novelChapterRepository = FakeNovelChapterRepository(chapter),
            getNovel = GetNovel(FakeNovelRepository(novel)),
            sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
            pluginStorage = FakeNovelPluginStorage(emptyList()),
            novelReaderPreferences = createNovelReaderPreferences(),
            isSystemDark = { false },
            )

            withTimeout(1_000) {
            while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
            yield()
            }
            }

            val state = screenModel.state.value
            state.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.html.contains("Hello") shouldBe true
            state.lastSavedIndex shouldBe 0
            Unit
        }
    }

    @Test
    fun `loads custom js and css for plugin source`() {
        runBlocking {
            val pluginId = "plugin.test"
            val entry = NovelPluginRepoEntry(
                id = pluginId,
                name = "Plugin",
                site = "https://example.org",
                lang = "en",
                version = 1,
                url = "https://example.org/plugin.js",
                iconUrl = null,
                customJsUrl = null,
                customCssUrl = null,
                hasSettings = false,
                sha256 = "ignored",
            )
            val customJs = "console.log('custom');"
            val customCss = "body { color: red; }"
            val pkg = NovelPluginPackage(
                entry = entry,
                script = "console.log('main');".toByteArray(),
                customJs = customJs.toByteArray(),
                customCss = customCss.toByteArray(),
            )

            val novel = Novel.create().copy(id = 1L, source = pluginId.hashCode().toLong(), title = "Novel")
            val chapter = NovelChapter.create().copy(id = 5L, novelId = 1L, name = "Chapter 1", url = "https://example.org/ch1")

            val screenModel = NovelReaderScreenModel(
            chapterId = chapter.id,
            novelChapterRepository = FakeNovelChapterRepository(chapter),
            getNovel = GetNovel(FakeNovelRepository(novel)),
            sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
            pluginStorage = FakeNovelPluginStorage(listOf(pkg)),
            novelReaderPreferences = createNovelReaderPreferences(),
            isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val state = screenModel.state.value
            state.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.enableJs shouldBe true
            state.html.contains(customJs) shouldBe true
            state.html.contains(customCss) shouldBe true
            state.lastSavedIndex shouldBe 0
            Unit
        }
    }

    @Test
    fun `applies reader settings to html`() {
        runBlocking {
            val store = InMemoryPreferenceStore(
                sequenceOf(
                    InMemoryPreferenceStore.InMemoryPreference(
                        "novel_reader_font_size",
                        20,
                        NovelReaderPreferences.DEFAULT_FONT_SIZE,
                    ),
                    InMemoryPreferenceStore.InMemoryPreference(
                        "novel_reader_line_height",
                        1.8f,
                        NovelReaderPreferences.DEFAULT_LINE_HEIGHT,
                    ),
                    InMemoryPreferenceStore.InMemoryPreference(
                        "novel_reader_margins",
                        24,
                        NovelReaderPreferences.DEFAULT_MARGIN,
                    ),
                    InMemoryPreferenceStore.InMemoryPreference(
                        "novel_reader_theme",
                        NovelReaderTheme.LIGHT,
                        NovelReaderTheme.SYSTEM,
                    ),
                ),
            )
            val prefs = NovelReaderPreferences(
                preferenceStore = store,
                json = Json { encodeDefaults = true },
            )

            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(id = 5L, novelId = 1L, name = "Chapter 1", url = "https://example.org/ch1")

            val screenModel = NovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = prefs,
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val state = screenModel.state.value
            state.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.html.contains("font-size: 20px") shouldBe true
            state.html.contains("line-height: 1.8") shouldBe true
            state.html.contains("padding: 24px") shouldBe true
            state.lastSavedIndex shouldBe 0
            Unit
        }
    }

    @Test
    fun `missing chapter shows error state`() {
        runBlocking {
            val screenModel = NovelReaderScreenModel(
            chapterId = 99L,
            novelChapterRepository = FakeNovelChapterRepository(null),
            getNovel = GetNovel(FakeNovelRepository(Novel.create())),
            sourceManager = FakeNovelSourceManager(sourceId = 10L, chapterHtml = "<p>Hello</p>"),
            pluginStorage = FakeNovelPluginStorage(emptyList()),
            novelReaderPreferences = createNovelReaderPreferences(),
            isSystemDark = { false },
            )

            withTimeout(1_000) {
            while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
            yield()
            }
            }

            screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Error>()
            Unit
        }
    }

    @Test
    fun `update reading progress marks read near end`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(id = 5L, novelId = 1L, name = "Chapter 1", url = "https://example.org/ch1")
            val chapterRepo = FakeNovelChapterRepository(chapter)

            val screenModel = NovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = chapterRepo,
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            screenModel.updateReadingProgress(currentIndex = 9, totalItems = 10)
            yield()

            chapterRepo.lastUpdate?.read shouldBe true
            chapterRepo.lastUpdate?.lastPageRead shouldBe 0L
        }
    }

    @Test
    fun `computes previous and next chapter ids from source order`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter1 = NovelChapter.create().copy(id = 1L, novelId = 1L, name = "Chapter 1", url = "https://example.org/ch1", sourceOrder = 0L)
            val chapter2 = NovelChapter.create().copy(id = 2L, novelId = 1L, name = "Chapter 2", url = "https://example.org/ch2", sourceOrder = 1L)
            val chapter3 = NovelChapter.create().copy(id = 3L, novelId = 1L, name = "Chapter 3", url = "https://example.org/ch3", sourceOrder = 2L)
            val chapterRepo = FakeNovelChapterRepository(chapter2, listOf(chapter1, chapter2, chapter3))

            val screenModel = NovelReaderScreenModel(
                chapterId = chapter2.id,
                novelChapterRepository = chapterRepo,
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val state = screenModel.state.value
            state.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.previousChapterId shouldBe chapter1.id
            state.nextChapterId shouldBe chapter3.id
        }
    }

    private class FakeNovelChapterRepository(
        private val chapter: NovelChapter?,
        private val chaptersByNovel: List<NovelChapter> = emptyList(),
    ) : NovelChapterRepository {
        override suspend fun addAllChapters(chapters: List<NovelChapter>): List<NovelChapter> = chapters
        var lastUpdate: NovelChapterUpdate? = null
        override suspend fun updateChapter(chapterUpdate: NovelChapterUpdate) {
            lastUpdate = chapterUpdate
        }
        override suspend fun updateAllChapters(chapterUpdates: List<NovelChapterUpdate>) = Unit
        override suspend fun removeChaptersWithIds(chapterIds: List<Long>) = Unit
        override suspend fun getChapterByNovelId(novelId: Long, applyScanlatorFilter: Boolean) = chaptersByNovel
        override suspend fun getBookmarkedChaptersByNovelId(novelId: Long) = emptyList<NovelChapter>()
        override suspend fun getChapterById(id: Long): NovelChapter? = chapter?.takeIf { it.id == id }
        override suspend fun getChapterByNovelIdAsFlow(
            novelId: Long,
            applyScanlatorFilter: Boolean,
        ): Flow<List<NovelChapter>> = MutableStateFlow(emptyList())
        override suspend fun getChapterByUrlAndNovelId(url: String, novelId: Long): NovelChapter? = null
    }

    private class FakeNovelPluginStorage(
        private val packages: List<NovelPluginPackage>,
    ) : NovelPluginStorage {
        override suspend fun save(pkg: NovelPluginPackage) = Unit
        override suspend fun get(id: String): NovelPluginPackage? =
            packages.firstOrNull { it.entry.id == id }
        override suspend fun getAll(): List<NovelPluginPackage> = packages
    }

    private fun createNovelReaderPreferences(): NovelReaderPreferences {
        return NovelReaderPreferences(
            preferenceStore = InMemoryPreferenceStore(),
            json = Json { encodeDefaults = true },
        )
    }

    private class FakeNovelRepository(
        private val novel: Novel,
    ) : NovelRepository {
        override suspend fun getNovelById(id: Long): Novel = novel
        override suspend fun getNovelByIdAsFlow(id: Long) = MutableStateFlow(novel)
        override suspend fun getNovelByUrlAndSourceId(url: String, sourceId: Long): Novel? = null
        override fun getNovelByUrlAndSourceIdAsFlow(url: String, sourceId: Long) = MutableStateFlow<Novel?>(null)
        override suspend fun getNovelFavorites(): List<Novel> = emptyList()
        override suspend fun getReadNovelNotInLibrary(): List<Novel> = emptyList()
        override suspend fun getLibraryNovel(): List<LibraryNovel> = emptyList()
        override fun getLibraryNovelAsFlow() = MutableStateFlow(emptyList<LibraryNovel>())
        override fun getNovelFavoritesBySourceId(sourceId: Long) = MutableStateFlow(emptyList<Novel>())
        override suspend fun insertNovel(novel: Novel): Long? = null
        override suspend fun updateNovel(update: NovelUpdate): Boolean = true
        override suspend fun updateAllNovel(novelUpdates: List<NovelUpdate>): Boolean = true
        override suspend fun resetNovelViewerFlags(): Boolean = true
    }

    private class FakeNovelSourceManager(
        private val sourceId: Long,
        private val chapterHtml: String,
    ) : NovelSourceManager {
        override val isInitialized = MutableStateFlow(true)
        override val catalogueSources = MutableStateFlow(emptyList<eu.kanade.tachiyomi.novelsource.NovelCatalogueSource>())
        override fun get(sourceKey: Long): NovelSource? =
            if (sourceKey == sourceId) FakeNovelSource(sourceId, chapterHtml) else null
        override fun getOrStub(sourceKey: Long): NovelSource =
            get(sourceKey) ?: object : NovelSource {
                override val id: Long = sourceKey
                override val name: String = "Stub"
            }
        override fun getOnlineSources() = emptyList<eu.kanade.tachiyomi.novelsource.online.NovelHttpSource>()
        override fun getCatalogueSources() = emptyList<eu.kanade.tachiyomi.novelsource.NovelCatalogueSource>()
        override fun getStubSources() = emptyList<StubNovelSource>()
    }

    private class FakeNovelSource(
        override val id: Long,
        private val chapterHtml: String,
    ) : NovelSource {
        override val name: String = "NovelSource"

        override suspend fun getChapterText(chapter: SNovelChapter): String = chapterHtml
    }
}
