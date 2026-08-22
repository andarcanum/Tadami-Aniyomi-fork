package eu.kanade.domain.entries.migration

import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.novel.model.Novel
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class MigrationSerializationTest {

    @Test
    fun testMangaSerializationRoundTrip() {
        val manga = Manga(
            id = 123L,
            source = 456L,
            favorite = true,
            pinned = false,
            lastUpdate = 0L,
            nextUpdate = 0L,
            fetchInterval = 0,
            dateAdded = 0L,
            viewerFlags = 0L,
            chapterFlags = 0L,
            coverLastModified = 0L,
            url = "/test",
            title = "Test Manga",
            artist = null,
            author = null,
            description = null,
            genre = null,
            status = 0L,
            thumbnailUrl = null,
            updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
            initialized = true,
            lastModifiedAt = 0L,
            favoriteModifiedAt = null,
            version = 1L,
            memo = JsonObject(mapOf("key" to JsonPrimitive("value"))),
        )

        val bytes = ByteArrayOutputStream().use { baos ->
            ObjectOutputStream(baos).use { oos ->
                oos.writeObject(manga)
            }
            baos.toByteArray()
        }

        val deserialized = ByteArrayInputStream(bytes).use { bais ->
            ObjectInputStream(bais).use { ois ->
                @Suppress("UNCHECKED_CAST")
                ois.readObject() as Manga
            }
        }

        assertEquals(manga.id, deserialized.id)
        assertEquals(manga.title, deserialized.title)
        assertNotNull(deserialized.memo)
    }

    @Test
    fun testAnimeSerializationRoundTrip() {
        val anime = Anime(
            id = 234L,
            source = 567L,
            favorite = true,
            pinned = false,
            lastUpdate = 0L,
            nextUpdate = 0L,
            fetchInterval = 0,
            dateAdded = 0L,
            viewerFlags = 0L,
            episodeFlags = 0L,
            coverLastModified = 0L,
            backgroundLastModified = 0L,
            url = "/test-anime",
            title = "Test Anime",
            artist = null,
            author = null,
            description = null,
            genre = null,
            status = 0L,
            thumbnailUrl = null,
            backgroundUrl = null,
            updateStrategy = AnimeUpdateStrategy.ALWAYS_UPDATE,
            initialized = true,
            lastModifiedAt = 0L,
            favoriteModifiedAt = null,
            version = 1L,
            fetchType = FetchType.Episodes,
            parentId = null,
            seasonFlags = 0L,
            seasonNumber = 1.0,
            seasonSourceOrder = 0L,
            memo = JsonObject(mapOf("key" to JsonPrimitive("value"))),
        )

        val bytes = ByteArrayOutputStream().use { baos ->
            ObjectOutputStream(baos).use { oos ->
                oos.writeObject(anime)
            }
            baos.toByteArray()
        }

        val deserialized = ByteArrayInputStream(bytes).use { bais ->
            ObjectInputStream(bais).use { ois ->
                @Suppress("UNCHECKED_CAST")
                ois.readObject() as Anime
            }
        }

        assertEquals(anime.id, deserialized.id)
        assertEquals(anime.title, deserialized.title)
        assertNotNull(deserialized.memo)
    }

    @Test
    fun testNovelSerializationRoundTrip() {
        val novel = Novel(
            id = 345L,
            source = 678L,
            favorite = true,
            pinned = false,
            lastUpdate = 0L,
            nextUpdate = 0L,
            fetchInterval = 0,
            dateAdded = 0L,
            viewerFlags = 0L,
            chapterFlags = 0L,
            coverLastModified = 0L,
            url = "/test-novel",
            title = "Test Novel",
            author = null,
            description = null,
            genre = null,
            status = 0L,
            thumbnailUrl = null,
            updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
            initialized = true,
            lastModifiedAt = 0L,
            favoriteModifiedAt = null,
            version = 1L,
            memo = JsonObject(mapOf("key" to JsonPrimitive("value"))),
        )

        val bytes = ByteArrayOutputStream().use { baos ->
            ObjectOutputStream(baos).use { oos ->
                oos.writeObject(novel)
            }
            baos.toByteArray()
        }

        val deserialized = ByteArrayInputStream(bytes).use { bais ->
            ObjectInputStream(bais).use { ois ->
                @Suppress("UNCHECKED_CAST")
                ois.readObject() as Novel
            }
        }

        assertEquals(novel.id, deserialized.id)
        assertEquals(novel.title, deserialized.title)
        assertNotNull(deserialized.memo)
    }
}
