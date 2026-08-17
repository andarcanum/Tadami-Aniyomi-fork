package eu.kanade.presentation.entries.components.aurora

import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AuroraTitleStaggerTest {

    @Test
    fun `disabled state always returns progress of 1 for any index and poster`() {
        val state = AuroraTitleStaggerState(enabled = false)
        state.posterProgress() shouldBe 1f
        for (i in 0..10) {
            state.progressFor(i) shouldBe 1f
        }
    }

    @Test
    fun `initial un-animated enabled state progress is 0 for all indices and poster`() {
        val state = AuroraTitleStaggerState(enabled = true)
        state.posterProgress() shouldBe 0f
        for (i in 0..10) {
            state.progressFor(i) shouldBe 0f
        }
    }

    @Test
    fun `progress is clamped within 0 and 1 range`() {
        val state = AuroraTitleStaggerState(enabled = true)
        state.posterProgress() shouldBeGreaterThanOrEqual 0f
        state.posterProgress() shouldBeLessThanOrEqual 1f
        for (i in 0..10) {
            val progress = state.progressFor(i)
            progress shouldBeGreaterThanOrEqual 0f
            progress shouldBeLessThanOrEqual 1f
        }
    }
}
