package eu.kanade.tachiyomi.data.download.novel

import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.assertEquals
import org.junit.Test

class NovelDiskCacheTest {

    @Test
    fun testRoundtrip() {
        val original = NovelDiskCache(
            data = mapOf(
                1L to setOf(10L, 20L, 30L),
                2L to setOf(5L),
                3L to setOf(),
            ),
        )

        val bytes = ProtoBuf.encodeToByteArray(original)
        val restored = ProtoBuf.decodeFromByteArray<NovelDiskCache>(bytes)

        assertEquals(original.data.keys, restored.data.keys)
        original.data.forEach { (novelId, chapterIds) ->
            assertEquals(chapterIds, restored.data[novelId])
        }
    }

    @Test
    fun testEmptyCache() {
        val original = NovelDiskCache()

        val bytes = ProtoBuf.encodeToByteArray(original)
        val restored = ProtoBuf.decodeFromByteArray<NovelDiskCache>(bytes)

        assertEquals(emptyMap<Long, Set<Long>>(), restored.data)
    }
}
