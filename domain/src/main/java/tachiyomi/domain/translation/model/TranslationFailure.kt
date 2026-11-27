package tachiyomi.domain.translation.model

import kotlinx.serialization.Serializable

/** A provider failure is data, not an English log message used as a retry classifier. */
@Serializable
enum class TranslationFailureKind {
    TRANSIENT,
    RATE_LIMIT,
    QUOTA,
    AUTHORIZATION,
    CONFIGURATION,
    TRUNCATED,
    INVALID_RESPONSE,
    REFUSED,
    STORAGE,
    WAITING,
}

@Serializable
data class TranslationFailure(
    val kind: TranslationFailureKind,
    val message: String,
    val httpStatus: Int? = null,
    val providerCode: String? = null,
    val retryAfterAt: Long? = null,
    /** Cooldown admission failures have not sent a request and must not consume an attempt. */
    val attempted: Boolean = true,
)

class TranslationAttemptException(
    val failure: TranslationFailure,
) : IllegalStateException(failure.message)
