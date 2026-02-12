package eu.kanade.domain.source.novel.interactor

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.source.novel.model.Pin
import tachiyomi.domain.source.novel.model.Source
import tachiyomi.domain.source.novel.repository.NovelSourceRepository

class GetEnabledNovelSourcesTest {

    @Test
    fun `filters by enabled languages, removes disabled, and marks pinned and last used`() {
        runBlocking {
            val enabledLanguages = FakePreference<Set<String>>(setOf("en"))
            val disabledSources = FakePreference<Set<String>>(setOf("2"))
            val pinnedSources = FakePreference<Set<String>>(setOf("1"))
            val lastUsedSource = FakePreference(3L)

            val sources = MutableStateFlow(
                listOf(
                    source(id = 1, lang = "en"),
                    source(id = 2, lang = "en"),
                    source(id = 3, lang = "en"),
                ),
            )
            val repository = FakeNovelSourceRepository(sources)

            val interactor = GetEnabledNovelSources(
                repository = repository,
                enabledLanguages = enabledLanguages,
                disabledSources = disabledSources,
                pinnedSources = pinnedSources,
                lastUsedSource = lastUsedSource,
            )

            val result = interactor.subscribe().first()

            result.shouldHaveSize(3)
            result.map { it.id } shouldBe listOf(1L, 3L, 3L)
            result.first { it.id == 1L }.pin.contains(Pin.Pinned) shouldBe true
            result.count { it.id == 3L && it.isUsedLast } shouldBe 1
        }
    }

    private fun source(id: Long, lang: String) = Source(
        id = id,
        lang = lang,
        name = "Source $id",
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
        ) =
            TODO()
        override fun getPopularNovels(
            sourceId: Long,
            filterList: eu.kanade.tachiyomi.novelsource.model.NovelFilterList,
        ) = TODO()
        override fun getLatestNovels(
            sourceId: Long,
            filterList: eu.kanade.tachiyomi.novelsource.model.NovelFilterList,
        ) = TODO()
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
