package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupTranslationConfiguration
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import eu.kanade.tachiyomi.data.translation.profile.LegacyMigrationReport
import eu.kanade.tachiyomi.data.translation.profile.LegacyMigrationStatus
import eu.kanade.tachiyomi.data.translation.profile.TranslationConfigurationMutationGate
import eu.kanade.tachiyomi.data.translation.profile.TranslationProfileServices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import tachiyomi.domain.entries.novel.repository.NovelRepository
import tachiyomi.domain.translation.credentials.CredentialRead
import tachiyomi.domain.translation.profile.CredentialHealth
import tachiyomi.domain.translation.profile.CredentialHealthStatus
import tachiyomi.domain.translation.profile.ProfileAssignment
import tachiyomi.domain.translation.profile.ProfileAssignmentScope
import tachiyomi.domain.translation.profile.TranslationProfileRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Safe counts/statuses for the restore report; no vault value or platform error is retained. */
data class TranslationConfigurationRestoreResult(
    val configurationFailed: Boolean = false,
    val unresolvedNovelAssignments: Int = 0,
    val missingCredentials: Int = 0,
    val unavailableCredentials: Int = 0,
    val corruptCredentials: Int = 0,
    val legacyMigration: LegacyMigrationReport? = null,
)

class TranslationConfigurationRestorer(
    private val profiles: TranslationProfileRepository? = null,
    private val novels: NovelRepository? = null,
    private val readCredential: suspend (
        String,
    ) -> CredentialRead = { TranslationProfileServices.credentials.read(it) },
    private val migrateRestored: suspend () -> LegacyMigrationReport = {
        TranslationProfileServices.runRestoredLegacyMigration()
    },
) {
    /** Caller awaits all preference and library restore jobs before invoking this method. */
    suspend fun restore(
        backup: BackupTranslationConfiguration?,
        options: RestoreOptions,
    ): TranslationConfigurationRestoreResult? {
        if (!options.appSettings) return null
        val repository = profiles ?: TranslationProfileServices.repository
        var result = TranslationConfigurationRestoreResult()
        var importedIds = emptyList<String>()
        if (backup != null) {
            try {
                val data = backup.decode()
                val resolved = mutableListOf<ProfileAssignment>()
                var unresolved = 0
                for (reference in data.novelAssignments) {
                    val novel = (novels ?: Injekt.get<NovelRepository>())
                        .getNovelByUrlAndSourceId(reference.url, reference.source)
                    if (novel != null && novel.id > 0 && novel.source == reference.source &&
                        novel.url == reference.url
                    ) {
                        resolved += ProfileAssignment(ProfileAssignmentScope.NOVEL, novel.id, reference.profileId)
                    } else {
                        unresolved++
                    }
                }
                // The repository performs the additive merge and rejects retired credential IDs atomically.
                TranslationConfigurationMutationGate.mutex.withLock {
                    repository.importConfiguration(data.toCatalog(resolved))
                }
                importedIds = data.credentials.map { it.id }
                result = result.copy(unresolvedNovelAssignments = unresolved)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                result = result.copy(configurationFailed = true)
            }
        }

        // RELEASE the import gate first: the approved migration callback acquires that same gate.
        // Old private backups have no catalog envelope but still require checked plaintext migration.
        val migration = try {
            migrateRestored()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            LegacyMigrationReport(LegacyMigrationStatus.FAILED)
        }
        result = result.copy(legacyMigration = migration)

        // Read after migration so successfully transferred legacy keys are not falsely reported missing.
        try {
            result = TranslationConfigurationMutationGate.mutex.withLock {
                inspectCredentials(repository, importedIds, result)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            result = result.copy(configurationFailed = true)
        }
        return result
    }

    private suspend fun inspectCredentials(
        repository: TranslationProfileRepository,
        ids: List<String>,
        initial: TranslationConfigurationRestoreResult,
    ): TranslationConfigurationRestoreResult {
        var result = initial
        for (id in ids) {
            val stored = try {
                readCredential(id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                CredentialRead.Unavailable
            }
            if (stored is CredentialRead.Available) {
                stored.secret.close()
                // A local key and its existing health/cooldowns are not replaced or reset by a backup.
                continue
            }
            // Lost vault availability does not authorize clearing an existing credential due.
            val notBeforeAt = repository.readRoutingState().health[id]?.notBeforeAt ?: 0L
            repository.setCredentialHealth(id, CredentialHealth(CredentialHealthStatus.NEEDS_ACTION, notBeforeAt))
            result = when (stored) {
                CredentialRead.Missing -> result.copy(missingCredentials = result.missingCredentials + 1)
                CredentialRead.Unavailable -> result.copy(unavailableCredentials = result.unavailableCredentials + 1)
                CredentialRead.Corrupt -> result.copy(corruptCredentials = result.corruptCredentials + 1)
                is CredentialRead.Available -> result
            }
        }
        return result
    }
}
