package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.ui.reader.novel.NovelDictionaryEntry
import eu.kanade.tachiyomi.ui.reader.novel.NovelDictionaryResult
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextTranslationErrorReason
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.atomic.AtomicLong

/**
 * Online dictionary backed by the Wiktionary.
 *
 * Two backend modes are supported based on [languageCode]:
 *
 *  - **English ("en")** — Wiktionary REST API (`en.wiktionary.org/api/rest_v1/page/definition/{term}`).
 *    Returns structured JSON with definitions in English. This is the only edition that implements
 *    the REST endpoint; other editions return HTTP 501.
 *
 *  - **Other languages ("ru", "de", "fr", …)** — MediaWiki Action API
 *    (`{lang}.wiktionary.org/w/api.php?action=query&prop=extracts&explaintext=true`).
 *    Returns plain-text article content in the target language. The relevant language section
 *    (matching [NovelDictionaryRequest.sourceLanguageHint]) is extracted and parsed into entries.
 */
class WiktionaryDictionaryProvider(
    private val client: OkHttpClient,
    private val json: Json,
    private val languageCode: String,
) : NovelDictionaryProvider {

    override val fingerprint: String = "wiktionary:" + languageCode.lowercase()

    private val cooldownUntil = AtomicLong(0L)

    private val useMediaWikiApi: Boolean = languageCode.lowercase() != "en"

    override suspend fun lookup(request: NovelDictionaryRequest): NovelDictionaryProviderOutcome {
        val term = request.term.trim()
        if (term.isEmpty()) {
            return NovelDictionaryProviderOutcome.Unavailable(
                NovelSelectedTextTranslationErrorReason.EmptySelection,
            )
        }
        if (term.length > MAX_TERM_LENGTH) {
            return NovelDictionaryProviderOutcome.Unavailable(
                NovelSelectedTextTranslationErrorReason.TooLongSelection,
            )
        }

        val now = System.currentTimeMillis()
        val until = cooldownUntil.get()
        if (now < until) {
            val remaining = ((until - now) / 1000L).toInt().coerceAtLeast(1)
            return NovelDictionaryProviderOutcome.Unavailable(
                NovelSelectedTextTranslationErrorReason.Cooldown(remaining),
            )
        }

        val firstOutcome = executeLookup(term, request)
        if (firstOutcome is NovelDictionaryProviderOutcome.Success) {
            return firstOutcome
        }

        // Smart Casing Retry: if the first lookup failed/empty and the term starts with an uppercase letter,
        // retry with the first letter decapitalized (e.g., "Понял" -> "понял", "Понять" -> "понять").
        if (term.isNotEmpty() && term[0].isUpperCase()) {
            val decapitalized = term.replaceFirstChar { it.lowercase() }
            val secondOutcome = executeLookup(decapitalized, request)
            if (secondOutcome is NovelDictionaryProviderOutcome.Success) {
                return secondOutcome
            }
        }

        return firstOutcome
    }

    private suspend fun executeLookup(term: String, request: NovelDictionaryRequest): NovelDictionaryProviderOutcome {
        return if (useMediaWikiApi) {
            lookupMediaWiki(term, request)
        } else {
            lookupRestApi(term, request)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REST API path (en.wiktionary.org)
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun lookupRestApi(
        term: String,
        request: NovelDictionaryRequest,
    ): NovelDictionaryProviderOutcome {
        return try {
            val body = client.newCall(buildRestRequest(term)).await().use { response ->
                when {
                    response.code == 404 -> return NovelDictionaryProviderOutcome.Unavailable(
                        NovelSelectedTextTranslationErrorReason.ParserFailure,
                    )
                    response.code == 429 -> {
                        cooldownUntil.set(System.currentTimeMillis() + COOLDOWN_MILLIS)
                        return NovelDictionaryProviderOutcome.Unavailable(
                            NovelSelectedTextTranslationErrorReason.Cooldown(
                                (COOLDOWN_MILLIS / 1000L).toInt(),
                            ),
                        )
                    }
                    response.code == 403 -> {
                        return NovelDictionaryProviderOutcome.Unavailable(
                            NovelSelectedTextTranslationErrorReason.BackendUnavailable("HTTP 403: Forbidden"),
                        )
                    }
                    response.code == 501 -> {
                        return NovelDictionaryProviderOutcome.Unavailable(
                            NovelSelectedTextTranslationErrorReason.BackendUnavailable("HTTP 501: Not Implemented"),
                        )
                    }
                    !response.isSuccessful -> return NovelDictionaryProviderOutcome.Unavailable(
                        NovelSelectedTextTranslationErrorReason.BackendUnavailable(
                            "HTTP " + response.code,
                        ),
                    )
                    else -> response.body?.string().orEmpty()
                }
            }

            val entries = parseRestResponse(body, term, request.sourceLanguageHint)
            if (entries.isEmpty()) {
                NovelDictionaryProviderOutcome.Unavailable(
                    NovelSelectedTextTranslationErrorReason.ParserFailure,
                )
            } else {
                NovelDictionaryProviderOutcome.Success(
                    NovelDictionaryResult(
                        entries = entries,
                        providerFingerprint = fingerprint,
                        attribution = "Source: Wiktionary (CC BY-SA)",
                    ),
                )
            }
        } catch (e: Exception) {
            NovelDictionaryProviderOutcome.Unavailable(
                NovelSelectedTextTranslationErrorReason.NetworkFailure(e.message),
            )
        }
    }

    private fun buildRestRequest(term: String): Request {
        // The Wiktionary REST `page/definition` endpoint is only implemented on the English
        // Wiktionary; other editions (e.g. ru.wiktionary.org) return HTTP 501. Always query
        // en.wiktionary.org — the response contains definitions for every language, keyed by
        // English language name, so we can still serve non-English terms.
        // We use addPathSegment(term) so OkHttp encodes it exactly once, preventing double URL encoding.
        val url = ("https://" + REST_API_HOST)
            .toHttpUrl()
            .newBuilder()
            .addPathSegments("api/rest_v1/page/definition")
            .addPathSegment(term)
            .addQueryParameter("redirect", "true")
            .build()
        return Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
    }

    private fun parseRestResponse(body: String, term: String, sourceLangHint: String?): List<NovelDictionaryEntry> {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }
            .getOrNull() ?: return emptyList()
        val result = mutableListOf<NovelDictionaryEntry>()

        val expectedLangName = getExpectedLanguageNameForRestApi(sourceLangHint)

        var filteredRoot: Map<String, kotlinx.serialization.json.JsonElement> = root
        if (expectedLangName != null) {
            val matchedKey = root.keys.firstOrNull { it.equals(expectedLangName, ignoreCase = true) }
            if (matchedKey != null) {
                filteredRoot = mapOf(matchedKey to root[matchedKey]!!)
            }
        }

        for ((_, group) in filteredRoot) {
            val groupArray = (group as? JsonArray) ?: continue
            for (pos in groupArray) {
                val obj = (pos as? JsonObject) ?: continue
                val partOfSpeech = obj["partOfSpeech"]?.jsonPrimitive?.contentOrNullSafe()
                val language = obj["language"]?.jsonPrimitive?.contentOrNullSafe()
                val defs = (obj["definitions"] as? JsonArray) ?: continue
                val rendered = defs.mapNotNull { def ->
                    val defObj = (def as? JsonObject) ?: return@mapNotNull null
                    val html = defObj["definition"]?.jsonPrimitive?.contentOrNullSafe()
                        ?: return@mapNotNull null
                    val clean = sanitizeDefinition(html)
                    clean.ifEmpty { null }
                }
                if (rendered.isEmpty()) continue
                val html = rendered.joinToString(separator = "<br/>") { "\u2022 " + it }
                result += NovelDictionaryEntry(
                    headword = term,
                    pronunciation = null,
                    partOfSpeech = partOfSpeech,
                    definitionsHtml = html,
                    sourceLanguage = language ?: sourceLangHint ?: languageCode,
                )
            }
        }
        return result
    }

    /** Maps source-language hint to the English language name used as key in the REST API response. */
    private fun getExpectedLanguageNameForRestApi(sourceLanguage: String?): String? {
        val normalized = sourceLanguage?.lowercase()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return when (normalized) {
            "ja", "japanese", "японский" -> "Japanese"
            "zh", "zh-cn", "zh-tw", "chinese", "китайский" -> "Chinese"
            "ko", "korean", "корейский" -> "Korean"
            "ru", "russian", "русский" -> "Russian"
            "en", "english", "английский" -> "English"
            "es", "spanish", "испанский" -> "Spanish"
            "fr", "french", "французский" -> "French"
            "de", "german", "немецкий" -> "German"
            "it", "italian", "итальянский" -> "Italian"
            "pt", "portuguese", "португальский" -> "Portuguese"
            else -> null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MediaWiki Action API path (any other Wiktionary edition)
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun lookupMediaWiki(
        term: String,
        request: NovelDictionaryRequest,
    ): NovelDictionaryProviderOutcome {
        return try {
            val body = client.newCall(buildMediaWikiRequest(term)).await().use { response ->
                when {
                    response.code == 404 -> return NovelDictionaryProviderOutcome.Unavailable(
                        NovelSelectedTextTranslationErrorReason.ParserFailure,
                    )
                    response.code == 429 -> {
                        cooldownUntil.set(System.currentTimeMillis() + COOLDOWN_MILLIS)
                        return NovelDictionaryProviderOutcome.Unavailable(
                            NovelSelectedTextTranslationErrorReason.Cooldown(
                                (COOLDOWN_MILLIS / 1000L).toInt(),
                            ),
                        )
                    }
                    response.code == 403 -> {
                        return NovelDictionaryProviderOutcome.Unavailable(
                            NovelSelectedTextTranslationErrorReason.BackendUnavailable("HTTP 403: Forbidden"),
                        )
                    }
                    response.code == 501 -> {
                        return NovelDictionaryProviderOutcome.Unavailable(
                            NovelSelectedTextTranslationErrorReason.BackendUnavailable("HTTP 501: Not Implemented"),
                        )
                    }
                    !response.isSuccessful -> return NovelDictionaryProviderOutcome.Unavailable(
                        NovelSelectedTextTranslationErrorReason.BackendUnavailable(
                            "HTTP " + response.code,
                        ),
                    )
                    else -> response.body?.string().orEmpty()
                }
            }

            val entries = parseMediaWikiResponse(body, term, request.sourceLanguageHint)
            if (entries.isEmpty()) {
                NovelDictionaryProviderOutcome.Unavailable(
                    NovelSelectedTextTranslationErrorReason.ParserFailure,
                )
            } else {
                val attribution = when (languageCode.lowercase()) {
                    "ru" -> "Источник: Викисловарь (CC BY-SA)"
                    "de" -> "Quelle: Wiktionary (CC BY-SA)"
                    "fr" -> "Source : Wiktionnaire (CC BY-SA)"
                    else -> "Source: Wiktionary (CC BY-SA)"
                }
                NovelDictionaryProviderOutcome.Success(
                    NovelDictionaryResult(
                        entries = entries,
                        providerFingerprint = fingerprint,
                        attribution = attribution,
                    ),
                )
            }
        } catch (e: Exception) {
            NovelDictionaryProviderOutcome.Unavailable(
                NovelSelectedTextTranslationErrorReason.NetworkFailure(e.message),
            )
        }
    }

    private fun buildMediaWikiRequest(term: String): Request {
        val host = languageCode.lowercase() + ".wiktionary.org"
        val url = "https://$host/w/api.php"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("titles", term)
            .addQueryParameter("redirects", "1")
            .addQueryParameter("prop", "extracts")
            .addQueryParameter("explaintext", "true")
            .addQueryParameter("exsectionformat", "plain")
            .addQueryParameter("format", "json")
            .addQueryParameter("formatversion", "2")
            .build()
        return Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
    }

    /**
     * Parses the plain-text extract returned by the MediaWiki Action API.
     *
     * Structure of the extract (e.g., from ru.wiktionary.org for "great"):
     * ```
     * Английский
     *
     * great I
     *
     * Морфологические и синтаксические свойства
     * great
     * Прилагательное.
     *
     * Произношение
     * МФА: [ɡɹeit]
     *
     * Семантические свойства
     *
     * Значение
     *  большой ◆ example sentence
     *  удивительный ◆ ...
     *
     * Синонимы
     * large, big
     * ...
     * ```
     *
     * We find the section matching the source language, extract pronunciation (МФА),
     * part-of-speech, and definitions (lines under "Значение" up to the next heading).
     */
    private fun parseMediaWikiResponse(
        body: String,
        term: String,
        sourceLangHint: String?,
    ): List<NovelDictionaryEntry> {
        // The extract is inside pages[0].extract in formatversion=2 response
        val extract = runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            val pages = root["query"]?.jsonObject?.get("pages")
            // formatversion=2 returns pages as array
            val pageArr = pages as? JsonArray
            val firstPage = pageArr?.firstOrNull() as? JsonObject
            firstPage?.get("extract")?.jsonPrimitive?.contentOrNullSafe()
        }.getOrNull() ?: return emptyList()

        if (extract.isBlank()) return emptyList()

        // Find heading matching the source language in the Wiktionary edition's language
        val langHeading = getExpectedSectionHeadingForMediaWiki(sourceLangHint)

        // Split the extract into top-level language sections.
        // Sections are separated by triple newlines in exsectionformat=plain.
        val lines = extract.lines()
        val result = mutableListOf<NovelDictionaryEntry>()

        // Find the start of the matching language section
        var inTargetSection = langHeading == null // if no hint, use everything
        var sectionDepth = 0

        var pronunciation: String? = null
        var partOfSpeech: String? = null
        val definitions = mutableListOf<String>()
        var inDefinitionsBlock = false
        var inPronunciationBlock = false
        var inPosBlock = false

        val posKeywords = getPosKeywords()

        for (line in lines) {
            val trimmed = line.trim()

            // Detect top-level language section heading (single non-empty line after blank lines
            // that matches a known language name pattern)
            if (langHeading != null && trimmed.equals(langHeading, ignoreCase = true)) {
                // Save any accumulated entries from previous section
                if (definitions.isNotEmpty()) {
                    result += buildEntry(term, pronunciation, partOfSpeech, definitions, sourceLangHint)
                }
                inTargetSection = true
                sectionDepth = 0
                pronunciation = null
                partOfSpeech = null
                definitions.clear()
                inDefinitionsBlock = false
                inPronunciationBlock = false
                inPosBlock = false
                continue
            }

            // If we're in a different language section, skip
            if (!inTargetSection) continue

            // Detect section break into another language (non-empty line that matches another heading)
            if (langHeading != null && trimmed.isNotEmpty() && isOtherLanguageHeading(trimmed, langHeading)) {
                break
            }

            // Detect subsection headings
            when {
                trimmed.startsWith("Произношение") ||
                    trimmed.startsWith("Pronunciation") ||
                    trimmed.startsWith("Aussprache") ||
                    trimmed.startsWith("Prononciation") -> {
                    inPronunciationBlock = true
                    inDefinitionsBlock = false
                    inPosBlock = false
                    continue
                }

                trimmed.startsWith("Значение") ||
                    trimmed.startsWith("Meaning") ||
                    trimmed.startsWith("Definitions") ||
                    trimmed.startsWith("Bedeutungen") ||
                    trimmed.startsWith("Sens") -> {
                    inDefinitionsBlock = true
                    inPronunciationBlock = false
                    inPosBlock = false
                    continue
                }

                // Any other known heading terminates the definitions block
                isKnownSubsectionHeading(trimmed) -> {
                    if (inDefinitionsBlock && definitions.isNotEmpty()) {
                        result += buildEntry(term, pronunciation, partOfSpeech, definitions, sourceLangHint)
                        pronunciation = null
                        partOfSpeech = null
                        definitions.clear()
                    }
                    inDefinitionsBlock = false
                    inPronunciationBlock = false
                    inPosBlock = trimmed.startsWith("Морфологические") || trimmed.startsWith("Morphological")
                    continue
                }
            }

            // Parse content lines
            when {
                inPronunciationBlock && (trimmed.startsWith("МФА:") || trimmed.startsWith("IPA:")) -> {
                    pronunciation = trimmed.removePrefix("МФА:").removePrefix("IPA:").trim()
                        .removeSurrounding("[", "]")
                        .removeSurrounding("/", "/")
                        .trim()
                }

                inPosBlock && trimmed.isNotEmpty() -> {
                    val detected = posKeywords.entries.firstOrNull { (keyword, _) ->
                        trimmed.contains(keyword, ignoreCase = true)
                    }?.value
                    if (detected != null && partOfSpeech == null) {
                        partOfSpeech = detected
                    }
                }

                inDefinitionsBlock && trimmed.isNotEmpty() -> {
                    // Lines may start with bullet ◆ or number; strip citation after ◆
                    val rawDef = trimmed
                        .trimStart { it == ' ' || it == '•' || it == '◆' || it == '-' || it.isDigit() || it == '.' }
                        .trim()
                    val defText = if (rawDef.contains("◆")) {
                        rawDef.substringBefore("◆").trim()
                    } else {
                        rawDef
                    }
                    val sanitized = sanitizeDefinition(defText)
                    if (sanitized.isNotEmpty()) {
                        definitions += sanitized
                    }
                }
            }
        }

        // Flush remaining definitions
        if (inTargetSection && definitions.isNotEmpty()) {
            result += buildEntry(term, pronunciation, partOfSpeech, definitions, sourceLangHint)
        }

        return result
    }

    private fun buildEntry(
        term: String,
        pronunciation: String?,
        partOfSpeech: String?,
        definitions: List<String>,
        sourceLangHint: String?,
    ): NovelDictionaryEntry {
        val html = definitions.joinToString("<br/>") { "\u2022 $it" }
        return NovelDictionaryEntry(
            headword = term,
            pronunciation = pronunciation,
            partOfSpeech = partOfSpeech,
            definitionsHtml = html,
            sourceLanguage = sourceLangHint ?: languageCode,
        )
    }

    /**
     * Maps the source-language hint to the section heading used in the target Wiktionary edition.
     * e.g., for ru.wiktionary.org: "en" → "Английский", "ja" → "Японский".
     */
    private fun getExpectedSectionHeadingForMediaWiki(sourceLanguage: String?): String? {
        val normalized = sourceLanguage?.lowercase()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return when (languageCode.lowercase()) {
            "ru" -> when (normalized) {
                "en", "english", "английский" -> "Английский"
                "ru", "russian", "русский" -> "Русский"
                "ja", "japanese", "японский" -> "Японский"
                "zh", "zh-cn", "zh-tw", "chinese", "китайский" -> "Китайский"
                "ko", "korean", "корейский" -> "Корейский"
                "de", "german", "немецкий" -> "Немецкий"
                "fr", "french", "французский" -> "Французский"
                "es", "spanish", "испанский" -> "Испанский"
                "it", "italian", "итальянский" -> "Итальянский"
                "pt", "portuguese", "португальский" -> "Португальский"
                else -> null
            }
            "de" -> when (normalized) {
                "en", "english" -> "Englisch"
                "de", "german", "deutsch" -> "Deutsch"
                "ja", "japanese" -> "Japanisch"
                "zh", "chinese" -> "Chinesisch"
                "ru", "russian" -> "Russisch"
                "fr", "french" -> "Französisch"
                "es", "spanish" -> "Spanisch"
                else -> null
            }
            "fr" -> when (normalized) {
                "en", "english" -> "Anglais"
                "fr", "french" -> "Français"
                "de", "german" -> "Allemand"
                "ja", "japanese" -> "Japonais"
                "zh", "chinese" -> "Chinois"
                "ru", "russian" -> "Russe"
                else -> null
            }
            else -> null // For unknown editions, show all sections
        }
    }

    private fun isOtherLanguageHeading(line: String, currentHeading: String): Boolean {
        if (line.equals(currentHeading, ignoreCase = true)) return false
        // Known multi-word language names in common Wiktionary editions
        val headings = when (languageCode.lowercase()) {
            "ru" -> setOf(
                "Английский", "Русский", "Японский", "Китайский", "Корейский",
                "Немецкий", "Французский", "Испанский", "Итальянский", "Португальский",
                "Украинский", "Польский", "Нидерландский", "Шведский", "Турецкий",
                "Арабский", "Латинский", "Среднеанглийский",
            )
            "de" -> setOf(
                "Englisch", "Deutsch", "Japanisch", "Chinesisch", "Russisch",
                "Französisch", "Spanisch", "Italienisch", "Portugiesisch",
            )
            "fr" -> setOf(
                "Anglais", "Français", "Allemand", "Japonais", "Chinois",
                "Russe", "Espagnol", "Italien", "Portugais",
            )
            else -> emptySet()
        }
        return headings.any { it.equals(line, ignoreCase = true) }
    }

    private fun isKnownSubsectionHeading(line: String): Boolean {
        return KNOWN_SUBSECTION_HEADINGS.any { line.startsWith(it, ignoreCase = true) }
    }

    private fun getPosKeywords(): Map<String, String> = when (languageCode.lowercase()) {
        "ru" -> mapOf(
            "Прилагательное" to "прил.",
            "Существительное" to "сущ.",
            "Глагол" to "гл.",
            "Наречие" to "нареч.",
            "Предлог" to "предлог",
            "Союз" to "союз",
            "Местоимение" to "мест.",
            "Числительное" to "числ.",
        )
        "de" -> mapOf(
            "Adjektiv" to "Adj.",
            "Substantiv" to "Subst.",
            "Verb" to "Verb",
            "Adverb" to "Adv.",
        )
        else -> mapOf(
            "Adjective" to "adj.",
            "Noun" to "n.",
            "Verb" to "v.",
            "Adverb" to "adv.",
            "Preposition" to "prep.",
        )
    }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? {
        return runCatching { this.content }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun sanitizeDefinition(text: String): String {
        // 1. Strip all HTML and decode entities
        // First parse decodes any HTML entities (e.g. &lt;i&gt; -> <i>)
        val decoded = Jsoup.parse(text).text()
        // Second parse strips any HTML tags that were decoded or originally present
        var result = Jsoup.parse(decoded).text()

        // 2. Recursively resolve nested templates
        val templateRegex = Regex("\\{\\{([^{}]+)\\}\\}")
        var templateMatch = templateRegex.find(result)
        var tempIterations = 0
        while (templateMatch != null && tempIterations < 15) {
            result = result.replace(templateRegex) { matchResult ->
                val content = matchResult.groupValues[1]
                val parts = content.split('|').map { it.trim() }
                val templateName = parts[0].lowercase()
                val positionalParams = parts.drop(1).filter { !it.contains("=") && it.isNotEmpty() }

                when (templateName) {
                    "l", "link", "m", "mention" -> {
                        if (positionalParams.size >= 3 && positionalParams[2].isNotEmpty()) {
                            positionalParams[2]
                        } else if (positionalParams.size >= 2 && positionalParams[1].isNotEmpty()) {
                            positionalParams[1]
                        } else {
                            positionalParams.firstOrNull() ?: ""
                        }
                    }
                    "taxlink" -> {
                        positionalParams.firstOrNull() ?: ""
                    }
                    "gloss", "g" -> {
                        if (positionalParams.isNotEmpty()) {
                            "(${positionalParams.joinToString(", ")})"
                        } else {
                            ""
                        }
                    }
                    "lb", "label", "lbl" -> {
                        val labels = positionalParams.drop(1)
                        if (labels.isNotEmpty()) {
                            "(${labels.joinToString(", ")})"
                        } else {
                            ""
                        }
                    }
                    "w", "wikipedia" -> {
                        if (positionalParams.size >= 3 && positionalParams[2].isNotEmpty()) {
                            positionalParams[2]
                        } else if (positionalParams.size >= 2 && positionalParams[1].isNotEmpty()) {
                            positionalParams[1]
                        } else {
                            positionalParams.firstOrNull() ?: ""
                        }
                    }
                    "non-gloss definition", "n-g" -> {
                        positionalParams.firstOrNull() ?: ""
                    }
                    else -> {
                        positionalParams.lastOrNull() ?: ""
                    }
                }
            }
            templateMatch = templateRegex.find(result)
            tempIterations++
        }

        // 3. Resolve piped and simple wiki links
        val linkRegex = Regex("\\[\\[([^\\[\\]]+)\\]\\]")
        var linkMatch = linkRegex.find(result)
        var linkIterations = 0
        while (linkMatch != null && linkIterations < 15) {
            result = result.replace(linkRegex) { matchResult ->
                val content = matchResult.groupValues[1]
                val parts = content.split('|')
                parts.last().trim()
            }
            linkMatch = linkRegex.find(result)
            linkIterations++
        }

        // 4. Clean up empty parentheses/brackets (like (), [], ( , ), etc.)
        var prevResult: String
        var cleanIterations = 0
        val emptyParensRegex = Regex("\\(\\s*[,;\\s]*\\)")
        val emptyBracketsRegex = Regex("\\[\\s*[,;\\s]*\\]")
        do {
            prevResult = result
            result = result.replace(Regex("\\(\\s*[,;\\s]+"), "(")
            result = result.replace(Regex("[,;\\s]+\\)"), ")")
            result = result.replace(Regex("\\[\\s*[,;\\s]+"), "[")
            result = result.replace(Regex("[,;\\s]+\\]"), "]")
            result = result.replace(emptyParensRegex, "")
            result = result.replace(emptyBracketsRegex, "")
            cleanIterations++
        } while (result != prevResult && cleanIterations < 5)

        // 5. Collapse multiple spaces
        result = result.replace(Regex("\\s+"), " ")

        // 6. Trim leading/trailing whitespace and punctuation
        result = result.trim { it == ',' || it == ';' || it == ':' || it == '-' || it.isWhitespace() }

        return result
    }

    private companion object {
        const val MAX_TERM_LENGTH = 80
        const val COOLDOWN_MILLIS = 60_000L

        // The REST `page/definition` endpoint only exists on the English Wiktionary.
        const val REST_API_HOST = "en.wiktionary.org"
        const val USER_AGENT =
            "Tadami-Aniyomi-fork/dictionary (https://github.com/andarcanum/Tadami-Aniyomi-fork)"

        val KNOWN_SUBSECTION_HEADINGS = setOf(
            // Russian
            "Морфологические", "Семантические", "Синонимы", "Антонимы", "Гиперонимы",
            "Гипонимы", "Родственные слова", "Этимология", "Фразеологизмы",
            "Перевод", "Библиография",
            // German
            "Grammatik", "Synonyme", "Antonyme", "Herkunft", "Übersetzungen", "Bedeutungen",
            // French
            "Synonymes", "Antonymes", "Étymologie", "Traductions",
            // Generic
            "Synonyms", "Antonyms", "Etymology", "Translations", "Pronunciation",
            "Derived terms", "Related terms",
        )
    }
}
