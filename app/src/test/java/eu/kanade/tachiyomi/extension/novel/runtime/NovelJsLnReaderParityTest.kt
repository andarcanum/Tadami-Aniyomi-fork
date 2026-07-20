package eu.kanade.tachiyomi.extension.novel.runtime

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

class NovelJsLnReaderParityTest {

    private val registry = NovelJsModuleRegistry()

    private fun script(name: String): String =
        registry.modules().first { it.name == name }.script

    @Test
    fun `registry exposes libs utils module`() {
        registry.modules().map { it.name }.shouldContain("utils.js")
        val utils = script("utils.js")
        utils shouldContain "__defineModule(\"@libs/utils\""
        utils shouldContain "utf8ToBytes: utf8ToBytes"
        utils shouldContain "bytesToUtf8: bytesToUtf8"
    }

    @Test
    fun `aes module calls the bound native decrypt method`() {
        val aes = script("aes.js")
        aes shouldContain "__native.aesGcmDecrypt("
        aes shouldNotContain "__native.__aesGcmDecrypt"
    }

    @Test
    fun `default cover points at the current lnreader asset`() {
        script("defaultCover.js") shouldContain
            "https://github.com/lnreader/lnreader-plugins/blob/master/public/static/coverNotAvailable.webp?raw=true"
    }

    @Test
    fun `isUrlAbsolute follows lnreader semantics`() {
        val script = script("isAbsoluteUrl.js")
        script shouldContain "indexOf(\"//\") === 0"
        script shouldContain "indexOf(\"://\")"
        script shouldNotContain "a-zA-Z0-9+.-"
    }

    @Test
    fun `fetchText never throws and honors text encoding`() {
        val fetch = script("fetch.js")
        fetch shouldContain "res.ok ? res.text() : \"\""
        fetch shouldContain ".catch(function() { return \"\"; })"
        fetch shouldContain "mergedOptions.textEncoding = String(encoding);"
        fetch shouldContain "textEncoding: init.textEncoding == null ? null : String(init.textEncoding)"
    }

    @Test
    fun `urlencode module supports non-utf8 charsets via native bridge`() {
        val urlencode = script("urlencode.js")
        urlencode shouldContain "__native.urlEncode"
        urlencode shouldContain "__native.urlDecode"
        urlencode shouldContain "encodeURIComponent"
    }
}
