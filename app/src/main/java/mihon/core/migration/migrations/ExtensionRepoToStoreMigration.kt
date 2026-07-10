package mihon.core.migration.migrations

import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionstore.anime.repository.AnimeExtensionStoreRepository
import mihon.domain.extensionstore.manga.repository.MangaExtensionStoreRepository
import mihon.domain.extensionstore.model.ExtensionStore
import mihon.domain.extensionstore.novel.repository.NovelExtensionStoreRepository
import mihon.domain.extensionstore.toLegacyExtensionStore
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.data.handlers.novel.NovelDatabaseHandler

class ExtensionRepoToStoreMigration : Migration {
    override val version = Migration.ALWAYS

    /**
     * Ports user-added extension repos (from pre-refactor ExtensionRepo tables)
     * into the new unified ExtensionStore tables (one per media DB).
     * Uses ALWAYS so that users who updated across the refactor (versionCode
     * ranges that skipped the old 139f gate) still get their repos migrated
     * on next launch if their store tables are empty.
     */
    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val mangaHandler = migrationContext.get<MangaDatabaseHandler>() ?: return@withIOContext false
        val animeHandler = migrationContext.get<AnimeDatabaseHandler>() ?: return@withIOContext false
        val novelHandler = migrationContext.get<NovelDatabaseHandler>() ?: return@withIOContext false
        val mangaStoreRepo = migrationContext.get<MangaExtensionStoreRepository>() ?: return@withIOContext false
        val animeStoreRepo = migrationContext.get<AnimeExtensionStoreRepository>() ?: return@withIOContext false
        val novelStoreRepo = migrationContext.get<NovelExtensionStoreRepository>() ?: return@withIOContext false

        if (mangaStoreRepo.getAll().isEmpty()) {
            mangaHandler.awaitList { db ->
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
                mangaStoreRepo.upsertStore(repo.toLegacyExtensionStore())
            }
        }

        if (animeStoreRepo.getAll().isEmpty()) {
            animeHandler.awaitList { db ->
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
                animeStoreRepo.upsertStore(repo.toLegacyExtensionStore())
            }
        }

        if (novelStoreRepo.getAll().isEmpty()) {
            novelHandler.awaitList { db ->
                db.novel_extension_reposQueries.findAll { baseUrl, name, shortName, website, fingerprint ->
                    ExtensionStore(
                        indexUrl = baseUrl,
                        name = name,
                        badgeLabel = shortName ?: name,
                        signingKey = fingerprint,
                        contact = ExtensionStore.Contact(
                            website = website,
                            discord = null,
                        ),
                        isLegacy = true,
                        extensionListUrl = null,
                    )
                }
            }.forEach { store ->
                novelStoreRepo.upsertStore(store)
            }
        }

        true
    }
}
