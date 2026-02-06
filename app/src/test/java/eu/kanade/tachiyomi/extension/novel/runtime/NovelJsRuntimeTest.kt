package eu.kanade.tachiyomi.extension.novel.runtime

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class NovelJsRuntimeTest {

    @Test
    fun `module registry lists builtins`() {
        val modules = NovelJsModuleRegistry().modules().map { it.name }
        modules.shouldContain("novelStatus.js")
        modules.shouldContain("storage.js")
        modules.shouldContain("filterInputs.js")
        modules.shouldContain("fetch.js")
    }

    @Test
    fun `novel status module includes constants`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "novelStatus.js" }.script
        script.shouldContain("Ongoing")
        script.shouldContain("Completed")
    }

    @Test
    fun `storage module binds native api`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "storage.js" }.script
        script.shouldContain("storageGet")
        script.shouldContain("storageSet")
        script.shouldContain("storageRemove")
    }

    @Test
    fun `filter inputs module exposes text type`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "filterInputs.js" }.script
        script.shouldContain("FilterTypes")
        script.shouldContain("Text")
    }

    @Test
    fun `cheerio module uses native selector bridge`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "cheerio.js" }.script
        script.shouldContain("module.exports = { load: load }")
        script.shouldContain("__native.select")
        script.shouldContain("api.find")
    }
}
