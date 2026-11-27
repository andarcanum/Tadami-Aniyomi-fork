package tachiyomi.data.translation.profile

import datanovel.GetCatalogRecords
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.data.translation.NovelDatabaseMutationGate
import tachiyomi.data.translation.frozenRunCredentialIds
import tachiyomi.domain.translation.credentials.CredentialIds
import tachiyomi.domain.translation.profile.ConnectionProfile
import tachiyomi.domain.translation.profile.CredentialHealth
import tachiyomi.domain.translation.profile.CredentialHealthStatus
import tachiyomi.domain.translation.profile.CredentialMetadata
import tachiyomi.domain.translation.profile.CredentialRouteDecision
import tachiyomi.domain.translation.profile.CredentialRoutingPolicy
import tachiyomi.domain.translation.profile.CredentialRoutingState
import tachiyomi.domain.translation.profile.ExecutionPolicy
import tachiyomi.domain.translation.profile.LegacyCredentialReference
import tachiyomi.domain.translation.profile.LegacyCredentialScope
import tachiyomi.domain.translation.profile.ProfileAssignment
import tachiyomi.domain.translation.profile.ProfileAssignmentScope
import tachiyomi.domain.translation.profile.ProfileAuth
import tachiyomi.domain.translation.profile.ProfileCatalog
import tachiyomi.domain.translation.profile.ProfileRequestReservation
import tachiyomi.domain.translation.profile.ProfileStorageException
import tachiyomi.domain.translation.profile.ProfileStorageFailure
import tachiyomi.domain.translation.profile.RequestBudgetLedger
import tachiyomi.domain.translation.profile.RequestBudgetPolicy
import tachiyomi.domain.translation.profile.RequestReservationDecision
import tachiyomi.domain.translation.profile.RoutingFailure
import tachiyomi.domain.translation.profile.TranslationProfile
import tachiyomi.domain.translation.profile.TranslationProfileRepository
import tachiyomi.novel.data.NovelDatabase

class TranslationProfileRepositoryImpl(
    private val handler: NovelDatabaseHandler,
    json: Json,
) : TranslationProfileRepository {
    private val json = Json(json) {
        encodeDefaults = true
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
    }

    @Volatile
    private var mutationMutex: Mutex? = null

    override fun observeCatalog(): Flow<ProfileCatalog> = handler.subscribeToList {
        it.translation_profilesQueries.getCatalogRecords()
    }.map { getCatalog() }.catch { error -> throwSafe(error) }

    override suspend fun getCatalog(): ProfileCatalog = read { loadCatalog(it) }

    override suspend fun getConnection(id: String): ConnectionProfile? = read { db ->
        validId(id)
        db.translation_profilesQueries.getConnection(id).executeAsOneOrNull()?.let { record ->
            decode<ConnectionProfile>(record.record_json).also { if (it.id != record._id) corrupt() }
        }
    }

    override suspend fun getProfile(id: String): TranslationProfile? = read { db ->
        validId(id)
        db.translation_profilesQueries.getProfile(id).executeAsOneOrNull()?.let { record ->
            decode<TranslationProfile>(record.record_json).also {
                if (it.id != record._id || it.connectionId != record.connection_id) corrupt()
            }
        }
    }

    override suspend fun saveConnection(connection: ConnectionProfile) = mutate<Unit> { db ->
        val current = loadCatalog(db)
        validate(current.copy(connections = replace(current.connections, connection) { it.id }))
        db.translation_profilesQueries.putConnection(connection.id, encode(connection))
    }

    override suspend fun saveProfile(profile: TranslationProfile) = mutate<Unit> { db ->
        val current = loadCatalog(db)
        validate(current.copy(profiles = replace(current.profiles, profile) { it.id }))
        db.translation_profilesQueries.putProfile(profile.id, profile.connectionId, encode(profile))
    }

    override suspend fun deleteProfile(id: String): Boolean = mutate { db ->
        validId(id)
        if (loadCatalog(db).profiles.none { it.id == id }) return@mutate false
        db.translation_profilesQueries.removeAssignmentsForProfile(id)
        db.translation_profilesQueries.removeProfile(id)
        true
    }

    override suspend fun deleteConnection(id: String): Boolean = mutate { db ->
        validId(id)
        val catalog = loadCatalog(db)
        if (catalog.connections.none { it.id == id } || catalog.profiles.any { it.connectionId == id }) {
            return@mutate false
        }
        db.translation_profilesQueries.removeConnection(id)
        true
    }

    override suspend fun getCredentialMetadata(id: String): CredentialMetadata? = read { db ->
        validId(id)
        db.translation_profilesQueries.getMetadata(id).executeAsOneOrNull()?.let { record ->
            decode<CredentialMetadata>(record.record_json).also { if (it.id != record._id) corrupt() }
        }
    }

    override suspend fun saveCredentialMetadata(metadata: CredentialMetadata) = mutate<Unit> { db ->
        if (db.translation_profilesQueries.getRetiredCredential(metadata.id).executeAsOneOrNull() != null) invalid()
        db.translation_profilesQueries.putMetadata(metadata.id, encode(metadata))
    }

    override suspend fun deleteCredentialMetadataIfUnused(id: String): Boolean = mutate { db ->
        validId(id)
        val catalog = loadCatalog(db)
        if (catalog.credentials.none { it.id == id } ||
            catalog.connections.any { connection -> connection.credentialBindings.any { it.credentialId == id } } ||
            catalog.legacyReferences.any { it.credentialId == id }
        ) {
            return@mutate false
        }
        val active = db.translation_queueQueries.getActiveRuns().executeAsList()
        for (run in active) {
            val references = frozenRunCredentialIds(run.profile_snapshot_json, json) ?: return@mutate false
            if (id in references) return@mutate false
        }
        val routing = loadRouting(db)
        saveRouting(
            db,
            routing.copy(
                health = routing.health - id,
                lastSelectedByConnection = routing.lastSelectedByConnection.filterValues { it != id },
            ),
        )
        db.translation_profilesQueries.retireCredential(id)
        db.translation_profilesQueries.removeCredentialMetadata(id)
        true
    }

    override suspend fun resetServiceHealth(
        connectionSnapshot: ConnectionProfile,
        modelId: String,
        credentialId: String?,
        now: Long,
    ) = mutate<Unit> { db ->
        if (now < 0) invalid()
        try {
            CredentialRoutingPolicy.serviceBucket(connectionSnapshot, modelId)
        } catch (
            _: IllegalArgumentException,
        ) {
            invalid()
        }
        if (connectionSnapshot.auth == tachiyomi.domain.translation.profile.ProfileAuth.NONE) {
            if (credentialId != null) invalid()
        } else {
            val id = credentialId ?: invalid()
            if (connectionSnapshot.credentialBindings.none { it.credentialId == id } ||
                db.translation_profilesQueries.getMetadata(id).executeAsOneOrNull() == null ||
                db.translation_profilesQueries.getRetiredCredential(id).executeAsOneOrNull() != null
            ) {
                invalid()
            }
        }
        val state = loadRouting(db)
        val health = if (credentialId == null) {
            state.health
        } else {
            state.health +
                (
                    credentialId to CredentialHealth(
                        CredentialHealthStatus.READY,
                        state.health[credentialId]?.notBeforeAt ?: 0,
                    )
                    )
        }
        // A successful explicit probe clears action state, never still-live account/model cooldowns.
        saveRouting(
            db,
            state.copy(
                health = health,
                connectionsNeedingAction = state.connectionsNeedingAction - connectionSnapshot.id,
            ),
        )
    }

    override suspend fun saveAssignment(assignment: ProfileAssignment) = mutate<Unit> { db ->
        val catalog = loadCatalog(db)
        if (catalog.profiles.none { it.id == assignment.profileId }) invalid()
        db.translation_profilesQueries.putAssignment(
            assignment.scope.ordinal.toLong(),
            assignment.scopeId,
            assignment.profileId,
            encode(assignment),
        )
    }

    override suspend fun removeAssignment(scope: ProfileAssignmentScope, scopeId: Long): Boolean = mutate { db ->
        val exists = loadCatalog(db).assignments.any { it.scope == scope && it.scopeId == scopeId }
        if (exists) db.translation_profilesQueries.removeAssignment(scope.ordinal.toLong(), scopeId)
        exists
    }

    override suspend fun pruneOrphanNovelAssignments(): Int = mutate { db ->
        val novelScope = ProfileAssignmentScope.NOVEL.ordinal.toLong()
        val count = db.translation_profilesQueries.countOrphanNovelAssignments(novelScope).executeAsOne().toInt()
        if (count > 0) {
            db.translation_profilesQueries.pruneOrphanNovelAssignments(novelScope)
        }
        count
    }

    override suspend fun getLegacyCredentialReference(
        provider: String,
        scope: LegacyCredentialScope,
        scopeId: Long,
    ): LegacyCredentialReference? = read { db ->
        db.translation_profilesQueries.getLegacyReference(provider, scope.ordinal.toLong(), scopeId)
            .executeAsOneOrNull()?.let { record ->
                decode<LegacyCredentialReference>(record.record_json).also {
                    if (it.provider != record.provider || it.scope.ordinal.toLong() != record.scope ||
                        it.scopeId != record.scope_id || it.credentialId != record.credential_id
                    ) {
                        corrupt()
                    }
                }
            }
    }

    override suspend fun saveLegacyCredentialReference(reference: LegacyCredentialReference) = mutate<Unit> { db ->
        if (loadCatalog(db).credentials.none { it.id == reference.credentialId }) invalid()
        db.translation_profilesQueries.putLegacyReference(
            reference.provider,
            reference.scope.ordinal.toLong(),
            reference.scopeId,
            reference.credentialId,
            encode(reference),
        )
    }

    override suspend fun resolveProfile(
        explicitProfileId: String?,
        novelId: Long?,
        sourceId: Long?,
    ): TranslationProfile? = read { db ->
        val catalog = loadCatalog(db)
        if (explicitProfileId != null) {
            validId(explicitProfileId)
            return@read catalog.profiles.singleOrNull { it.id == explicitProfileId }
        }
        fun assigned(scope: ProfileAssignmentScope, id: Long?): String? = id?.let { value ->
            catalog.assignments.singleOrNull { it.scope == scope && it.scopeId == value }?.profileId
        }
        val profileId = assigned(ProfileAssignmentScope.NOVEL, novelId)
            ?: assigned(ProfileAssignmentScope.SOURCE, sourceId)
            ?: assigned(ProfileAssignmentScope.GLOBAL, 0)
        catalog.profiles.singleOrNull { it.id == profileId }
    }

    override suspend fun importConfiguration(bundle: ProfileCatalog) = mutate<Unit> { db ->
        val current = loadCatalog(db)
        validateUnique(bundle)
        val mergedConnections = mergePreservingExisting(
            current.connections,
            bundle.connections,
            { it.id },
            ::mergeConnection,
        )
        val mergedProfiles = mergePreservingExisting(current.profiles, bundle.profiles, { it.id }) { local, _ ->
            local
        }
        val mergedCredentials = mergePreservingExisting(current.credentials, bundle.credentials, { it.id }) {
                local,
                _,
            ->
            local
        }
        val mergedAssignments = mergePreservingExisting(
            current.assignments,
            bundle.assignments,
            { it.scope to it.scopeId },
        ) { local, _ -> local }
        val mergedReferences = mergePreservingExisting(
            current.legacyReferences,
            bundle.legacyReferences,
            { Triple(it.provider, it.scope, it.scopeId) },
        ) { local, _ -> local }
        val merged = ProfileCatalog(
            mergedConnections,
            mergedProfiles,
            mergedCredentials,
            mergedAssignments,
            mergedReferences,
        )
        validate(merged)
        if ((bundle.credentials + merged.credentials).any {
                db.translation_profilesQueries.getRetiredCredential(it.id).executeAsOneOrNull() != null
            }
        ) {
            invalid()
        }
        val existingConnections = current.connections.associateBy { it.id }
        val existingProfiles = current.profiles.associateBy { it.id }
        val existingCredentials = current.credentials.associateBy { it.id }
        val existingAssignments = current.assignments.associateBy { it.scope to it.scopeId }
        val existingReferences = current.legacyReferences.associateBy { Triple(it.provider, it.scope, it.scopeId) }
        // Encode every bounded payload before modifying any table.
        val credentials = mergedCredentials.filter { existingCredentials[it.id] != it }.map { it to encode(it) }
        val connections = mergedConnections.filter { existingConnections[it.id] != it }.map { it to encode(it) }
        val profiles = mergedProfiles.filter { existingProfiles[it.id] != it }.map { it to encode(it) }
        val assignments = mergedAssignments.filter { existingAssignments[it.scope to it.scopeId] != it }
            .map { it to encode(it) }
        val references = mergedReferences
            .filter { existingReferences[Triple(it.provider, it.scope, it.scopeId)] != it }
            .map { it to encode(it) }
        credentials.forEach { (value, encoded) -> db.translation_profilesQueries.putMetadata(value.id, encoded) }
        connections.forEach { (value, encoded) -> db.translation_profilesQueries.putConnection(value.id, encoded) }
        profiles.forEach { (value, encoded) ->
            db.translation_profilesQueries.putProfile(value.id, value.connectionId, encoded)
        }
        assignments.forEach { (value, encoded) ->
            db.translation_profilesQueries.putAssignment(
                value.scope.ordinal.toLong(),
                value.scopeId,
                value.profileId,
                encoded,
            )
        }
        references.forEach { (value, encoded) ->
            db.translation_profilesQueries.putLegacyReference(
                value.provider,
                value.scope.ordinal.toLong(),
                value.scopeId,
                value.credentialId,
                encoded,
            )
        }
    }

    override fun observeRoutingState(): Flow<CredentialRoutingState> = handler.subscribeToList {
        it.translation_profilesQueries.getRoutingState()
    }.map { readRoutingState() }.catch { error -> throwSafe(error) }

    override suspend fun readRoutingState(): CredentialRoutingState = read { loadRouting(it) }

    override suspend fun readLedger(runId: Long, generation: Long): RequestBudgetLedger = read { db ->
        validRunKey(runId, generation)
        loadLedger(db, runId, generation)
    }

    override suspend fun reserveRequest(
        runId: Long,
        generation: Long,
        connectionSnapshot: ConnectionProfile,
        modelId: String,
        policy: ExecutionPolicy,
        requestId: String,
        fragmentIds: Set<String>,
        tokenEstimate: Long?,
        now: Long,
    ): ProfileRequestReservation = mutate { db ->
        validRunKey(runId, generation)
        if (!currentRun(db, runId, generation, setOf(1L))) return@mutate ProfileRequestReservation.Stale
        if (connectionSnapshot.credentialBindings.any { binding ->
                db.translation_profilesQueries.getMetadata(binding.credentialId).executeAsOneOrNull() == null ||
                    db.translation_profilesQueries.getRetiredCredential(binding.credentialId)
                        .executeAsOneOrNull() != null
            }
        ) {
            return@mutate ProfileRequestReservation.NeedsAction
        }
        val connectionJson = encode(connectionSnapshot)
        val policyJson = encode(policy)
        val header = db.translation_profilesQueries.getLedger(runId, generation).executeAsOneOrNull()
        if (header != null && (
                header.connection_snapshot_json != connectionJson ||
                    header.model_id != modelId || header.policy_json != policyJson
                )
        ) {
            return@mutate ProfileRequestReservation.NeedsAction
        }
        val ledger = loadLedger(db, runId, generation)
        if (db.translation_profilesQueries.getReservation(runId, generation, requestId).executeAsOneOrNull() != null) {
            return@mutate ProfileRequestReservation.AlreadyReserved(ledger)
        }
        val budget = try {
            RequestBudgetPolicy.reserve(policy, ledger, ledger.scopeId, requestId, fragmentIds, tokenEstimate)
        } catch (_: IllegalArgumentException) {
            invalid()
        }
        when (budget) {
            is RequestReservationDecision.NoWork -> return@mutate ProfileRequestReservation.NoWork(budget.ledger)
            is RequestReservationDecision.AlreadyReserved -> {
                return@mutate ProfileRequestReservation.AlreadyReserved(budget.ledger)
            }
            is RequestReservationDecision.Blocked -> return@mutate ProfileRequestReservation.BudgetStop(budget.reason)
            is RequestReservationDecision.Reserved -> {
                val route = try {
                    CredentialRoutingPolicy.route(connectionSnapshot, modelId, loadRouting(db), now)
                } catch (_: IllegalArgumentException) {
                    invalid()
                }
                when (route) {
                    is CredentialRouteDecision.Waiting -> {
                        return@mutate ProfileRequestReservation.Waiting(route.notBeforeAt)
                    }
                    CredentialRouteDecision.NeedsAction -> return@mutate ProfileRequestReservation.NeedsAction
                    is CredentialRouteDecision.Selected -> {
                        val fragmentsJson = encode(budget.pendingFragmentIds)
                        val routingJson = encode(StoredRoutingState.from(route.nextState))
                        db.translation_profilesQueries.putLedger(
                            runId,
                            generation,
                            connectionJson,
                            modelId,
                            policyJson,
                            budget.ledger.reservedTokens,
                            budget.ledger.hasUnknownTokenReservations,
                        )
                        db.translation_profilesQueries.putReservation(
                            runId,
                            generation,
                            requestId,
                            route.credentialId,
                            route.quotaGroup,
                            fragmentsJson,
                            tokenEstimate,
                            now,
                        )
                        db.translation_profilesQueries.putRoutingState(routingJson)
                        return@mutate ProfileRequestReservation.Reserved(
                            route.credentialId,
                            route.quotaGroup,
                            budget.ledger,
                            budget.pendingFragmentIds,
                        )
                    }
                }
            }
        }
    }

    override suspend fun setCredentialHealth(credentialId: String, health: CredentialHealth) = mutate<Unit> { db ->
        validId(credentialId)
        if (loadCatalog(db).credentials.none { it.id == credentialId }) invalid()
        val state = loadRouting(db)
        saveRouting(db, state.copy(health = state.health + (credentialId to health)))
    }

    override suspend fun recordRoutingFailure(
        connectionSnapshot: ConnectionProfile,
        modelId: String,
        credentialId: String?,
        failure: RoutingFailure,
        notBeforeAt: Long,
        now: Long,
    ) = mutate<Unit> { db ->
        val next = try {
            CredentialRoutingPolicy.onFailure(
                connectionSnapshot,
                modelId,
                credentialId,
                failure,
                notBeforeAt,
                loadRouting(db),
                now,
            )
        } catch (_: IllegalArgumentException) {
            invalid()
        }
        saveRouting(db, next)
    }

    override suspend fun markCompletedFragments(
        runId: Long,
        generation: Long,
        fragmentIds: Set<String>,
    ): Boolean = mutate { db ->
        validRunKey(runId, generation)
        if (!currentRun(db, runId, generation, setOf(1L, 6L))) return@mutate false
        try {
            RequestBudgetPolicy.markComplete(loadLedger(db, runId, generation), fragmentIds)
        } catch (
            _: IllegalArgumentException,
        ) {
            invalid()
        }
        fragmentIds.forEach { db.translation_profilesQueries.putCompleted(runId, generation, it) }
        true
    }

    private fun currentRun(db: NovelDatabase, id: Long, generation: Long, statuses: Set<Long>): Boolean {
        val run = db.translation_profilesQueries.getRunState(id).executeAsOneOrNull() ?: return false
        return run.generation == generation && run.status in statuses
    }

    private fun loadLedger(db: NovelDatabase, id: Long, generation: Long): RequestBudgetLedger {
        val header = db.translation_profilesQueries.getLedger(id, generation).executeAsOneOrNull()
        val reservations = db.translation_profilesQueries.getLedgerReservations(id, generation).executeAsList()
        val requests = reservations.map { it.request_id }.toSet()
        var reservedTokens = 0L
        reservations.forEach { reservation ->
            val fragmentIds = decode<Set<String>>(reservation.fragments_json)
            try {
                RequestBudgetLedger(
                    "run:$id:generation:$generation",
                    setOf(reservation.request_id),
                    fragmentIds,
                )
            } catch (_: IllegalArgumentException) {
                corrupt()
            }
            val estimate = reservation.token_estimate
            val credential = reservation.credential_id
            if ((estimate != null && estimate <= 0) ||
                (credential != null && !CredentialIds.isValid(credential)) ||
                reservation.quota_group.isBlank() || reservation.created_at < 0
            ) {
                corrupt()
            }
            val tokens = estimate ?: 0L
            if (reservedTokens > Long.MAX_VALUE - tokens) corrupt()
            reservedTokens += tokens
        }
        val completed = db.translation_profilesQueries.getCompletedIds(id, generation).executeAsList().toSet()
        if (header == null && requests.isNotEmpty()) corrupt()
        if (header != null) {
            if (header.reserved_tokens != reservedTokens ||
                header.unknown_token_reservations != reservations.any { it.token_estimate == null }
            ) {
                corrupt()
            }
            decode<ConnectionProfile>(header.connection_snapshot_json)
            decode<ExecutionPolicy>(header.policy_json)
        }
        return try {
            RequestBudgetLedger(
                "run:$id:generation:$generation",
                requests,
                completed,
                header?.reserved_tokens ?: 0,
                header?.unknown_token_reservations ?: false,
            )
        } catch (_: IllegalArgumentException) {
            corrupt()
        }
    }

    private fun loadRouting(db: NovelDatabase): CredentialRoutingState =
        db.translation_profilesQueries.getRoutingState().executeAsOneOrNull()?.let {
            try {
                decode<StoredRoutingState>(it).toPolicyState()
            } catch (_: IllegalArgumentException) {
                corrupt()
            }
        } ?: CredentialRoutingState()

    private fun saveRouting(db: NovelDatabase, state: CredentialRoutingState) {
        db.translation_profilesQueries.putRoutingState(encode(StoredRoutingState.from(state)))
    }

    private fun loadCatalog(db: NovelDatabase): ProfileCatalog =
        decodeCatalog(db.translation_profilesQueries.getCatalogRecords().executeAsList())

    private fun decodeCatalog(records: List<GetCatalogRecords>): ProfileCatalog {
        val connections = mutableListOf<ConnectionProfile>()
        val profiles = mutableListOf<TranslationProfile>()
        val credentials = mutableListOf<CredentialMetadata>()
        val assignments = mutableListOf<ProfileAssignment>()
        val references = mutableListOf<LegacyCredentialReference>()
        records.forEach { record ->
            when (record.record_type) {
                0L -> connections += decode<ConnectionProfile>(record.record_json).also {
                    if (it.id != record.record_key) corrupt()
                }
                1L -> profiles += decode<TranslationProfile>(record.record_json).also {
                    if (it.id != record.record_key) corrupt()
                }
                2L -> credentials += decode<CredentialMetadata>(record.record_json).also {
                    if (it.id != record.record_key) corrupt()
                }
                3L -> assignments += decode<ProfileAssignment>(record.record_json).also {
                    if (it.profileId != record.record_key || it.scope.ordinal.toLong() != record.scope ||
                        it.scopeId != record.scope_id
                    ) {
                        corrupt()
                    }
                }
                4L -> references += decode<LegacyCredentialReference>(record.record_json).also {
                    if (it.provider != record.record_key || it.scope.ordinal.toLong() != record.scope ||
                        it.scopeId != record.scope_id
                    ) {
                        corrupt()
                    }
                }
                else -> corrupt()
            }
        }
        return ProfileCatalog(connections, profiles, credentials, assignments, references).also {
            try {
                validate(it)
            } catch (_: ProfileStorageException) {
                corrupt()
            }
        }
    }

    private fun validate(catalog: ProfileCatalog) {
        validateUnique(catalog)
        val connectionIds = catalog.connections.map { it.id }.toSet()
        val profileIds = catalog.profiles.map { it.id }.toSet()
        val credentials = catalog.credentials.map { it.id }.toSet()
        if (catalog.profiles.any { it.connectionId !in connectionIds } ||
            catalog.connections.any { connection ->
                connection.credentialBindings.any { it.credentialId !in credentials }
            } || catalog.assignments.any { it.profileId !in profileIds } ||
            catalog.legacyReferences.any { it.credentialId !in credentials }
        ) {
            invalid()
        }
    }

    private fun validateUnique(catalog: ProfileCatalog) {
        if (catalog.connections.map { it.id }.distinct().size != catalog.connections.size ||
            catalog.profiles.map { it.id }.distinct().size != catalog.profiles.size ||
            catalog.credentials.map { it.id }.distinct().size != catalog.credentials.size ||
            catalog.assignments.map { it.scope to it.scopeId }.distinct().size != catalog.assignments.size ||
            catalog.legacyReferences.map { Triple(it.provider, it.scope, it.scopeId) }.distinct().size !=
            catalog.legacyReferences.size
        ) {
            invalid()
        }
    }

    private fun <T, K> replace(values: List<T>, value: T, key: (T) -> K): List<T> =
        values.filterNot { key(it) == key(value) } + value

    private fun mergeConnection(local: ConnectionProfile, incoming: ConnectionProfile): ConnectionProfile {
        if (local.auth == ProfileAuth.NONE || incoming.credentialBindings.isEmpty()) return local
        val existingIds = local.credentialBindings.mapTo(mutableSetOf()) { it.credentialId }
        val additions = incoming.credentialBindings.filter { it.credentialId !in existingIds }
        if (additions.isEmpty()) return local
        val remaining = (100 - local.credentialBindings.size).coerceAtLeast(0)
        val mergedBindings = additions.take(remaining)
        return if (mergedBindings.isEmpty()) {
            local
        } else {
            local.copy(credentialBindings = local.credentialBindings + mergedBindings)
        }
    }

    private fun <T, K> mergePreservingExisting(
        first: List<T>,
        second: List<T>,
        key: (T) -> K,
        onCollision: (local: T, incoming: T) -> T,
    ): List<T> = first.associateBy(key).toMutableMap().apply {
        second.forEach { incoming ->
            val k = key(incoming)
            val existing = get(k)
            put(k, if (existing == null) incoming else onCollision(existing, incoming))
        }
    }.values.toList()

    private inline fun <reified T> encode(value: T): String {
        val encoded = JsonObject(mapOf("version" to JsonPrimitive(1), "value" to json.encodeToJsonElement(value)))
            .toString()
        if (encoded.toByteArray(Charsets.UTF_8).size > MAX_JSON_BYTES) {
            throw ProfileStorageException(ProfileStorageFailure.OVERSIZED_RECORD)
        }
        return encoded
    }

    private inline fun <reified T> decode(encoded: String): T = try {
        if (encoded.toByteArray(Charsets.UTF_8).size > MAX_JSON_BYTES) corrupt()
        val envelope = json.parseToJsonElement(encoded).jsonObject
        val version = envelope["version"] as? JsonPrimitive ?: corrupt()
        if (envelope.keys != setOf("version", "value") || version.isString || version.intOrNull != 1) {
            corrupt()
        }
        json.decodeFromJsonElement<T>(envelope.getValue("value"))
    } catch (_: Exception) {
        corrupt()
    }

    private fun validId(id: String) {
        if (!CredentialIds.isValid(id)) invalid()
    }
    private fun validRunKey(id: Long, generation: Long) {
        if (id <= 0 || generation < 0) invalid()
    }
    private fun invalid(): Nothing = throw ProfileStorageException(ProfileStorageFailure.INVALID_REFERENCE)
    private fun corrupt(): Nothing = throw ProfileStorageException(ProfileStorageFailure.CORRUPT)

    private suspend fun gate(): Mutex = mutationMutex ?: handler.await {
        NovelDatabaseMutationGate.mutexFor(it)
    }.also { mutationMutex = it }

    private suspend fun <T> read(block: suspend (NovelDatabase) -> T): T = try {
        // A shared JDBC connection must not expose an import/reservation halfway through its transaction.
        gate().withLock { handler.await(block = block) }
    } catch (error: Throwable) {
        throwSafe(error)
    }

    private suspend fun <T> mutate(block: suspend (NovelDatabase) -> T): T = try {
        gate().withLock { handler.await(inTransaction = true, block = block) }
    } catch (error: Throwable) {
        throwSafe(error)
    }

    private fun throwSafe(error: Throwable): Nothing {
        if (error is CancellationException) throw error
        throw ProfileStorageException((error as? ProfileStorageException)?.kind ?: ProfileStorageFailure.UNAVAILABLE)
    }

    @Serializable
    private data class StoredHealth(val status: CredentialHealthStatus, val notBeforeAt: Long)

    @Serializable
    private data class StoredRoutingState(
        val health: Map<String, StoredHealth>,
        val groups: Map<String, Long>,
        val services: Map<String, Long>,
        val cursors: Map<String, String>,
        val needsAction: Set<String>,
    ) {
        fun toPolicyState() = CredentialRoutingState(
            health.mapValues { CredentialHealth(it.value.status, it.value.notBeforeAt) },
            groups,
            services,
            cursors,
            needsAction,
        )
        companion object {
            fun from(state: CredentialRoutingState) = StoredRoutingState(
                state.health.mapValues { StoredHealth(it.value.status, it.value.notBeforeAt) },
                state.groupNotBeforeAt,
                state.serviceNotBeforeAt,
                state.lastSelectedByConnection,
                state.connectionsNeedingAction,
            )
        }
    }

    private companion object {
        const val MAX_JSON_BYTES = 1_048_576
    }
}
