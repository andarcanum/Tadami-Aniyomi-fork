package tachiyomi.data.extension.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class NovelPluginStorageTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `readPluginScript treats unreadable script as missing`() {
        val storage = NovelPluginStorage(tempDir)
        // A directory at the script path passes exists() but fails readBytes(), emulating the
        // script file being deleted between the two calls (e.g. a concurrent uninstall).
        File(tempDir, "bad.plugin.js").mkdir()

        storage.readPluginScript("bad.plugin") shouldBe null
    }

    @Test
    fun `readCustomJs and readCustomCss treat unreadable assets as missing`() {
        val storage = NovelPluginStorage(tempDir)
        File(tempDir, "bad.plugin.custom.js").mkdir()
        File(tempDir, "bad.plugin.custom.css").mkdir()

        storage.readCustomJs("bad.plugin") shouldBe null
        storage.readCustomCss("bad.plugin") shouldBe null
    }
}
