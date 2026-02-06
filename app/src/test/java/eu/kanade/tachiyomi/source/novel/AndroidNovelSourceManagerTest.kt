package eu.kanade.tachiyomi.source.novel

import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.NovelsPage
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import eu.kanade.tachiyomi.novelsource.online.NovelHttpSource
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import rx.Observable
import tachiyomi.domain.extension.novel.model.NovelPlugin
import tachiyomi.domain.source.novel.model.StubNovelSource
import tachiyomi.domain.source.novel.repository.NovelStubSourceRepository

class AndroidNovelSourceManagerTest {

    @Test
    fun `catalogueSources emits sources from extension manager`() {
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val extensionManager = FakeNovelExtensionManager()
            val repository = FakeNovelStubSourceRepository()
            val manager = AndroidNovelSourceManager(extensionManager, repository, dispatcher)

            val source = FakeNovelCatalogueSource(id = 10L, name = "Novel", lang = "en")
            extensionManager.emitSources(listOf(source))
            advanceUntilIdle()

            val sources = manager.catalogueSources.first()

            sources.first() shouldBe source
        }
    }

        @Test
        fun `getOrStub returns stub from repository`() {
            runTest {
                val dispatcher = StandardTestDispatcher(testScheduler)
                val extensionManager = FakeNovelExtensionManager()
                val repository = FakeNovelStubSourceRepository()
                val manager = AndroidNovelSourceManager(extensionManager, repository, dispatcher)

                repository.upsertStubNovelSource(1L, "en", "Stub")
                advanceUntilIdle()

                val source = manager.getOrStub(1L) as StubNovelSource

                source.id shouldBe 1L
                source.lang shouldBe "en"
                source.name shouldBe "Stub"
            }
        }

        @Test
        fun `getStubSources excludes online sources`() {
            runTest {
                val dispatcher = StandardTestDispatcher(testScheduler)
                val extensionManager = FakeNovelExtensionManager()
                val repository = FakeNovelStubSourceRepository()
                val manager = AndroidNovelSourceManager(extensionManager, repository, dispatcher)

                val onlineSource = FakeNovelCatalogueSource(id = 1L, name = "Online", lang = "en")
                extensionManager.emitSources(listOf(onlineSource))
                repository.upsertStubNovelSource(1L, "en", "Online")
                repository.upsertStubNovelSource(2L, "en", "Offline")
                advanceUntilIdle()

                val stubs = manager.getStubSources()

                stubs.size shouldBe 1
                stubs.first().id shouldBe 2L
            }
        }

        private class FakeNovelExtensionManager : NovelExtensionManager {
            private val sourcesState = MutableStateFlow<List<NovelSource>>(emptyList())

            override val installedSourcesFlow: Flow<List<NovelSource>> = sourcesState
            override val installedPluginsFlow = MutableStateFlow<List<NovelPlugin.Installed>>(emptyList())
            override val availablePluginsFlow = MutableStateFlow<List<NovelPlugin.Available>>(emptyList())
            override val updatesFlow = MutableStateFlow<List<NovelPlugin.Installed>>(emptyList())

            fun emitSources(list: List<NovelSource>) {
                sourcesState.value = list
            }

            override suspend fun refreshAvailablePlugins() = Unit

            override suspend fun installPlugin(plugin: NovelPlugin.Available): NovelPlugin.Installed {
                throw UnsupportedOperationException("Not used in this test")
            }

            override suspend fun uninstallPlugin(plugin: NovelPlugin.Installed) = Unit

            override suspend fun getSourceData(id: Long): StubNovelSource? = null

            override fun getPluginIconUrlForSource(sourceId: Long): String? = null
        }

    private class FakeNovelStubSourceRepository : NovelStubSourceRepository {
        private val state = MutableStateFlow<List<StubNovelSource>>(emptyList())

        override fun subscribeAllNovel(): Flow<List<StubNovelSource>> = state

        override suspend fun getStubNovelSource(id: Long): StubNovelSource? =
            state.value.firstOrNull { it.id == id }

        override suspend fun upsertStubNovelSource(id: Long, lang: String, name: String) {
            val updated = StubNovelSource(id = id, lang = lang, name = name)
            state.value = state.value.filterNot { it.id == id } + updated
        }
    }

    private class FakeNovelCatalogueSource(
        override val id: Long,
        override val name: String,
        override val lang: String,
    ) : NovelHttpSource {
        override val supportsLatest: Boolean = true

        override fun fetchPopularNovels(page: Int): Observable<NovelsPage> =
            Observable.just(NovelsPage(emptyList(), false))

        override fun fetchSearchNovels(
            page: Int,
            query: String,
            filters: NovelFilterList,
        ): Observable<NovelsPage> =
            Observable.just(NovelsPage(emptyList(), false))

        override fun fetchLatestUpdates(page: Int): Observable<NovelsPage> =
            Observable.just(NovelsPage(emptyList(), false))

        override fun getFilterList(): NovelFilterList = NovelFilterList()

        override fun fetchNovelDetails(novel: SNovel): Observable<SNovel> = Observable.just(novel)

        override fun fetchChapterList(novel: SNovel): Observable<List<SNovelChapter>> =
            Observable.just(emptyList())

        override fun fetchChapterText(chapter: SNovelChapter): Observable<String> =
            Observable.just("")
    }
}
