package eu.kanade.presentation.more.settings.screen

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class SearchableSettingsBackPressTest {

    @Test
    fun `resolve searchable settings back press prefers parent handler`() {
        val parentBackCalls = AtomicInteger(0)
        val navigatorPopCalls = AtomicInteger(0)

        val resolved = resolveSearchableSettingsBackPress(
            handleBack = { parentBackCalls.incrementAndGet() },
            navigatorPop = { navigatorPopCalls.incrementAndGet() },
        )

        resolved()

        parentBackCalls.get() shouldBe 1
        navigatorPopCalls.get() shouldBe 0
    }

    @Test
    fun `resolve searchable settings back press falls back to navigator pop`() {
        val navigatorPopCalls = AtomicInteger(0)

        val resolved = resolveSearchableSettingsBackPress(
            handleBack = null,
            navigatorPop = { navigatorPopCalls.incrementAndGet() },
        )

        resolved()

        navigatorPopCalls.get() shouldBe 1
    }
}
