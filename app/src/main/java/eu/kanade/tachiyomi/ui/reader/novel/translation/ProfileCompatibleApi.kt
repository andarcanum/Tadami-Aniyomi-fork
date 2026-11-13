package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.jsonMime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.domain.translation.credentials.CredentialSecret
import tachiyomi.domain.translation.model.TranslationAttemptException
import tachiyomi.domain.translation.model.TranslationFailure
import tachiyomi.domain.translation.model.TranslationFailureKind
import tachiyomi.domain.translation.profile.ConnectionProfile
import tachiyomi.domain.translation.profile.PresetFlavor
import tachiyomi.domain.translation.profile.ProfileAuth
import tachiyomi.domain.translation.profile.TranslationProtocol

/** Compatible protocols share one typed one-shot transport and the exact source-ID validator. */
internal class ProfileCompatibleApi(
    client: OkHttpClient,
    private val json: Json,
    private val transport: TranslationHttpTransport = TranslationHttpTransport(client, json),
) {

    suspend fun dispatch(
        connection: ConnectionProfile,
        prepared: PreparedProfileRequest,
        expectedCount: Int,
        resolveCredential: suspend () -> CredentialSecret?,
        quotaKeyOverride: String? = null,
        beforeAttempt: suspend () -> Unit = {},
    ): List<String?> {
        var carrier: CredentialSecret? = null
        val request = POST(prepared.url, body = prepared.body.toString().toRequestBody(jsonMime))
        val raw = try {
            transport.execute(request, "Translation API", "", quotaKeyOverride) { admitted ->
                beforeAttempt()
                carrier = resolveCredential()
                val key = carrier?.reveal().orEmpty()
                validateTranslationCredential(key)
                if (connection.auth != ProfileAuth.NONE && key.isBlank()) {
                    throw TranslationAttemptException(
                        TranslationFailure(
                            TranslationFailureKind.AUTHORIZATION,
                            "Translation connection needs a credential",
                            attempted = false,
                        ),
                    )
                }
                admitted.newBuilder().headers(headers(connection, key)).build()
            }
        } finally {
            carrier?.close()
        }
        val payload = parseTranslationPayload(raw, json, "Translation API")
        return when (connection.protocol) {
            TranslationProtocol.OPENAI_CHAT ->
                parseOpenAiTranslationResponse(payload, expectedCount, "Translation API", null)
            TranslationProtocol.GEMINI_GENERATE -> parseGemini(payload, expectedCount, prepared.isPlaintext)
            TranslationProtocol.OLLAMA_NATIVE -> parseOllama(payload, expectedCount)
        }
    }

    private fun headers(connection: ConnectionProfile, credential: String): Headers =
        Headers.Builder().add("Content-Type", "application/json").apply {
            when (connection.auth) {
                ProfileAuth.NONE -> Unit
                ProfileAuth.BEARER -> add("Authorization", "Bearer " + credential)
                ProfileAuth.X_GOOG_API_KEY -> add("x-goog-api-key", credential)
            }
            if (connection.presetFlavor == PresetFlavor.OPENROUTER) {
                add("HTTP-Referer", "https://aniyomi.org")
                add("X-Title", "Aniyomi Novel Reader")
            }
        }.build()

    private fun parseGemini(payload: JsonObject, count: Int, isPlaintext: Boolean): List<String?> {
        val blocked = payload["promptFeedback"].asObjectOrNull()?.get("blockReason").asStringOrNull()
        if (!blocked.isNullOrBlank() && blocked != "BLOCK_REASON_UNSPECIFIED") {
            throw TranslationAttemptException(
                TranslationFailure(TranslationFailureKind.REFUSED, "Provider refused the translation"),
            )
        }
        val candidate = payload["candidates"].asArrayOrNull()?.firstOrNull().asObjectOrNull()
            ?: throw TranslationAttemptException(
                TranslationFailure(TranslationFailureKind.INVALID_RESPONSE, "Provider returned no candidate"),
            )
        validateTranslationFinish(candidate["finishReason"].asStringOrNull(), "Custom translation API")
        val answer = candidate["content"].asObjectOrNull()?.get("parts").asArrayOrNull().orEmpty().mapNotNull { part ->
            val obj = part.asObjectOrNull() ?: return@mapNotNull null
            if ((obj["thought"] as? JsonPrimitive)?.booleanOrNull == true) null else obj["text"].asStringOrNull()
        }.joinToString("")
        return if (isPlaintext) {
            GeminiXmlSegmentParser.parsePlaintext(answer, count)
        } else {
            GeminiXmlSegmentParser.parseValidated(answer, count, true)
        }
    }

    private fun parseOllama(payload: JsonObject, count: Int): List<String?> {
        if ((payload["done"] as? JsonPrimitive)?.booleanOrNull == false) {
            throw TranslationAttemptException(
                TranslationFailure(TranslationFailureKind.TRUNCATED, "Provider returned an unfinished answer"),
            )
        }
        validateTranslationFinish(payload["done_reason"].asStringOrNull(), "Custom translation API")
        val message = payload["message"].asObjectOrNull()
        val answer = listOf(message?.get("content"), payload["response"], payload["content"], payload["text"])
            .firstNotNullOfOrNull { it.asLooseStringOrNull()?.trim()?.takeIf(String::isNotBlank) }.orEmpty()
        return GeminiXmlSegmentParser.parseValidated(answer, count, true)
    }
}
