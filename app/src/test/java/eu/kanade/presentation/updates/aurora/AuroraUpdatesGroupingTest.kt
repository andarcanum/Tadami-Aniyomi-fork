package eu.kanade.presentation.updates.aurora

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate

class AuroraUpdatesGroupingTest {

    @Test
    fun `buildAuroraUpdatesGroups groups by date and title preserving insertion order`() {
        val dayOne = LocalDate.of(2026, 2, 14)
        val dayTwo = LocalDate.of(2026, 2, 13)
        val items = listOf(
            TestUpdate(id = 1L, title = "Alpha", date = dayOne, itemId = 101L),
            TestUpdate(id = 1L, title = "Alpha", date = dayOne, itemId = 102L),
            TestUpdate(id = 2L, title = "Beta", date = dayOne, itemId = 201L),
            TestUpdate(id = 3L, title = "Gamma", date = dayTwo, itemId = 301L),
        )

        val sections = buildAuroraUpdatesGroups(
            items = items,
            dateSelector = { it.date },
            titleIdSelector = { it.id },
            titleSelector = { it.title },
        )

        sections.map { it.date }.shouldContainExactly(dayOne, dayTwo)
        sections.first().groups.map { it.entryId }.shouldContainExactly(1L, 2L)
        sections.first().groups.first().items.map { it.itemId }.shouldContainExactly(101L, 102L)
        sections.first().groups.first().itemCount shouldBe 2
    }

    @Test
    fun `buildAuroraUpdatesGroups creates independent groups for same title id on different dates`() {
        val dayOne = LocalDate.of(2026, 2, 14)
        val dayTwo = LocalDate.of(2026, 2, 13)
        val items = listOf(
            TestUpdate(id = 7L, title = "Shared", date = dayOne, itemId = 701L),
            TestUpdate(id = 7L, title = "Shared", date = dayTwo, itemId = 702L),
        )

        val sections = buildAuroraUpdatesGroups(
            items = items,
            dateSelector = { it.date },
            titleIdSelector = { it.id },
            titleSelector = { it.title },
        )

        sections.size shouldBe 2
        sections[0].groups.single().items.single().itemId shouldBe 701L
        sections[1].groups.single().items.single().itemId shouldBe 702L
    }

    @Test
    fun `buildAuroraUpdatesGroups produces stable unique keys per date and title`() {
        val dayOne = LocalDate.of(2026, 2, 14)
        val dayTwo = LocalDate.of(2026, 2, 13)
        val items = listOf(
            TestUpdate(id = 4L, title = "A", date = dayOne, itemId = 401L),
            TestUpdate(id = 4L, title = "A", date = dayTwo, itemId = 402L),
            TestUpdate(id = 5L, title = "B", date = dayOne, itemId = 501L),
        )

        val sections = buildAuroraUpdatesGroups(
            items = items,
            dateSelector = { it.date },
            titleIdSelector = { it.id },
            titleSelector = { it.title },
        )

        val keys = sections.flatMap { section -> section.groups.map { it.key } }
        keys.toSet().size shouldBe keys.size
    }
}

private data class TestUpdate(
    val id: Long,
    val title: String,
    val date: LocalDate,
    val itemId: Long,
)
