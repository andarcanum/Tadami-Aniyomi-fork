package eu.kanade.tachiyomi.data.translation.profile

import android.app.Application
import eu.kanade.presentation.more.settings.translation.ProfileModelOperations
import eu.kanade.tachiyomi.data.translation.credentials.createAndroidCredentialStore
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import logcat.LogPriority
import logcat.logcat
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.data.translation.profile.TranslationProfileRepositoryImpl
import tachiyomi.domain.translation.credentials.CredentialStore
import tachiyomi.domain.translation.profile.ProfileCatalog
import tachiyomi.domain.translation.profile.TranslationProfileRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** One process authority shared by settings, migration, reader and worker profile dispatch. */
internal object TranslationProfileServices {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var catalog: ProfileCatalog? = null
    val cachedCatalog: ProfileCatalog? get() = catalog
    val legacyCredentials: LegacyTranslationCredentials by lazy {
        LegacyTranslationCredentials(Injekt.get(), repository, credentials)
    }
    val migration: LegacyTranslationMigration by lazy {
        LegacyTranslationMigration(
            LegacyTranslationPreferenceAdapter(Injekt.get(), Injekt.get()),
            credentials,
            repository,
        )
    }

    @Volatile var lastMigrationReport: LegacyMigrationReport? = null
        private set

    /**
     * Startup and manual entry point. Touching [migration] also starts the catalog mirror that the
     * reader gate reads synchronously, and scrubs plaintext restored from a private backup.
     */
    suspend fun runLegacyMigration(now: Long = System.currentTimeMillis()): LegacyMigrationReport =
        migration.migrate(now).also { lastMigrationReport = it }

    /** Called only after an explicit settings restore has completed, never by ordinary credential reads. */
    suspend fun runRestoredLegacyMigration(now: Long = System.currentTimeMillis()): LegacyMigrationReport =
        migration.migrateRestoredPreferences(now).also { lastMigrationReport = it }
    val runtime: ProfileTranslationRuntime by lazy {
        ProfileTranslationRuntime(
            repository,
            credentials,
            eu.kanade.tachiyomi.ui.reader.novel.translation.ProfileCompatibleApi(
                Injekt.get<NetworkHelper>().client,
                Injekt.get<Json>(),
            ),
        )
    }
    private val modelOperations by lazy {
        ProfileModelOperationsImpl(Injekt.get<NetworkHelper>().client, Injekt.get<Json>(), credentials, repository)
    }
    val models: ProfileModelOperations get() = modelOperations

    suspend fun probeTranslation(
        snapshot: eu.kanade.tachiyomi.data.translation.TranslationQueueProfileSnapshot,
        segments: List<String>,
    ): List<String?> = modelOperations.probeTranslation(snapshot, segments)
    suspend fun translateConfigured(
        snapshot: eu.kanade.tachiyomi.data.translation.TranslationQueueProfileSnapshot,
        segments: List<String>,
    ): List<String?> = modelOperations.translateConfigured(snapshot, segments)

    val credentials: CredentialStore by lazy { createAndroidCredentialStore(Injekt.get<Application>()) }
    val repository: TranslationProfileRepository by lazy {
        TranslationProfileRepositoryImpl(Injekt.get<NovelDatabaseHandler>(), Injekt.get<Json>()).also { repository ->
            scope.launch {
                runCatalogMirrorLoop(
                    repository = repository,
                    onCatalog = { value ->
                        catalog = value
                        LegacyCredentialReferences.replace(value?.legacyReferences.orEmpty())
                    },
                    onFailure = { error ->
                        logcat(LogPriority.WARN) {
                            "Translation catalog mirror failed; retrying (${error.javaClass.simpleName})"
                        }
                    },
                )
            }
        }
    }
}

internal suspend fun runCatalogMirrorLoop(
    repository: TranslationProfileRepository,
    onCatalog: (ProfileCatalog?) -> Unit,
    initialBackoffMillis: Long = 1_000L,
    maxBackoffMillis: Long = 30_000L,
    onFailure: (Throwable) -> Unit = {},
    delayFn: suspend (Long) -> Unit = { delay(it) },
) {
    var delayMillis = initialBackoffMillis
    while (currentCoroutineContext().isActive) {
        try {
            repository.observeCatalog().collect { value ->
                delayMillis = initialBackoffMillis
                onCatalog(value)
            }
            if (currentCoroutineContext().isActive) delayFn(delayMillis)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            onFailure(error)
            onCatalog(null)
            delayFn(delayMillis)
            delayMillis = (delayMillis * 2).coerceAtMost(maxBackoffMillis)
        }
    }
}
