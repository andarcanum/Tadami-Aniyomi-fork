package eu.kanade.domain.source.novel.interactor

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference

class ToggleNovelSourceTest {

    @Test
    fun `disables and enables sources by updating preference set`() {
        val disabledSources = FakePreference(setOf<String>())
        val toggle = ToggleNovelSource(disabledSources)

        toggle.await(1L, enable = false)
        disabledSources.get() shouldBe setOf("1")

        toggle.await(1L, enable = true)
        disabledSources.get() shouldBe emptySet()
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
