package eu.kanade.tachiyomi.util.lang

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset

class DateGroupUiModelBuilderTest {

    private sealed interface Row {
        data class Header(val date: LocalDate) : Row
        data class Entry(val id: Int) : Row
    }

    private data class Input(val id: Int, val epochMillis: Long?)

    private fun epoch(year: Int, month: Int, day: Int, hour: Int): Long {
        return LocalDate.of(year, month, day).atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    }

    @Test
    fun `inserts date headers on day transitions and preserves null-timestamp entries`() {
        val inputs = listOf(
            Input(1, epoch(2026, 9, 20, 18)),
            Input(2, epoch(2026, 9, 20, 9)),
            Input(3, epoch(2026, 9, 19, 22)),
            Input(4, null),
            Input(5, epoch(2026, 9, 19, 10)),
        )

        val rows = buildDateGroupedUiModels(
            items = inputs,
            zoneId = ZoneOffset.UTC,
            timestampMillis = { it.epochMillis },
            header = Row::Header,
            item = { Row.Entry(it.id) },
        )

        rows shouldBe listOf(
            Row.Header(LocalDate.of(2026, 9, 20)),
            Row.Entry(1),
            Row.Entry(2),
            Row.Header(LocalDate.of(2026, 9, 19)),
            Row.Entry(3),
            Row.Entry(4),
            Row.Header(LocalDate.of(2026, 9, 19)),
            Row.Entry(5),
        )
    }

    @Test
    fun `returns empty list for empty input`() {
        val rows = buildDateGroupedUiModels(
            items = emptyList<Input>(),
            zoneId = ZoneOffset.UTC,
            timestampMillis = { it.epochMillis },
            header = Row::Header,
            item = { Row.Entry(it.id) },
        )

        rows shouldBe emptyList()
    }
}
