package eu.kanade.presentation.reader.novel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class NovelReaderFontStorageTest {

    @Test
    fun `formats reader font labels from file names`() {
        assertEquals("AGUniversityCyr Roman Normal", formatReaderFontLabel("AGUniversityCyr Roman Normal.ttf"))
        assertEquals("My Serif Display", formatReaderFontLabel("my-serif_display.otf"))
    }

    @Test
    fun `lists only supported imported reader fonts`() {
        val directory = Files.createTempDirectory("reader-font-test").toFile()
        try {
            File(directory, "serif.ttf").writeText("font")
            File(directory, "display.otf").writeText("font")
            File(directory, "notes.txt").writeText("ignore")

            val fonts = getNovelReaderUserFonts(directory)

            assertEquals(listOf("user:display.otf", "user:serif.ttf"), fonts.map { it.id })
            assertTrue(fonts.all { it.source == NovelReaderFontSource.USER_IMPORTED })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `selected reader font falls back to original when unavailable`() {
        val selected = resolveNovelReaderSelectedFont(
            fonts = novelReaderBuiltInFonts,
            selectedFontId = "user:missing.ttf",
        )

        assertEquals("", selected.id)
        assertEquals("Original", selected.label)
    }
}
