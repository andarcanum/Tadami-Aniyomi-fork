package eu.kanade.tachiyomi.ui.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.translation.formatGeminiThrowableForLog
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.net.SocketTimeoutException

class NovelReaderGeminiFailureLogTest {

    @Test
    fun `failure log includes exception type and message`() {
        val message = formatGeminiThrowableForLog(SocketTimeoutException("timeout"))

        message.shouldContain("SocketTimeoutException")
        message.shouldContain("timeout")
        message.shouldContain("Timeout before translation provider returned full response payload")
        message.shouldNotContain("Timeout before Gemini returned full response payload")
    }

    @Test
    fun `failure log includes cause chain`() {
        val error = IllegalStateException("chunk failed", SocketTimeoutException("read timed out"))

        val message = formatGeminiThrowableForLog(error)

        message.shouldContain("IllegalStateException: chunk failed")
        message.shouldContain("Caused by SocketTimeoutException: read timed out")
    }
}
