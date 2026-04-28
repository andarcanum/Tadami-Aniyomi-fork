package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.extension.novel.normalizeNovelLang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class GoogleTranslationService(
    private val client: OkHttpClient,
    private val translateUrl: HttpUrl = DEFAULT_TRANSLATE_URL,
    private val userAgent: String = DEFAULT_USER_AGENT,
    private val maxChunkChars: Int = DEFAULT_MAX_CHUNK_CHARS,
    private val postThresholdChars: Int = DEFAULT_POST_THRESHOLD_CHARS,
    private val maxDirectTextChars: Int = DEFAULT_MAX_DIRECT_TEXT_CHARS,
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun translateSingle(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
        retryCount: Int = DEFAULT_RETRY_COUNT,
    ): String? = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext text

        val normalizedSource = normalizeSourceLanguage(sourceLanguage)
        val normalizedTarget = normalizeTargetLanguage(targetLanguage)
        if (normalizedTarget.isBlank()) return@withContext null

        if (text.length > maxDirectTextChars) {
            return@withContext translateLongText(text, normalizedSource, normalizedTarget)
        }

        var lastFailure: Throwable? = null
        repeat(retryCount) { attempt ->
            try {
                val request = buildSingleTranslationRequest(
                    text = text,
                    sourceLanguage = normalizedSource,
                    targetLanguage = normalizedTarget,
                )
                client.newCall(request).execute().use { response ->
                    val body = response.body.string()
                    if (!response.isSuccessful || body.isBlank()) {
                        lastFailure = IllegalStateException("HTTP ${response.code}")
                        if (response.code == 429 && attempt < retryCount - 1) {
                            val retryAfterMs = response.header("Retry-After")
                                ?.toLongOrNull()
                                ?.times(1000L)
                                ?: (1_500L * (attempt + 1))
                            delay(retryAfterMs.coerceIn(1_000L, 15_000L))
                        }
                    } else {
                        parseTranslatedText(body)?.let { translated ->
                            if (translated.isNotBlank()) {
                                return@withContext translated
                            }
                        }
                    }
                }
            } catch (error: Throwable) {
                lastFailure = error
            }

            if (attempt < retryCount - 1) {
                delay(200L * (attempt + 1))
            }
        }

        lastFailure
        null
    }

    suspend fun translateBatch(
        texts: List<String>,
        params: GoogleTranslationParams,
        onLog: ((String) -> Unit)? = null,
        onProgress: ((TranslationPhase, Int) -> Unit)? = null,
    ): GoogleTranslationBatchResponse = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext GoogleTranslationBatchResponse(emptyMap())

        onProgress?.invoke(TranslationPhase.IDLE, 0)

        val normalizedSource = normalizeSourceLanguage(params.sourceLang)
        val normalizedTarget = normalizeTargetLanguage(params.targetLang)
        if (normalizedTarget.isBlank()) {
            return@withContext GoogleTranslationBatchResponse(emptyMap())
        }

        val translations = linkedMapOf<String, String>()
        val chunks = buildChunks(texts)

        coroutineScope {
            chunks.withIndex()
                .chunked(DEFAULT_MAX_PARALLEL_CHUNKS)
                .forEach { chunkGroup ->
                    chunkGroup.map { (chunkIndex, chunk) ->
                        async {
                            val percent = ((chunkIndex + 1) * 100) / chunks.size
                            onProgress?.invoke(TranslationPhase.TRANSLATING, percent)
                            val chunkChars = chunk.sumOf { it.second.length }
                            onLog?.invoke(
                                "Simple chunk ${chunkIndex + 1}/${chunks.size}: " +
                                    "paragraphs=${chunk.size}, chars=$chunkChars",
                            )
                            val wrappedRequest = chunk.joinToString("\n\n") { (index, text) -> "[$index]\n$text" }
                            val translatedBody = translateSingle(
                                text = wrappedRequest,
                                sourceLanguage = normalizedSource,
                                targetLanguage = normalizedTarget,
                            )

                            if (translatedBody == null) {
                                onLog?.invoke(
                                    "Simple chunk ${chunkIndex + 1}/${chunks.size}: chunk request failed, falling back",
                                )
                                fallbackChunk(
                                    chunk = chunk,
                                    sourceLanguage = normalizedSource,
                                    targetLanguage = normalizedTarget,
                                    translations = translations,
                                )
                                return@async
                            }

                            var applied = 0
                            chunk.forEach { (index, original) ->
                                val markerRegex = Regex(
                                    pattern = """^\[\s*$index\s*\.?\]\s*\n?(.*?)(?=\n*\[\s*\d+\s*\.?\]|\z)""",
                                    options = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE),
                                )
                                val result = markerRegex.find(translatedBody)?.groupValues?.getOrNull(1)?.trim()
                                if (result.isNullOrBlank()) {
                                    translateSingle(
                                        text = original,
                                        sourceLanguage = normalizedSource,
                                        targetLanguage = normalizedTarget,
                                    )?.let { fallback ->
                                        synchronized(translations) {
                                            translations[original] = fallback
                                        }
                                        applied += 1
                                    }
                                } else {
                                    synchronized(translations) {
                                        translations[original] = result
                                    }
                                    applied += 1
                                }
                            }
                            onLog?.invoke(
                                "Simple chunk ${chunkIndex + 1}/${chunks.size} applied: translated=$applied/${chunk.size}",
                            )
                        }
                    }.awaitAll()
                }
        }

        GoogleTranslationBatchResponse(
            translatedByText = translations,
            detectedSourceLanguage = if (normalizedSource == "auto") "auto" else normalizedSource,
        )
    }

    private suspend fun fallbackChunk(
        chunk: List<Pair<Int, String>>,
        sourceLanguage: String,
        targetLanguage: String,
        translations: MutableMap<String, String>,
    ) {
        chunk.forEach { (_, original) ->
            translateSingle(
                text = original,
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage,
            )?.let { translated ->
                synchronized(translations) {
                    translations[original] = translated
                }
            }
        }
    }

    private fun buildSingleTranslationRequest(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
    ): Request {
        return if (text.length > postThresholdChars) {
            val formBody = FormBody.Builder()
                .add("client", "gtx")
                .add("sl", sourceLanguage)
                .add("tl", targetLanguage)
                .add("dt", "t")
                .add("q", text)
                .build()
            Request.Builder()
                .url(translateUrl)
                .post(formBody)
                .addHeader("User-Agent", userAgent)
                .build()
        } else {
            val url = translateUrl.newBuilder()
                .addQueryParameter("client", "gtx")
                .addQueryParameter("sl", sourceLanguage)
                .addQueryParameter("tl", targetLanguage)
                .addQueryParameter("dt", "t")
                .addQueryParameter("q", text)
                .build()
            Request.Builder()
                .url(url)
                .addHeader("User-Agent", userAgent)
                .build()
        }
    }

    private fun parseTranslatedText(body: String): String? {
        return runCatching {
            val root = json.parseToJsonElement(body).jsonArray
            buildString {
                root.getOrNull(0)?.jsonArray?.forEach { item ->
                    append(item.jsonArray.getOrNull(0)?.jsonPrimitive?.contentOrNull.orEmpty())
                }
            }.trim().ifBlank { null }
        }.getOrNull()
    }

    private fun buildChunks(texts: List<String>): List<List<Pair<Int, String>>> {
        val chunks = mutableListOf<List<Pair<Int, String>>>()
        var currentChunk = mutableListOf<Pair<Int, String>>()
        var currentLength = 0

        texts.forEachIndexed { index, text ->
            val estimatedLength = text.length + 10
            if (currentChunk.isNotEmpty() && currentLength + estimatedLength > maxChunkChars) {
                chunks += currentChunk
                currentChunk = mutableListOf()
                currentLength = 0
            }

            currentChunk += index to text
            currentLength += estimatedLength
        }

        if (currentChunk.isNotEmpty()) {
            chunks += currentChunk
        }

        return chunks
    }

    private suspend fun translateLongText(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
    ): String? {
        val sentences = text.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
        if (sentences.size <= 1) return null

        val midpoint = sentences.size / 2
        val firstPart = sentences.take(midpoint).joinToString(" ")
        val secondPart = sentences.drop(midpoint).joinToString(" ")

        return coroutineScope {
            val first = async { translateSingle(firstPart, sourceLanguage, targetLanguage) }
            val second = async { translateSingle(secondPart, sourceLanguage, targetLanguage) }
            val firstResult = first.await()
            val secondResult = second.await()
            if (firstResult != null && secondResult != null) {
                "$firstResult $secondResult"
            } else {
                null
            }
        }
    }

    private fun normalizeSourceLanguage(sourceLanguage: String): String {
        return normalizeNovelLang(sourceLanguage).takeIf { it.isNotBlank() } ?: "auto"
    }

    private fun normalizeTargetLanguage(targetLanguage: String): String {
        return normalizeNovelLang(targetLanguage)
    }

    private companion object {
        val DEFAULT_TRANSLATE_URL: HttpUrl =
            "https://translate.googleapis.com/translate_a/single".toHttpUrl()
        const val DEFAULT_MAX_CHUNK_CHARS = 8_000
        const val DEFAULT_POST_THRESHOLD_CHARS = 500
        const val DEFAULT_MAX_DIRECT_TEXT_CHARS = 13_000
        const val DEFAULT_RETRY_COUNT = 3
        const val DEFAULT_MAX_PARALLEL_CHUNKS = 3
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro Build/UQ1A.240205.004) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.6834.83 Mobile Safari/537.36"
    }
}
