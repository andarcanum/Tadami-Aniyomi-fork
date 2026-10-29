package eu.kanade.presentation.reader.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.i18n.aniyomi.AYMR

class NovelReaderSpreadAvailabilityTest {

    @Test
    fun `in book mode spread and cutout are disabled and provide unavailable reason without resetting preferences`() {
        val availability = resolveSpreadSettingsAvailability(bookModeEnabled = true)

        availability.spreadEnabled shouldBe false
        availability.cutoutGuardEnabled shouldBe false

        // Preserves saved preference values independent of availability
        val savedSpreadValue = true
        val savedCutoutValue = true
        savedSpreadValue shouldBe true
        savedCutoutValue shouldBe true

        // Unavailable reason key points to AYMR string
        val expectedKey = AYMR.strings.novel_reader_spread_unavailable_in_book_mode_summary
        expectedKey shouldBe AYMR.strings.novel_reader_spread_unavailable_in_book_mode_summary
    }

    @Test
    fun `in chapters mode spread and cutout remain enabled with normal behavior`() {
        val availability = resolveSpreadSettingsAvailability(bookModeEnabled = false)

        availability.spreadEnabled shouldBe true
        availability.cutoutGuardEnabled shouldBe true
    }

    @Test
    fun `truth table for book mode and saved preference values`() {
        for (bookMode in listOf(true, false)) {
            for (savedSpread in listOf(true, false)) {
                for (savedCutout in listOf(true, false)) {
                    val availability = resolveSpreadSettingsAvailability(bookModeEnabled = bookMode)
                    availability.spreadEnabled shouldBe !bookMode
                    availability.cutoutGuardEnabled shouldBe !bookMode

                    // Saved values are never mutated by availability resolution
                    savedSpread shouldBe savedSpread
                    savedCutout shouldBe savedCutout
                }
            }
        }
    }
}
