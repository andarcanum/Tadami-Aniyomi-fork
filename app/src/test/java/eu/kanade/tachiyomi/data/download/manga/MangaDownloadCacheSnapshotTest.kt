package eu.kanade.tachiyomi.data.download.manga

import com.hippo.unifile.UniFile
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Test

class MangaDownloadCacheSnapshotTest {

    @Test
    fun `matching uses map names and never asks restored UniFiles for metadata`() {
        val file = mockk<UniFile> {
            every { getName() } throws AssertionError("Provider metadata read on cached lookup")
        }
        val scoped = MangaDirectory(file, mutableSetOf("scoped"))
        val legacy = MangaDirectory(file, mutableSetOf("legacy"))
        val drifted = MangaDirectory(file, mutableSetOf("drifted"))
        val directories = linkedMapOf(
            "Title [5]" to scoped,
            "Title" to legacy,
            "Old Title [5]" to drifted,
            "Other [6]" to MangaDirectory(file),
        )

        cachedMangaDirectories(directories, "Title [5]", "Title", 5L) shouldBe
            listOf(scoped, legacy, drifted)
        verify(exactly = 0) { file.getName() }
    }

    @Test
    fun `exact and suffix matches are deduplicated by key`() {
        val directory = MangaDirectory(null)
        cachedMangaDirectories(mapOf("Title [5]" to directory), "Title [5]", "Title", 5L) shouldBe
            listOf(directory)
    }

    @Test
    fun `null manga id selects only the legacy directory`() {
        val legacy = MangaDirectory(null)
        cachedMangaDirectories(
            mapOf("Title" to legacy, "Title [5]" to MangaDirectory(null)),
            null,
            "Title",
            null,
        ) shouldBe listOf(legacy)
    }

    @Test
    fun `suffix matching does not match a different numeric id`() {
        cachedMangaDirectories(mapOf("Title [15]" to MangaDirectory(null)), "Title [5]", "Title", 5L) shouldBe
            emptyList()
    }

    @Test
    fun `disk snapshot detaches every mutable collection level`() {
        val manga = MangaDirectory(null, mutableSetOf("one"))
        val source = SourceDirectory(null, mapOf("Title [5]" to manga))
        val root = RootDirectory(null, mapOf(10L to source), 123L)
        val snapshot = root.copyForDisk()

        manga.chapterDirs.add("two")
        source.mangaDirs = emptyMap()
        root.sourceDirs = emptyMap()
        root.lastSuccessfulScanAt = 456L

        snapshot.sourceDirs.getValue(10L).mangaDirs.getValue("Title [5]").chapterDirs shouldBe setOf("one")
        snapshot.lastSuccessfulScanAt shouldBe 123L
    }

    @Test
    fun `successful scan time survives protobuf round trip`() {
        val original = RootDirectory(null, lastSuccessfulScanAt = 123L)
        val restored = ProtoBuf.decodeFromByteArray<RootDirectory>(ProtoBuf.encodeToByteArray(original))
        restored.lastSuccessfulScanAt shouldBe 123L
    }

    @Test
    fun `missing scan timestamp is unvalidated rather than fresh`() {
        val restored = ProtoBuf.decodeFromByteArray<RootDirectory>(ProtoBuf.encodeToByteArray(RootDirectory(null)))
        restored.lastSuccessfulScanAt shouldBe 0L
    }
}
