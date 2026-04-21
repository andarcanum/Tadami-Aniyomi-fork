package eu.kanade.tachiyomi.ui.updates.pacing

import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.NovelsPage
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import rx.Observable
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class LibraryUpdatePacingScreenModelTest {

    private lateinit var testDispatcher: TestDispatcher
    private lateinit var animeSourceManager: FakeAnimeSourceManager
    private lateinit var mangaSourceManager: FakeMangaSourceManager
    private lateinit var novelSourceManager: FakeNovelSourceManager
    private lateinit var uiPreferences: UiPreferences
    private lateinit var activeScreenModels: MutableList<LibraryUpdatePacingScreenModel>

    @BeforeEach
    fun setup() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        activeScreenModels = mutableListOf()

        animeSourceManager = FakeAnimeSourceManager()
        mangaSourceManager = FakeMangaSourceManager()
        novelSourceManager = FakeNovelSourceManager()
        uiPreferences = UiPreferences(FakePreferenceStore())
    }

    @AfterEach
    fun tearDown() {
        activeScreenModels.forEach { it.onDispose() }
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    @Test
    fun `builds sorted and searchable pacing sources`() = runTest(testDispatcher) {
        animeSourceManager.sources.value = listOf(
            FakeAnimeCatalogueSource(id = 2L, name = "Zulu Anime", lang = "en"),
            FakeAnimeCatalogueSource(id = 1L, name = "Alpha Anime", lang = "ja"),
        )
        mangaSourceManager.sources.value = listOf(
            FakeMangaCatalogueSource(id = 4L, name = "Beta Manga", lang = "en"),
        )
        novelSourceManager.sources.value = listOf(
            FakeNovelCatalogueSource(id = 3L, name = "Gamma Novel", lang = "ru"),
        )
        uiPreferences.libraryUpdatePacingSourceKeys().set(
            setOf("anime:1", "novel:3"),
        )

        val screenModel = createScreenModel()

        advanceUntilIdle()

        screenModel.state.value.sources.map { it.sourceKey } shouldContainExactly listOf(
            "anime:1",
            "anime:2",
            "manga:4",
            "novel:3",
        )
        screenModel.state.value.selectedSourceCount shouldBe 2

        screenModel.onSearchQueryChanged("beta")
        advanceUntilIdle()

        screenModel.state.value.filteredSources.map { it.displayName } shouldContainExactly listOf(
            "Beta Manga (EN)",
        )
    }

    @Test
    fun `toggle source selection persists media aware source keys`() = runTest(testDispatcher) {
        animeSourceManager.sources.value = listOf(
            FakeAnimeCatalogueSource(id = 7L, name = "Anime Source", lang = "en"),
        )
        novelSourceManager.sources.value = listOf(
            FakeNovelCatalogueSource(id = 7L, name = "Novel Source", lang = "en"),
        )

        val screenModel = createScreenModel()

        advanceUntilIdle()

        screenModel.toggleSourceSelection(LibraryUpdatePacingMediaType.ANIME, 7L)
        advanceUntilIdle()

        uiPreferences.libraryUpdatePacingSourceKeys().get() shouldBe setOf("anime:7")
        screenModel.state.value.sources.first { it.mediaType == LibraryUpdatePacingMediaType.ANIME }.selected shouldBe
            true
        screenModel.state.value.sources.first { it.mediaType == LibraryUpdatePacingMediaType.NOVEL }.selected shouldBe
            false

        screenModel.toggleSourceSelection(LibraryUpdatePacingMediaType.NOVEL, 7L)
        advanceUntilIdle()

        uiPreferences.libraryUpdatePacingSourceKeys().get() shouldBe setOf("anime:7", "novel:7")
        screenModel.state.value.sources.first { it.mediaType == LibraryUpdatePacingMediaType.NOVEL }.selected shouldBe
            true

        screenModel.toggleSourceSelection(LibraryUpdatePacingMediaType.ANIME, 7L)
        advanceUntilIdle()

        uiPreferences.libraryUpdatePacingSourceKeys().get() shouldBe setOf("novel:7")
        screenModel.state.value.sources.first { it.mediaType == LibraryUpdatePacingMediaType.ANIME }.selected shouldBe
            false
    }

    @Test
    fun `timeout is clamped and persisted`() = runTest(testDispatcher) {
        val screenModel = createScreenModel()

        advanceUntilIdle()

        screenModel.setTimeoutSeconds(-12)
        advanceUntilIdle()

        uiPreferences.libraryUpdatePacingTimeoutSeconds().get() shouldBe 0
        screenModel.state.value.timeoutSeconds shouldBe 0

        screenModel.setTimeoutSeconds(18)
        advanceUntilIdle()

        uiPreferences.libraryUpdatePacingTimeoutSeconds().get() shouldBe 18
        screenModel.state.value.timeoutSeconds shouldBe 18
    }

    private fun createScreenModel(): LibraryUpdatePacingScreenModel {
        return LibraryUpdatePacingScreenModel(
            uiPreferences = uiPreferences,
            animeSourceManager = animeSourceManager,
            mangaSourceManager = mangaSourceManager,
            novelSourceManager = novelSourceManager,
        ).also { activeScreenModels += it }
    }

    private class FakeAnimeSourceManager : tachiyomi.domain.source.anime.service.AnimeSourceManager {
        val sources = MutableStateFlow<List<AnimeCatalogueSource>>(emptyList())

        override val isInitialized = MutableStateFlow(true)
        override val catalogueSources = sources

        override fun get(sourceKey: Long) = sources.value.firstOrNull { it.id == sourceKey }

        override fun getOrStub(sourceKey: Long) =
            get(sourceKey) ?: error("Anime source $sourceKey not found")

        override fun getOnlineSources() = emptyList<eu.kanade.tachiyomi.animesource.online.AnimeHttpSource>()

        override fun getCatalogueSources() = sources.value

        override fun getStubSources() = emptyList<tachiyomi.domain.source.anime.model.StubAnimeSource>()
    }

    private class FakeMangaSourceManager : tachiyomi.domain.source.manga.service.MangaSourceManager {
        val sources = MutableStateFlow<List<CatalogueSource>>(emptyList())

        override val isInitialized = MutableStateFlow(true)
        override val catalogueSources = sources

        override fun get(sourceKey: Long) = sources.value.firstOrNull { it.id == sourceKey }

        override fun getOrStub(sourceKey: Long) =
            get(sourceKey) ?: error("Manga source $sourceKey not found")

        override fun getOnlineSources() = emptyList<eu.kanade.tachiyomi.source.online.HttpSource>()

        override fun getCatalogueSources() = sources.value

        override fun getStubSources() = emptyList<tachiyomi.domain.source.manga.model.StubMangaSource>()
    }

    private class FakeNovelSourceManager : tachiyomi.domain.source.novel.service.NovelSourceManager {
        val sources = MutableStateFlow<List<NovelCatalogueSource>>(emptyList())

        override val isInitialized = MutableStateFlow(true)
        override val catalogueSources = sources

        override fun get(sourceKey: Long) = sources.value.firstOrNull { it.id == sourceKey }

        override fun getOrStub(sourceKey: Long) =
            get(sourceKey) ?: error("Novel source $sourceKey not found")

        override fun getOnlineSources() = emptyList<eu.kanade.tachiyomi.novelsource.online.NovelHttpSource>()

        override fun getCatalogueSources() = sources.value

        override fun getStubSources() = emptyList<tachiyomi.domain.source.novel.model.StubNovelSource>()
    }

    private data class FakeAnimeCatalogueSource(
        override val id: Long,
        override val name: String,
        override val lang: String,
    ) : AnimeCatalogueSource {
        override val supportsLatest: Boolean = true

        override suspend fun getSeasonList(anime: SAnime): List<SAnime> = emptyList()

        override fun getFilterList(): AnimeFilterList = AnimeFilterList()

        override fun fetchPopularAnime(page: Int): Observable<AnimesPage> =
            Observable.error(IllegalStateException("Not used"))

        override fun fetchSearchAnime(
            page: Int,
            query: String,
            filters: AnimeFilterList,
        ): Observable<AnimesPage> = Observable.error(IllegalStateException("Not used"))

        override fun fetchLatestUpdates(page: Int): Observable<AnimesPage> =
            Observable.error(IllegalStateException("Not used"))
    }

    private data class FakeMangaCatalogueSource(
        override val id: Long,
        override val name: String,
        override val lang: String,
    ) : CatalogueSource {
        override val supportsLatest: Boolean = true

        override fun getFilterList(): FilterList = FilterList()
    }

    private data class FakeNovelCatalogueSource(
        override val id: Long,
        override val name: String,
        override val lang: String,
    ) : NovelCatalogueSource {
        override val supportsLatest: Boolean = true

        override fun getFilterList(): NovelFilterList = NovelFilterList()

        override fun fetchPopularNovels(page: Int): Observable<NovelsPage> =
            Observable.error(IllegalStateException("Not used"))

        override fun fetchSearchNovels(
            page: Int,
            query: String,
            filters: NovelFilterList,
        ): Observable<NovelsPage> = Observable.error(IllegalStateException("Not used"))

        override fun fetchLatestUpdates(page: Int): Observable<NovelsPage> =
            Observable.error(IllegalStateException("Not used"))
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
            strings.getOrPut(key) { FakePreference(key, defaultValue) }

        override fun getLong(key: String, defaultValue: Long): Preference<Long> =
            longs.getOrPut(key) { FakePreference(key, defaultValue) }

        override fun getInt(key: String, defaultValue: Int): Preference<Int> =
            ints.getOrPut(key) { FakePreference(key, defaultValue) }

        override fun getFloat(key: String, defaultValue: Float): Preference<Float> =
            floats.getOrPut(key) { FakePreference(key, defaultValue) }

        override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> =
            booleans.getOrPut(key) { FakePreference(key, defaultValue) }

        override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
            stringSets.getOrPut(key) { FakePreference(key, defaultValue) }

        @Suppress("UNCHECKED_CAST")
        override fun <T> getObject(
            key: String,
            defaultValue: T,
            serializer: (T) -> String,
            deserializer: (String) -> T,
        ): Preference<T> {
            return objects.getOrPut(key) { FakePreference(key, defaultValue as Any) } as Preference<T>
        }

        override fun getAll(): Map<String, *> = emptyMap<String, Any>()
    }

    private class FakePreference<T>(
        private val preferenceKey: String,
        defaultValue: T,
    ) : Preference<T> {
        private var value = defaultValue

        override fun key(): String = preferenceKey
        override fun get(): T = value
        override fun set(value: T) {
            this.value = value
        }
        override fun isSet(): Boolean = true
        override fun delete() = Unit
        override fun defaultValue(): T = value
        override fun changes(): Flow<T> = flow { emit(value) }
        override fun stateIn(scope: CoroutineScope): StateFlow<T> =
            changes().stateIn(scope, SharingStarted.Eagerly, value)
    }
}
