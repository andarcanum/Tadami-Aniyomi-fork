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

    @Test
    fun `extractMangaDirId matches canonical suffix ids only`() {
        extractMangaDirId("Title [5]") shouldBe 5L
        extractMangaDirId("Title [v2] [42]") shouldBe 42L
        extractMangaDirId(" [7]") shouldBe 7L
        extractMangaDirId("Title [05]") shouldBe null
        extractMangaDirId("Title [+5]") shouldBe null
        extractMangaDirId("[5]") shouldBe null
        extractMangaDirId("Title [5] [extra]") shouldBe null
        extractMangaDirId("Legacy Title") shouldBe null
    }

    @Test
    fun `indexed lookup preserves priority and deduplicates matches`() {
        val scoped = MangaDirectory(null, mutableSetOf("scoped"))
        val legacy = MangaDirectory(null, mutableSetOf("legacy"))
        val drifted = MangaDirectory(null, mutableSetOf("drifted"))
        val source = SourceDirectory(
            dir = null,
            mangaDirs = linkedMapOf(
                "Title [5]" to scoped,
                "Title" to legacy,
                "Old Title [5]" to drifted,
                "Other [6]" to MangaDirectory(null),
            ),
        )
        val snapshot = source.snapshot()

        cachedMangaDirectories(
            directories = snapshot.byName,
            scopedName = "Title [5]",
            legacyName = "Title",
            mangaId = 5L,
            byIdIndex = snapshot.byId,
        ) shouldBe listOf(scoped, legacy, drifted)
    }

    @Test
    fun `empty byIdIndex on legacy-only source never falls back to linear scan`() {
        val backing = mapOf("Legacy One" to MangaDirectory(null), "Legacy Two" to MangaDirectory(null))
        val noScanMap = object : Map<String, MangaDirectory> by backing {
            override val entries: Set<Map.Entry<String, MangaDirectory>>
                get() = throw AssertionError("Unexpected full-map entry scan when byIdIndex is provided")
            override val keys: Set<String>
                get() = throw AssertionError("Unexpected full-map key scan when byIdIndex is provided")
        }

        cachedMangaDirectories(
            directories = noScanMap,
            scopedName = "Legacy One [5]",
            legacyName = "Legacy One",
            mangaId = 5L,
            byIdIndex = emptyMap(),
        ) shouldBe listOf(backing.getValue("Legacy One"))

        cachedMangaDirectories(
            directories = noScanMap,
            scopedName = "Missing [99]",
            legacyName = "Missing",
            mangaId = 99L,
            byIdIndex = emptyMap(),
        ) shouldBe emptyList()
    }

    @Test
    fun `source directory mutations update snapshot atomically without mutating prior snapshots`() {
        val first = MangaDirectory(null, mutableSetOf("c1"))
        val drifted = MangaDirectory(null, mutableSetOf("c2"))
        val source = SourceDirectory(null, mapOf("Old Title [5]" to drifted))

        val beforeAdd = source.snapshot()
        source.addMangaDir("New Title [5]", first)
        val afterAdd = source.snapshot()

        beforeAdd.byName.keys shouldBe setOf("Old Title [5]")
        beforeAdd.byId[5L] shouldBe listOf(drifted)
        afterAdd.byName.keys shouldBe setOf("Old Title [5]", "New Title [5]")
        afterAdd.byId[5L] shouldBe listOf(drifted, first)

        source.removeMangaDirs(listOf("New Title [5]", "Missing"))
        val afterRemove = source.snapshot()

        afterAdd.byId[5L] shouldBe listOf(drifted, first)
        afterRemove.byName.keys shouldBe setOf("Old Title [5]")
        afterRemove.byId[5L] shouldBe listOf(drifted)
    }

    @Test
    fun `protobuf round trip preserves source directory schema and rebuilds id index`() {
        val original = RootDirectory(
            dir = null,
            sourceDirs = mapOf(
                10L to SourceDirectory(
                    dir = null,
                    mangaDirs = mapOf(
                        "Drifted [5]" to MangaDirectory(null, mutableSetOf("ch1")),
                        "Legacy Only" to MangaDirectory(null, mutableSetOf("ch2")),
                    ),
                ),
            ),
            lastSuccessfulScanAt = 999L,
        )
        val restored = ProtoBuf.decodeFromByteArray<RootDirectory>(ProtoBuf.encodeToByteArray(original))
        val snapshot = restored.sourceDirs.getValue(10L).snapshot()

        snapshot.byId[5L]?.map { it.chapterDirs } shouldBe listOf(setOf("ch1"))
        snapshot.byName["Legacy Only"]?.chapterDirs shouldBe setOf("ch2")
    }
}
