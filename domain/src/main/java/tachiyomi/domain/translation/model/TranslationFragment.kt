package tachiyomi.domain.translation.model

import kotlinx.serialization.Serializable

/** A bounded, reversible slice of one canonical source block. No credential belongs here. */
@Serializable
data class TranslationFragment(
    val id: Int,
    val sourceIndex: Int,
    val partIndex: Int,
    val partCount: Int,
    val sourceText: String,
    val separatorAfter: String = "",
    val translatedText: String? = null,
    val attempts: Int = 0,
    val firstFailureAt: Long? = null,
    val nextAttemptAt: Long = 0,
    val failure: TranslationFailure? = null,
) {
    val isComplete: Boolean get() = !translatedText.isNullOrBlank()
}
