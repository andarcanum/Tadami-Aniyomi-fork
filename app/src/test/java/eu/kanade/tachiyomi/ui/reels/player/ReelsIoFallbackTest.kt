package eu.kanade.tachiyomi.ui.reels.player

import android.os.Bundle
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.io.IOException

class ReelsIoFallbackTest {

    @Test
    fun `calculateIoFallbackPosition returns null if position is at zero`() {
        calculateIoFallbackPosition(0L, 0L) shouldBe null
        calculateIoFallbackPosition(-100L, 0L) shouldBe null
    }

    @Test
    fun `calculateIoFallbackPosition falls back to zero when no safe position exists`() {
        calculateIoFallbackPosition(45_000L, 0L) shouldBe 0L
    }

    @Test
    fun `calculateIoFallbackPosition falls back to safe position if it is reasonably behind`() {
        // Safe position at 20s, current failed position at 45s (> 5s gap) -> safe to resume from 20s
        calculateIoFallbackPosition(45_000L, 20_000L) shouldBe 20_000L
    }

    @Test
    fun `calculateIoFallbackPosition falls back to zero if safe position is too close to failing offset`() {
        // Safe position at 42s, failed position at 45s (< 5s gap) -> fall back to 0s to guarantee clean stream
        calculateIoFallbackPosition(45_000L, 42_000L) shouldBe 0L
    }

    @Test
    fun `classifyPlaybackError detects HTTP 503 as TRANSIENT_IO`() {
        val dataSpec = mockk<DataSpec>(relaxed = true)
        val http503 = HttpDataSource.InvalidResponseCodeException(
            503,
            "Service Unavailable",
            null,
            emptyMap<String, List<String>>(),
            dataSpec,
            byteArrayOf(),
        )
        val wrapper = Exception("Source error", http503)

        classifyPlaybackError(wrapper) shouldBe PlaybackErrorCategory.TRANSIENT_IO
    }

    @Test
    fun `classifyPlaybackError detects HTTP 429 as RATE_LIMITED`() {
        val dataSpec = mockk<DataSpec>(relaxed = true)
        val http429 = HttpDataSource.InvalidResponseCodeException(
            429,
            "Too Many Requests",
            null,
            emptyMap<String, List<String>>(),
            dataSpec,
            byteArrayOf(),
        )
        val wrapper = Exception("Rate limited", http429)

        classifyPlaybackError(wrapper) shouldBe PlaybackErrorCategory.RATE_LIMITED
    }

    @Test
    fun `classifyPlaybackError detects IOException as TRANSIENT_IO`() {
        val ioException = IOException("Connection reset by peer")
        val wrapper = Exception("IO error", ioException)

        classifyPlaybackError(wrapper) shouldBe PlaybackErrorCategory.TRANSIENT_IO
    }

    @Test
    fun `classifyPlaybackError detects 404 as FATAL`() {
        val dataSpec = mockk<DataSpec>(relaxed = true)
        val http404 = HttpDataSource.InvalidResponseCodeException(
            404,
            "Not Found",
            null,
            emptyMap<String, List<String>>(),
            dataSpec,
            byteArrayOf(),
        )
        val wrapper = Exception("Not found", http404)

        classifyPlaybackError(wrapper) shouldBe PlaybackErrorCategory.FATAL
    }

    @Test
    fun `classifyPlaybackError detects HTTP 401 403 410 as EXPIRED_URL`() {
        val dataSpec = mockk<DataSpec>(relaxed = true)
        for (code in listOf(401, 403, 410)) {
            val httpErr = HttpDataSource.InvalidResponseCodeException(
                code,
                "Expired",
                null,
                emptyMap<String, List<String>>(),
                dataSpec,
                byteArrayOf(),
            )
            classifyPlaybackError(Exception("Expired url", httpErr)) shouldBe PlaybackErrorCategory.EXPIRED_URL
        }
    }

    @Test
    fun `classifyPlaybackError detects hardware decoder failures as CODEC_ERROR`() {
        val extras = mockk<Bundle>(relaxed = true)
        val initFailed = object : PlaybackException(
            "Decoder init failed",
            null,
            ERROR_CODE_DECODER_INIT_FAILED,
            extras,
            0L,
        ) {}
        val decodeFailed = object : PlaybackException(
            "Decoding failed",
            null,
            ERROR_CODE_DECODING_FAILED,
            extras,
            0L,
        ) {}
        classifyPlaybackError(initFailed) shouldBe PlaybackErrorCategory.CODEC_ERROR
        classifyPlaybackError(decodeFailed) shouldBe PlaybackErrorCategory.CODEC_ERROR
    }
}
