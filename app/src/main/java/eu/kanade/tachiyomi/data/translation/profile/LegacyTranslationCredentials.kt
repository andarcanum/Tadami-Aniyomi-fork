package eu.kanade.tachiyomi.data.translation.profile

import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.translation.validateTranslationCredential
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tachiyomi.domain.translation.credentials.CredentialRead
import tachiyomi.domain.translation.credentials.CredentialSecret
import tachiyomi.domain.translation.credentials.CredentialStore
import tachiyomi.domain.translation.model.TranslationAttemptException
import tachiyomi.domain.translation.model.TranslationFailure
import tachiyomi.domain.translation.model.TranslationFailureKind
import tachiyomi.domain.translation.profile.CredentialBinding
import tachiyomi.domain.translation.profile.CredentialHealth
import tachiyomi.domain.translation.profile.CredentialMetadata
import tachiyomi.domain.translation.profile.LegacyCredentialReference
import tachiyomi.domain.translation.profile.LegacyCredentialScope
import tachiyomi.domain.translation.profile.ProfileAuth
import tachiyomi.domain.translation.profile.TranslationProfileRepository
import java.util.UUID

/** Saved references are authoritative. Missing/corrupt vault data never falls back to plaintext. */
internal class LegacyTranslationCredentials(
    private val preferences: NovelReaderPreferences,
    private val repository: TranslationProfileRepository,
    private val vault: CredentialStore,
) {
    private val edits = TranslationConfigurationMutationGate.mutex

    suspend fun resolveLegacy(provider: NovelTranslationProvider, sourceId: Long? = null): CredentialSecret =
        resolveLegacyOrNull(provider, sourceId) ?: missing()

    /** Null means nothing is stored for the scope; an acknowledged reference that cannot be read still fails. */
    suspend fun resolveLegacyOrNull(provider: NovelTranslationProvider, sourceId: Long? = null): CredentialSecret? =
        withContext(Dispatchers.IO) {
            val legacy = LegacyProvider.valueOf(provider.name)
            val raw = preferences.legacyTranslationPreferenceValues().values
            if (sourceId != null) {
                val reference = repository.getLegacyCredentialReference(
                    legacy.name,
                    LegacyCredentialScope.SOURCE,
                    sourceId,
                )
                if (reference != null) return@withContext readRequired(reference.credentialId)
                val owned = LegacyPreferenceAccess.sourceCredential(raw, legacy, sourceId)
                if (!owned.isNullOrBlank()) return@withContext legacyCarrier(owned)
            }
            val global = repository.getLegacyCredentialReference(legacy.name, LegacyCredentialScope.GLOBAL, 0)
            if (global != null) return@withContext readRequired(global.credentialId)
            // Only legacy fields without an acknowledged saved reference can be used before migration.
            LegacyPreferenceAccess.globalCredential(raw, legacy).takeIf { it.isNotBlank() }?.let { legacyCarrier(it) }
        }

    suspend fun saveLegacy(
        provider: NovelTranslationProvider,
        sourceId: Long?,
        value: String,
        now: Long = System.currentTimeMillis(),
    ): Unit = withContext(Dispatchers.IO) {
        validateTranslationCredential(value)
        if (value.isBlank()) missing()
        edits.withLock {
            withContext(NonCancellable) {
                val scope = if (sourceId == null) LegacyCredentialScope.GLOBAL else LegacyCredentialScope.SOURCE
                val scopeId = sourceId ?: 0L
                val legacy = LegacyProvider.valueOf(provider.name)
                val providers = if (legacy == LegacyProvider.GEMINI || legacy == LegacyProvider.GEMINI_PRIVATE) {
                    listOf(LegacyProvider.GEMINI, LegacyProvider.GEMINI_PRIVATE)
                } else {
                    listOf(legacy)
                }
                val refs = providers.mapNotNull {
                    repository.getLegacyCredentialReference(it.name, scope, scopeId)
                }
                val ids = refs.map { it.credentialId }.distinct().ifEmpty { listOf(UUID.randomUUID().toString()) }
                ids.forEach { id ->
                    vault.put(id, value)
                    readRequired(id).use { if (it.reveal() != value) missing() }
                    val metadata = repository.getCredentialMetadata(id)
                    repository.saveCredentialMetadata(
                        CredentialMetadata(
                            id,
                            metadata?.alias ?: "Legacy $provider $scope $scopeId",
                            maskLegacyCredential(value),
                            now,
                        ),
                    )
                    val previous = repository.readRoutingState().health[id]
                    repository.setCredentialHealth(id, CredentialHealth(notBeforeAt = previous?.notBeforeAt ?: 0))
                }
                val proofs = mutableListOf<VerifiedLegacyCredential>()
                try {
                    providers.forEach { current ->
                        val id = refs.firstOrNull { it.provider == current.name }?.credentialId ?: ids.first()
                        repository.saveLegacyCredentialReference(
                            LegacyCredentialReference(current.name, scope, scopeId, id),
                        )
                        val acknowledged = repository.getLegacyCredentialReference(current.name, scope, scopeId)
                        if (acknowledged?.credentialId != id) missing()
                        LegacyCredentialReferences.remember(acknowledged)
                        readRequired(id).use { if (it.reveal() != value) missing() }
                        val connectionId = UUID.nameUUIDFromBytes(
                            "tadami.translation.legacy.v1|connection|$current|$scope|$scopeId".toByteArray(),
                        ).toString()
                        val connection = repository.getConnection(connectionId)
                        if (connection != null &&
                            connection.auth != ProfileAuth.NONE &&
                            connection.credentialBindings.none { it.credentialId == id } &&
                            connection.credentialBindings.size < 100
                        ) {
                            repository.saveConnection(
                                connection.copy(
                                    credentialBindings = connection.credentialBindings + CredentialBinding(id),
                                ),
                            )
                        }
                        proofs += VerifiedLegacyCredential(current, scope, scopeId, id, CredentialSecret(value))
                    }
                    preferences.compareAndRemoveLegacyCredentials(proofs)
                } finally {
                    proofs.forEach { it.close() }
                }
            }
        }
    }

    suspend fun readRequired(id: String): CredentialSecret {
        val stored = try {
            vault.read(id)
        } catch (
            error: CancellationException,
        ) {
            throw error
        } catch (_: Exception) {
            missing()
        }
        if (stored !is CredentialRead.Available) missing()
        try {
            validateTranslationCredential(stored.secret.reveal())
        } catch (
            error: Exception,
        ) {
            stored.secret.close()
            throw error
        }
        if (stored.secret.reveal().isBlank()) {
            stored.secret.close()
            missing()
        }
        return stored.secret
    }

    private fun legacyCarrier(value: String): CredentialSecret {
        validateTranslationCredential(value)
        if (value.isBlank()) missing()
        return CredentialSecret(value)
    }
    private fun missing(): Nothing = throw TranslationAttemptException(
        TranslationFailure(
            TranslationFailureKind.AUTHORIZATION,
            "Translation credential is missing or unavailable; enter it again",
            attempted = false,
        ),
    )
}

internal fun maskLegacyCredential(value: String): String = if (value.length <= 4) {
    "••••"
} else {
    "…" + value.takeLast(4).map {
        if (it.isISOControl() || it.isSurrogate()) '•' else it
    }.joinToString("")
}
