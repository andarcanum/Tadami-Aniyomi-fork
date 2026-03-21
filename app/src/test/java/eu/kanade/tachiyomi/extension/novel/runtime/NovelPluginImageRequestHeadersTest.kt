package eu.kanade.tachiyomi.extension.novel.runtime

import io.kotest.matchers.maps.shouldContainExactly
import org.junit.jupiter.api.Test

class NovelPluginImageRequestHeadersTest {

    @Test
    fun `decodePluginImageRequestHeaders returns normalized headers`() {
        val headers = decodePluginImageRequestHeaders(
            """
                {
                  "headers": {
                    "Accept": "image/webp,image/*",
                    "Referer": "https://ranobelib.me/",
                    "Blank": "   "
                  }
                }
            """.trimIndent(),
        )

        headers.shouldContainExactly(
            mapOf(
                "Accept" to "image/webp,image/*",
                "Referer" to "https://ranobelib.me/",
            ),
        )
    }
}
