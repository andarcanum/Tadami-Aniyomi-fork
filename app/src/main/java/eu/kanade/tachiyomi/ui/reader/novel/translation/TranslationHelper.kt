package eu.kanade.tachiyomi.ui.reader.novel.translation

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal val xmlSegmentStartRegex = Regex("(?i)<s\\s+i=['\"]\\d+['\"]>")
internal val xmlSegmentEndRegex = Regex("(?i)</s>")

fun String.trimNonXmlTail(): String {
    val source = trim()
    val start = xmlSegmentStartRegex.find(source)?.range?.first ?: return source
    val end = xmlSegmentEndRegex.findAll(source).lastOrNull()?.range?.last ?: return source
    if (end < start) return source
    return source.substring(start, end + 1).trim()
}

fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject
fun JsonElement?.asArrayOrNull(): JsonArray? = this as? JsonArray

fun JsonElement?.asStringOrNull(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    return if (primitive.isString) primitive.content else null
}

fun JsonElement?.asLooseStringOrNull(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    return primitive.content.takeIf { it.isNotBlank() }
}

fun JsonElement?.extractTextCandidates(includeThinking: Boolean = false): List<String> {
    return when (this) {
        is JsonPrimitive -> {
            if (isString) {
                content.trim().takeIf { it.isNotBlank() }?.let(::listOf).orEmpty()
            } else {
                emptyList()
            }
        }
        is JsonArray -> flatMap { it.extractTextCandidates(includeThinking) }
        is JsonObject -> {
            val isThinking = this["type"].asStringOrNull()?.equals("thinking", ignoreCase = true) == true
            if (isThinking && !includeThinking) return emptyList()
            val direct = listOf("text", "content", "output_text")
                .flatMap { key -> this[key].extractTextCandidates(includeThinking) }
            val functionArgs = this["function"].asObjectOrNull()?.get("arguments")
                .extractTextCandidates(includeThinking)
            (direct + functionArgs).distinct()
        }
        else -> emptyList()
    }
}

private val retryAfterSecondsRegex =
    Regex("(?i)try\\s+again\\s+in\\s+([0-9]+(?:\\.[0-9]+)?)\\s*seconds?")

fun extractRetryAfterSeconds(raw: String): Double? {
    val match = retryAfterSecondsRegex.find(raw) ?: return null
    return match.groupValues.getOrNull(1)?.toDoubleOrNull()
}

fun computeRateLimitDelayMs(
    attempt: Int,
    hintSeconds: Double?,
): Long {
    if (hintSeconds != null) {
        val ms = ((hintSeconds + 0.3) * 1000.0).toLong()
        return ms.coerceIn(1_200L, 120_000L)
    }
    return when (attempt) {
        1 -> 2_000L
        2 -> 5_000L
        3 -> 15_000L
        else -> 60_000L
    }
}
