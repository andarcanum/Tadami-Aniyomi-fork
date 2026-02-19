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
        modules.shouldContain("proseMirrorToHtml.js")
        modules.shouldContain("fetch.js")
        modules.shouldContain("isAbsoluteUrl.js")
        modules.shouldContain("typesConstants.js")
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
    fun `cheerio module uses handle-based dom bridge`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "cheerio.js" }.script
        script.shouldContain("module.exports = { load: load }")
        script.shouldContain("__native.domSelect")
        script.shouldContain("__native.domLoad")
        script.shouldContain("__native.domParent")
        script.shouldContain("__native.domChildren")
        script.shouldContain("__native.domNext")
        script.shouldContain("__native.domPrev")
        script.shouldContain("__native.domSiblings")
        script.shouldContain("__native.domClosest")
        script.shouldContain("$.text = function(selector)")
        script.shouldContain("$.html = function(selector)")
        script.shouldContain("length: handles.length")
        script.shouldContain("last: function()")
        script.shouldContain("remove: function()")
        script.shouldContain("next: function(selector)")
        script.shouldContain("find: function(selector)")
        script.shouldContain("filter: function(predicate)")
        script.shouldContain("map: function(fn)")
        script.shouldContain("toArray: function() { return mapped.slice(); }")
        script.shouldContain("parent: function(selector)")
        script.shouldContain("children: function(selector)")
        script.shouldContain("siblings: function(selector)")
        script.shouldContain("closest: function(selector)")
        script.shouldContain("contents: function()")
        script.shouldContain("hasClass: function(className)")
    }

    @Test
    fun `prose mirror module exports renderer`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "proseMirrorToHtml.js" }.script
        script.shouldContain("__defineModule(\"@libs/proseMirrorToHtml\"")
        script.shouldContain("module.exports = { proseMirrorToHtml: proseMirrorToHtml }")
        script.shouldContain("normalizeType")
    }

    @Test
    fun `is absolute url module exposes helper`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "isAbsoluteUrl.js" }.script
        script.shouldContain("__defineModule(\"@libs/isAbsoluteUrl\"")
        script.shouldContain("isUrlAbsolute")
    }

    @Test
    fun `types constants module exposes backward compatible exports`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "typesConstants.js" }.script
        script.shouldContain("__defineModule(\"@/types/constants\"")
        script.shouldContain("defaultCover")
        script.shouldContain("NovelStatus")
    }

    @Test
    fun `fetch module delegates fetchProto to native bridge`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "fetch.js" }.script
        script.shouldContain("__native.fetchProto")
    }

    @Test
    fun `fetch module preserves top level referrer aliases`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "fetch.js" }.script
        script.shouldContain("init.referrer")
        script.shouldContain("init.Referer")
        script.shouldContain("referer")
    }

    @Test
    fun `bootstrap script defines URLSearchParams append`() {
        val field = NovelJsRuntime::class.java.getDeclaredField("bootstrapScript").apply {
            isAccessible = true
        }
        val script = field.get(null) as String
        script.shouldContain("URLSearchParams.prototype.append")
    }

    @Test
    fun `bootstrap script defines URLSearchParams set`() {
        val field = NovelJsRuntime::class.java.getDeclaredField("bootstrapScript").apply {
            isAccessible = true
        }
        val script = field.get(null) as String
        script.shouldContain("URLSearchParams.prototype.set")
    }

    @Test
    fun `bootstrap url polyfill supports toString`() {
        val field = NovelJsRuntime::class.java.getDeclaredField("bootstrapScript").apply {
            isAccessible = true
        }
        val script = field.get(null) as String
        script.shouldContain("URL.prototype.toString")
    }

    @Test
    fun `fetch module resolves url-like objects`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "fetch.js" }.script
        script.shouldContain("url.href")
        script.shouldContain("url.url")
    }

    @Test
    fun `fetch module exposes binary response api`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "fetch.js" }.script
        script.shouldContain("arrayBuffer: function()")
        script.shouldContain("bodyBase64")
    }
}
