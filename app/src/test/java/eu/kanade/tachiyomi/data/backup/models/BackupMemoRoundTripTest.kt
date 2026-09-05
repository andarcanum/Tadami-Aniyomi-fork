package eu.kanade.tachiyomi.data.backup.models

import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.core.common.extensions.EMPTY
import org.junit.jupiter.api.Test

/**
 * E-L-16: the fork's memo (JsonObject on both Manga and Chapter) was missing from the native
 * backup format - notes/tags were silently lost on backup->restore round-trips.
 */
class BackupMemoRoundTripTest {

    @Test
    fun `manga memo survives the proto round-trip`() {
        val memo = JsonObject(mapOf("note" to JsonPrimitive("keep me")))
        val backup = BackupManga(
            source = 1L,
            url = "/entry",
            title = "Title",
            memoJson = toBackupMemoJson(memo),
        )

        val decoded = ProtoBuf.decodeFromByteArray<BackupManga>(ProtoBuf.encodeToByteArray(backup))

        decoded.getMangaImpl().memo shouldBe memo
    }

    @Test
    fun `chapter memo survives the proto round-trip`() {
        val memo = JsonObject(mapOf("k" to JsonPrimitive("v")))
        val backup = BackupChapter(url = "/ch", name = "Ch 1", memoJson = toBackupMemoJson(memo))

        val decoded = ProtoBuf.decodeFromByteArray<BackupChapter>(ProtoBuf.encodeToByteArray(backup))

        decoded.toChapterImpl().memo shouldBe memo
    }

    @Test
    fun `empty or broken memo degrades to empty`() {
        toBackupMemoJson(JsonObject.EMPTY) shouldBe null
        parseBackupMemo(null) shouldBe JsonObject.EMPTY
        parseBackupMemo("not json{{") shouldBe JsonObject.EMPTY
    }
}
