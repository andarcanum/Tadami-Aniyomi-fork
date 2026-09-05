package eu.kanade.tachiyomi.ui.browse.manga.migration.search

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * F-M1: the single-migration dialog used to persist the bitmap narrowed to the migrated entry's
 * applicable checkboxes, silently clearing stored defaults for flags the entry merely lacked
 * (custom cover, notes, delete downloaded). mergeMigrationFlags keeps the non-applicable bits.
 */
class MigrateMangaDialogFlagsTest {

    private val chapters = 0b0000001
    private val categories = 0b0000010
    private val tracking = 0b0000100
    private val customCover = 0b0001000
    private val deleteDownloaded = 0b0010000
    private val extra = 0b0100000
    private val notes = 0b1000000
    private val allFlags = chapters or categories or tracking or customCover or
        deleteDownloaded or extra or notes

    @Test
    fun `non-applicable stored bits survive the merge`() {
        // Entry has no cover/notes/downloads: those flags are not applicable. The user unchecks
        // chapters in the dialog; the stored cover/notes/delete bits must survive.
        val stored = allFlags
        val applicableMask = chapters or categories or tracking or extra
        val selected = categories or tracking or extra

        mergeMigrationFlags(stored, applicableMask, selected) shouldBe
            (customCover or deleteDownloaded or notes or categories or tracking or extra)
    }

    @Test
    fun `applicable bits are replaced by the selection`() {
        val stored = allFlags
        mergeMigrationFlags(stored, allFlags, categories) shouldBe categories
    }

    @Test
    fun `bits outside the applicable mask in the selection are ignored`() {
        val stored = 0
        val applicableMask = chapters
        val selected = allFlags

        mergeMigrationFlags(stored, applicableMask, selected) shouldBe chapters
    }
}
