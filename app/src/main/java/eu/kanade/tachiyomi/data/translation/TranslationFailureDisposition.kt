package eu.kanade.tachiyomi.data.translation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import tachiyomi.domain.translation.model.TranslationActionPauseReason
import tachiyomi.domain.translation.model.TranslationFailure
import tachiyomi.domain.translation.model.TranslationFailureKind

/** Action recovery is explicit and preserves the frozen C/D execution; legacy A/B failure policy is unchanged. */
internal fun translationActionPauseReason(
    encoded: String?,
    failure: TranslationFailure,
): TranslationActionPauseReason? {
    val modern = encoded != null && encoded.toByteArray(Charsets.UTF_8).size <= 1_048_576 && runCatching {
        val root = Json.parseToJsonElement(encoded) as? JsonObject
        val version = root?.get("snapshotVersion") as? JsonPrimitive
        version != null && !version.isString && version.intOrNull == 2
    }.getOrDefault(false)
    if (!modern) return null
    return when (failure.kind) {
        TranslationFailureKind.AUTHORIZATION -> TranslationActionPauseReason.NEEDS_ACTION
        TranslationFailureKind.CONFIGURATION -> TranslationActionPauseReason.CONFIGURATION
        TranslationFailureKind.QUOTA -> if (failure.attempted) {
            TranslationActionPauseReason.NEEDS_ACTION
        } else {
            TranslationActionPauseReason.BUDGET_LIMIT
        }
        else -> null
    }
}
