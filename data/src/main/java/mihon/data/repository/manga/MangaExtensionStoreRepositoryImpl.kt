package mihon.data.repository.manga

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.supervisorScope
import logcat.LogPriority
import mihon.data.extension.repository.extensionStoreMapper
import mihon.data.extension.service.ExtensionStoreService
import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionstore.manga.repository.MangaExtensionStoreRepository
import mihon.domain.extensionstore.model.ExtensionStore
import mihon.domain.extensionstore.toLegacyExtensionStore
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.Database
import tachiyomi.data.handlers.manga.MangaDatabaseHandler

class MangaExtensionStoreRepositoryImpl(
    private val handler: MangaDatabaseHandler,
    private val service: ExtensionStoreService,
) : MangaExtensionStoreRepository {
    override suspend fun insert(indexUrl: String): Result<Unit> {
        return service.fetch(indexUrl).mapCatching { upsert(it) }
    }

    override suspend fun insertFromPreference(indexUrl: String, name: String) {
        handler.await { db ->
            db.extension_storeQueries.upsert(
                indexUrl = indexUrl,
                name = name,
                badgeLabel = name,
                signingKey = "NO_SIGNING_KEY",
                contactWebsite = indexUrl,
                contactDiscord = null,
                isLegacy = true,
                extensionListUrl = null,
            )
        }
    }

    override suspend fun refreshAll() {
        try {
            val stores = handler.awaitList { db -> db.extension_storeQueries.getAll(::extensionStoreMapper) }
            supervisorScope {
                stores.map { store ->
                    async {
                        service.fetch(store.indexUrl)
                            .mapCatching { fetched ->
                                handler.await(inTransaction = true) { db ->
                                    upsert(db, fetched)
                                    if (store.indexUrl != fetched.indexUrl) {
                                        db.extension_storeQueries.delete(store.indexUrl)
                                    }
                                }
                            }
                            .onFailure {
                                logcat(LogPriority.ERROR, it) {
                                    "Failed to refresh extension store '${store.name} (${store.indexUrl})'"
                                }
                            }
                    }
                }.awaitAll()
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    private suspend fun upsert(store: ExtensionStore) {
        handler.await { db -> upsert(db, store) }
    }

    private fun upsert(db: Database, store: ExtensionStore) {
        db.extension_storeQueries.upsert(
            indexUrl = store.indexUrl,
            name = store.name,
            badgeLabel = store.badgeLabel,
            signingKey = store.signingKey,
            contactWebsite = store.contact.website,
            contactDiscord = store.contact.discord,
            isLegacy = store.isLegacy,
            extensionListUrl = store.extensionListUrl,
        )
    }

    override suspend fun upsertStore(store: ExtensionStore) {
        upsert(store)
    }

    override suspend fun getAll(): List<ExtensionStore> {
        return handler.awaitList { db -> db.extension_storeQueries.getAll(::extensionStoreMapper) }
    }

    /**
     * One-time port from legacy extension_repos if this store table is empty.
     * Called from extension code paths (which are deferred after first frame).
     * Uses cheap count + volatile to avoid repeated work.
     */
    override suspend fun ensureLegacyMigrated() {
        migrateLegacyIfNeeded()
    }

    @Volatile
    private var legacyMigrationChecked = false

    private suspend fun migrateLegacyIfNeeded() {
        if (legacyMigrationChecked) return

        val count = handler.awaitOneOrNull { db -> db.extension_storeQueries.getCount() } ?: 0L
        if (count > 0L) {
            legacyMigrationChecked = true
            return
        }

        handler.awaitList { db ->
            db.extension_reposQueries.findAll { baseUrl, name, shortName, website, fingerprint ->
                ExtensionRepo(
                    baseUrl = baseUrl,
                    name = name,
                    shortName = shortName,
                    website = website,
                    signingKeyFingerprint = fingerprint,
                )
            }
        }.forEach { repo ->
            upsertStore(repo.toLegacyExtensionStore())
        }
        legacyMigrationChecked = true
    }

    override fun getAllAsFlow(): Flow<List<ExtensionStore>> {
        return handler.subscribeToList { db ->
            db.extension_storeQueries.getAll(::extensionStoreMapper)
        }
    }

    override fun getCountAsFlow(): Flow<Long> {
        return handler.subscribeToOne { db -> db.extension_storeQueries.getCount() }
    }

    override suspend fun remove(indexUrl: String) {
        handler.await { db -> db.extension_storeQueries.delete(indexUrl) }
    }
}
