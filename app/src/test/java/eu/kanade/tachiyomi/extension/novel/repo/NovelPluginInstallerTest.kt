package eu.kanade.tachiyomi.extension.novel.repo

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class NovelPluginInstallerTest {

    @Test
    fun `install stores downloaded package`() {
        runTest {
            val entry = sampleEntry()
            val pkg = NovelPluginPackage(
            entry = entry,
            script = "main".toByteArray(),
            customJs = null,
            customCss = null,
            )
            val downloader = FakeDownloader(Result.success(pkg))
            val storage = RecordingStorage()
            val installer = NovelPluginInstaller(downloader, storage)

            val result = installer.install(entry)

            result.isSuccess shouldBe true
            storage.saved shouldBe pkg
        }
    }

        @Test
        fun `install returns error when download fails`() {
            runTest {
                val entry = sampleEntry()
                val downloader = FakeDownloader(Result.failure(IllegalStateException("fail")))
                val storage = RecordingStorage()
                val installer = NovelPluginInstaller(downloader, storage)

                val result = installer.install(entry)

                result.isFailure shouldBe true
                storage.saved shouldBe null
            }
        }

        private fun sampleEntry() = NovelPluginRepoEntry(
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
            sha256 = "deadbeef",
        )

        private class FakeDownloader(
            private val result: Result<NovelPluginPackage>,
        ) : NovelPluginDownloaderContract {
            override suspend fun download(entry: NovelPluginRepoEntry): Result<NovelPluginPackage> = result
        }

        private class RecordingStorage : NovelPluginStorage {
            var saved: NovelPluginPackage? = null

            override suspend fun save(pkg: NovelPluginPackage) {
                saved = pkg
        }

        override suspend fun get(id: String): NovelPluginPackage? = saved?.takeIf { it.entry.id == id }

        override suspend fun getAll(): List<NovelPluginPackage> = saved?.let { listOf(it) }.orEmpty()
    }
}
