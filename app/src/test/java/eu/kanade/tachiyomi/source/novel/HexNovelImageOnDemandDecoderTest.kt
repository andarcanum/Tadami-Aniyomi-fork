package eu.kanade.tachiyomi.source.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class HexNovelImageOnDemandDecoderTest {

    @Test
    fun `parses hexnovels image reference payload`() {
        val payload = """
            {
              "imageUrl": "https://static.inuko.me/rich/12345678-1234-s234-1234-123456789012.webp?token=a%2Bb",
              "secretKey": "my-secret",
              "cacheKey": "chapter-0-image-1"
            }
        """.trimIndent()

        val parsed = HexNovelImageOnDemandDecoder.parseRef(payload)

        parsed?.imageUrl shouldBe "https://static.inuko.me/rich/12345678-1234-s234-1234-123456789012.webp?token=a%2Bb"
        parsed?.secretKey shouldBe "my-secret"
        parsed?.cacheKey shouldBe "chapter-0-image-1"
    }

    @Test
    fun `detects sec and xor encryption markers from file id`() {
        HexNovelImageOnDemandDecoder.detectEncryptionMode(
            "https://static.inuko.me/rich/12345678-1234-s234-1234-123456789012.webp",
        ) shouldBe HexNovelImageOnDemandDecoder.HexImageEncryptionMode.SEC

        HexNovelImageOnDemandDecoder.detectEncryptionMode(
            "https://static.inuko.me/rich/12345678-1234-x234-1234-123456789012.webp",
        ) shouldBe HexNovelImageOnDemandDecoder.HexImageEncryptionMode.XOR
    }

    @Test
    fun `converts sec file marker to xor marker without dropping query`() {
        val converted = HexNovelImageOnDemandDecoder.toXorVariantUrl(
            "https://static.inuko.me/rich/12345678-1234-s234-1234-123456789012.webp?token=abc",
        )

        converted shouldBe "https://static.inuko.me/rich/12345678-1234-x234-1234-123456789012.webp?token=abc"
    }

    @Test
    fun `xor decode is reversible with same key`() {
        val source = byteArrayOf(3, 12, 98, 7, 44, 111)

        val encrypted = HexNovelImageOnDemandDecoder.xorDecode(source, "hex-key")
        val decrypted = HexNovelImageOnDemandDecoder.xorDecode(encrypted, "hex-key")

        decrypted.contentEquals(source) shouldBe true
    }

    @Test
    fun `detects png mime from signature`() {
        val pngBytes = byteArrayOf(
            0x89.toByte(),
            0x50.toByte(),
            0x4E.toByte(),
            0x47.toByte(),
            0x0D.toByte(),
            0x0A.toByte(),
            0x1A.toByte(),
            0x0A.toByte(),
            0x00.toByte(),
        )

        HexNovelImageOnDemandDecoder.detectMimeType(
            bytes = pngBytes,
            sourceUrl = "https://static.inuko.me/rich/image.bin",
        ) shouldBe "image/png"
    }
}
