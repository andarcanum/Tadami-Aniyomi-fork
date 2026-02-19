package eu.kanade.domain.source.novel.interactor

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.source.novel.model.Source

class ToggleNovelSourcePinTest {

    @Test
    fun `toggles pinned sources in preference set`() {
        val pinnedSources = FakePreference(setOf<String>())
        val toggle = ToggleNovelSourcePin(pinnedSources)
        val source = Source(
            id = 5,
            lang = "en",
            name = "Test",
            supportsLatest = false,
            isStub = false,
        )

        toggle.await(source)
        pinnedSources.get() shouldBe setOf("5")

        toggle.await(source)
        pinnedSources.get() shouldBe emptySet()
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
