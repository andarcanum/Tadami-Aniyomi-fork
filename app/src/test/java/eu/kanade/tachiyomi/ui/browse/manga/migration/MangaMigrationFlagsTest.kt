package eu.kanade.tachiyomi.ui.browse.manga.migration

import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MangaMigrationFlagsTest {

    @Test
    fun `getFlags includes tracking and extra entries by default`() {
        val flags = MangaMigrationFlags.getFlags(null, Int.MAX_VALUE)

        assertEquals(
            listOf(
                MR.strings.chapters,
                MR.strings.categories,
                MR.strings.track,
                MR.strings.migration_extra,
                MR.strings.action_notes,
            ),
            flags.map { it.titleId },
        )
        assertTrue(flags.all { it.isDefaultSelected })
    }

    @Test
    fun `hasTracking hasExtra and hasNotes recognize their bit masks`() {
        assertTrue(MangaMigrationFlags.hasTracking(0b00100))
        assertFalse(MangaMigrationFlags.hasTracking(0))
        assertTrue(MangaMigrationFlags.hasExtra(0b100000))
        assertFalse(MangaMigrationFlags.hasExtra(0))
        assertTrue(MangaMigrationFlags.hasNotes(0b1000000))
        assertFalse(MangaMigrationFlags.hasNotes(0))
    }

    @Test
    fun `getSelectedFlagsBitMap combines selected bits`() {
        val flags = listOf(
            MangaMigrationFlag(0b00001, false, MR.strings.chapters),
            MangaMigrationFlag(0b00010, false, MR.strings.categories),
            MangaMigrationFlag(0b00100, false, MR.strings.track),
            MangaMigrationFlag(0b100000, false, MR.strings.migration_extra),
        )

        val bitmap = MangaMigrationFlags.getSelectedFlagsBitMap(
            selectedFlags = listOf(true, false, true, true),
            flags = flags,
        )

        assertEquals(0b100101, bitmap)
    }
}
