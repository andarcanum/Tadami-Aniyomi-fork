package eu.kanade.tachiyomi.extension.novel.runtime

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.extension.novel.NovelPluginStorage
import java.io.File
import java.nio.file.Files

class NovelPluginAssetBindingsTest {

    private lateinit var tempDir: File
    private lateinit var pluginStorage: NovelPluginStorage
    private lateinit var assetBindings: NovelPluginAssetBindings

    @BeforeEach
    fun setup() {
        tempDir = createTempDirectory()
        pluginStorage = NovelPluginStorage(tempDir)
        assetBindings = NovelPluginAssetBindings(pluginStorage)
    }

    @Test
    fun `getCustomJs returns null when no custom JS exists`() {
        val result = assetBindings.getCustomJs("test-plugin")

        result shouldBe null
    }

    @Test
    fun `getCustomJs returns custom JS content from persisted file`() {
        val pluginId = "test-plugin"
        val customJs = "console.log('custom js');"
        writePluginFiles(pluginId, customJs = customJs.toByteArray())

        val result = assetBindings.getCustomJs(pluginId)

        result shouldBe customJs
    }

    @Test
    fun `getCustomCss returns null when no custom CSS exists`() {
        val result = assetBindings.getCustomCss("test-plugin")

        result shouldBe null
    }

    @Test
    fun `getCustomCss returns custom CSS content from persisted file`() {
        val pluginId = "test-plugin"
        val customCss = ".custom { color: red; }"
        writePluginFiles(pluginId, customCss = customCss.toByteArray())

        val result = assetBindings.getCustomCss(pluginId)

        result shouldBe customCss
    }

    @Test
    fun `generateAssetInjectionScript returns empty string when no assets exist`() {
        val result = assetBindings.generateAssetInjectionScript("test-plugin")

        result shouldBe ""
    }

    @Test
    fun `generateAssetInjectionScript includes custom JS for plugin-owned surfaces`() {
        val pluginId = "test-plugin"
        val customJs = "console.log('plugin js');"
        writePluginFiles(pluginId, customJs = customJs.toByteArray())

        val result = assetBindings.generateAssetInjectionScript(pluginId)

        result shouldContain "Plugin custom JS"
        result shouldContain customJs
    }

    @Test
    fun `generateAssetInjectionScript includes custom CSS for plugin-owned surfaces`() {
        val pluginId = "test-plugin"
        val customCss = ".custom { color: blue; }"
        writePluginFiles(pluginId, customCss = customCss.toByteArray())

        val result = assetBindings.generateAssetInjectionScript(pluginId)

        result shouldContain "Plugin custom CSS"
        result shouldContain customCss
    }

    @Test
    fun `generateAssetInjectionScript includes both JS and CSS when both exist`() {
        val pluginId = "test-plugin"
        val customJs = "console.log('js');"
        val customCss = ".custom { color: green; }"
        writePluginFiles(pluginId, customJs = customJs.toByteArray(), customCss = customCss.toByteArray())

        val result = assetBindings.generateAssetInjectionScript(pluginId)

        result shouldContain "Plugin custom JS"
        result shouldContain customJs
        result shouldContain "Plugin custom CSS"
        result shouldContain customCss
    }

    @Test
    fun `hasCustomAssets returns false when no assets exist`() {
        val result = assetBindings.hasCustomAssets("test-plugin")

        result shouldBe false
    }

    @Test
    fun `hasCustomAssets returns true when custom JS exists`() {
        val pluginId = "test-plugin"
        writePluginFiles(pluginId, customJs = "console.log('js');".toByteArray())

        val result = assetBindings.hasCustomAssets(pluginId)

        result shouldBe true
    }

    @Test
    fun `hasCustomAssets returns true when custom CSS exists`() {
        val pluginId = "test-plugin"
        writePluginFiles(pluginId, customCss = ".custom { color: red; }".toByteArray())

        val result = assetBindings.hasCustomAssets(pluginId)

        result shouldBe true
    }

    @Test
    fun `clearCache removes cached assets for specific plugin`() {
        val pluginId = "test-plugin"
        writePluginFiles(pluginId, customJs = "console.log('js');".toByteArray())

        val before = assetBindings.getCustomJs(pluginId)
        assetBindings.clearCache(pluginId)
        val after = assetBindings.getCustomJs(pluginId)

        before shouldBe after
    }

    @Test
    fun `clearAllCaches removes all cached assets`() {
        val pluginId1 = "plugin-1"
        val pluginId2 = "plugin-2"
        writePluginFiles(pluginId1, customJs = "console.log('1');".toByteArray())
        writePluginFiles(pluginId2, customJs = "console.log('2');".toByteArray())

        assetBindings.getCustomJs(pluginId1)
        assetBindings.getCustomJs(pluginId2)
        assetBindings.clearAllCaches()

        val result1 = assetBindings.getCustomJs(pluginId1)
        val result2 = assetBindings.getCustomJs(pluginId2)

        result1 shouldBe "console.log('1');"
        result2 shouldBe "console.log('2');"
    }

    @Test
    fun `assets are isolated per plugin`() {
        val pluginId1 = "plugin-1"
        val pluginId2 = "plugin-2"
        val customJs1 = "console.log('plugin 1');"
        val customJs2 = "console.log('plugin 2');"
        writePluginFiles(pluginId1, customJs = customJs1.toByteArray())
        writePluginFiles(pluginId2, customJs = customJs2.toByteArray())

        val result1 = assetBindings.getCustomJs(pluginId1)
        val result2 = assetBindings.getCustomJs(pluginId2)

        result1 shouldBe customJs1
        result2 shouldBe customJs2
        result1 shouldNotContain "plugin 2"
        result2 shouldNotContain "plugin 1"
    }

    private fun createTempDirectory(): File {
        val tempDir = Files.createTempDirectory("novel-plugin-asset-test-").toFile()
        tempDir.deleteOnExit()
        return tempDir
    }

    private fun writePluginFiles(
        pluginId: String,
        script: ByteArray = "main".toByteArray(),
        customJs: ByteArray? = null,
        customCss: ByteArray? = null,
    ) {
        pluginStorage.writePluginFiles(pluginId, script, customJs, customCss)
    }
}
