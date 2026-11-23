package eu.kanade.tachiyomi.extension.anime.util

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AnimeExtensionLoaderVersionTest {
    @Test
    fun `lib30 is accepted without excluding legacy 12 through 29`() {
        AnimeExtensionLoader.LIB_VERSION_MIN shouldBe 12.0
        AnimeExtensionLoader.LIB_VERSION_MAX shouldBe 30.0
        (12..30).forEach { version ->
            (version.toDouble() in AnimeExtensionLoader.SUPPORTED_LIB_VERSIONS) shouldBe true
        }
        (11.0 in AnimeExtensionLoader.SUPPORTED_LIB_VERSIONS) shouldBe false
        (31.0 in AnimeExtensionLoader.SUPPORTED_LIB_VERSIONS) shouldBe false
    }
}
