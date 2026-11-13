package tachiyomi.data.translation

import datanovel.Translation_batch_state
import datanovel.Translation_queue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.translation.model.TranslationActionPauseReason
import tachiyomi.domain.translation.model.TranslationArtifactReceipt
import tachiyomi.domain.translation.model.TranslationBatch
import tachiyomi.domain.translation.model.TranslationBatchStatus
import tachiyomi.domain.translation.model.TranslationEnqueueResult
import tachiyomi.domain.translation.model.TranslationFragment
import tachiyomi.domain.translation.model.TranslationRun
import tachiyomi.domain.translation.model.TranslationRunStatus
import tachiyomi.domain.translation.profile.ConnectionProfile
import tachiyomi.domain.translation.profile.ProfileEndpointPolicy
import tachiyomi.domain.translation.profile.TranslationProfile
import tachiyomi.domain.translation.repository.TranslationRunRepository
import tachiyomi.domain.translation.service.TranslationFragmentPlanner
import tachiyomi.novel.data.NovelDatabase

/**
 * One writer at a time per authoritative NovelDatabase object, including repository/handler wrappers
 * sharing that object. Separate database objects/drivers/processes are not serialized by this lock.
 */
class TranslationRunRepositoryImpl(
    private val handler: NovelDatabaseHandler,
    private val json: Json,
) : TranslationRunRepository {

    @Volatile
    private var mutationMutex: Mutex? = null

    override suspend fun enqueue(
        chapterIds: List<Long>,
        novelId: Long,
        profileSnapshotJson: String?,
        profileFingerprint: String,
        batchToken: String,
        forceRetranslate: Boolean,
        priority: Int,
        now: Long,
        sessionOnly: Boolean,
    ): TranslationEnqueueResult = awaitMutation { db ->
        val token = batchToken.takeUnless { it.isBlank() }.orEmpty()
        val requested = chapterIds.distinct()
        if (token.isNotEmpty()) {
            val batch = db.translation_queueQueries.getBatchState(token).executeAsOneOrNull()?.toDomain()
            if (batch != null && (batch.novelId != novelId || batch.status !in activeBatchStatuses)) {
                return@awaitMutation TranslationEnqueueResult(token, emptyList(), emptyList(), requested)
            }
            db.translation_queueQueries.insertRunBatch(token, novelId, now)
        }
        val inserted = mutableListOf<Long>()
        val joined = mutableListOf<Long>()
        val conflicting = mutableListOf<Long>()
        requested.forEachIndexed { order, chapterId ->
            val previous = db.translation_queueQueries.getByChapterId(chapterId).executeAsOneOrNull()?.toDomain()
            val sameProfile = previous != null && previous.novelId == novelId &&
                previous.profileSnapshotJson == profileSnapshotJson &&
                previous.profileFingerprint == profileFingerprint &&
                previous.sessionOnly == sessionOnly
            val current: TranslationRun
            if (previous != null && !previous.status.isTerminal) {
                if (forceRetranslate || !sameProfile) {
                    conflicting += chapterId
                    return@forEachIndexed
                }
                db.translation_queueQueries.joinRun(
                    implicitOwner = previous.implicitOwner || token.isEmpty(),
                    priority = priority.toLong(),
                    now = now,
                    queueId = previous.id,
                    generation = previous.generation,
                )
                current = currentRun(db, previous).shouldExist()
                joined += chapterId
            } else if (previous != null) {
                check(previous.generation < Long.MAX_VALUE) { "Translation generation is exhausted" }
                val cleared = previous.status == TranslationRunStatus.CANCELLED &&
                    previous.forceRetranslate &&
                    previous.sourceFingerprint == null
                val effectiveForce = forceRetranslate || cleared
                val reuse = sameProfile && !effectiveForce && !sessionOnly &&
                    (
                        previous.status == TranslationRunStatus.FAILED ||
                            previous.status == TranslationRunStatus.CANCELLED
                        ) &&
                    previous.sourceFingerprint != null
                val copies = if (reuse) {
                    readFragments(db, previous).map { fragment ->
                        if (fragment.isComplete) {
                            fragment
                        } else {
                            fragment.copy(
                                translatedText = null,
                                attempts = 0,
                                firstFailureAt = null,
                                nextAttemptAt = 0,
                                failure = null,
                            )
                        }
                    }
                } else {
                    emptyList()
                }
                val payloads = copies.map { it to encodeFragment(it).shouldExist() }
                db.translation_queueQueries.rerun(
                    novelId = novelId,
                    batchToken = token,
                    batchOrder = order.toLong(),
                    profileSnapshotJson = profileSnapshotJson,
                    newGeneration = previous.generation + 1,
                    progress = if (reuse) previous.progress.coerceIn(0, 99).toLong() else 0L,
                    now = now,
                    sourceFingerprint = if (reuse) previous.sourceFingerprint else null,
                    profileFingerprint = profileFingerprint,
                    sourceSegmentCount = if (reuse) previous.sourceSegmentCount.toLong() else 0L,
                    forceRetranslate = effectiveForce,
                    priority = priority.toLong(),
                    implicitOwner = token.isEmpty(),
                    sessionOnly = sessionOnly,
                    queueId = previous.id,
                    generation = previous.generation,
                )
                current = db.translation_queueQueries.getRunById(previous.id).executeAsOne().toDomain()
                payloads.forEach { (fragment, payload) -> saveFragment(db, current, fragment, payload, now) }
                inserted += chapterId
            } else {
                db.translation_queueQueries.insertRun(
                    chapterId = chapterId,
                    novelId = novelId,
                    batchToken = token,
                    batchOrder = order.toLong(),
                    profileSnapshotJson = profileSnapshotJson,
                    now = now,
                    profileFingerprint = profileFingerprint,
                    forceRetranslate = forceRetranslate,
                    priority = priority.toLong(),
                    implicitOwner = token.isEmpty(),
                    sessionOnly = sessionOnly,
                )
                current = db.translation_queueQueries.getByChapterId(chapterId).executeAsOne().toDomain()
                inserted += chapterId
            }
            if (token.isNotEmpty()) {
                db.translation_checkpointQueries.insertMembership(
                    token,
                    current.id,
                    current.generation,
                    chapterId,
                    order.toLong(),
                )
            }
            reconcileOwners(db, current, now)
        }
        if (token.isNotEmpty()) {
            db.translation_queueQueries.updateRunBatchAdmission(
                batchToken = token,
                conflictingCount = conflicting.size.toLong(),
                requestedCount = requested.size.toLong(),
                now = now,
            )
            refreshBatch(db, token, now)
        }
        TranslationEnqueueResult(token, inserted, joined, conflicting)
    }

    override suspend fun promotePrefetchRun(
        chapterId: Long,
        batchToken: String,
        profileSnapshotJson: String,
        profileFingerprint: String,
        now: Long,
    ): Boolean = awaitMutation { db ->
        if (batchToken.isBlank()) return@awaitMutation false
        val run = db.translation_queueQueries.getByChapterId(chapterId).executeAsOneOrNull()?.toDomain()
            ?: return@awaitMutation false
        if (run.status.isTerminal ||
            run.profileSnapshotJson != profileSnapshotJson ||
            run.profileFingerprint != profileFingerprint
        ) {
            return@awaitMutation false
        }
        val batch = db.translation_queueQueries.getBatchState(batchToken).executeAsOneOrNull()?.toDomain()
            ?: return@awaitMutation false
        if (batch.status != TranslationBatchStatus.QUEUED &&
            batch.status != TranslationBatchStatus.RUNNING
        ) {
            return@awaitMutation false
        }
        if (
            batchToken !in db.translation_checkpointQueries.getPendingRunBatchTokens(
                run.id,
                run.generation,
            ).executeAsList()
        ) {
            return@awaitMutation false
        }
        db.translation_queueQueries.joinRun(
            implicitOwner = true,
            priority = 2L,
            now = now,
            queueId = run.id,
            generation = run.generation,
        )
        if (db.translation_queueQueries.mutationChanges().executeAsOne() != 1L) return@awaitMutation false
        reconcileOwners(db, run, now)
        true
    }

    override suspend fun claimNext(now: Long): TranslationRun? = awaitMutation { db ->
        val pending = db.translation_queueQueries.getRunnableDue(now).executeAsOneOrNull()?.toDomain()
            ?: return@awaitMutation null
        db.translation_queueQueries.claimRun(now, pending.id, pending.generation)
        if (db.translation_queueQueries.mutationChanges().executeAsOne() != 1L) return@awaitMutation null
        val claimed = currentRun(db, pending).shouldExist()
        refreshOwners(db, claimed, now)
        claimed
    }

    override suspend fun getByChapterId(chapterId: Long): TranslationRun? = handler.await { db ->
        db.translation_queueQueries.getByChapterId(chapterId).executeAsOneOrNull()?.toDomain()
    }

    override fun observeRuns(novelId: Long?): Flow<List<TranslationRun>> = handler.subscribeToList { db ->
        if (
            novelId == null
        ) {
            db.translation_queueQueries.getAllRuns()
        } else {
            db.translation_queueQueries.getRunsForNovel(novelId)
        }
    }.map { rows -> rows.map { it.toDomain() } }

    override suspend fun getFragments(run: TranslationRun): List<TranslationFragment> = handler.await { db ->
        if (run.sessionOnly) emptyList() else readFragments(db, run)
    }

    override suspend fun prepare(
        run: TranslationRun,
        sourceFingerprint: String,
        profileFingerprint: String,
        sourceSegmentCount: Int,
        fragments: List<TranslationFragment>,
        now: Long,
    ): Boolean = awaitMutation { db ->
        val current = writable(db, run) ?: return@awaitMutation false
        if (current.profileSnapshotJson == null ||
            sourceFingerprint.isBlank() ||
            profileFingerprint.isBlank()
        ) {
            return@awaitMutation false
        }
        if (current.profileFingerprint != null &&
            current.profileFingerprint != profileFingerprint
        ) {
            return@awaitMutation false
        }
        if (!validCoverage(fragments, sourceSegmentCount)) return@awaitMutation false
        if (current.sourceFingerprint != null && current.sessionOnly != run.sessionOnly) return@awaitMutation false
        val sessionOnly = current.sessionOnly || run.sessionOnly
        if (sessionOnly && !current.sessionOnly &&
            db.translation_checkpointQueries.countFragments(current.id, current.generation).executeAsOne() != 0L
        ) {
            return@awaitMutation false
        }
        if (current.sourceFingerprint != null) {
            if (current.sourceFingerprint != sourceFingerprint ||
                current.sourceSegmentCount != sourceSegmentCount
            ) {
                return@awaitMutation false
            }
            if (current.sessionOnly) return@awaitMutation true
            val stored = readFragments(db, current)
            return@awaitMutation stored.size == fragments.size && stored.associateBy { it.id }.let { byId ->
                fragments.all { incoming -> byId[incoming.id]?.let { sameSource(it, incoming) } == true }
            }
        }
        // Session-only work never serializes or persists source/output fragment text.
        val payloads = if (sessionOnly) {
            emptyList()
        } else {
            fragments.map { fragment ->
                fragment to (encodeFragment(fragment) ?: return@awaitMutation false)
            }
        }
        db.translation_queueQueries.prepareRunIdentity(
            sourceFingerprint = sourceFingerprint,
            profileFingerprint = profileFingerprint,
            sourceSegmentCount = sourceSegmentCount.toLong(),
            sessionOnly = sessionOnly,
            now = now,
            queueId = current.id,
            generation = current.generation,
        )
        if (db.translation_queueQueries.mutationChanges().executeAsOne() != 1L) return@awaitMutation false
        payloads.forEach { (fragment, payload) -> saveFragment(db, current, fragment, payload, now) }
        true
    }

    override suspend fun freezeProfile(
        run: TranslationRun,
        json: String,
        fingerprint: String,
        now: Long,
    ): TranslationRun? =
        awaitMutation { db ->
            val current = currentRun(db, run) ?: return@awaitMutation null
            if (current.status.isTerminal || current.profileSnapshotJson != null) return@awaitMutation null
            db.translation_queueQueries.freezeRunProfile(json, fingerprint, now, current.id, current.generation)
            if (db.translation_queueQueries.mutationChanges().executeAsOne() != 1L) return@awaitMutation null
            currentRun(db, run)
        }

    override suspend fun checkpoint(
        run: TranslationRun,
        changes: List<TranslationFragment>,
        progress: Int,
        now: Long,
    ): Boolean = awaitMutation { db ->
        val current = writable(db, run) ?: return@awaitMutation false
        if (current.sourceFingerprint == null || current.sessionOnly != run.sessionOnly) return@awaitMutation false
        if (!current.sessionOnly) {
            if (changes.map { it.id }.distinct().size != changes.size) return@awaitMutation false
            val stored = readFragments(db, current).associateBy { it.id }
            val payloads = changes.mapNotNull { incoming ->
                val prior = stored[incoming.id] ?: return@awaitMutation false
                if (!validFragment(incoming) || !sameSource(prior, incoming)) return@awaitMutation false
                // A late failure cannot erase a response already checkpointed in this generation.
                if (prior.isComplete) null else incoming to (encodeFragment(incoming) ?: return@awaitMutation false)
            }
            payloads.forEach { (fragment, payload) -> saveFragment(db, current, fragment, payload, now) }
        }
        db.translation_queueQueries.checkpointRunProgress(
            progress.coerceIn(0, 99).toLong(),
            now,
            current.id,
            current.generation,
        )
        db.translation_queueQueries.mutationChanges().executeAsOne() == 1L
    }

    override suspend fun release(
        run: TranslationRun,
        status: TranslationRunStatus,
        progress: Int,
        error: String?,
        nextAttemptAt: Long,
        now: Long,
    ): Boolean = awaitMutation { db ->
        if (status !in releaseStatuses) return@awaitMutation false
        val current = writable(db, run) ?: return@awaitMutation false
        if (current.actionPaused) return@awaitMutation false
        val effective = when {
            status == TranslationRunStatus.FAILED -> status
            !hasRunnableOwner(db, current) -> TranslationRunStatus.PAUSED
            status == TranslationRunStatus.PAUSED -> TranslationRunStatus.PENDING
            else -> status
        }
        val due = when (effective) {
            TranslationRunStatus.WAITING -> nextAttemptAt.coerceAtLeast(0)
            TranslationRunStatus.PAUSED -> maxOf(current.nextAttemptAt, nextAttemptAt, 0)
            else -> 0L
        }
        db.translation_queueQueries.releaseRun(
            status = effective.ordinal.toLong(),
            progress = progress.coerceIn(0, 99).toLong(),
            errorMessage = error,
            nextAttemptAt = due,
            now = now,
            queueId = current.id,
            generation = current.generation,
        )
        if (db.translation_queueQueries.mutationChanges().executeAsOne() != 1L) return@awaitMutation false
        if (effective == TranslationRunStatus.FAILED) {
            db.translation_checkpointQueries.setRunMembersOutcome(FAILED_OUTCOME, current.id, current.generation)
        }
        refreshOwners(db, current, now)
        true
    }

    override suspend fun publish(run: TranslationRun, now: Long, publish: () -> Unit): Boolean =
        awaitMutation { db ->
            val current = currentRun(db, run) ?: return@awaitMutation false
            if (current.status != TranslationRunStatus.RUNNING || current.sessionOnly != run.sessionOnly ||
                !hasRunnableOwner(db, current)
            ) {
                return@awaitMutation false
            }
            if (current.profileSnapshotJson == null || current.profileFingerprint == null ||
                current.sourceFingerprint == null || current.sourceSegmentCount <= 0
            ) {
                return@awaitMutation false
            }
            if (!current.sessionOnly) {
                val fragments = readFragments(db, current)
                if (!validCoverage(fragments, current.sourceSegmentCount) ||
                    fragments.any { !it.isComplete }
                ) {
                    return@awaitMutation false
                }
            }
            completePublication(db, current, now, publish)
        }

    override suspend fun publishCached(
        run: TranslationRun,
        now: Long,
        receipt: TranslationArtifactReceipt,
        publish: () -> Unit,
    ): Boolean = awaitMutation { db ->
        val current = currentRun(db, run) ?: return@awaitMutation false
        if (current.status != TranslationRunStatus.RUNNING || current.sessionOnly != run.sessionOnly ||
            current.forceRetranslate || !hasRunnableOwner(db, current)
        ) {
            return@awaitMutation false
        }
        if (current.profileSnapshotJson == null || current.sourceSegmentCount <= 0 ||
            current.sourceFingerprint != receipt.sourceFingerprint ||
            current.profileFingerprint != receipt.profileFingerprint ||
            current.sourceSegmentCount != receipt.sourceSegmentCount
        ) {
            return@awaitMutation false
        }
        completePublication(db, current, now, publish)
    }

    override suspend fun getBatch(batchToken: String): TranslationBatch? = handler.await { db ->
        db.translation_queueQueries.getBatchState(batchToken).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun getActiveBatches(): List<TranslationBatch> = handler.await { db ->
        db.translation_queueQueries.getActiveRunBatches().executeAsList().map { it.toDomain() }
    }

    override suspend fun pauseBatch(batchToken: String, now: Long): Boolean = awaitMutation { db ->
        val batch = db.translation_queueQueries.getBatchState(batchToken).executeAsOneOrNull()?.toDomain()
            ?: return@awaitMutation false
        if (batch.status != TranslationBatchStatus.QUEUED &&
            batch.status != TranslationBatchStatus.RUNNING
        ) {
            return@awaitMutation false
        }
        db.translation_queueQueries.setRunBatchStatus(TranslationBatchStatus.PAUSED.ordinal.toLong(), now, batchToken)
        db.translation_checkpointQueries.getBatchRuns(batchToken).executeAsList().forEach {
            reconcileOwners(db, it.toDomain(), now)
        }
        refreshBatch(db, batchToken, now)
        true
    }

    override suspend fun resumeBatch(batchToken: String, now: Long): Boolean = awaitMutation { db ->
        val batch = db.translation_queueQueries.getBatchState(batchToken).executeAsOneOrNull()?.toDomain()
            ?: return@awaitMutation false
        if (batch.status != TranslationBatchStatus.PAUSED) return@awaitMutation false
        db.translation_queueQueries.setRunBatchStatus(TranslationBatchStatus.QUEUED.ordinal.toLong(), now, batchToken)
        db.translation_checkpointQueries.getBatchRuns(batchToken).executeAsList().forEach {
            reconcileOwners(db, it.toDomain(), now)
        }
        refreshBatch(db, batchToken, now)
        true
    }

    override suspend fun cancelBatch(batchToken: String, now: Long): Boolean = awaitMutation { db ->
        val batch = db.translation_queueQueries.getBatchState(batchToken).executeAsOneOrNull()?.toDomain()
            ?: return@awaitMutation false
        if (batch.status !in activeBatchStatuses) return@awaitMutation false
        val affected = db.translation_checkpointQueries.getBatchRuns(batchToken).executeAsList().map { it.toDomain() }
        db.translation_queueQueries.setRunBatchStatus(
            TranslationBatchStatus.CANCELLED.ordinal.toLong(),
            now,
            batchToken,
        )
        db.translation_checkpointQueries.cancelBatchMemberships(batchToken)
        affected.forEach { reconcileOwners(db, it, now) }
        refreshBatch(db, batchToken, now)
        true
    }

    override suspend fun pauseImplicitOwner(
        chapterId: Long,
        expectedGeneration: Long,
        now: Long,
    ): Boolean = changeImplicitOwner(chapterId, expectedGeneration, now, pause = true, detach = false)

    override suspend fun resumeImplicitOwner(
        chapterId: Long,
        expectedGeneration: Long,
        now: Long,
    ): Boolean = changeImplicitOwner(chapterId, expectedGeneration, now, pause = false, detach = false)

    override suspend fun detachImplicitOwner(
        chapterId: Long,
        expectedGeneration: Long,
        now: Long,
    ): Boolean = changeImplicitOwner(chapterId, expectedGeneration, now, pause = false, detach = true)

    private suspend fun changeImplicitOwner(
        chapterId: Long,
        expectedGeneration: Long,
        now: Long,
        pause: Boolean,
        detach: Boolean,
    ): Boolean = awaitMutation { db ->
        val run = db.translation_queueQueries.getByChapterId(chapterId).executeAsOneOrNull()?.toDomain()
            ?: return@awaitMutation false
        if (run.generation != expectedGeneration || run.status.isTerminal || !run.implicitOwner ||
            (!detach && run.implicitPaused == pause)
        ) {
            return@awaitMutation false
        }
        db.translation_queueQueries.setImplicitOwnerState(!detach, pause && !detach, now, run.id, run.generation)
        if (db.translation_queueQueries.mutationChanges().executeAsOne() != 1L) return@awaitMutation false
        reconcileOwners(db, run, now)
        refreshOwners(db, run, now)
        true
    }

    override suspend fun parkForAction(
        chapterId: Long,
        expectedGeneration: Long,
        reason: TranslationActionPauseReason,
        now: Long,
    ): Boolean = awaitMutation { db ->
        val run = db.translation_queueQueries.getByChapterId(chapterId).executeAsOneOrNull()?.toDomain()
            ?: return@awaitMutation false
        val owned = run.implicitOwner ||
            db.translation_checkpointQueries.countRunOwners(run.id, run.generation).executeAsOne() > 0L
        if (run.generation != expectedGeneration || run.status.isTerminal || !owned) return@awaitMutation false
        db.translation_queueQueries.parkAction(reason.name, now, run.id, run.generation)
        db.translation_queueQueries.mutationChanges().executeAsOne() == 1L
    }

    override suspend fun resumeActionPaused(
        chapterId: Long,
        expectedGeneration: Long,
        now: Long,
    ): Boolean = awaitMutation { db ->
        val run = db.translation_queueQueries.getByChapterId(chapterId).executeAsOneOrNull()?.toDomain()
            ?: return@awaitMutation false
        val owned = run.implicitOwner ||
            db.translation_checkpointQueries.countRunOwners(run.id, run.generation).executeAsOne() > 0L
        if (run.generation != expectedGeneration || run.status != TranslationRunStatus.PAUSED ||
            !run.actionPaused || !owned
        ) {
            return@awaitMutation false
        }
        val status = when {
            !hasRunnableOwner(db, run) -> TranslationRunStatus.PAUSED
            run.nextAttemptAt > 0 -> TranslationRunStatus.WAITING
            else -> TranslationRunStatus.PENDING
        }
        db.translation_queueQueries.resumeAction(status.ordinal.toLong(), now, run.id, run.generation)
        db.translation_queueQueries.mutationChanges().executeAsOne() == 1L
    }

    override suspend fun retryOwnedRun(
        chapterId: Long,
        expectedGeneration: Long,
        force: Boolean,
        now: Long,
    ): Boolean = awaitMutation { db ->
        val run = db.translation_queueQueries.getByChapterId(chapterId).executeAsOneOrNull()?.toDomain()
            ?: return@awaitMutation false
        if (run.generation != expectedGeneration || !run.status.isTerminal || run.generation == Long.MAX_VALUE) {
            return@awaitMutation false
        }
        val snapshot = run.profileSnapshotJson ?: return@awaitMutation false
        val references = frozenRunCredentialIds(snapshot, json) ?: return@awaitMutation false
        if (!validRetrySnapshot(snapshot, json) || references.any {
                db.translation_profilesQueries.getMetadata(it).executeAsOneOrNull() == null ||
                    db.translation_profilesQueries.getRetiredCredential(it).executeAsOneOrNull() != null
            }
        ) {
            return@awaitMutation false
        }
        val effectiveForce = force || (run.forceRetranslate && run.sourceFingerprint == null)
        val reuse = !effectiveForce && !run.sessionOnly && run.sourceFingerprint != null &&
            (run.status == TranslationRunStatus.FAILED || run.status == TranslationRunStatus.CANCELLED)
        val fragments = if (reuse) {
            try {
                readFragments(db, run).also {
                    if (!validCoverage(it, run.sourceSegmentCount)) return@awaitMutation false
                }.map {
                    if (it.isComplete) {
                        it
                    } else {
                        it.copy(
                            translatedText = null,
                            attempts = 0,
                            firstFailureAt = null,
                            nextAttemptAt = 0,
                            failure = null,
                        )
                    }
                }
            } catch (_: Exception) {
                return@awaitMutation false
            }
        } else {
            emptyList()
        }
        val payloads = fragments.map { it to (encodeFragment(it) ?: return@awaitMutation false) }
        val fingerprint = run.profileFingerprint ?: TranslationFragmentPlanner.sourceFingerprint(listOf(snapshot))
        db.translation_queueQueries.rerun(
            novelId = run.novelId, batchToken = "", batchOrder = 0, profileSnapshotJson = snapshot,
            newGeneration = run.generation + 1, progress = if (reuse) run.progress.coerceIn(0, 99).toLong() else 0,
            now = now, sourceFingerprint = if (reuse) run.sourceFingerprint else null,
            profileFingerprint = fingerprint, sourceSegmentCount = if (reuse) run.sourceSegmentCount.toLong() else 0,
            forceRetranslate = effectiveForce, priority = 2, implicitOwner = true, sessionOnly = run.sessionOnly,
            queueId = run.id, generation = run.generation,
        )
        if (db.translation_queueQueries.mutationChanges().executeAsOne() != 1L) return@awaitMutation false
        val current = db.translation_queueQueries.getRunById(run.id).executeAsOne().toDomain()
        payloads.forEach { (fragment, payload) -> saveFragment(db, current, fragment, payload, now) }
        true
    }

    override suspend fun cancelChapter(chapterId: Long, now: Long): Boolean = awaitMutation { db ->
        val current = db.translation_queueQueries.getByChapterId(chapterId).executeAsOneOrNull()?.toDomain()
            ?: return@awaitMutation false
        cancelCurrent(db, current, now)
    }

    override suspend fun cancelAll(now: Long): Int = awaitMutation { db ->
        val batches = db.translation_queueQueries.getActiveRunBatches().executeAsList()
        val runs = db.translation_queueQueries.getActiveRuns().executeAsList()
        batches.forEach { batch ->
            db.translation_queueQueries.setRunBatchStatus(
                TranslationBatchStatus.CANCELLED.ordinal.toLong(),
                now,
                batch.batch_token,
            )
            db.translation_checkpointQueries.cancelBatchMemberships(batch.batch_token)
        }
        val cancelled = runs.count { cancelCurrent(db, it.toDomain(), now) }
        batches.forEach { refreshBatch(db, it.batch_token, now) }
        cancelled
    }

    override suspend fun invalidateCheckpoint(chapterId: Long, now: Long): Boolean = awaitMutation { db ->
        val run = db.translation_queueQueries.getByChapterId(chapterId).executeAsOneOrNull()?.toDomain()
            ?: return@awaitMutation false
        invalidateCurrent(db, run, now)
    }

    override suspend fun invalidateAllCheckpoints(now: Long): Int = awaitMutation { db ->
        db.translation_queueQueries.getAllRuns().executeAsList().count { invalidateCurrent(db, it.toDomain(), now) }
    }

    override suspend fun isRunnable(run: TranslationRun): Boolean = handler.await { db ->
        val current = currentRun(db, run) ?: return@await false
        current.status == TranslationRunStatus.RUNNING && !current.actionPaused && hasRunnableOwner(db, current)
    }

    override suspend fun nextWakeAt(): Long? = handler.await { db ->
        db.translation_queueQueries.getNextWakeAt().executeAsOneOrNull()
    }

    override suspend fun recoverInterrupted(now: Long): Int = awaitMutation { db ->
        val interrupted = db.translation_queueQueries.getRunningRuns().executeAsList().map { it.toDomain() }
        interrupted.count { run ->
            if (!run.implicitOwner &&
                db.translation_checkpointQueries.countRunOwners(run.id, run.generation).executeAsOne() == 0L
            ) {
                cancelCurrent(db, run, now)
            } else {
                val status = if (
                    hasRunnableOwner(db, run)
                ) {
                    TranslationRunStatus.PENDING
                } else {
                    TranslationRunStatus.PAUSED
                }
                db.translation_queueQueries.recoverRun(status.ordinal.toLong(), now, run.id, run.generation)
                val changed = db.translation_queueQueries.mutationChanges().executeAsOne() == 1L
                refreshOwners(db, run, now)
                changed
            }
        }
    }

    private fun completePublication(db: NovelDatabase, run: TranslationRun, now: Long, publish: () -> Unit): Boolean {
        val owners = db.translation_checkpointQueries.getPendingRunBatchTokens(run.id, run.generation).executeAsList()
        // The callback is deliberately inside the same ownership transaction. Do not catch it.
        publish()
        db.translation_queueQueries.completeRun(now, run.id, run.generation)
        check(
            db.translation_queueQueries.mutationChanges().executeAsOne() == 1L,
        ) { "Translation publication ownership changed" }
        db.translation_checkpointQueries.setRunMembersOutcome(COMPLETE_OUTCOME, run.id, run.generation)
        owners.forEach { token ->
            db.translation_queueQueries.recordRunBatchSuccess(run.chapterId, now, token)
            refreshBatch(db, token, now)
        }
        return true
    }

    private suspend fun <T> awaitMutation(block: suspend (NovelDatabase) -> T): T {
        val mutex = mutationMutex ?: handler.await { db ->
            // This lookup issues no SQL and cannot alter the connection-local changes() result.
            NovelDatabaseMutationGate.mutexFor(db)
        }.also { mutationMutex = it }
        return mutex.withLock { handler.await(inTransaction = true, block = block) }
    }

    private fun currentRun(db: NovelDatabase, expected: TranslationRun): TranslationRun? =
        db.translation_queueQueries.getRunById(expected.id).executeAsOneOrNull()?.toDomain()
            ?.takeIf { it.generation == expected.generation }

    private fun writable(db: NovelDatabase, expected: TranslationRun): TranslationRun? =
        currentRun(db, expected)?.takeIf {
            it.status == TranslationRunStatus.RUNNING ||
                it.status == TranslationRunStatus.PAUSED
        }

    private fun hasRunnableOwner(db: NovelDatabase, run: TranslationRun): Boolean =
        (run.implicitOwner && !run.implicitPaused) ||
            db.translation_checkpointQueries.countRunnableOwners(run.id, run.generation).executeAsOne() > 0L

    private fun reconcileOwners(db: NovelDatabase, expected: TranslationRun, now: Long) {
        val run = currentRun(db, expected) ?: return
        if (run.status.isTerminal) return
        val runnable = hasRunnableOwner(db, run)
        val owned = run.implicitOwner ||
            db.translation_checkpointQueries.countRunOwners(run.id, run.generation).executeAsOne() > 0L
        if (!owned) {
            cancelCurrent(db, run, now)
        } else if (run.actionPaused) {
            // Joining, resuming or detaching an owner is never an implicit action-pause resume.
            return
        } else if (run.status != TranslationRunStatus.RUNNING) {
            val status = when {
                !runnable -> TranslationRunStatus.PAUSED
                run.status != TranslationRunStatus.PAUSED -> run.status
                run.nextAttemptAt > 0 -> TranslationRunStatus.WAITING
                else -> TranslationRunStatus.PENDING
            }
            db.translation_queueQueries.setIdleRunStatus(status.ordinal.toLong(), now, run.id, run.generation)
        } else {
            // Publish ownership-only changes to row observers without discarding in-flight work.
            db.translation_queueQueries.touchRunOwnership(now, run.id, run.generation)
        }
        // RUNNING stays writable while paused; the engine's beforeRequest gate checks owner state.
    }

    private fun cancelCurrent(db: NovelDatabase, run: TranslationRun, now: Long): Boolean {
        if (run.status.isTerminal) return false
        db.translation_queueQueries.cancelRun(now, run.id, run.generation)
        if (db.translation_queueQueries.mutationChanges().executeAsOne() != 1L) return false
        db.translation_checkpointQueries.setRunMembersOutcome(CANCELLED_OUTCOME, run.id, run.generation)
        refreshOwners(db, run, now)
        return true
    }

    private fun invalidateCurrent(db: NovelDatabase, expected: TranslationRun, now: Long): Boolean {
        val run = currentRun(db, expected) ?: return false
        val alreadyCleared = run.status == TranslationRunStatus.CANCELLED && run.forceRetranslate &&
            run.sourceFingerprint == null &&
            db.translation_checkpointQueries.countFragments(run.id, run.generation).executeAsOne() == 0L
        if (alreadyCleared) return false
        check(run.generation < Long.MAX_VALUE) { "Translation generation is exhausted" }
        db.translation_queueQueries.invalidateRun(run.generation + 1, now, run.id, run.generation)
        if (db.translation_queueQueries.mutationChanges().executeAsOne() != 1L) return false
        db.translation_checkpointQueries.setRunMembersOutcome(CANCELLED_OUTCOME, run.id, run.generation)
        refreshOwners(db, run, now)
        return true
    }

    private fun refreshOwners(db: NovelDatabase, run: TranslationRun, now: Long) {
        db.translation_checkpointQueries.getRunBatchTokens(run.id, run.generation).executeAsList().forEach {
            refreshBatch(db, it, now)
        }
    }

    private fun refreshBatch(db: NovelDatabase, token: String, now: Long) {
        db.translation_queueQueries.recomputeRunBatchCounters(token, now)
        val batch = db.translation_queueQueries.getBatchState(token).executeAsOneOrNull()?.toDomain() ?: return
        if (batch.status == TranslationBatchStatus.CANCELLED) return
        val pending = db.translation_checkpointQueries.countPendingBatchMembers(token).executeAsOne()
        val terminal = pending == 0L && batch.completed.toLong() + batch.failed + batch.cancelled >= batch.enqueued
        val status = when {
            terminal && batch.failed > 0 -> TranslationBatchStatus.FAILED
            terminal && batch.cancelled > 0 -> TranslationBatchStatus.CANCELLED
            terminal -> TranslationBatchStatus.COMPLETED
            batch.status == TranslationBatchStatus.PAUSED -> TranslationBatchStatus.PAUSED
            db.translation_checkpointQueries.countRunningBatchMembers(
                token,
            ).executeAsOne() > 0L -> TranslationBatchStatus.RUNNING
            else -> TranslationBatchStatus.QUEUED
        }
        db.translation_queueQueries.setRunBatchStatus(status.ordinal.toLong(), now, token)
    }

    private fun readFragments(db: NovelDatabase, run: TranslationRun): List<TranslationFragment> =
        db.translation_checkpointQueries.getFragments(run.id, run.generation).executeAsList().map { record ->
            val fragment = try {
                json.decodeFromString<TranslationFragment>(record.payload_json)
            } catch (_: Exception) {
                // Parser exceptions can quote payload text. Never attach them to diagnostics.
                throw IllegalStateException("Stored translation checkpoint is invalid")
            }
            check(
                fragment.id.toLong() == record.fragment_id &&
                    validFragment(fragment),
            ) { "Stored translation checkpoint is invalid" }
            fragment
        }

    private fun saveFragment(
        db: NovelDatabase,
        run: TranslationRun,
        fragment: TranslationFragment,
        payload: String,
        now: Long,
    ) {
        db.translation_checkpointQueries.saveFragment(run.id, run.generation, fragment.id.toLong(), payload, now)
    }

    private fun encodeFragment(fragment: TranslationFragment): String? =
        runCatching { json.encodeToString(fragment) }.getOrNull()?.takeIf { it.length <= MAX_PAYLOAD_CHARS }

    private fun validFragment(fragment: TranslationFragment): Boolean =
        fragment.id >= 0 && fragment.sourceIndex >= 0 && fragment.partIndex >= 0 && fragment.partCount > 0 &&
            fragment.partIndex < fragment.partCount &&
            fragment.sourceText.isNotBlank() &&
            fragment.sourceText.length <= MAX_SOURCE_CHARS &&
            fragment.attempts >= 0 && fragment.nextAttemptAt >= 0 && (fragment.firstFailureAt ?: 0) >= 0

    private fun validCoverage(fragments: List<TranslationFragment>, sourceSegmentCount: Int): Boolean {
        if (sourceSegmentCount < 0 || fragments.any { !validFragment(it) }) return false
        if (fragments.map { it.id }.distinct().size != fragments.size) return false
        val groups = fragments.groupBy { it.sourceIndex }
        if (groups.size != sourceSegmentCount || groups.keys.any { it !in 0 until sourceSegmentCount }) return false
        return groups.values.all { parts ->
            val count = parts.first().partCount
            parts.size == count && parts.all { it.partCount == count } &&
                parts.map { it.partIndex }.sorted().withIndex().all { (index, value) -> index == value }
        }
    }

    private fun sameSource(first: TranslationFragment, second: TranslationFragment): Boolean =
        first.id == second.id && first.sourceIndex == second.sourceIndex && first.partIndex == second.partIndex &&
            first.partCount == second.partCount &&
            first.sourceText == second.sourceText &&
            first.separatorAfter == second.separatorAfter

    private fun <T : Any> T?.shouldExist(): T = this ?: error("Translation checkpoint state is invalid")

    private companion object {
        const val MAX_SOURCE_CHARS = 2_000
        const val MAX_PAYLOAD_CHARS = 65_536
        const val COMPLETE_OUTCOME = 1L
        const val FAILED_OUTCOME = 2L
        const val CANCELLED_OUTCOME = 3L
        val activeBatchStatuses = setOf(
            TranslationBatchStatus.QUEUED,
            TranslationBatchStatus.RUNNING,
            TranslationBatchStatus.PAUSED,
        )
        val releaseStatuses = setOf(
            TranslationRunStatus.PENDING,
            TranslationRunStatus.WAITING,
            TranslationRunStatus.PAUSED,
            TranslationRunStatus.FAILED,
        )
    }
}

private fun Translation_queue.toDomain() = TranslationRun(
    id = _id,
    chapterId = chapter_id,
    novelId = novel_id,
    batchToken = batch_token,
    batchOrder = batch_order.toInt(),
    profileSnapshotJson = profile_snapshot_json,
    status = TranslationRunStatus.entries.getOrNull(status.toInt()) ?: TranslationRunStatus.FAILED,
    progress = progress.toInt(),
    errorMessage = error_message,
    createdAt = created_at,
    updatedAt = updated_at,
    retryCount = retry_count.toInt(),
    generation = generation,
    nextAttemptAt = next_attempt_at,
    sourceFingerprint = source_fingerprint,
    profileFingerprint = profile_fingerprint,
    sourceSegmentCount = source_segment_count.toInt(),
    forceRetranslate = force_retranslate,
    priority = priority.toInt(),
    implicitOwner = implicit_owner,
    sessionOnly = session_only,
    implicitPaused = implicit_paused,
    actionPaused = action_paused,
    actionPauseReason = action_pause_reason?.let { value ->
        TranslationActionPauseReason.entries.singleOrNull { it.name == value }
    },
)

private fun Translation_batch_state.toDomain() = TranslationBatch(
    batchToken = batch_token,
    novelId = novel_id,
    status = TranslationBatchStatus.entries.getOrNull(status.toInt()) ?: TranslationBatchStatus.FAILED,
    total = total.toInt(),
    enqueued = enqueued.toInt(),
    skipped = skipped.toInt(),
    completed = completed.toInt(),
    failed = failed.toInt(),
    lastSuccessfulChapterId = last_successful_chapter_id,
    createdAt = created_at,
    updatedAt = updated_at,
    cancelled = cancelled_count.toInt(),
)

private val frozenSnapshotKeys = setOf(
    "snapshotVersion", "connectionProfile", "translationProfile",
    "systemPromptSnapshot", "geminiRequestShape",
    "geminiEnabled", "geminiModel", "geminiBatchSize", "geminiConcurrency", "geminiDisableCache",
    "geminiRelaxedMode", "geminiReasoningEffort", "geminiBudgetTokens", "geminiTemperature", "geminiTopP",
    "geminiTopK", "geminiSourceLang", "geminiTargetLang", "geminiPromptMode", "geminiEnabledPromptModifiers",
    "geminiCustomPromptModifier", "geminiStylePreset", "geminiPromptModifiers", "geminiAutoTranslateEnglishSource",
    "geminiPrefetchNextChapterTranslation", "geminiPrivateUnlocked", "geminiPrivatePythonLikeMode",
    "translationProvider", "openRouterBaseUrl", "openRouterModel", "deepSeekBaseUrl", "deepSeekModel",
    "mistralBaseUrl", "mistralModel", "nvidiaBaseUrl", "nvidiaModel", "ollamaCloudBaseUrl", "ollamaCloudModel",
    "replaceRulesFingerprint", "replaceRulesSnapshot", "geminiGenerationConfig", "geminiInputTokenLimit",
)

/** Null/v0/v1 have no C credential fields; unknown/corrupt C metadata fails closed. */
internal fun frozenRunCredentialIds(encoded: String?, json: Json): Set<String>? {
    if (encoded == null) return emptySet()
    return try {
        if (encoded.toByteArray(Charsets.UTF_8).size > 1_048_576) return null
        val parser = Json(json) { ignoreUnknownKeys = false }
        val root = parser.parseToJsonElement(encoded).jsonObject
        val prompt = root["systemPromptSnapshot"]
        if (prompt != null && prompt != JsonNull &&
            (prompt !is JsonPrimitive || !prompt.isString || prompt.content.length > 100_000)
        ) {
            return null
        }
        val shape = root["geminiRequestShape"]
        if (shape != null && (
                shape !is JsonPrimitive || !shape.isString ||
                    shape.content !in setOf("PUBLIC_LEGACY", "PRIVATE_TAGGED", "PRIVATE_PLAIN")
                )
        ) {
            return null
        }
        val versionNode = root["snapshotVersion"]?.jsonPrimitive
        if (versionNode?.isString == true) return null
        val version = versionNode?.intOrNull ?: if ("snapshotVersion" in root) -1 else 0
        when (version) {
            0, 1 -> if ("connectionProfile" in root || "translationProfile" in root ||
                root.keys.any { it !in frozenSnapshotKeys }
            ) {
                null
            } else {
                emptySet()
            }
            2 -> {
                if (root.keys.any { it !in frozenSnapshotKeys }) return null
                val connection = parser.decodeFromJsonElement<ConnectionProfile>(root.getValue("connectionProfile"))
                val profile = parser.decodeFromJsonElement<TranslationProfile>(root.getValue("translationProfile"))
                if (profile.connectionId != connection.id) {
                    null
                } else {
                    connection.credentialBindings.map { it.credentialId }.toSet()
                }
            }
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

private fun validRetrySnapshot(encoded: String, json: Json): Boolean = try {
    val root = json.parseToJsonElement(encoded).jsonObject
    val version = root["snapshotVersion"]?.jsonPrimitive?.intOrNull ?: 0
    if (version == 2) {
        frozenRunCredentialIds(encoded, json) != null
    } else {
        val provider = root["translationProvider"]?.jsonPrimitive?.contentOrNull ?: "GEMINI"
        val field = when (provider) {
            "GEMINI", "GEMINI_PRIVATE" -> "geminiModel"
            "OPENROUTER" -> "openRouterModel"
            "DEEPSEEK" -> "deepSeekModel"
            "MISTRAL" -> "mistralModel"
            "NVIDIA" -> "nvidiaModel"
            "OLLAMA_CLOUD" -> "ollamaCloudModel"
            else -> ""
        }
        val model = root[field]?.jsonPrimitive?.contentOrNull
        if (model.isNullOrBlank()) {
            false
        } else {
            val endpoint = when (provider) {
                "OPENROUTER" -> root["openRouterBaseUrl"]?.jsonPrimitive?.contentOrNull
                    ?: "https://openrouter.ai/api/v1"
                "DEEPSEEK" -> root["deepSeekBaseUrl"]?.jsonPrimitive?.contentOrNull ?: "https://api.deepseek.com"
                "MISTRAL" -> root["mistralBaseUrl"]?.jsonPrimitive?.contentOrNull ?: "https://api.mistral.ai/v1"
                "NVIDIA" -> root["nvidiaBaseUrl"]?.jsonPrimitive?.contentOrNull
                    ?: "https://integrate.api.nvidia.com/v1"
                "OLLAMA_CLOUD" -> root["ollamaCloudBaseUrl"]?.jsonPrimitive?.contentOrNull ?: "https://ollama.com/api"
                else -> "https://generativelanguage.googleapis.com/v1beta"
            }
            ProfileEndpointPolicy.origin(endpoint, ProfileEndpointPolicy.isLocalEndpoint(endpoint))
            true
        }
    }
} catch (_: Exception) {
    false
}
