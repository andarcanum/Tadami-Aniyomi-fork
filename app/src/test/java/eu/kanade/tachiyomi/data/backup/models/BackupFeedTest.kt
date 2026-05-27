package eu.kanade.tachiyomi.data.backup.models

import io.kotest.matchers.shouldBe
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Test

class BackupFeedTest {

    @Test
    fun `backup feed roundtrip keeps listing type`() {
        val original = BackupFeed(
            source = 7L,
            global = true,
            feedOrder = 2,
            sourceType = 1L,
            listingType = 1L,
            savedSearchName = null,
            savedSearchQuery = null,
            savedSearchFiltersJson = null,
        )

        val bytes = ProtoBuf.encodeToByteArray(BackupFeed.serializer(), original)
        val restored = ProtoBuf.decodeFromByteArray(BackupFeed.serializer(), bytes)

        restored.listingType shouldBe 1L
    }

    @Test
    fun `backup feed defaults listing type to zero`() {
        val original = BackupFeed(
            source = 7L,
            global = true,
            feedOrder = 2,
            sourceType = 1L,
            savedSearchName = null,
            savedSearchQuery = null,
            savedSearchFiltersJson = null,
        )

        original.listingType shouldBe 0L
    }

    @Test
    fun `backup feed roundtrip keeps all fields`() {
        val original = BackupFeed(
            source = 5L,
            global = false,
            feedOrder = 3,
            sourceType = 2L,
            listingType = 2L,
            savedSearchName = "My Search",
            savedSearchQuery = "isekai",
            savedSearchFiltersJson = """{"genre":"action"}""",
        )

        val bytes = ProtoBuf.encodeToByteArray(BackupFeed.serializer(), original)
        val restored = ProtoBuf.decodeFromByteArray(BackupFeed.serializer(), bytes)

        restored.source shouldBe 5L
        restored.global shouldBe false
        restored.feedOrder shouldBe 3L
        restored.sourceType shouldBe 2L
        restored.listingType shouldBe 2L
        restored.savedSearchName shouldBe "My Search"
        restored.savedSearchQuery shouldBe "isekai"
        restored.savedSearchFiltersJson shouldBe """{"genre":"action"}"""
    }
}
