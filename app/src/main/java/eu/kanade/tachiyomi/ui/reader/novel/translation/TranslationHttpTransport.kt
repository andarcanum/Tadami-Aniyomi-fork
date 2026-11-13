package eu.kanade.tachiyomi.ui.reader.novel.translation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import tachiyomi.domain.translation.model.TranslationAttemptException
import tachiyomi.domain.translation.model.TranslationFailure
import tachiyomi.domain.translation.model.TranslationFailureKind
import tachiyomi.domain.translation.service.TranslationRequestGate
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.ceil

internal object SharedTranslationRequests {
    val gate = TranslationRequestGate()
}

/** The clone shares connections, not verbose logging or credential-bearing redirects. */
internal fun OkHttpClient.translationSafeClient(): OkHttpClient = newBuilder().apply {
    followRedirects(false)
    followSslRedirects(false)
    retryOnConnectionFailure(false)
    interceptors().removeAll { it.javaClass.name == "okhttp3.logging.HttpLoggingInterceptor" }
    networkInterceptors().removeAll { it.javaClass.name == "okhttp3.logging.HttpLoggingInterceptor" }
    cache(null)
    if (this@translationSafeClient.callTimeoutMillis == 0) callTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
    if (this@translationSafeClient.readTimeoutMillis == 0) readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
}.build()

internal fun validateTranslationCredential(secret: String) {
    if (secret.length > 16_384 || secret.any { it.code !in 32..126 }) {
        throw TranslationAttemptException(
            TranslationFailure(TranslationFailureKind.CONFIGURATION, "Invalid translation credential"),
        )
    }
}

internal fun validatedTranslationUrl(url: String): String {
    val endpoint = url.toHttpUrlOrNull()

        ?: throw TranslationAttemptException(
            TranslationFailure(TranslationFailureKind.CONFIGURATION, "Invalid translation endpoint"),
        )
    if (endpoint.username.isNotEmpty() ||
        endpoint.password.isNotEmpty() ||
        endpoint.query != null ||
        endpoint.fragment != null
    ) {
        throw TranslationAttemptException(
            TranslationFailure(
                TranslationFailureKind.CONFIGURATION,
                "Translation endpoint must not contain credentials, query or fragment",
            ),
        )
    }
    return endpoint.toString()
}

internal fun translationQuotaKey(url: String): String {
    val endpoint = url.toHttpUrlOrNull() ?: throw TranslationAttemptException(
        TranslationFailure(TranslationFailureKind.CONFIGURATION, "Invalid translation endpoint"),
    )
    return "${endpoint.scheme}://${endpoint.host}:${endpoint.port}"
}

/** Exactly one cancellable HTTP attempt. The caller owns retry scheduling and durable state. */
internal class TranslationHttpTransport(
    client: OkHttpClient,
    private val json: Json,
    private val gate: TranslationRequestGate = SharedTranslationRequests.gate,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val client = client.translationSafeClient()

    suspend fun execute(
        request: Request,
        label: String,
        secret: String,
        quotaKeyOverride: String? = null,
        beforeAttempt: suspend (Request) -> Request = { it },
    ): String {
        val key = quotaKeyOverride ?: translationQuotaKey(request.url.toString())
        return gate.execute(key) {
            try {
                // Admission WAIT never reserves a budget or resolves an authentication header.
                val admitted = beforeAttempt(request)
                require(admitted.url == request.url && admitted.method == request.method) {
                    "Translation admission cannot change the frozen destination"
                }
                // OkHttp can follow a 503 Retry-After:0 even with connection retries disabled.
                // Mark a POST body one-shot so the single-attempt domain budget stays authoritative.
                val body = admitted.body
                val once = if (
                    body == null
                ) {
                    admitted
                } else {
                    admitted.newBuilder().method(
                        admitted.method,
                        object : okhttp3.RequestBody() {
                            override fun contentType() = body.contentType()
                            override fun contentLength() = body.contentLength()
                            override fun isOneShot() = true
                            override fun writeTo(sink: okio.BufferedSink) = body.writeTo(sink)
                        },
                    ).build()
                }
                val response = client.newCall(once).readResponse()
                if (response.code !in 200..299) {
                    throw TranslationAttemptException(
                        classifyTranslationHttpFailure(
                            code = response.code,
                            rawBody = response.body,
                            retryAfter = response.retryAfter,
                            now = clock(),
                            label = label,
                            secret = secret,
                            json = json,
                        ),
                    )
                }
                throwIfTranslationPayloadError(
                    raw = response.body,
                    json = json,
                    label = label,
                    secret = secret,
                    now = clock(),
                    retryAfter = response.retryAfter,
                )
                response.body
            } catch (error: CancellationException) {
                throw error
            } catch (error: TranslationAttemptException) {
                if (quotaKeyOverride == null && (
                        error.failure.kind == TranslationFailureKind.TRANSIENT ||
                            error.failure.kind == TranslationFailureKind.RATE_LIMIT
                        )
                ) {
                    gate.defer(key, maxOf(error.failure.retryAfterAt ?: 0L, clock() + 2_000L))
                }
                throw error
            } catch (error: IOException) {
                val kind = if (
                    error is SSLException
                ) {
                    TranslationFailureKind.CONFIGURATION
                } else {
                    TranslationFailureKind.TRANSIENT
                }
                if (kind == TranslationFailureKind.TRANSIENT && quotaKeyOverride == null) {
                    gate.defer(key, clock() + 2_000L)
                }
                // Exception messages may contain URLs or credentials; only the safe class is exposed.
                throw TranslationAttemptException(
                    TranslationFailure(kind, "$label network failure (${error.javaClass.simpleName})"),
                )
            }
        }
    }

    private data class HttpResponse(val code: Int, val body: String, val retryAfter: String?)

    private suspend fun Call.readResponse(): HttpResponse = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use {
                        val code = it.code
                        val retryAfter = it.header("Retry-After")
                        val source = it.body.source()
                        source.request(MAX_RESPONSE_BYTES + 1)
                        if (source.buffer.size > MAX_RESPONSE_BYTES) {
                            if (code !in 200..299) {
                                val limit = minOf(source.buffer.size, MAX_ERROR_PREVIEW_BYTES)
                                val preview = source.buffer.readByteString(limit).utf8()
                                HttpResponse(code, preview, retryAfter)
                            } else {
                                throw TranslationAttemptException(
                                    TranslationFailure(
                                        TranslationFailureKind.INVALID_RESPONSE,
                                        "Translation response exceeded the safe size limit",
                                    ),
                                )
                            }
                        } else {
                            HttpResponse(code, source.readUtf8(), retryAfter)
                        }
                    }
                    if (continuation.isActive) continuation.resume(result)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        })
    }
}

private const val MAX_RESPONSE_BYTES = 2L * 1024 * 1024
private const val MAX_ERROR_PREVIEW_BYTES = 64L * 1024

private val TRANSIENT_HTTP_CODES = setOf(
    408,
    425,
    500,
    502,
    503,
    504,
    520,
    521,
    522,
    523,
    524,
    529,
)

private val QUOTA_PROVIDER_CODES = setOf(
    "insufficient_quota",
    "billing_hard_limit_reached",
    "quota_exceeded",
)

private val SAFE_PROVIDER_CODES = setOf(
    "UNAVAILABLE",
    "RESOURCE_EXHAUSTED",
    "UNAUTHENTICATED",
    "PERMISSION_DENIED",
    "NOT_FOUND",
    "INVALID_ARGUMENT",
    "FAILED_PRECONDITION",
    "INTERNAL",
    "insufficient_quota",
    "billing_hard_limit_reached",
    "quota_exceeded",
    "rate_limit_exceeded",
    "overloaded_error",
    "server_error",
)

internal fun classifyTranslationHttpFailure(
    code: Int,
    rawBody: String,
    retryAfter: String?,
    now: Long,
    label: String,
    secret: String = "",
    json: Json = Json,
): TranslationFailure {
    val payload = runCatching { json.parseToJsonElement(rawBody) as? JsonObject }.getOrNull()
    val error = payload?.get("error").asObjectOrNull()
    val rawStatus = error?.get("status").asStringOrNull()?.trim()
    val rawType = error?.get("type").asStringOrNull()?.trim()
    val rawCodeString = error?.get("code").asStringOrNull()?.trim()
    val candidateCodes = listOfNotNull(rawStatus, rawType, rawCodeString)
    val normalizedCodes = candidateCodes.map { it.lowercase() }.toSet()
    val details = error?.get("details").asArrayOrNull().orEmpty()
    val dailyQuota = details.any { detail ->
        detail.asObjectOrNull()?.get("violations").asArrayOrNull().orEmpty().any {
            val quota = it.asObjectOrNull()?.get("quotaId").asStringOrNull().orEmpty().lowercase()
            quota.contains("perday") || quota.contains("per_day")
        }
    }
    val kind = when {
        normalizedCodes.any { it in QUOTA_PROVIDER_CODES } || dailyQuota -> TranslationFailureKind.QUOTA
        code == 401 || code == 403 -> TranslationFailureKind.AUTHORIZATION
        code == 429 || "rate_limit_exceeded" in normalizedCodes -> TranslationFailureKind.RATE_LIMIT
        code in TRANSIENT_HTTP_CODES ||
            normalizedCodes.any {
                it in
                    setOf(
                        "unavailable",
                        "service_unavailable",
                        "overloaded",
                        "overloaded_error",
                        "temporarily_unavailable",
                    )
            } -> TranslationFailureKind.TRANSIENT
        else -> TranslationFailureKind.CONFIGURATION
    }
    val rpcDelay = details.firstNotNullOfOrNull {
        val obj = it.asObjectOrNull() ?: return@firstNotNullOfOrNull null
        obj["retryDelay"].asStringOrNull()?.removeSuffix("s")?.toDoubleOrNull()
    }
    val due = maxOf(
        parseTranslationRetryAfter(retryAfter, now) ?: 0L,
        secondsToDue(rpcDelay ?: extractRetryAfterSeconds(rawBody), now) ?: 0L,
    ).takeIf { it > 0 }
    // Free-form server details can echo the chapter or trimmed credential; never persist them.
    val safeCode = candidateCodes.firstOrNull { it in SAFE_PROVIDER_CODES }
    return TranslationFailure(
        kind = kind,
        message = "$label HTTP $code (${kind.name})" + safeCode?.let { ": $it" }.orEmpty(),
        httpStatus = code,
        providerCode = safeCode,
        retryAfterAt = due,
    )
}

internal fun parseTranslationRetryAfter(value: String?, now: Long): Long? {
    if (value.isNullOrBlank()) return null
    value.trim().toDoubleOrNull()?.let { return secondsToDue(it, now) }
    return runCatching {
        ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
    }
        .getOrNull()?.coerceAtLeast(now)
}

private fun secondsToDue(seconds: Double?, now: Long): Long? {
    if (seconds == null || !seconds.isFinite() || seconds < 0) return null
    val milliseconds = ceil(seconds * 1000).toLong()
    return if (milliseconds > Long.MAX_VALUE - now) Long.MAX_VALUE else now + milliseconds
}

internal fun redactTranslationDiagnostic(text: String, secrets: List<String> = emptyList()): String {
    var safe = text
    secrets.flatMap {
        listOf(it, it.trim())
    }.filter { it.isNotBlank() }.distinct().forEach { safe = safe.replace(it, "[redacted]") }
    // Do not leave URL query/header authentication in shared/exported diagnostics.
    safe = safe.replace(Regex("(?i)([?&](?:key|api_key|token)=)[^& ]+"), "$1[redacted]")
    safe = safe.replace(Regex("(?i)(Bearer +)[A-Za-z0-9._:-]+"), "$1[redacted]")
    return safe
}

private fun extractEmbeddedErrorStatus(errorElement: JsonElement?): Int {
    if (errorElement is JsonPrimitive && errorElement.isString) {
        return errorElement.content.trim().toIntOrNull()?.takeIf { it in 400..599 } ?: 400
    }
    val error = errorElement.asObjectOrNull() ?: return 400
    (error["code"] as? JsonPrimitive)?.intOrNull?.takeIf { it in 400..599 }?.let { return it }
    error["code"].asStringOrNull()?.trim()?.toIntOrNull()?.takeIf { it in 400..599 }?.let { return it }
    val semantic = listOfNotNull(
        error["status"].asStringOrNull(),
        error["type"].asStringOrNull(),
        error["code"].asStringOrNull(),
    ).map { it.trim().lowercase() }
    return when {
        semantic.any {
            it in setOf(
                "resource_exhausted",
                "rate_limit_exceeded",
                "too_many_requests",
                "rate_limit",
                "insufficient_quota",
                "billing_hard_limit_reached",
                "quota_exceeded",
            )
        } -> 429
        semantic.any { it in setOf("unauthenticated", "invalid_api_key", "authentication_error") } -> 401
        semantic.any { it in setOf("permission_denied", "forbidden") } -> 403
        semantic.any {
            it in
                setOf("unavailable", "service_unavailable", "overloaded", "overloaded_error", "temporarily_unavailable")
        } -> 503
        semantic.any { it in setOf("internal", "internal_error", "server_error") } -> 500
        semantic.any { it in setOf("deadline_exceeded", "timeout") } -> 504
        else -> 400
    }
}

private fun throwIfTranslationPayloadError(
    raw: String,
    json: Json,
    label: String,
    secret: String,
    now: Long,
    retryAfter: String? = null,
) {
    val payload = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return
    val errorElement = payload["error"]?.takeUnless { it is JsonNull } ?: return
    throw TranslationAttemptException(
        classifyTranslationHttpFailure(
            code = extractEmbeddedErrorStatus(errorElement),
            rawBody = raw,
            retryAfter = retryAfter,
            now = now,
            label = label,
            secret = secret,
            json = json,
        ),
    )
}

internal fun parseTranslationPayload(raw: String, json: Json, label: String, secret: String = ""): JsonObject {
    val payload = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()

        ?: throw TranslationAttemptException(
            TranslationFailure(TranslationFailureKind.INVALID_RESPONSE, "$label returned invalid JSON"),
        )
    val errorElement = payload["error"]?.takeUnless { it is JsonNull }
    if (errorElement != null) {
        throw TranslationAttemptException(
            classifyTranslationHttpFailure(
                extractEmbeddedErrorStatus(errorElement),
                raw,
                null,
                System.currentTimeMillis(),
                label,
                secret = secret,
                json = json,
            ),
        )
    }
    return payload
}

internal fun validateTranslationFinish(reason: String?, label: String) {
    if (reason.isNullOrBlank()) return // Some compatible servers omit finish metadata; IDs still require validation.
    val kind = when (reason.trim().lowercase()) {
        "stop", "end_turn", "completed" -> return
        "max_tokens", "length", "limit", "continuation" -> TranslationFailureKind.TRUNCATED
        "safety",
        "content_filter",
        "blocklist",
        "prohibited_content",
        "recitation",
        "refusal",
        -> TranslationFailureKind.REFUSED
        else -> TranslationFailureKind.INVALID_RESPONSE
    }
    throw TranslationAttemptException(TranslationFailure(kind, "$label did not complete its answer (${kind.name})"))
}

internal fun parseOpenAiTranslationResponse(
    payload: JsonObject,
    expectedCount: Int,
    label: String,
    onLog: ((String) -> Unit)?,
): List<String?> {
    val choice = payload["choices"].asArrayOrNull()?.firstOrNull().asObjectOrNull()

        ?: throw TranslationAttemptException(
            TranslationFailure(TranslationFailureKind.INVALID_RESPONSE, "$label returned no candidate"),
        )
    if (!choice["message"].asObjectOrNull()?.get("refusal").asStringOrNull().isNullOrBlank()) {
        throw TranslationAttemptException(
            TranslationFailure(TranslationFailureKind.REFUSED, "$label declined the request"),
        )
    }
    validateTranslationFinish(choice["finish_reason"].asStringOrNull(), label)
    val candidate = choice.extractOpenAiStyleChoiceContent().trim()
    if (candidate.isBlank()) {
        throw TranslationAttemptException(
            TranslationFailure(TranslationFailureKind.INVALID_RESPONSE, "$label returned no answer text"),
        )
    }
    val parsed = GeminiXmlSegmentParser.parseValidated(candidate, expectedCount, allowSinglePlaintext = true)
    onLog?.invoke("$label response: ${parsed.count { !it.isNullOrBlank() }}/$expectedCount valid text blocks")
    return parsed
}
