package eu.kanade.presentation.entries.novel.components.aurora

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

class NovelStatsCardTest {

    @Test
    fun `next update days are null when next update is absent`() {
        rememberNovelNextUpdateDays(nextUpdate = null) shouldBe null
    }

    @Test
    fun `next update days are rounded down and clamped at zero`() {
        val now = Instant.parse("2026-04-06T00:00:00Z")

        rememberNovelNextUpdateDays(
            nextUpdate = now.plusSeconds(2 * 24 * 60 * 60),
            now = now,
        ) shouldBe 2

        rememberNovelNextUpdateDays(
            nextUpdate = now.minusSeconds(6 * 60 * 60),
            now = now,
        ) shouldBe 0
    }
}
