package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.extension.novel.normalizeNovelLang
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextTranslationCacheKey
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextTranslationErrorReason
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextTranslationResult
import eu.kanade.tachiyomi.ui.reader.novel.normalizeNovelSelectedText
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Request.Builder
import java.util.Locale

internal class GoogleUnofficialSelectedTextTranslationProvider(
    private val client: OkHttpClient,
    private val json: Json,
    private val endpointBaseUrl: String = DEFAULT_ENDPOINT_BASE_URL,
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
) : NovelSelectedTextTranslationProvider {

    override val fingerprint: String = DEFAULT_FINGERPRINT

    private val lock = Any()
    private val failureStates = mutableMapOf<NovelSelectedTextTranslationCacheKey, RequestFailureState>()

    override suspend fun translate(
        request: NovelSelectedTextTranslationRequest,
    ): NovelSelectedTextTranslationProviderOutcome {
        val normalizedSelection = normalizeNovelSelectedText(request.selectedText)
        if (normalizedSelection.isBlank()) {
            return NovelSelectedTextTranslationProviderOutcome.Unavailable(
                NovelSelectedTextTranslationErrorReason.EmptySelection,
            )
        }
        if (normalizedSelection.length > MAX_NORMALIZED_SELECTION_LENGTH) {
            return NovelSelectedTextTranslationProviderOutcome.Unavailable(
                NovelSelectedTextTranslationErrorReason.TooLongSelection,
            )
        }

        val normalizedTargetLanguage = request.targetLanguage.trim()
        if (normalizedTargetLanguage.isBlank()) {
            return NovelSelectedTextTranslationProviderOutcome.Unavailable(
                NovelSelectedTextTranslationErrorReason.BackendUnavailable("Target language is blank"),
            )
        }

        val requestKey = buildNovelSelectedTextTranslationRequestKey(
            providerFingerprint = fingerprint,
            request = request.copy(selectedText = normalizedSelection),
        )
        val now = clockMillis()

        stateOrNull(requestKey, now)?.cooldownUntilMillis?.let { cooldownUntilMillis ->
            return NovelSelectedTextTranslationProviderOutcome.Unavailable(
                NovelSelectedTextTranslationErrorReason.Cooldown(
                    remainingSeconds = remainingSeconds(now, cooldownUntilMillis),
                ),
            )
        }

        val httpRequest = buildRequest(
            selectedText = normalizedSelection,
            targetLanguage = normalizedTargetLanguage,
            sourceLanguageHint = request.sourceLanguageHint?.trim(),
        )

        val response = runCatching {
            client.newCall(httpRequest).await()
        }.getOrElse { error ->
            return registerNetworkFailure(
                requestKey = requestKey,
                error = error,
            )
        }

        response.use {
            val rawBody = it.body.string()
            if (!it.isSuccessful) {
                return handleHttpFailure(
                    requestKey = requestKey,
                    now = now,
                    code = it.code,
                    rawBody = rawBody,
                )
            }

            val parsed = GoogleSelectedTextTranslationParser.parse(
                rawBody = rawBody,
                json = json,
            ) ?: return handleParserFailure(requestKey, now, NovelSelectedTextTranslationErrorReason.ParserFailure)

            clearState(requestKey)
            return NovelSelectedTextTranslationProviderOutcome.Success(
                result = NovelSelectedTextTranslationResult(
                    translation = parsed.translation,
                    detectedSourceLanguage = parsed.detectedSourceLanguage,
                    providerFingerprint = fingerprint,
                ),
            )
        }
    }

    private fun buildRequest(
        selectedText: String,
        targetLanguage: String,
        sourceLanguageHint: String?,
    ): Request {
        val url = endpointBaseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("client", "gtx")
            .addQueryParameter("sl", sourceLanguageHint?.toGoogleLanguageCode().orEmpty().ifBlank { "auto" })
            .addQueryParameter("tl", targetLanguage.toGoogleLanguageCode().orEmpty())
            .addQueryParameter("dt", "t")
            .addQueryParameter("ie", "UTF-8")
            .addQueryParameter("oe", "UTF-8")
            .addQueryParameter("q", selectedText)
            .build()

        return Builder()
            .url(url)
            .get()
            .build()
    }

    private fun handleHttpFailure(
        requestKey: NovelSelectedTextTranslationCacheKey,
        now: Long,
        code: Int,
        rawBody: String,
    ): NovelSelectedTextTranslationProviderOutcome {
        val normalizedBody = rawBody.lowercase(Locale.ROOT)
        val isCooldownFailure =
            code == 429 ||
                normalizedBody.contains("captcha") ||
                normalizedBody.contains("unusual traffic") ||
                normalizedBody.contains("too many requests")

        return if (isCooldownFailure) {
            registerCooldown(requestKey, now)
        } else {
            handleParserFailure(requestKey, now, NovelSelectedTextTranslationErrorReason.ParserFailure)
        }
    }

    private fun registerNetworkFailure(
        requestKey: NovelSelectedTextTranslationCacheKey,
        error: Throwable,
    ): NovelSelectedTextTranslationProviderOutcome {
        // Network failures are transient. Do not contribute to the retry budget.
        val message = error.message?.takeIf { it.isNotBlank() }
            ?: error::class.simpleName
        return NovelSelectedTextTranslationProviderOutcome.Unavailable(
            NovelSelectedTextTranslationErrorReason.NetworkFailure(message),
        )
    }

    private fun handleParserFailure(
        requestKey: NovelSelectedTextTranslationCacheKey,
        now: Long,
        reason: NovelSelectedTextTranslationErrorReason,
    ): NovelSelectedTextTranslationProviderOutcome {
        val nextCount = synchronized(lock) {
            val current = failureStates[requestKey]
            val currentCount = current?.failureCount ?: 0
            currentCount + 1
        }

        if (nextCount >= MAX_CONSECUTIVE_FAILURES) {
            return registerCooldown(
                requestKey = requestKey,
                now = now,
            )
        }

        synchronized(lock) {
            failureStates[requestKey] = RequestFailureState(failureCount = nextCount)
        }
        return NovelSelectedTextTranslationProviderOutcome.Unavailable(reason)
    }

    private fun registerCooldown(
        requestKey: NovelSelectedTextTranslationCacheKey,
        now: Long,
    ): NovelSelectedTextTranslationProviderOutcome {
        val cooldownUntilMillis = now + COOLDOWN_MILLIS
        synchronized(lock) {
            failureStates[requestKey] = RequestFailureState(
                failureCount = 0,
                cooldownUntilMillis = cooldownUntilMillis,
            )
        }
        return NovelSelectedTextTranslationProviderOutcome.Unavailable(
            NovelSelectedTextTranslationErrorReason.Cooldown(
                remainingSeconds = remainingSeconds(now, cooldownUntilMillis),
            ),
        )
    }

    private fun clearState(requestKey: NovelSelectedTextTranslationCacheKey) {
        synchronized(lock) {
            failureStates.remove(requestKey)
        }
    }

    private fun stateOrNull(
        requestKey: NovelSelectedTextTranslationCacheKey,
        now: Long,
    ): RequestFailureState? {
        synchronized(lock) {
            val current = failureStates[requestKey] ?: return null
            val cooldownUntilMillis = current.cooldownUntilMillis
            if (cooldownUntilMillis != null && cooldownUntilMillis <= now) {
                failureStates.remove(requestKey)
                return null
            }
            return current
        }
    }

    private fun String.toGoogleLanguageCode(): String? {
        val normalized = normalizeNovelLang(this).trim()
        return normalized.takeIf { it.isNotBlank() }
    }

    private fun remainingSeconds(
        nowMillis: Long,
        cooldownUntilMillis: Long,
    ): Int {
        val remainingMillis = (cooldownUntilMillis - nowMillis).coerceAtLeast(0L)
        return ((remainingMillis + 999L) / 1000L).toInt().coerceAtLeast(1)
    }

    private data class RequestFailureState(
        val failureCount: Int,
        val cooldownUntilMillis: Long? = null,
    )

    companion object {
        private const val DEFAULT_ENDPOINT_BASE_URL = "https://translate.googleapis.com/translate_a/single"
        private const val DEFAULT_FINGERPRINT = "google-unofficial-translate_a_single"
        private const val MAX_CONSECUTIVE_FAILURES = 3
        private const val MAX_NORMALIZED_SELECTION_LENGTH = 320
        private const val COOLDOWN_MILLIS = 60_000L
    }
}
