package eu.kanade.presentation.reader.novel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class NovelReaderBackgroundCatalogStorageTest {

    @Test
    fun `catalog storage adds multiple custom backgrounds`() {
        val directory = createTempReaderBackgroundDirectory()
        try {
            val storage = FileBackedReaderBackgroundCatalogStorage(
                directory = directory,
                idFactory = SequentialIdFactory(),
                nowProvider = { 1000L },
            )

            val first = storage.add(
                fileName = "first.jpg",
                displayName = "First",
                isDarkHint = false,
            )
            val second = storage.add(
                fileName = "second.jpg",
                displayName = "Second",
                isDarkHint = true,
            )

            assertNotEquals(first.id, second.id)
            assertEquals(2, storage.read().items.size)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `manifest storage persists and reloads items`() {
        val directory = createTempReaderBackgroundDirectory()
        try {
            val storage = FileBackedReaderBackgroundCatalogStorage(
                directory = directory,
                idFactory = SequentialIdFactory(),
                nowProvider = { 1773212345000L },
            )

            val created = storage.add(
                fileName = "bg.jpg",
                displayName = "Paper",
                isDarkHint = null,
            )

            val reloaded = FileBackedReaderBackgroundCatalogStorage(directory = directory).read()

            assertEquals(created.id, reloaded.items.single().id)
            assertEquals("Paper", reloaded.items.single().displayName)
            assertEquals("bg.jpg", reloaded.items.single().fileName)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `rename and delete update catalog state`() {
        val directory = createTempReaderBackgroundDirectory()
        try {
            val storage = FileBackedReaderBackgroundCatalogStorage(
                directory = directory,
                idFactory = SequentialIdFactory(),
                nowProvider = { 1000L },
            )
            val created = storage.add(
                fileName = "bg.jpg",
                displayName = "Old Name",
                isDarkHint = false,
            )

            val renamed = storage.rename(
                id = created.id,
                displayName = "New Name",
            )
            assertNotNull(renamed)
            assertEquals("New Name", renamed?.displayName)

            val deleted = storage.remove(created.id)
            assertTrue(deleted)
            assertFalse(storage.read().items.any { it.id == created.id })
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun createTempReaderBackgroundDirectory(): File {
        val directory = Files.createTempDirectory("novel-reader-bg-catalog-test").toFile()
        directory.mkdirs()
        return directory
    }

    private class SequentialIdFactory : () -> String {
        private var next = 1
        override fun invoke(): String {
            return "custom-${next++}"
        }
    }
}
