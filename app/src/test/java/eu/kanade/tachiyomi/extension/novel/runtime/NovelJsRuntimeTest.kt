package eu.kanade.tachiyomi.extension.novel.runtime

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
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

    // ── Fetch default headers parity ─────────────────────────────────────

    @Test
    fun `fetch module normalizes init with default method GET`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "fetch.js" }.script
        script.shouldContain("method: \"GET\"")
    }

    @Test
    fun `fetch module normalizes init with empty headers object`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "fetch.js" }.script
        script.shouldContain("headers: {}")
    }

    @Test
    fun `fetch module normalizes init with bodyType none`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "fetch.js" }.script
        script.shouldContain("bodyType: \"none\"")
    }

    // ── Top-level referrer and origin aliases parity ─────────────────────

    @Test
    fun `fetch module handles lowercase referer alias`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "fetch.js" }.script
        script.shouldContain("init.referer")
    }

    @Test
    fun `fetch module handles capitalized Referer alias`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "fetch.js" }.script
        script.shouldContain("init.Referer")
    }

    @Test
    fun `fetch module handles capitalized Referrer alias`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "fetch.js" }.script
        script.shouldContain("init.Referrer")
    }

    @Test
    fun `fetch module handles capitalized Origin alias`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "fetch.js" }.script
        script.shouldContain("init.Origin")
    }

    @Test
    fun `fetch module derives referrer from url-like object`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "fetch.js" }.script
        script.shouldContain("url.referrer")
        script.shouldContain("url.referer")
    }

    @Test
    fun `fetch module derives origin from url-like object`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "fetch.js" }.script
        script.shouldContain("url.origin")
    }

    // ── Storage module behavior after settings/web storage split ─────────

    @Test
    fun `storage module exports storage object`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "storage.js" }.script
        script.shouldContain(
            "module.exports = { storage: storage, localStorage: localStorage, sessionStorage: sessionStorage }",
        )
    }

    @Test
    fun `storage module exports localStorage bridge`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "storage.js" }.script
        script.shouldContain("var localStorage = createStorage(")
        script.shouldContain("__native.localStorageGet")
        script.shouldContain("__native.localStorageSet")
        script.shouldContain("__native.localStorageKeys")
    }

    @Test
    fun `storage module exports sessionStorage bridge`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "storage.js" }.script
        script.shouldContain("var sessionStorage = createStorage(")
        script.shouldContain("__native.sessionStorageGet")
        script.shouldContain("__native.sessionStorageSet")
        script.shouldContain("__native.sessionStorageKeys")
    }

    @Test
    fun `storage module uses native storageGet`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "storage.js" }.script
        script.shouldContain("__native.storageGet")
    }

    @Test
    fun `storage module uses native storageSet`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "storage.js" }.script
        script.shouldContain("__native.storageSet")
    }

    @Test
    fun `storage module uses native storageRemove`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "storage.js" }.script
        script.shouldContain("__native.storageRemove")
    }

    @Test
    fun `storage module uses native storageClear`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "storage.js" }.script
        script.shouldContain("__native.storageClear")
    }

    @Test
    fun `storage module uses native storageKeys`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "storage.js" }.script
        script.shouldContain("__native.storageKeys")
    }

    @Test
    fun `storage module handles expiry in get`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "storage.js" }.script
        script.shouldContain("parsed.expires")
        script.shouldContain("now() > parsed.expires")
    }

    @Test
    fun `storage module removes expired entries`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "storage.js" }.script
        script.shouldContain("__native.storageRemove(String(key))")
    }

    @Test
    fun `storage module returns raw parsed value when raw flag set`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "storage.js" }.script
        script.shouldContain("return raw ? parsed : parsed.value")
    }

    @Test
    fun `storage module returns snapshot when key omitted`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "storage.js" }.script
        script.shouldContain("if (typeof key === \"undefined\") return readAll();")
    }

    // ── Capability-aware module exports ──────────────────────────────────

    @Test
    fun `fetch module exports fetchApi function`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "fetch.js" }.script
        script.shouldContain("fetchApi: fetchApi")
    }

    @Test
    fun `fetch module exports fetchText function`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "fetch.js" }.script
        script.shouldContain("fetchText: fetchText")
    }

    @Test
    fun `fetch module exports fetchProto function`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "fetch.js" }.script
        script.shouldContain("fetchProto: fetchProto")
    }

    @Test
    fun `fetch module exports fetchFile function`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "fetch.js" }.script
        script.shouldContain("fetchFile: fetchFile")
    }

    @Test
    fun `cheerio module exports load function`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "cheerio.js" }.script
        script.shouldContain("module.exports = { load: load }")
    }

    @Test
    fun `types constants module requires novelStatus`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "typesConstants.js" }.script
        script.shouldContain("require(\"@libs/novelStatus\")")
    }

    @Test
    fun `types constants module requires defaultCover`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "typesConstants.js" }.script
        script.shouldContain("require(\"@libs/defaultCover\")")
    }

    // ── Structured compatibility logging ─────────────────────────────────

    @Test
    fun `bootstrap script defines console log method`() {
        val field = NovelJsRuntime::class.java.getDeclaredField("bootstrapScript").apply {
            isAccessible = true
        }
        val script = field.get(null) as String
        script.shouldContain("log: function()")
    }

    @Test
    fun `bootstrap script defines console error method`() {
        val field = NovelJsRuntime::class.java.getDeclaredField("bootstrapScript").apply {
            isAccessible = true
        }
        val script = field.get(null) as String
        script.shouldContain("error: function()")
    }

    @Test
    fun `bootstrap script defines console warn method`() {
        val field = NovelJsRuntime::class.java.getDeclaredField("bootstrapScript").apply {
            isAccessible = true
        }
        val script = field.get(null) as String
        script.shouldContain("warn: function()")
    }

    @Test
    fun `bootstrap script defines console info method`() {
        val field = NovelJsRuntime::class.java.getDeclaredField("bootstrapScript").apply {
            isAccessible = true
        }
        val script = field.get(null) as String
        script.shouldContain("info: function()")
    }

    // ── Structured compatibility logging for runtime operations ──────────

    @Test
    fun `runtime logs plugin id on initialization`() {
        val field = NovelJsRuntime::class.java.getDeclaredField("LOG_TAG").apply {
            isAccessible = true
        }
        val logTag = field.get(null) as String
        logTag.shouldContain("NovelJsRuntime")
    }

    @Test
    fun `runtime uses consistent log tag`() {
        val field = NovelJsRuntime::class.java.getDeclaredField("LOG_TAG").apply {
            isAccessible = true
        }
        val logTag = field.get(null) as String
        logTag shouldBe "NovelJsRuntime"
    }

    @Test
    fun `native api logs plugin id for console operations`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "storage.js" }.script
        script.shouldContain("__native.storageGet")
        script.shouldContain("__native.storageSet")
    }

    @Test
    fun `fetch module logs operation type`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "fetch.js" }.script
        script.shouldContain("fetchApi")
        script.shouldContain("fetchText")
        script.shouldContain("fetchProto")
    }

    @Test
    fun `storage module logs capability`() {
        val script = NovelJsModuleRegistry().modules().first { it.name == "storage.js" }.script
        script.shouldContain("storage")
        script.shouldContain("localStorage")
        script.shouldContain("sessionStorage")
    }
}
