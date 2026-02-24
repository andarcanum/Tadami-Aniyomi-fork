package tachiyomi.core.common.preference

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class InMemoryPreferenceStoreTest {

    @Test
    fun `getStringSet returns stored value and falls back to default`() {
        val store = InMemoryPreferenceStore(
            sequenceOf(
                InMemoryPreferenceStore.InMemoryPreference(
                    key = "letters",
                    data = setOf("a", "b"),
                    defaultValue = emptySet(),
                ),
            ),
        )

        store.getStringSet("letters", setOf("x")).get() shouldBe setOf("a", "b")
        store.getStringSet("missing", setOf("x")).get() shouldBe setOf("x")
    }
}
