package eu.kanade.tachiyomi.ui.player.controls.components

import dev.vivvvek.seeker.Segment
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SeekbarSegmentsTest {
    @Test
    fun `buildSeekbarSegments deduplicates chapters and prepends zero segment without duplicating list`() {
        val chapters = listOf(
            Segment("OP", 90f),
            Segment("Part A", 180f),
            Segment("OP Duplicate", 90f),
            Segment("Out of range", 2000f),
        )

        val result = buildSeekbarSegments(chapters, duration = 1400f)

        result.map { it.start } shouldBe listOf(0f, 90f, 180f)
        result.map { it.name } shouldBe listOf("", "OP", "Part A")
    }

    @Test
    fun `buildSeekbarSegments preserves leading zero chapter without prepending blank segment`() {
        val chapters = listOf(
            Segment("Intro", 0f),
            Segment("Main", 120f),
        )

        val result = buildSeekbarSegments(chapters, duration = 1200f)

        result.map { it.start } shouldBe listOf(0f, 120f)
        result.first().name shouldBe "Intro"
    }
}
