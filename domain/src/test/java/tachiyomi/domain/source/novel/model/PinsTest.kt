package tachiyomi.domain.source.novel.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class PinsTest {

    @Test
    fun `contains returns true for included pin`() {
        val pins = Pins(Pin.Pinned)

        (Pin.Pinned in pins) shouldBe true
        (Pin.Unpinned in pins) shouldBe true
    }

    @Test
    fun `plus adds pin`() {
        val pins = Pins(Pin.Unpinned) + Pin.Pinned

        (Pin.Pinned in pins) shouldBe true
    }
}
