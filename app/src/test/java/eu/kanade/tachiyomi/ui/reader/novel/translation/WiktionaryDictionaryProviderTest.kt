package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextTranslationErrorReason
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WiktionaryDictionaryProviderTest {

    private val server = MockWebServer()
    private lateinit var client: OkHttpClient
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeEach
    fun setup() {
        server.start()
        client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val originalUrl = originalRequest.url
                val newUrl = originalUrl.newBuilder()
                    .scheme("http")
                    .host(server.hostName)
                    .port(server.port)
                    .build()
                chain.proceed(
                    originalRequest.newBuilder()
                        .url(newUrl)
                        .header("X-Original-Host", originalUrl.host)
                        .build(),
                )
            }
            .build()
    }

    @AfterEach
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun `english word to russian definitions using mediawiki api`() = runTest {
        val extract = """
            Английский

            great

            Морфологические и синтаксические свойства
            great
            Прилагательное.

            Произношение
            МФА: [ɡɹeɪt]

            Семантические свойства

            Значение
             великий ◆ великий человек
             большой, огромный ◆ большой дом

            Синонимы
            large, big
        """.trimIndent()

        val jsonResponse = """
            {
              "query": {
                "pages": [
                  {
                    "pageid": 12345,
                    "ns": 0,
                    "title": "great",
                    "extract": ${Json.encodeToString(kotlinx.serialization.serializer(), extract)}
                  }
                ]
              }
            }
        """.trimIndent()

        server.enqueue(MockResponse().setBody(jsonResponse))

        val provider = WiktionaryDictionaryProvider(client, json, "ru")
        val outcome = provider.lookup(
            NovelDictionaryRequest(
                term = "great",
                targetLanguageCode = "ru",
                sourceLanguageHint = "en",
            ),
        )

        val success = outcome.shouldBeInstanceOf<NovelDictionaryProviderOutcome.Success>()
        success.result.providerFingerprint shouldBe "wiktionary:ru"
        success.result.entries.size shouldBe 1
        val entry = success.result.entries.first()
        entry.headword shouldBe "great"
        entry.pronunciation shouldBe "ɡɹeɪt"
        entry.partOfSpeech shouldBe "прил."
        entry.definitionsHtml shouldBe "• великий<br/>• большой, огромный"

        val request = server.takeRequest()
        request.requestUrl?.queryParameter("titles") shouldBe "great"
        request.requestUrl?.queryParameter("action") shouldBe "query"
    }

    @Test
    fun `russian word to english definitions using rest api`() = runTest {
        val jsonResponse = """
            {
              "Russian": [
                {
                  "partOfSpeech": "Noun",
                  "language": "Russian",
                  "definitions": [
                    {
                      "definition": "<b>слово</b> (speech sound)"
                    },
                    {
                      "definition": "promise"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        server.enqueue(MockResponse().setBody(jsonResponse))

        val provider = WiktionaryDictionaryProvider(client, json, "en")
        val outcome = provider.lookup(
            NovelDictionaryRequest(
                term = "слово",
                targetLanguageCode = "en",
                sourceLanguageHint = "ru",
            ),
        )

        val success = outcome.shouldBeInstanceOf<NovelDictionaryProviderOutcome.Success>()
        success.result.providerFingerprint shouldBe "wiktionary:en"
        success.result.entries.size shouldBe 1
        val entry = success.result.entries.first()
        entry.headword shouldBe "слово"
        entry.partOfSpeech shouldBe "Noun"
        entry.definitionsHtml shouldBe "• слово (speech sound)<br/>• promise"

        val request = server.takeRequest()
        request.path shouldContain "/api/rest_v1/page/definition/%D1%81%D0%BB%D0%BE%D0%B2%D0%BE"
    }

    @Test
    fun `japanese word to russian and fallback to english when russian is unavailable`() = runTest {
        // First request to ru.wiktionary.org fails with 404
        server.enqueue(MockResponse().setResponseCode(404))

        // Second request to en.wiktionary.org (fallback) succeeds
        val jsonResponse = """
            {
              "Japanese": [
                {
                  "partOfSpeech": "Noun",
                  "language": "Japanese",
                  "definitions": [
                    {
                      "definition": "book"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
        server.enqueue(MockResponse().setBody(jsonResponse))

        val onlineProvider = OnlineDictionaryProvider(
            client = client,
            json = json,
            fallbackLanguage = "en",
        )

        val outcome = onlineProvider.lookup(
            NovelDictionaryRequest(
                term = "本",
                targetLanguageCode = "ru", // User wants Russian
                sourceLanguageHint = "ja",
            ),
        )

        val success = outcome.shouldBeInstanceOf<NovelDictionaryProviderOutcome.Success>()
        success.result.providerFingerprint shouldBe "wiktionary:en" // fallback provider fingerprint
        val entry = success.result.entries.first()
        entry.definitionsHtml shouldBe "• book"

        server.requestCount shouldBe 2
        val firstRequest = server.takeRequest()
        firstRequest.getHeader("X-Original-Host") shouldBe "ru.wiktionary.org"

        val secondRequest = server.takeRequest()
        secondRequest.getHeader("X-Original-Host") shouldBe "en.wiktionary.org"
    }

    @Test
    fun `edge cases empty query and too long query`() = runTest {
        val provider = WiktionaryDictionaryProvider(client, json, "en")

        val emptyOutcome = provider.lookup(
            NovelDictionaryRequest(
                term = "   ",
                targetLanguageCode = "en",
            ),
        )
        emptyOutcome.shouldBeInstanceOf<NovelDictionaryProviderOutcome.Unavailable>()
            .reason shouldBe NovelSelectedTextTranslationErrorReason.EmptySelection

        val longOutcome = provider.lookup(
            NovelDictionaryRequest(
                term = "a".repeat(100),
                targetLanguageCode = "en",
            ),
        )
        longOutcome.shouldBeInstanceOf<NovelDictionaryProviderOutcome.Unavailable>()
            .reason shouldBe NovelSelectedTextTranslationErrorReason.TooLongSelection

        server.requestCount shouldBe 0
    }

    @Test
    fun `sanitization of HTML and Wiktionary templates and wiki links`() = runTest {
        // We will test sanitization by mocking a REST response containing various templates, HTML, and links
        val jsonResponse = """
            {
              "English": [
                {
                  "partOfSpeech": "Verb",
                  "language": "English",
                  "definitions": [
                    {
                      "definition": "To &lt;i&gt;resolve&lt;/i&gt; {{l|en|nested}} templates like {{taxlink|Canis lupus|species}}."
                    },
                    {
                      "definition": "A {{gloss|very}} cool [[link|piped link]] and a [[simple link]] here."
                    },
                    {
                      "definition": "Empty parentheses {{lb|en|}} and {{lb|en|transitive|}} and {{lb|en| , }} should be cleaned."
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        server.enqueue(MockResponse().setBody(jsonResponse))

        val provider = WiktionaryDictionaryProvider(client, json, "en")
        val outcome = provider.lookup(
            NovelDictionaryRequest(
                term = "test",
                targetLanguageCode = "en",
                sourceLanguageHint = "en",
            ),
        )

        val success = outcome.shouldBeInstanceOf<NovelDictionaryProviderOutcome.Success>()
        val entry = success.result.entries.first()

        // Assertions verifying that output does not contain raw HTML, wiki syntax, or JSON characters
        entry.definitionsHtml shouldNotContain "<i>"
        entry.definitionsHtml shouldNotContain "</i>"
        entry.definitionsHtml shouldNotContain "{{"
        entry.definitionsHtml shouldNotContain "}}"
        entry.definitionsHtml shouldNotContain "[["
        entry.definitionsHtml shouldNotContain "]]"
        entry.definitionsHtml shouldNotContain "&lt;"
        entry.definitionsHtml shouldNotContain "&gt;"

        // Verify the exact resolved texts
        entry.definitionsHtml shouldContain "To resolve nested templates like Canis lupus"
        entry.definitionsHtml shouldContain "A (very) cool piped link and a simple link here"
        entry.definitionsHtml shouldContain "Empty parentheses and (transitive) and should be cleaned"
    }

    @Test
    fun `error resiliency 403 429 501`() = runTest {
        val provider = WiktionaryDictionaryProvider(client, json, "en")

        // 1. Test 403
        server.enqueue(MockResponse().setResponseCode(403))
        val outcome403 = provider.lookup(NovelDictionaryRequest("test", "en"))
        val err403 = outcome403.shouldBeInstanceOf<NovelDictionaryProviderOutcome.Unavailable>()
        val msg403 = err403.reason.shouldBeInstanceOf<NovelSelectedTextTranslationErrorReason.BackendUnavailable>()
        msg403.message shouldContain "403"

        // 2. Test 501
        server.enqueue(MockResponse().setResponseCode(501))
        val outcome501 = provider.lookup(NovelDictionaryRequest("test", "en"))
        val err501 = outcome501.shouldBeInstanceOf<NovelDictionaryProviderOutcome.Unavailable>()
        val msg501 = err501.reason.shouldBeInstanceOf<NovelSelectedTextTranslationErrorReason.BackendUnavailable>()
        msg501.message shouldContain "501"

        // 3. Test 429
        server.enqueue(MockResponse().setResponseCode(429))
        val outcome429 = provider.lookup(NovelDictionaryRequest("test", "en"))
        val err429 = outcome429.shouldBeInstanceOf<NovelDictionaryProviderOutcome.Unavailable>()
        err429.reason.shouldBeInstanceOf<NovelSelectedTextTranslationErrorReason.Cooldown>()
    }

    @Test
    fun `smart casing retry for uppercase words`() = runTest {
        // First request for "Понял" returns 404/empty
        server.enqueue(MockResponse().setResponseCode(404))

        // Second request (retry) for "понял" succeeds
        val extract = """
            Русский

            понял

            Значение
             прош. вр. от понять
        """.trimIndent()
        val jsonResponse = """
            {
              "query": {
                "pages": [
                  {
                    "pageid": 123,
                    "ns": 0,
                    "title": "понял",
                    "extract": ${Json.encodeToString(kotlinx.serialization.serializer(), extract)}
                  }
                ]
              }
            }
        """.trimIndent()
        server.enqueue(MockResponse().setBody(jsonResponse))

        val provider = WiktionaryDictionaryProvider(client, json, "ru")
        val outcome = provider.lookup(
            NovelDictionaryRequest(
                term = "Понял",
                targetLanguageCode = "ru",
                sourceLanguageHint = "ru",
            ),
        )

        val success = outcome.shouldBeInstanceOf<NovelDictionaryProviderOutcome.Success>()
        success.result.entries.first().definitionsHtml shouldBe "• прош. вр. от понять"

        // Verify two requests were made
        server.requestCount shouldBe 2

        val firstRequest = server.takeRequest()
        firstRequest.requestUrl?.queryParameter("titles") shouldBe "Понял"

        val secondRequest = server.takeRequest()
        secondRequest.requestUrl?.queryParameter("titles") shouldBe "понял"
    }

    @Test
    fun `mediawiki api uses redirects parameter`() = runTest {
        val extract = "Русский\n\nпонять\n\nЗначение\n понимать"
        val jsonResponse = """
            {
              "query": {
                "pages": [
                  {
                    "pageid": 124,
                    "ns": 0,
                    "title": "понять",
                    "extract": ${Json.encodeToString(kotlinx.serialization.serializer(), extract)}
                  }
                ]
              }
            }
        """.trimIndent()
        server.enqueue(MockResponse().setBody(jsonResponse))

        val provider = WiktionaryDictionaryProvider(client, json, "ru")
        val outcome = provider.lookup(
            NovelDictionaryRequest(
                term = "понять",
                targetLanguageCode = "ru",
                sourceLanguageHint = "ru",
            ),
        )

        outcome.shouldBeInstanceOf<NovelDictionaryProviderOutcome.Success>()

        val request = server.takeRequest()
        request.requestUrl?.queryParameter("redirects") shouldBe "1"
    }
}
