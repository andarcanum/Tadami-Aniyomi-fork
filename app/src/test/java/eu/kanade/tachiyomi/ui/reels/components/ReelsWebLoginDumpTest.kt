package eu.kanade.tachiyomi.ui.reels.components

import eu.kanade.tachiyomi.animesource.model.WebLoginCookie
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class ReelsWebLoginDumpTest {

    @Test
    fun `prompt payload parses its request id and json`() {
        val dump = parseDumpPrompt("""__DUMP2__:42:{"kinde":"abc:c"}""")

        dump shouldBe DumpPrompt(42, """{"kinde":"abc:c"}""")
    }

    @Test
    fun `payloads without a numeric request id are rejected`() {
        parseDumpPrompt("""__DUMP2__:{"sid":"1"}""").shouldBeNull()
        parseDumpPrompt("__DUMP2__:").shouldBeNull()
        parseDumpPrompt("__DUMP2__:x:{}").shouldBeNull()
        parseDumpPrompt("not a dump").shouldBeNull()
        parseDumpPrompt(null).shouldBeNull()
    }

    @Test
    fun `dump js embeds the request id in the prompt bridge`() {
        val js = localStorageDumpJs(7)

        js shouldContain """window.prompt('__DUMP2__:7:'+JSON.stringify(obj))"""
        js shouldContain "window.kinde"
        localStorageDumpJs(8) shouldNotBe js
    }

    @Test
    fun `origin normalization drops path and default ports but keeps custom ones`() {
        originOf("https://www.example.com/watch/abc?x=1#frag") shouldBe "https://www.example.com"
        originOf("https://www.example.com:443/watch") shouldBe "https://www.example.com"
        originOf("http://example.com:80/") shouldBe "http://example.com"
        originOf("https://example.com:8443/x") shouldBe "https://example.com:8443"
        originOf("not a url").shouldBeNull()
        originOf(null).shouldBeNull()
    }

    @Test
    fun `seeded origins guess only the www-stripped api sibling for multi-part domains`() {
        seedOrigins("https://www.service.example.co.uk/watch") shouldBe setOf(
            "https://www.service.example.co.uk",
            "https://api.service.example.co.uk",
        )
    }

    @Test
    fun `source-supplied origins disable the api guess and are normalized`() {
        seedOrigins(
            "https://www.example.com/login",
            listOf("https://auth.example.invalid/oauth", "https://www.example.com:8443"),
        ) shouldBe setOf(
            "https://auth.example.invalid",
            "https://www.example.com:8443",
            "https://www.example.com",
        )
    }

    @Test
    fun `ipv4 and single-label hosts get no api sibling`() {
        seedOrigins("http://192.168.1.10:8080/app") shouldBe setOf("http://192.168.1.10:8080")
        seedOrigins("http://localhost:8080/") shouldBe setOf("http://localhost:8080")
    }

    @Test
    fun `api sibling keeps a non-default port`() {
        seedOrigins("https://www.example.com:8443/app") shouldBe setOf(
            "https://www.example.com:8443",
            "https://api.example.com:8443",
        )
    }

    @Test
    fun `legacy merge keeps the last value for duplicate names`() {
        legacyCookieMap(
            listOf(
                WebLoginCookie("https://web.example.invalid", "sid", "web"),
                WebLoginCookie("https://api.example.invalid", "sid", "api"),
                WebLoginCookie("https://api.example.invalid", "csrf", "x"),
            ),
        ) shouldBe mapOf("sid" to "api", "csrf" to "x")
    }
}
