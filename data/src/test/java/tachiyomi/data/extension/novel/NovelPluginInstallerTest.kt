package tachiyomi.data.extension.novel

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.domain.extension.novel.model.NovelPlugin
import tachiyomi.domain.extension.novel.repository.NovelPluginRepository
import java.io.File

class NovelPluginInstallerTest {

    @Test
    fun `install skips checksum when sha256 missing`() {
        val storageDir = createTempDir()
        val storage = NovelPluginStorage(storageDir)
        val repository = InMemoryNovelPluginRepository()
        val downloader = object : NovelPluginDownloader {
            override suspend fun download(url: String): ByteArray = "content".toByteArray()
        }
        val installer = NovelPluginInstaller(downloader, repository, storage)

        val plugin = NovelPlugin.Available(
            id = "RLIB",
            name = "RanobeLib",
            site = "https://ranobelib.me",
            lang = "Русский",
            version = 2002001,
            url = "https://example.org/ranobelib.js",
            iconUrl = null,
            customJs = null,
            customCss = null,
            hasSettings = false,
            sha256 = "",
            repoUrl = "https://repo.example/",
        )

        shouldNotThrowAny {
            runBlocking {
                installer.install(plugin)
            }
        }

        repository.items.shouldHaveSize(1)
        repository.items[0].id shouldBe "RLIB"
        File(storageDir, "RLIB.js").exists() shouldBe true
    }

    private class InMemoryNovelPluginRepository : NovelPluginRepository {
        val items = mutableListOf<NovelPlugin.Installed>()

        override fun subscribeAll() = kotlinx.coroutines.flow.flow { emit(items.toList()) }

        override suspend fun getAll(): List<NovelPlugin.Installed> = items.toList()

        override suspend fun getById(id: String): NovelPlugin.Installed? = items.firstOrNull { it.id == id }

        override suspend fun upsert(plugin: NovelPlugin.Installed) {
            items.removeAll { it.id == plugin.id }
            items.add(plugin)
        }

        override suspend fun delete(id: String) {
            items.removeAll { it.id == id }
        }
    }
}
