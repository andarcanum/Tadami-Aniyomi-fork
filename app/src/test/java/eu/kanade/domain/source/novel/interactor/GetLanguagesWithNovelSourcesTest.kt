package eu.kanade.domain.source.novel.interactor

import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.source.novel.model.Source
import tachiyomi.domain.source.novel.repository.NovelSourceRepository

class GetLanguagesWithNovelSourcesTest {

    @Test
    fun `groups sources by language and drops blank language`() {
        runTest {
            val enabledLanguages = FakePreference<Set<String>>(setOf("ru"))
            val disabledSources = FakePreference<Set<String>>(setOf("2"))
            val sources = MutableStateFlow(
                listOf(
                    source(id = 1, lang = "en", name = "Alpha"),
                    source(id = 2, lang = "", name = "Blank"),
                    source(id = 3, lang = "ru", name = "Beta"),
                ),
            )
            val repository = FakeNovelSourceRepository(sources)

            val interactor = GetLanguagesWithNovelSources(
                repository = repository,
                enabledLanguages = enabledLanguages,
                disabledSources = disabledSources,
            )

            val result = interactor.subscribe().first { grouped ->
                grouped.containsKey("en") && grouped.containsKey("ru")
            }

            result.shouldContainKey("en")
            result.shouldContainKey("ru")
            result.shouldNotContainKey("")
            result.getValue("en").map { it.id } shouldBe listOf(1L)
        }
    }

    private fun source(id: Long, lang: String, name: String) = Source(
        id = id,
        lang = lang,
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
