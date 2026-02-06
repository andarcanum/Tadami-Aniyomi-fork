package eu.kanade.tachiyomi.ui.browse.novel.source

import eu.kanade.domain.source.novel.interactor.GetEnabledNovelSources
import eu.kanade.domain.source.novel.interactor.ToggleNovelSource
import eu.kanade.domain.source.novel.interactor.ToggleNovelSourcePin
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.source.novel.model.Source
import tachiyomi.domain.source.novel.repository.NovelSourceRepository

class NovelSourcesScreenModelTest {

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `updates state and filters pinned based on search`() {
        runBlocking {
            val enabledLanguages = FakePreference(setOf("en"))
            val disabledSources = FakePreference<Set<String>>(emptySet())
            val pinnedSources = FakePreference(setOf("1"))
            val lastUsedSource = FakePreference(2L)

            val sourcesFlow = MutableStateFlow(
                listOf(
                    source(id = 1, name = "Alpha"),
                    source(id = 2, name = "Beta"),
                ),
            )
            val repository = FakeNovelSourceRepository(sourcesFlow)

            val getEnabledSources = GetEnabledNovelSources(
                repository = repository,
                enabledLanguages = enabledLanguages,
                disabledSources = disabledSources,
                pinnedSources = pinnedSources,
                lastUsedSource = lastUsedSource,
            )
            val toggleSource = ToggleNovelSource(disabledSources)
            val togglePin = ToggleNovelSourcePin(pinnedSources)

            val screenModel = NovelSourcesScreenModel(
                getEnabledSources = getEnabledSources,
                toggleSource = toggleSource,
                togglePin = togglePin,
            )

            withTimeout(1_000) {
                while (screenModel.state.value.isLoading) {
                    yield()
                }
            }

            val initial = screenModel.state.value
            initial.pinnedItems.size shouldBe 1

            screenModel.search("Beta")

            val searched = screenModel.state.value
            searched.pinnedItems.size shouldBe 0
            searched.items.size shouldBe 4
        }
    }

    private fun source(id: Long, name: String) = Source(
        id = id,
        lang = "en",
        name = name,
        supportsLatest = false,
        isStub = false,
    )

    private class FakeNovelSourceRepository(
        private val sources: MutableStateFlow<List<Source>>,
    ) : NovelSourceRepository {
        override fun getNovelSources() = sources
        override fun getOnlineNovelSources() = sources
        override fun getNovelSourcesWithFavoriteCount() = TODO()
        override fun getNovelSourcesWithNonLibraryNovels() = TODO()
        override fun searchNovels(
            sourceId: Long,
            query: String,
            filterList: eu.kanade.tachiyomi.novelsource.model.NovelFilterList,
        ) = TODO()
        override fun getPopularNovels(sourceId: Long) = TODO()
        override fun getLatestNovels(sourceId: Long) = TODO()
    }

    private class FakePreference<T>(
        initial: T,
    ) : Preference<T> {
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
