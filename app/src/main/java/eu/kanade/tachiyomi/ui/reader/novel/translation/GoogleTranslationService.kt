package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.extension.novel.normalizeNovelLang
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.ui.reader.novel.translation.GoogleSelectedTextTranslationParser
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class GoogleTranslationService(
    private val client: OkHttpClient,
    private val json: Json,
) {

    sealed interface TranslateOutcome {
        data class Success(val results: List<String?>) : TranslateOutcome
        data class RateLimited(val statusCode: Int, val body: String) : TranslateOutcome
        data class Error(val message: String) : TranslateOutcome
    }

    suspend fun translateBatch(
        segments: List<String>,
        params: GoogleTranslationParams,
        onLog: ((String) -> Unit)? = null,
    ): TranslateOutcome {
        if (segments.isEmpty()) return TranslateOutcome.Success(emptyList())

        val results = mutableListOf<String?>()
        for (segment in segments) {
            if (segment.isBlank()) {
                results.add(null)
                continue
            }
            when (val outcome = translateSegment(segment, params, onLog)) {
                is TranslateOutcome.Success -> results.addAll(outcome.results)
                is TranslateOutcome.RateLimited -> return outcome
                is TranslateOutcome.Error -> {
                    results.add(null)
                }
            }
        }
        return TranslateOutcome.Success(results)
    }

    private suspend fun translateSegment(
        text: String,
        params: GoogleTranslationParams,
        onLog: ((String) -> Unit)?,
    ): TranslateOutcome {
        val sourceLang = normalizeNovelLang(params.sourceLang).takeIf { it.isNotBlank() } ?: "auto"
        val targetLang = normalizeNovelLang(params.targetLang)
        if (targetLang.isBlank()) return TranslateOutcome.Success(listOf(null))

        val url = buildString {
            append("https://translate.googleapis.com/translate_a/single")
            append("?client=gtx")
            append("&sl=").append(sourceLang)
            append("&tl=").append(targetLang)
            append("&dt=t")
            append("&ie=UTF-8")
            append("&oe=UTF-8")
            append("&q=").append(java.net.URLEncoder.encode(text, "UTF-8"))
        }

        val request = okhttp3.Request.Builder()
            .url(url)
            .get()
            .build()

        val response = runCatching {
            client.newCall(request).await()
        }.getOrElse { error ->
            onLog?.invoke("Network error: ${error.message}")
            return TranslateOutcome.Error(error.message ?: "Unknown network error")
        }

        response.use {
            val rawBody = it.body.string()
            if (!it.isSuccessful) {
                if (it.code == 429) {
                    return TranslateOutcome.RateLimited(it.code, rawBody.take(200))
                }
                onLog?.invoke("HTTP ${it.code}: ${rawBody.take(200)}")
                return TranslateOutcome.Error("HTTP ${it.code}")
            }

            val parsed = GoogleSelectedTextTranslationParser.parse(rawBody, json)
            val translation = parsed?.translation?.takeIf { it.isNotBlank() }
            return TranslateOutcome.Success(listOf(translation))
        }
    }
}
