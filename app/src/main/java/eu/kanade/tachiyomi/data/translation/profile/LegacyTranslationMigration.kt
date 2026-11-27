package eu.kanade.tachiyomi.data.translation.profile

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tachiyomi.domain.translation.credentials.CredentialRead
import tachiyomi.domain.translation.credentials.CredentialSecret
import tachiyomi.domain.translation.credentials.CredentialStore
import tachiyomi.domain.translation.profile.ConnectionProfile
import tachiyomi.domain.translation.profile.CredentialBinding
import tachiyomi.domain.translation.profile.CredentialHealth
import tachiyomi.domain.translation.profile.CredentialHealthStatus
import tachiyomi.domain.translation.profile.CredentialMetadata
import tachiyomi.domain.translation.profile.ExecutionPolicy
import tachiyomi.domain.translation.profile.LegacyCredentialReference
import tachiyomi.domain.translation.profile.LegacyCredentialScope
import tachiyomi.domain.translation.profile.ModelSelection
import tachiyomi.domain.translation.profile.PresetFlavor
import tachiyomi.domain.translation.profile.ProfileAssignment
import tachiyomi.domain.translation.profile.ProfileAssignmentScope
import tachiyomi.domain.translation.profile.ProfileAuth
import tachiyomi.domain.translation.profile.ProfileCatalog
import tachiyomi.domain.translation.profile.ProfileEndpointPolicy
import tachiyomi.domain.translation.profile.PromptSettings
import tachiyomi.domain.translation.profile.TranslationLanguages
import tachiyomi.domain.translation.profile.TranslationProfile
import tachiyomi.domain.translation.profile.TranslationProfileRepository
import tachiyomi.domain.translation.profile.TranslationProtocol
import java.io.Closeable
import java.security.MessageDigest
import java.util.UUID

/** The real preference adapter owns atomic comparison/removal; no live preferences are read here. */
interface LegacyTranslationSettings {
    suspend fun readSnapshot(): LegacyTranslationSnapshot
    suspend fun compareAndRemoveVerified(values: List<VerifiedLegacyCredential>): LegacyRemovalResult
    suspend fun hasCompletedKeylessMigration(): Boolean = false
    suspend fun markKeylessMigrationComplete() {}
}

data class LegacyRemovalResult(val removed: Int, val changed: Int)

enum class LegacyProvider { GEMINI, GEMINI_PRIVATE, OPENROUTER, DEEPSEEK, MISTRAL, NVIDIA, OLLAMA_CLOUD }
enum class LegacyConfigurationIssue { INVALID_SETTING, UNSUPPORTED_SETTING }

data class LegacyProviderConfiguration(
    val provider: LegacyProvider,
    val baseUrl: String,
    val protocol: TranslationProtocol,
    val auth: ProfileAuth,
    val presetFlavor: PresetFlavor,
    val model: ModelSelection?,
    val languages: TranslationLanguages,
    val prompt: PromptSettings = PromptSettings(),
    val execution: ExecutionPolicy = ExecutionPolicy(),
    val allowInsecureLocalHTTP: Boolean = false,
    val issue: LegacyConfigurationIssue? = null,
    val maxConcurrency: Int = 2,
) {
    override fun toString(): String = "LegacyProviderConfiguration(provider=$provider)"
}

data class LegacyScopeSettings(
    val scope: LegacyCredentialScope,
    val scopeId: Long,
    val selectedProvider: LegacyProvider,
    val providers: List<LegacyProviderConfiguration>,
    /** The live adapter/caller retains the legacy enable flag; migration never starts HTTP work. */
    val enabled: Boolean = false,
)

class LegacyCredentialValue(
    val provider: LegacyProvider,
    val scope: LegacyCredentialScope,
    val scopeId: Long,
    val secret: CredentialSecret,
) {
    override fun toString(): String = "LegacyCredentialValue([REDACTED])"
}

/** Fresh carriers on each read; caller strings and JVM/runtime copies cannot be reliably erased. */
class LegacyTranslationSnapshot(
    val scopeSettings: List<LegacyScopeSettings>,
    val credentials: List<LegacyCredentialValue>,
    val hasConfiguredKeylessSettings: Boolean = false,
) : Closeable {
    override fun close() {
        credentials.forEach { it.secret.close() }
    }
    override fun toString(): String =
        "LegacyTranslationSnapshot(scopes=${scopeSettings.size}, keys=${credentials.size})"
}

/** Non-serializable proof carrier, usable only during the adapter's atomic compare/remove operation. */
class VerifiedLegacyCredential internal constructor(
    val provider: LegacyProvider,
    val scope: LegacyCredentialScope,
    val scopeId: Long,
    val credentialId: String,
    private val expected: CredentialSecret,
) : Closeable {
    fun matches(current: String): Boolean {
        val original = expected.reveal()
        if (current != original) return false
        val wanted = original.toByteArray(Charsets.UTF_8)
        val actual = current.toByteArray(Charsets.UTF_8)
        return try {
            MessageDigest.isEqual(wanted, actual)
        } finally {
            wanted.fill(0)
            actual.fill(0)
        }
    }
    override fun close() = expected.close()
    override fun toString(): String = "VerifiedLegacyCredential([REDACTED])"
}

enum class LegacyMigrationStatus { COMPLETE, NEEDS_ACTION, NEEDS_REVIEW, FAILED }
enum class LegacyMigrationIssue {
    INVALID_SNAPSHOT,
    INVALID_CONFIGURATION,
    ACTIVE_MODEL_MISSING,
    UNSAFE_ENDPOINT,
    VAULT_UNAVAILABLE,
    READBACK_FAILED,
    CREDENTIAL_CONFLICT,
    IMPORT_FAILED,
    LINK_FAILED,
    LEGACY_REMOVE_FAILED,
    LEGACY_CHANGED,
}

data class LegacyMigrationReport(
    val status: LegacyMigrationStatus,
    val migratedCredentials: Int = 0,
    val removed: Int = 0,
    val changed: Int = 0,
    val connectionCount: Int = 0,
    val profileCount: Int = 0,
    val issues: Set<LegacyMigrationIssue> = emptySet(),
)

/** Service only: strict copy/readback/link precede atomic compare-current removal by the adapter. */
class LegacyTranslationMigration(
    private val settings: LegacyTranslationSettings,
    private val vault: CredentialStore,
    private val profiles: TranslationProfileRepository,
) {
    suspend fun migrate(now: Long): LegacyMigrationReport = migrate(now, restoreMissingReferences = false)

    /** Explicit backup restore may refill missing records; unavailable/corrupt or conflicting records never change. */
    suspend fun migrateRestoredPreferences(now: Long): LegacyMigrationReport =
        migrate(now, restoreMissingReferences = true)

    private suspend fun migrate(
        now: Long,
        restoreMissingReferences: Boolean,
    ): LegacyMigrationReport = migrationMutex.withLock {
        if (now < 0) return@withLock failure(LegacyMigrationStatus.NEEDS_ACTION, LegacyMigrationIssue.INVALID_SNAPSHOT)
        val snapshot = try {
            settings.readSnapshot()
        } catch (
            error: CancellationException,
        ) {
            throw error
        } catch (_: Exception) {
            return@withLock failure(LegacyMigrationStatus.FAILED, LegacyMigrationIssue.INVALID_SNAPSHOT)
        }
        val proofs = mutableListOf<VerifiedLegacyCredential>()
        try {
            migrateSnapshot(snapshot, now, proofs, restoreMissingReferences)
        } catch (
            abort: AbortMigration,
        ) {
            failure(abort.status, abort.issue)
        } catch (
            error: CancellationException,
        ) {
            throw error
        } catch (
            _: Exception,
        ) {
            failure(LegacyMigrationStatus.FAILED, LegacyMigrationIssue.INVALID_SNAPSHOT)
        } finally {
            proofs.forEach { it.close() }
            snapshot.close()
        }
    }

    private suspend fun migrateSnapshot(
        snapshot: LegacyTranslationSnapshot,
        now: Long,
        proofs: MutableList<VerifiedLegacyCredential>,
        restoreMissingReferences: Boolean,
    ): LegacyMigrationReport {
        val validConfigs = validateSnapshot(snapshot)
        val catalog = try {
            profiles.getCatalog()
        } catch (
            error: CancellationException,
        ) {
            throw error
        } catch (
            _: Exception,
        ) {
            abort(LegacyMigrationStatus.FAILED, LegacyMigrationIssue.IMPORT_FAILED)
        }
        val plans = snapshot.credentials.filter { it.secret.reveal().isNotBlank() }.map { legacy ->
            val existing = catalog.legacyReferences.singleOrNull {
                it.provider == legacy.provider.name && it.scope == legacy.scope && it.scopeId == legacy.scopeId
            }
            val id = existing?.credentialId ?: stableId("credential", legacy.provider, legacy.scope, legacy.scopeId)
            val write = when (val stored = readVault(id)) {
                is CredentialRead.Available -> {
                    val same = stored.secret.use { it.reveal() == legacy.secret.reveal() }
                    if (!same) abort(LegacyMigrationStatus.NEEDS_REVIEW, LegacyMigrationIssue.CREDENTIAL_CONFLICT)
                    false
                }
                CredentialRead.Missing -> {
                    if (existing != null && !restoreMissingReferences) {
                        abort(LegacyMigrationStatus.NEEDS_ACTION, LegacyMigrationIssue.VAULT_UNAVAILABLE)
                    }
                    true
                }
                CredentialRead.Unavailable, CredentialRead.Corrupt ->
                    abort(LegacyMigrationStatus.NEEDS_ACTION, LegacyMigrationIssue.VAULT_UNAVAILABLE)
            }
            CopyPlan(legacy, id, write)
        }
        val catalogEmpty = catalog.connections.isEmpty() &&
            catalog.profiles.isEmpty() &&
            catalog.credentials.isEmpty() &&
            catalog.assignments.isEmpty() &&
            catalog.legacyReferences.isEmpty()
        val migrateKeyless = plans.isEmpty() &&
            catalogEmpty &&
            hasKeylessConfiguration(snapshot) &&
            (restoreMissingReferences || !settings.hasCompletedKeylessMigration())
        // Nothing left to copy: a repeat run must not resurrect configuration the user edited or deleted.
        if (plans.isEmpty() && !migrateKeyless) return LegacyMigrationReport(LegacyMigrationStatus.COMPLETE)
        val bundle = buildBundle(snapshot, plans, catalog, validConfigs, now)
        if (plans.isEmpty() && bundle.connections.isEmpty() && bundle.profiles.isEmpty()) {
            return LegacyMigrationReport(LegacyMigrationStatus.COMPLETE)
        }
        plans.forEach { plan ->
            if (plan.write) {
                try {
                    vault.put(plan.id, plan.legacy.secret.reveal())
                } catch (
                    error: CancellationException,
                ) {
                    throw error
                } catch (_: Exception) {
                    abort(LegacyMigrationStatus.NEEDS_ACTION, LegacyMigrationIssue.VAULT_UNAVAILABLE)
                }
            }
            verifyVault(plan)
            proofs += VerifiedLegacyCredential(
                plan.legacy.provider,
                plan.legacy.scope,
                plan.legacy.scopeId,
                plan.id,
                CredentialSecret(plan.legacy.secret.reveal()),
            )
        }
        try {
            profiles.importConfiguration(bundle)
        } catch (
            error: CancellationException,
        ) {
            throw error
        } catch (
            _: Exception,
        ) {
            abort(LegacyMigrationStatus.FAILED, LegacyMigrationIssue.IMPORT_FAILED)
        }
        plans.forEach { plan ->
            val ref = try {
                profiles.getLegacyCredentialReference(
                    plan.legacy.provider.name,
                    plan.legacy.scope,
                    plan.legacy.scopeId,
                )
            } catch (
                error: CancellationException,
            ) {
                throw error
            } catch (
                _: Exception,
            ) {
                abort(LegacyMigrationStatus.FAILED, LegacyMigrationIssue.LINK_FAILED)
            }
            if (ref?.credentialId != plan.id) abort(LegacyMigrationStatus.FAILED, LegacyMigrationIssue.LINK_FAILED)
            verifyVault(plan)
            try {
                profiles.setCredentialHealth(plan.id, CredentialHealth(CredentialHealthStatus.READY))
            } catch (
                error: CancellationException,
            ) {
                throw error
            } catch (
                _: Exception,
            ) {
                abort(LegacyMigrationStatus.FAILED, LegacyMigrationIssue.LINK_FAILED)
            }
        }
        val removed = if (proofs.isEmpty()) {
            LegacyRemovalResult(0, 0)
        } else {
            try {
                settings.compareAndRemoveVerified(proofs)
            } catch (
                error: CancellationException,
            ) {
                throw error
            } catch (
                _: Exception,
            ) {
                abort(LegacyMigrationStatus.FAILED, LegacyMigrationIssue.LEGACY_REMOVE_FAILED)
            }
        }
        if (removed.removed < 0 || removed.changed < 0 || removed.removed + removed.changed != proofs.size) {
            abort(LegacyMigrationStatus.FAILED, LegacyMigrationIssue.LEGACY_REMOVE_FAILED)
        }
        try {
            settings.markKeylessMigrationComplete()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Non-fatal: catalog already imported and will remain non-empty unless user clears it.
        }
        return LegacyMigrationReport(
            if (removed.changed == 0) LegacyMigrationStatus.COMPLETE else LegacyMigrationStatus.NEEDS_REVIEW,
            plans.size,
            removed.removed,
            removed.changed,
            bundle.connections.size,
            bundle.profiles.size,
            if (removed.changed == 0) emptySet() else setOf(LegacyMigrationIssue.LEGACY_CHANGED),
        )
    }

    private fun hasKeylessConfiguration(snapshot: LegacyTranslationSnapshot): Boolean =
        snapshot.hasConfiguredKeylessSettings ||
            snapshot.scopeSettings.size > 1 ||
            snapshot.scopeSettings.any { scope ->
                scope.enabled ||
                    scope.selectedProvider != LegacyProvider.GEMINI ||
                    scope.providers.any { config ->
                        config.allowInsecureLocalHTTP ||
                            config.prompt.customText.isNotBlank() ||
                            config.prompt.modifierIds.isNotEmpty()
                    }
            }

    private fun validateSnapshot(snapshot: LegacyTranslationSnapshot): Set<ConfigKey> {
        val scopes = snapshot.scopeSettings
        if (scopes.isEmpty() || scopes.size > 1_001 || snapshot.credentials.size > 7_007 ||
            scopes.count { it.scope == LegacyCredentialScope.GLOBAL && it.scopeId == 0L } != 1 ||
            scopes.map { it.scope to it.scopeId }.distinct().size != scopes.size ||
            snapshot.credentials.map { Triple(it.provider, it.scope, it.scopeId) }.distinct().size !=
            snapshot.credentials.size
        ) {
            abort(LegacyMigrationStatus.NEEDS_ACTION, LegacyMigrationIssue.INVALID_SNAPSHOT)
        }
        snapshot.credentials.forEach { key ->
            if (scopes.none { scope ->
                    scope.scope == key.scope && scope.scopeId == key.scopeId &&
                        scope.providers.any { it.provider == key.provider }
                }
            ) {
                abort(LegacyMigrationStatus.NEEDS_ACTION, LegacyMigrationIssue.INVALID_SNAPSHOT)
            }
        }
        val keyedLocations = snapshot.credentials
            .filter { it.secret.reveal().isNotBlank() }
            .mapTo(mutableSetOf()) { ConfigKey(it.scope, it.scopeId, it.provider) }
        val keylessRun = keyedLocations.isEmpty() && hasKeylessConfiguration(snapshot)
        val validConfigs = mutableSetOf<ConfigKey>()
        scopes.forEach { scope ->
            if (scope.providers.isEmpty() || scope.providers.size > 7 ||
                scope.providers.map { it.provider }.distinct().size != scope.providers.size ||
                (scope.scope == LegacyCredentialScope.GLOBAL && scope.scopeId != 0L)
            ) {
                abort(LegacyMigrationStatus.NEEDS_ACTION, LegacyMigrationIssue.INVALID_SNAPSHOT)
            }
            val active = scope.providers.singleOrNull { it.provider == scope.selectedProvider }
                ?: abort(LegacyMigrationStatus.NEEDS_ACTION, LegacyMigrationIssue.INVALID_CONFIGURATION)
            val activeKey = ConfigKey(scope.scope, scope.scopeId, active.provider)
            val activeCritical = activeKey in keyedLocations ||
                (keylessRun && scope.scope == LegacyCredentialScope.GLOBAL && scope.enabled)
            if (active.model == null && activeCritical) {
                abort(LegacyMigrationStatus.NEEDS_ACTION, LegacyMigrationIssue.ACTIVE_MODEL_MISSING)
            }
            scope.providers.forEach { config ->
                val key = ConfigKey(scope.scope, scope.scopeId, config.provider)
                val issue = providerIssue(scope, config)
                if (issue == null) {
                    validConfigs += key
                } else {
                    val critical = key in keyedLocations ||
                        (config.provider == scope.selectedProvider && activeCritical)
                    if (critical) abort(LegacyMigrationStatus.NEEDS_ACTION, issue)
                }
            }
        }
        return validConfigs
    }

    private fun providerIssue(scope: LegacyScopeSettings, config: LegacyProviderConfiguration): LegacyMigrationIssue? {
        if (config.issue != null) return LegacyMigrationIssue.INVALID_CONFIGURATION
        try {
            ProfileEndpointPolicy.origin(config.baseUrl, config.allowInsecureLocalHTTP)
        } catch (_: Exception) {
            return LegacyMigrationIssue.UNSAFE_ENDPOINT
        }
        return try {
            connection(scope, config, emptyList())
            if (config.model != null) profile(scope, config)
            null
        } catch (_: Exception) {
            LegacyMigrationIssue.INVALID_CONFIGURATION
        }
    }

    private fun buildBundle(
        snapshot: LegacyTranslationSnapshot,
        plans: List<CopyPlan>,
        existing: ProfileCatalog,
        validConfigs: Set<ConfigKey>,
        now: Long,
    ): ProfileCatalog {
        val refs = existing.legacyReferences.associateBy { RefKey(it.provider, it.scope, it.scopeId) }.toMutableMap()
        val addedRefs = plans.map {
            LegacyCredentialReference(it.legacy.provider.name, it.legacy.scope, it.legacy.scopeId, it.id)
        }
        addedRefs.forEach { refs[RefKey(it.provider, it.scope, it.scopeId)] = it }
        val metadata = plans.distinctBy { it.id }.filter { plan -> existing.credentials.none { it.id == plan.id } }
            .map { CredentialMetadata(it.id, alias(it.legacy), maskLegacyCredential(it.legacy.secret.reveal()), now) }
        val connections = mutableListOf<ConnectionProfile>()
        val addedProfiles = mutableListOf<TranslationProfile>()
        val validProfileIds = existing.profiles.mapTo(mutableSetOf()) { it.id }
        val assignments = mutableListOf<ProfileAssignment>()
        snapshot.scopeSettings.forEach { scope ->
            scope.providers
                .filter { ConfigKey(scope.scope, scope.scopeId, it.provider) in validConfigs }
                .forEach { config ->
                    val own = refs[RefKey(config.provider.name, scope.scope, scope.scopeId)]
                    val global = refs[RefKey(config.provider.name, LegacyCredentialScope.GLOBAL, 0)]
                    val id = own?.credentialId ?: global?.credentialId
                    val bindings = if (config.auth == ProfileAuth.NONE || id == null) {
                        emptyList()
                    } else {
                        listOf(CredentialBinding(id))
                    }
                    val connection = connection(scope, config, bindings)
                    val existingConnection = existing.connections.singleOrNull { it.id == connection.id }
                    if (existingConnection == null) {
                        connections += connection
                    } else if (id != null && config.auth != ProfileAuth.NONE &&
                        existingConnection.credentialBindings.none { it.credentialId == id }
                    ) {
                        connections += existingConnection.copy(
                            credentialBindings = (existingConnection.credentialBindings + CredentialBinding(id))
                                .take(100),
                        )
                    }
                    if (config.model != null) {
                        val profile = profile(scope, config)
                        validProfileIds += profile.id
                        if (existing.profiles.none { it.id == profile.id }) addedProfiles += profile
                    }
                }
            val target = if (scope.scope == LegacyCredentialScope.GLOBAL) {
                ProfileAssignmentScope.GLOBAL
            } else {
                ProfileAssignmentScope.SOURCE
            }
            val activeProfileId = stableId("profile", scope.selectedProvider, scope.scope, scope.scopeId)
            if (activeProfileId in validProfileIds &&
                existing.assignments.none { it.scope == target && it.scopeId == scope.scopeId }
            ) {
                assignments += ProfileAssignment(target, scope.scopeId, activeProfileId)
            }
        }
        return ProfileCatalog(connections, addedProfiles, metadata, assignments, addedRefs)
    }

    private fun connection(
        scope: LegacyScopeSettings,
        config: LegacyProviderConfiguration,
        bindings: List<CredentialBinding>,
    ) = ConnectionProfile(
        stableId("connection", config.provider, scope.scope, scope.scopeId),
        "Legacy ${config.provider} ${scope.scope} ${scope.scopeId}", config.protocol, config.baseUrl, config.auth,
        presetFlavor = config.presetFlavor, credentialBindings = bindings,
        allowInsecureLocalHTTP = config.allowInsecureLocalHTTP, maxConcurrency = config.maxConcurrency,
    )

    private fun profile(scope: LegacyScopeSettings, config: LegacyProviderConfiguration) = TranslationProfile(
        stableId("profile", config.provider, scope.scope, scope.scopeId),
        "Legacy ${config.provider} ${scope.scope} ${scope.scopeId}",
        stableId("connection", config.provider, scope.scope, scope.scopeId),
        requireNotNull(config.model),
        config.languages,
        config.prompt,
        config.execution,
    )

    private suspend fun readVault(id: String): CredentialRead = try {
        vault.read(id)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        abort(LegacyMigrationStatus.NEEDS_ACTION, LegacyMigrationIssue.VAULT_UNAVAILABLE)
    }

    private suspend fun verifyVault(plan: CopyPlan) {
        val available = readVault(plan.id)
        if (available !is CredentialRead.Available) {
            abort(LegacyMigrationStatus.NEEDS_ACTION, LegacyMigrationIssue.READBACK_FAILED)
        }
        val same = available.secret.use { it.reveal() == plan.legacy.secret.reveal() }
        if (!same) abort(LegacyMigrationStatus.NEEDS_ACTION, LegacyMigrationIssue.READBACK_FAILED)
    }

    private fun stableId(kind: String, provider: LegacyProvider, scope: LegacyCredentialScope, id: Long): String =
        UUID.nameUUIDFromBytes(
            "tadami.translation.legacy.v1|$kind|$provider|$scope|$id".toByteArray(Charsets.UTF_8),
        ).toString()
    private fun alias(key: LegacyCredentialValue): String = "Legacy ${key.provider} ${key.scope} ${key.scopeId}"
    private fun failure(status: LegacyMigrationStatus, issue: LegacyMigrationIssue) =
        LegacyMigrationReport(status, issues = setOf(issue))
    private fun abort(status: LegacyMigrationStatus, issue: LegacyMigrationIssue): Nothing =
        throw AbortMigration(status, issue)
    private class AbortMigration(
        val status: LegacyMigrationStatus,
        val issue: LegacyMigrationIssue,
    ) : RuntimeException()
    private class CopyPlan(val legacy: LegacyCredentialValue, val id: String, val write: Boolean)
    private data class RefKey(val provider: String, val scope: LegacyCredentialScope, val id: Long)
    private data class ConfigKey(val scope: LegacyCredentialScope, val scopeId: Long, val provider: LegacyProvider)
    private companion object {
        val migrationMutex = TranslationConfigurationMutationGate.mutex
    }
}
