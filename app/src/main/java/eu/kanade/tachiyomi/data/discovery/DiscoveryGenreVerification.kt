package eu.kanade.tachiyomi.data.discovery

/**
 * What a required-genre check actually established.
 *
 * The distinction matters because "no match among verified cards", "we ran out
 * of budget before checking enough cards" and "metadata requests failed" are
 * different outcomes. Only the last one is an operational failure.
 */
enum class GenreVerificationOutcome {
    /** Verified cards satisfying the required genres were found. */
    SUCCESS,

    /** The verified set contained no match and no request failed. */
    VERIFIED_EMPTY,

    /** Coverage is incomplete: budget ran out, metadata was absent or a page timed out. */
    INCOMPLETE,

    /** No useful verification happened because metadata requests failed. */
    FAILED,
}

/**
 * Coverage-aware result of one required-genre verification.
 *
 * [verifiedCount] counts cards whose observed genres are known, including cards
 * rejected by the policy. [unattemptedCount] counts cards that were not checked
 * because the shared details budget or the source budget ran out.
 */
data class GenreVerificationSummary(
    val items: List<DiscoveryRowItem>,
    val verifiedCount: Int,
    val unattemptedCount: Int,
    val metadataUnavailableCount: Int,
    val failedCount: Int,
) {
    val outcome: GenreVerificationOutcome
        get() = when {
            items.isNotEmpty() -> GenreVerificationOutcome.SUCCESS
            failedCount > 0 && verifiedCount == 0 &&
                metadataUnavailableCount == 0 && unattemptedCount == 0 -> GenreVerificationOutcome.FAILED
            failedCount > 0 || metadataUnavailableCount > 0 ||
                unattemptedCount > 0 -> GenreVerificationOutcome.INCOMPLETE
            else -> GenreVerificationOutcome.VERIFIED_EMPTY
        }

    /** True when the row can be shown but its coverage is not complete. */
    val hasCoverageGap: Boolean
        get() = unattemptedCount > 0 || metadataUnavailableCount > 0 || failedCount > 0

    companion object {
        fun empty(
            unattempted: Int = 0,
            failed: Int = 0,
        ): GenreVerificationSummary = GenreVerificationSummary(
            items = emptyList(),
            verifiedCount = 0,
            unattemptedCount = unattempted,
            metadataUnavailableCount = 0,
            failedCount = failed,
        )
    }
}

/** One catalogue page plus the hydration coverage it achieved. */
data class DiscoveryGenrePage(
    val items: List<DiscoveryRowItem>,
    val verified: Int,
    val unattempted: Int,
    val metadataUnavailable: Int,
    val transientFailures: Int,
)

/** Verification result for a single selected source. */
internal data class GenreSourceVerification(
    val sourceId: Long,
    val summary: GenreVerificationSummary,
    val failure: GenreSourceException? = null,
)
