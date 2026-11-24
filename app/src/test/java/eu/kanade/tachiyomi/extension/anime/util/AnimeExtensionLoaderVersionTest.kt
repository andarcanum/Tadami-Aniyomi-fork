package eu.kanade.tachiyomi.extension.anime.util

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AnimeExtensionLoaderVersionTest {
    @Test
    fun `lib31 is accepted without excluding legacy 12 through 30`() {
        AnimeExtensionLoader.LIB_VERSION_MIN shouldBe 12.0
        AnimeExtensionLoader.LIB_VERSION_MAX shouldBe 31.0
        (12..31).forEach { version ->
            (version.toDouble() in AnimeExtensionLoader.SUPPORTED_LIB_VERSIONS) shouldBe true
        }
        (11.0 in AnimeExtensionLoader.SUPPORTED_LIB_VERSIONS) shouldBe false
        (32.0 in AnimeExtensionLoader.SUPPORTED_LIB_VERSIONS) shouldBe false
    }
}
