package eu.kanade.tachiyomi.extension.novel.repo

import eu.kanade.tachiyomi.util.lang.Hash
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelPluginPackageFactoryTest {

    @Test
    fun `create returns package when checksum matches`() {
        val script = "console.log('ok')".toByteArray()
        val entry = sampleEntry(sha256 = Hash.sha256(script))
        val factory = NovelPluginPackageFactory()

        val result = factory.create(entry, script, customJs = null, customCss = null)

        result.isSuccess shouldBe true
        result.getOrNull()?.entry shouldBe entry
    }

    @Test
    fun `create fails when checksum mismatches`() {
        val script = "console.log('bad')".toByteArray()
        val entry = sampleEntry(sha256 = "deadbeef")
        val factory = NovelPluginPackageFactory()

        val result = factory.create(entry, script, customJs = null, customCss = null)

        result.isFailure shouldBe true
        (result.exceptionOrNull() is NovelPluginChecksumMismatch) shouldBe true
    }

    private fun sampleEntry(sha256: String) = NovelPluginRepoEntry(
        id = "novel",
        name = "Novel Source",
        site = "https://example.org",
        lang = "en",
        version = 1,
        url = "https://example.org/novel.js",
        iconUrl = null,
        customJsUrl = null,
        customCssUrl = null,
        hasSettings = false,
        sha256 = sha256,
    )
}
