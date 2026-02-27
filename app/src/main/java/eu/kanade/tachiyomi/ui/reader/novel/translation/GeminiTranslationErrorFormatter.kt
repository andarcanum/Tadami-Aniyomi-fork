package eu.kanade.tachiyomi.ui.reader.novel.translation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

internal fun extractGeminiApiErrorMessage(rawBody: String): String? {
    val payload = runCatching { Json.parseToJsonElement(rawBody) as? JsonObject }
        .getOrNull()
        ?: return null
    val error = payload["error"] as? JsonObject ?: return null
    val status = (error["status"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
    val message = (error["message"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
    return when {
        status.isNotBlank() && message.isNotBlank() -> "$status: $message"
        status.isNotBlank() -> status
        message.isNotBlank() -> message
        else -> null
    }
}

internal fun extractOpenAiApiErrorMessage(rawBody: String): String? {
    val payload = runCatching { Json.parseToJsonElement(rawBody) as? JsonObject }
        .getOrNull()
        ?: return null
    val error = payload["error"] as? JsonObject ?: return null
    val type = (error["type"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
    val message = (error["message"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
    return when {
        type.isNotBlank() && message.isNotBlank() -> "$type: $message"
        type.isNotBlank() -> type
        message.isNotBlank() -> message
        else -> null
    }
}

internal fun formatGeminiThrowableForLog(error: Throwable): String {
    val details = mutableListOf(
        "${error.javaClass.simpleName}: ${error.message.orEmpty().ifBlank { "no message" }}",
    )

    generateSequence(error.cause) { it.cause }
        .take(4)
        .forEach { cause ->
            details += "Caused by ${cause.javaClass.simpleName}: ${cause.message.orEmpty().ifBlank { "no message" }}"
        }

    if (isTranslationTimeout(error)) {
        details += "Timeout before translation provider returned full response payload"
    }

    return details.joinToString(" | ")
}

private fun isTranslationTimeout(error: Throwable): Boolean {
    return generateSequence(error) { it.cause }
        .any { cause ->
            cause is SocketTimeoutException ||
                (cause is InterruptedIOException && cause.message?.contains("timeout", ignoreCase = true) == true)
        }
}
