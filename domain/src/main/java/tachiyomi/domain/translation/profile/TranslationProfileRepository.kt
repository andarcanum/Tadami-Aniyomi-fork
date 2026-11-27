package tachiyomi.domain.translation.profile

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import tachiyomi.domain.translation.credentials.CredentialIds

/** Public metadata only: never a raw secret, cipher envelope or secret carrier. */
@Serializable
data class CredentialMetadata(
    val id: String,
    val alias: String,
    val maskedSuffix: String,
    val updatedAt: Long,
) {
    init {
        CredentialIds.requireValid(id)
        requireProfileText(alias, 120)
        require(maskedSuffix.length <= 8 && maskedSuffix.none(Char::isISOControl)) {
            "Invalid masked credential suffix"
        }
        require(updatedAt >= 0) { "Invalid credential metadata time" }
    }
    override fun toString(): String = "CredentialMetadata(id=$id)"
}

@Serializable
enum class ProfileAssignmentScope { GLOBAL, SOURCE, NOVEL }

@Serializable
data class ProfileAssignment(
    val scope: ProfileAssignmentScope,
    val scopeId: Long,
    val profileId: String,
) {
    init {
        requireProfileUuid(profileId)
        require(scope != ProfileAssignmentScope.GLOBAL || scopeId == 0L) { "Global assignment has no scoped ID" }
    }
}

@Serializable
enum class LegacyCredentialScope { GLOBAL, SOURCE }

@Serializable
data class LegacyCredentialReference(
    val provider: String,
    val scope: LegacyCredentialScope,
    val scopeId: Long,
    val credentialId: String,
) {
    init {
        requireProfileToken(provider, 80)
        CredentialIds.requireValid(credentialId)
        require(scope != LegacyCredentialScope.GLOBAL || scopeId == 0L) {
            "Global credential reference has no scoped ID"
        }
    }
}

data class ProfileCatalog(
    val connections: List<ConnectionProfile> = emptyList(),
    val profiles: List<TranslationProfile> = emptyList(),
    val credentials: List<CredentialMetadata> = emptyList(),
    val assignments: List<ProfileAssignment> = emptyList(),
    val legacyReferences: List<LegacyCredentialReference> = emptyList(),
)

sealed interface ProfileRequestReservation {
    /** New admission only: route cursor and conservative budget are durable before returning. */
    data class Reserved(
        val credentialId: String?,
        val quotaGroup: String,
        val ledger: RequestBudgetLedger,
        val pendingFragmentIds: Set<String>,
    ) : ProfileRequestReservation

    /** A replay never authorizes another HTTP request and never refunds the original reservation. */
    data class AlreadyReserved(val ledger: RequestBudgetLedger) : ProfileRequestReservation
    data class NoWork(val ledger: RequestBudgetLedger) : ProfileRequestReservation
    data class Waiting(val notBeforeAt: Long) : ProfileRequestReservation
    data object NeedsAction : ProfileRequestReservation
    data class BudgetStop(val reason: RequestBudgetBlock) : ProfileRequestReservation
    data object Stale : ProfileRequestReservation
}

enum class ProfileStorageFailure { CORRUPT, INVALID_REFERENCE, UNAVAILABLE, OVERSIZED_RECORD }

/** No raw JSON, key value or platform exception cause is attached to this failure. */
class ProfileStorageException(val kind: ProfileStorageFailure) : IllegalStateException(
    when (kind) {
        ProfileStorageFailure.CORRUPT -> "Stored translation profile data is corrupt or unsupported"
        ProfileStorageFailure.INVALID_REFERENCE -> "Translation profile reference is invalid"
        ProfileStorageFailure.UNAVAILABLE -> "Translation profile storage is unavailable"
        ProfileStorageFailure.OVERSIZED_RECORD -> "Translation profile record exceeds the storage limit"
    },
)

interface TranslationProfileRepository {
    fun observeCatalog(): Flow<ProfileCatalog>
    suspend fun getCatalog(): ProfileCatalog
    suspend fun getConnection(id: String): ConnectionProfile?
    suspend fun getProfile(id: String): TranslationProfile?
    suspend fun saveConnection(connection: ConnectionProfile)
    suspend fun saveProfile(profile: TranslationProfile)
    suspend fun deleteProfile(id: String): Boolean

    /** False when missing or in use by a profile; vault credentials are never deleted. */
    suspend fun deleteConnection(id: String): Boolean
    suspend fun getCredentialMetadata(id: String): CredentialMetadata?
    suspend fun saveCredentialMetadata(metadata: CredentialMetadata)

    /** Removes metadata only under atomic reference guards. Vault deletion is an explicit caller step. */
    suspend fun deleteCredentialMetadataIfUnused(id: String): Boolean
    suspend fun saveAssignment(assignment: ProfileAssignment)
    suspend fun removeAssignment(scope: ProfileAssignmentScope, scopeId: Long): Boolean

    /** Removes NOVEL-scoped assignments whose target novel row no longer exists. */
    suspend fun pruneOrphanNovelAssignments(): Int

    suspend fun getLegacyCredentialReference(
        provider: String,
        scope: LegacyCredentialScope,
        scopeId: Long,
    ): LegacyCredentialReference?
    suspend fun saveLegacyCredentialReference(reference: LegacyCredentialReference)

    /** Explicit missing IDs do not silently fall back to a different paid configuration. */
    suspend fun resolveProfile(
        explicitProfileId: String? = null,
        novelId: Long? = null,
        sourceId: Long? = null,
    ): TranslationProfile?

    /** Caller verifies vault writes/readback first. Config metadata import is one idempotent transaction. */
    suspend fun importConfiguration(bundle: ProfileCatalog)

    fun observeRoutingState(): Flow<CredentialRoutingState>
    suspend fun readRoutingState(): CredentialRoutingState
    suspend fun readLedger(runId: Long, generation: Long): RequestBudgetLedger
    suspend fun reserveRequest(
        runId: Long,
        generation: Long,
        connectionSnapshot: ConnectionProfile,
        modelId: String,
        policy: ExecutionPolicy,
        requestId: String,
        fragmentIds: Set<String>,
        tokenEstimate: Long?,
        now: Long,
    ): ProfileRequestReservation

    /** READY is explicit evidence supplied by a caller, never inferred from a metadata ID. */
    suspend fun setCredentialHealth(credentialId: String, health: CredentialHealth)
    suspend fun recordRoutingFailure(
        connectionSnapshot: ConnectionProfile,
        modelId: String,
        credentialId: String?,
        failure: RoutingFailure,
        notBeforeAt: Long,
        now: Long,
    )

    /** Caller supplies only verified fragments; stale/terminal generations cannot be updated. */
    suspend fun markCompletedFragments(runId: Long, generation: Long, fragmentIds: Set<String>): Boolean

    /** Successful explicit probe clears action flags, never outstanding shared cooldowns. */
    suspend fun resetServiceHealth(
        connectionSnapshot: ConnectionProfile,
        modelId: String,
        credentialId: String?,
        now: Long,
    )
}
