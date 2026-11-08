package eu.kanade.domain.easteregg.crystal

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tachiyomi.domain.easteregg.crystal.CrystalEligibilityReason
import tachiyomi.domain.easteregg.crystal.CrystalMemorialRepository
import tachiyomi.domain.easteregg.crystal.CrystalMemorialState
import tachiyomi.domain.easteregg.crystal.CrystalProgressResult
import tachiyomi.domain.easteregg.crystal.CrystalProgressSource
import tachiyomi.domain.easteregg.crystal.CrystalReleasePolicy

internal data class CrystalCampaignConditions(
    val migrationsReady: Boolean,
    val catalogueReady: Boolean,
    val resumed: Boolean,
    val unlocked: Boolean,
    val incognito: Boolean,
    val restoring: Boolean,
    val startupSettled: Boolean,
    val windowFocused: Boolean,
    val atRoot: Boolean,
    val restoreRevision: Long = 0L,
) {
    val canEvaluate: Boolean get() = migrationsReady && catalogueReady && resumed && unlocked && !incognito &&
        !restoring
    val canPresent: Boolean get() = canEvaluate && startupSettled && windowFocused && atRoot
}

internal data class CrystalCampaignEvaluation(
    val state: CrystalMemorialState,
    val progress: CrystalProgressResult? = null,
    val shouldOffer: Boolean = false,
)

/** Local eligibility and presentation decisions only. Never grants XP or equips cosmetics. */
internal class CrystalCampaignController(
    private val repository: CrystalMemorialRepository,
    private val progressSource: CrystalProgressSource,
    private val versionCode: Int,
    private val productionRelease: Boolean,
    private val releaseCodes: Set<Int>,
    private val debugAllowed: Boolean = false,
) {
    private val mutation = Mutex()
    private var offeredGeneration: Long? = null
    val configured: Boolean get() = productionRelease && releaseCodes.isNotEmpty()
    fun progressChanges() = progressSource.changes()

    suspend fun refresh(
        conditions: CrystalCampaignConditions,
        liveConditions: () -> CrystalCampaignConditions = {
            conditions
        },
    ): CrystalCampaignEvaluation = mutation.withLock {
        var saved = repository.getState()
        // The controller mutex and the receipt read may both suspend: never trust their caller's old gate.
        val initialGate = liveConditions()
        if (!configured || !initialGate.canEvaluate ||
            saved.grantedAt != null
        ) {
            return@withLock CrystalCampaignEvaluation(saved)
        }
        val decision = CrystalReleasePolicy.evaluate(versionCode, productionRelease, releaseCodes, saved)
        var progress: CrystalProgressResult? = null
        if (decision.shouldMakeReleaseEligible) {
            val writeGate = liveConditions()
            if (writeGate.canEvaluate && writeGate.restoreRevision == initialGate.restoreRevision) {
                saved = repository.makeEligible(CrystalEligibilityReason.RELEASE)
            }
        } else if (decision.mayUseTitleUnlock) {
            val scanGate = liveConditions()
            if (scanGate.canEvaluate) {
                progress = progressSource.snapshot()
                if (progress.isEligible) {
                    val current = repository.getState()
                    // Re-read after the second suspended receipt read, immediately before the write.
                    val writeGate = liveConditions()
                    if (writeGate.canEvaluate && writeGate.restoreRevision == scanGate.restoreRevision &&
                        current.generation == saved.generation && current.grantedAt == null
                    ) {
                        saved = repository.makeEligible(CrystalEligibilityReason.TITLE)
                    } else {
                        saved = current
                    }
                }
            }
        }
        val after = CrystalReleasePolicy.evaluate(versionCode, productionRelease, releaseCodes, saved)
        CrystalCampaignEvaluation(saved, progress, after.shouldOfferPending && liveConditions().canPresent)
    }

    /** Explicit debug check uses the real saved flags but does not arm a release or replay XP. */
    suspend fun checkSavedProgressForDebug(
        conditions: CrystalCampaignConditions,
        liveConditions: () -> CrystalCampaignConditions = { conditions },
    ): CrystalCampaignEvaluation = mutation.withLock {
        check(debugAllowed) { "Saved-progress test activation is debug-only" }
        var saved = repository.getState()
        val scanGate = liveConditions()
        if (!scanGate.canEvaluate) return@withLock CrystalCampaignEvaluation(saved)
        val progress = progressSource.snapshot()
        if (progress.isEligible && !saved.isEligible && saved.grantedAt == null) {
            val current = repository.getState()
            val writeGate = liveConditions()
            if (writeGate.canEvaluate && writeGate.restoreRevision == scanGate.restoreRevision &&
                current.generation == saved.generation && !current.isEligible && current.grantedAt == null
            ) {
                saved = repository.makeEligible(CrystalEligibilityReason.TITLE)
            } else {
                saved = current
            }
        }
        CrystalCampaignEvaluation(saved, progress)
    }

    /** In-process dedup only; the screen persists consumption after it is actually visible. */
    @Synchronized
    fun claimPresentation(
        evaluation: CrystalCampaignEvaluation,
        liveState: CrystalMemorialState,
        conditions: CrystalCampaignConditions,
    ): Boolean {
        if (!evaluation.shouldOffer || !conditions.canPresent || !liveState.isEligible || liveState.grantedAt != null ||
            liveState.automaticPresentationConsumedAt != null ||
            liveState.eligibilityReason == CrystalEligibilityReason.DEBUG ||
            liveState.generation != evaluation.state.generation ||
            offeredGeneration == liveState.generation
        ) {
            return false
        }
        offeredGeneration = liveState.generation
        return true
    }
}
