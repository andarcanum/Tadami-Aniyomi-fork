package eu.kanade.tachiyomi.ui.reader.novel.tts

import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
import eu.kanade.tachiyomi.extension.novel.repo.NovelPluginPackage
import eu.kanade.tachiyomi.extension.novel.repo.NovelPluginStorage
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import eu.kanade.tachiyomi.source.novel.NovelWebUrlSource
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreenModel
import eu.kanade.tachiyomi.ui.reader.novel.PageReaderProgress
import eu.kanade.tachiyomi.ui.reader.novel.encodePageReaderProgress
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTtsHighlightMode
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
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

class NovelTtsChapterRepositoryTest {

    @Test
    fun `loads chapter snapshot without reader ui state`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel", url = "/novel")
            val previous = NovelChapter.create().copy(id = 4L, novelId = 1L, name = "Prev", sourceOrder = 1L)
            val current = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "/ch1",
                sourceOrder = 2L,
                lastPageRead = 42L,
            )
            val next = NovelChapter.create().copy(id = 6L, novelId = 1L, name = "Next", sourceOrder = 3L)

            val repository = NovelTtsChapterRepository(
                novelChapterRepository = FakeNovelChapterRepository(current, listOf(previous, current, next)),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(
                    sourceId = novel.source,
                    chapterHtml = "<p>Hello</p><img src=\"/image.jpg\" alt=\"Cover\" />",
                ),
                novelDownloadManager = NovelDownloadManager(),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
            )

            val snapshot = repository.loadChapterSnapshot(current.id)
            val titleBlock = snapshot.contentBlocks[0].shouldBeInstanceOf<NovelReaderScreenModel.ContentBlock.Text>()
            val firstParagraphBlock =
                snapshot.contentBlocks[1].shouldBeInstanceOf<NovelReaderScreenModel.ContentBlock.Text>()
            val imageBlock = snapshot.contentBlocks[2].shouldBeInstanceOf<NovelReaderScreenModel.ContentBlock.Image>()

            snapshot.chapter.id shouldBe current.id
            snapshot.novel.id shouldBe novel.id
            snapshot.previousChapterId shouldBe previous.id
            snapshot.nextChapterId shouldBe next.id
            snapshot.contentBlocks.shouldHaveSize(3)
            titleBlock.text shouldBe "Chapter 1"
            firstParagraphBlock.text shouldBe "Hello"
            imageBlock.url shouldBe "https://example.org/image.jpg"
            snapshot.richContentBlocks.shouldHaveSize(3)
            snapshot.lastSavedIndex shouldBe 42
            snapshot.lastSavedScrollOffsetPx shouldBe 0
            snapshot.lastSavedWebProgressPercent shouldBe 42
            snapshot.lastSavedPageReaderProgress shouldBe null
        }
    }

    @Test
    fun `preserves encoded progress metadata`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel", url = "/novel")
            val current = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "/ch1",
                sourceOrder = 2L,
                lastPageRead = encodePageReaderProgress(index = 7, totalItems = 20),
            )

            val repository = NovelTtsChapterRepository(
                novelChapterRepository = FakeNovelChapterRepository(current, listOf(current)),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                novelDownloadManager = NovelDownloadManager(),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
            )

            val snapshot = repository.loadChapterSnapshot(current.id)

            snapshot.lastSavedIndex shouldBe 7
            snapshot.lastSavedPageReaderProgress shouldBe PageReaderProgress(index = 7, totalItems = 20)
            snapshot.lastSavedWebProgressPercent shouldBe 0
        }
    }

    private fun createNovelReaderPreferences(): NovelReaderPreferences {
        return NovelReaderPreferences(
            preferenceStore = ReactivePreferenceStore(),
            json = Json { encodeDefaults = true },
        ).also { prefs ->
            prefs.cacheReadChapters().set(false)
            prefs.ttsHighlightMode().set(NovelTtsHighlightMode.AUTO)
            prefs.translationProvider().set(NovelTranslationProvider.GEMINI)
        }
    }

    private class FakeNovelChapterRepository(
        private val chapter: NovelChapter?,
        private val chaptersByNovel: List<NovelChapter> = emptyList(),
    ) : NovelChapterRepository {
        override suspend fun addAllChapters(chapters: List<NovelChapter>): List<NovelChapter> = chapters
        override suspend fun updateChapter(chapterUpdate: NovelChapterUpdate) = Unit
        override suspend fun updateAllChapters(chapterUpdates: List<NovelChapterUpdate>) = Unit
        override suspend fun removeChaptersWithIds(chapterIds: List<Long>) = Unit
        override suspend fun getChapterByNovelId(novelId: Long, applyScanlatorFilter: Boolean): List<NovelChapter> =
            chaptersByNovel

        override suspend fun getScanlatorsByNovelId(novelId: Long): List<String> = emptyList()
        override fun getScanlatorsByNovelIdAsFlow(novelId: Long): Flow<List<String>> = MutableStateFlow(emptyList())
        override suspend fun getBookmarkedChaptersByNovelId(novelId: Long): List<NovelChapter> = emptyList()
        override suspend fun getChapterById(id: Long): NovelChapter? = chapter?.takeIf { it.id == id }
        override suspend fun getChapterByNovelIdAsFlow(
            novelId: Long,
            applyScanlatorFilter: Boolean,
        ): Flow<List<NovelChapter>> = MutableStateFlow(emptyList())

        override suspend fun getChapterByUrlAndNovelId(url: String, novelId: Long): NovelChapter? = null
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
        override val catalogueSources =
            MutableStateFlow(emptyList<eu.kanade.tachiyomi.novelsource.NovelCatalogueSource>())

        override fun get(sourceKey: Long): NovelSource? =
            if (sourceKey == sourceId) {
                object : NovelSource, NovelWebUrlSource {
                    override val id: Long = sourceId
                    override val name: String = "NovelSource"

                    override suspend fun getChapterText(chapter: SNovelChapter): String = chapterHtml

                    override suspend fun getNovelWebUrl(novelPath: String): String? = null

                    override suspend fun getChapterWebUrl(chapterPath: String, novelPath: String?): String? {
                        return "https://example.org$chapterPath"
                    }
                }
            } else {
                null
            }

        override fun getOrStub(sourceKey: Long): NovelSource =
            get(sourceKey) ?: object : NovelSource {
                override val id: Long = sourceKey
                override val name: String = "Stub"
            }

        override fun getOnlineSources() = emptyList<eu.kanade.tachiyomi.novelsource.online.NovelHttpSource>()
        override fun getCatalogueSources() = emptyList<eu.kanade.tachiyomi.novelsource.NovelCatalogueSource>()
        override fun getStubSources() = emptyList<StubNovelSource>()
    }

    private class FakeNovelPluginStorage(
        private val packages: List<NovelPluginPackage>,
    ) : NovelPluginStorage {
        override suspend fun save(pkg: NovelPluginPackage) = Unit
        override suspend fun get(id: String): NovelPluginPackage? = packages.firstOrNull { it.entry.id == id }
        override suspend fun getAll(): List<NovelPluginPackage> = packages
    }

    private class ReactivePreferenceStore : PreferenceStore {
        private val values = mutableMapOf<String, Any?>()
        private val flows = mutableMapOf<String, MutableStateFlow<Any?>>()

        override fun getString(
            key: String,
            defaultValue: String,
        ): Preference<String> = createPreference(key, defaultValue)
        override fun getLong(key: String, defaultValue: Long): Preference<Long> = createPreference(key, defaultValue)
        override fun getInt(key: String, defaultValue: Int): Preference<Int> = createPreference(key, defaultValue)
        override fun getFloat(key: String, defaultValue: Float): Preference<Float> = createPreference(key, defaultValue)
        override fun getBoolean(
            key: String,
            defaultValue: Boolean,
        ): Preference<Boolean> = createPreference(key, defaultValue)
        override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
            createPreference(key, defaultValue)

        override fun <T> getObject(
            key: String,
            defaultValue: T,
            serializer: (T) -> String,
            deserializer: (String) -> T,
        ): Preference<T> = createPreference(key, defaultValue)

        override fun getAll(): Map<String, *> = values.toMap()

        @Suppress("UNCHECKED_CAST")
        private fun <T> createPreference(key: String, defaultValue: T): Preference<T> {
            val stateFlow = flows.getOrPut(key) {
                MutableStateFlow(values[key] ?: defaultValue)
            }
            return object : Preference<T> {
                override fun key(): String = key
                override fun get(): T = (values[key] as T?) ?: (stateFlow.value as T?) ?: defaultValue
                override fun set(value: T) {
                    values[key] = value
                    stateFlow.value = value
                }

                override fun isSet(): Boolean = values.containsKey(key)
                override fun delete() {
                    values.remove(key)
                    stateFlow.value = defaultValue
                }

                override fun defaultValue(): T = defaultValue
                override fun changes(): Flow<T> = stateFlow as Flow<T>
                override fun stateIn(scope: CoroutineScope) =
                    changes().stateIn(scope, SharingStarted.Eagerly, get())
            }
        }
    }
}
