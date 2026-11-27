package tachiyomi.domain.easteregg.crystal

/** Decisions only: the caller persists eligibility and consumption; this policy never grants rewards. */
data class CrystalReleaseDecision(
    val shouldMakeReleaseEligible: Boolean,
    val mayUseTitleUnlock: Boolean,
    val shouldOfferPending: Boolean,
)

object CrystalReleasePolicy {

    /**
     * [isReleaseBuild] must mean an actual production release, excluding preview, benchmark and debug.
     * Empty [targetReleaseCodes] disables production routes; no real release code is assigned here.
     *
     * Only an exact configured release can establish release eligibility. An unlisted later version
     * uses the title route, even if the user skipped every configured release. Existing eligibility
     * needs no new reading and survives version changes. An offer requires already persisted state;
     * persist eligibility first, then evaluate again. A consumed offer remains manually available
     * through the saved state, but is never automatically offered again.
     */
    fun evaluate(
        versionCode: Int,
        isReleaseBuild: Boolean,
        targetReleaseCodes: Set<Int>,
        state: CrystalMemorialState,
    ): CrystalReleaseDecision {
        if (!isReleaseBuild || targetReleaseCodes.isEmpty() || state.grantedAt != null) {
            return CrystalReleaseDecision(false, false, false)
        }

        val exactRelease = versionCode in targetReleaseCodes
        return CrystalReleaseDecision(
            shouldMakeReleaseEligible = !state.isEligible && exactRelease,
            mayUseTitleUnlock = !state.isEligible && !exactRelease && versionCode > targetReleaseCodes.min(),
            shouldOfferPending = state.isEligible &&
                state.eligibilityReason != CrystalEligibilityReason.DEBUG &&
                state.automaticPresentationConsumedAt == null,
        )
    }
}
