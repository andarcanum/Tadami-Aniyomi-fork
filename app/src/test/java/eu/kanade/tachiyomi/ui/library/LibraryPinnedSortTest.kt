package eu.kanade.tachiyomi.ui.library

import io.kotest.matchers.collections.shouldContainExactly
import org.junit.jupiter.api.Test

class LibraryPinnedSortTest {

    @Test
    fun `sortPinnedSeriesFirst keeps pinned first then series then singles`() {
        val items = listOf(
            item("non-pinned-single", pinned = false, series = false),
            item("pinned-series", pinned = true, series = true),
            item("non-pinned-series", pinned = false, series = true),
            item("pinned-single", pinned = true, series = false),
        )

        val sorted = items.sortPinnedSeriesFirst(
            isPinned = { it.pinned },
            isSeries = { it.series },
            comparator = compareBy { it.title },
        )

        sorted.map { it.title }.shouldContainExactly(
            "pinned-series",
            "pinned-single",
            "non-pinned-series",
            "non-pinned-single",
        )
    }

    private fun item(title: String, pinned: Boolean, series: Boolean): TestItem {
        return TestItem(
            title = title,
            pinned = pinned,
            series = series,
        )
    }

    private data class TestItem(
        val title: String,
        val pinned: Boolean,
        val series: Boolean,
    )
}
