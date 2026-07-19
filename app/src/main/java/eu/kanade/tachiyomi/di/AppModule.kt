package eu.kanade.tachiyomi.di

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import aniyomi.core.common.torrent.TorrentServerApi
import aniyomi.core.common.torrent.TorrentServerUtils
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.tadami.aurora.BuildConfig
import data.Chapters
import data.History
import data.Mangas
import dataanime.Animehistory
import dataanime.Animes
import dataanime.Episodes
import datanovel.Novel_history
import datanovel.Novel_chapters
import datanovel.Novels
import eu.kanade.domain.extension.novel.interactor.TrustNovelExtension
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.domain.track.anime.store.DelayedAnimeTrackingStore
import eu.kanade.domain.track.manga.store.DelayedMangaTrackingStore
import eu.kanade.domain.track.novel.store.DelayedNovelTrackingStore
import eu.kanade.tachiyomi.data.cache.AnimeBackgroundCache
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.MangaCoverCache
import eu.kanade.tachiyomi.data.cache.NovelCoverCache
import eu.kanade.tachiyomi.data.cache.SeriesCoverCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadProvider
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadProvider
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadCache
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.sync.service.GoogleDriveService
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.translation.TranslationNotificationManager
import eu.kanade.tachiyomi.data.translation.TranslationQueueManager
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.installer.ApkInstallBackend
import eu.kanade.tachiyomi.extension.installer.ApkInstallStateStore
import eu.kanade.tachiyomi.extension.installer.DhizukuApkInstallBackendAdapter
import eu.kanade.tachiyomi.extension.installer.PendingApkInstallStore
import eu.kanade.tachiyomi.extension.installer.PrivateApkInstallBackendAdapter
import eu.kanade.tachiyomi.extension.installer.ShizukuApkInstallBackendAdapter
import eu.kanade.tachiyomi.extension.installer.SystemIntentApkInstallBackendAdapter
import eu.kanade.tachiyomi.extension.installer.UnifiedApkExtensionInstallCoordinator
import eu.kanade.tachiyomi.extension.installer.UnifiedApkExtensionInstaller
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.novel.DefaultNovelExtensionManager
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import eu.kanade.tachiyomi.extension.novel.NovelExtensionUpdateChecker
import eu.kanade.tachiyomi.extension.novel.NovelPluginSourceFactory
import eu.kanade.tachiyomi.extension.novel.api.NetworkNovelPluginIndexFetcher
import eu.kanade.tachiyomi.extension.novel.api.NovelPluginApi
import eu.kanade.tachiyomi.extension.novel.api.NovelPluginApiFacade
import eu.kanade.tachiyomi.extension.novel.api.NovelPluginIndexFetcher
import eu.kanade.tachiyomi.extension.novel.api.NovelPluginIndexParser
import eu.kanade.tachiyomi.extension.novel.api.NovelPluginRepoProvider
import eu.kanade.tachiyomi.extension.novel.kotlin.KotlinNovelExtensionInstaller
import eu.kanade.tachiyomi.extension.novel.repo.InMemoryNovelPluginStorage
import eu.kanade.tachiyomi.extension.novel.repo.NovelPluginRepoParser
import eu.kanade.tachiyomi.extension.novel.repo.NovelPluginRepoService
import eu.kanade.tachiyomi.extension.novel.repo.NovelPluginRepoServiceContract
import eu.kanade.tachiyomi.extension.novel.repo.NovelPluginRepoUpdateInteractor
import eu.kanade.tachiyomi.extension.novel.runtime.NovelDomainAliasResolver
import eu.kanade.tachiyomi.extension.novel.runtime.NovelJsRuntimeFactory
import eu.kanade.tachiyomi.extension.novel.runtime.NovelJsSourceFactory
import eu.kanade.tachiyomi.extension.novel.runtime.NovelPluginAssetBindings
import eu.kanade.tachiyomi.extension.novel.runtime.NovelPluginRuntimeOverrides
import eu.kanade.tachiyomi.extension.novel.runtime.NovelPluginWebViewCoordinator
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.anime.AndroidAnimeSourceManager
import eu.kanade.tachiyomi.source.manga.AndroidMangaSourceManager
import eu.kanade.tachiyomi.source.novel.AndroidNovelSourceManager
import eu.kanade.tachiyomi.ui.player.ExternalIntents
import eu.kanade.tachiyomi.ui.player.subtitle.translation.AiSubtitleTranslationProvider
import eu.kanade.tachiyomi.ui.player.subtitle.translation.GoogleSubtitleTranslationProvider
import eu.kanade.tachiyomi.ui.player.subtitle.translation.NovelReaderAiSubtitleTranslationClient
import eu.kanade.tachiyomi.ui.player.subtitle.translation.SubtitleTranslationCoordinator
import eu.kanade.tachiyomi.ui.player.subtitle.translation.SubtitleTranslationDiskCache
import eu.kanade.tachiyomi.ui.player.subtitle.translation.SubtitleTranslationProviderId
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.domain.extensionrepo.novel.interactor.GetNovelExtensionRepo
import nl.adaptivity.xmlutil.XmlDeclMode.Charset
import nl.adaptivity.xmlutil.core.XmlVersion
import nl.adaptivity.xmlutil.serialization.XML
import tachiyomi.core.common.storage.AndroidStorageFolderProvider
import tachiyomi.data.AnimeUpdateStrategyColumnAdapter
import tachiyomi.`data`.Database
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.FetchTypeColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.achievement.database.AchievementsDatabase
import tachiyomi.data.extension.novel.AndroidNovelPluginKeyValueStore
import tachiyomi.data.extension.novel.NetworkNovelPluginDownloader
import tachiyomi.data.extension.novel.NovelPluginDownloader
import tachiyomi.data.extension.novel.NovelPluginInstaller
import tachiyomi.data.extension.novel.NovelPluginInstallerFacade
import tachiyomi.data.extension.novel.NovelPluginKeyValueStore
import tachiyomi.data.extension.novel.NovelPluginStorage
import tachiyomi.data.handlers.anime.AndroidAnimeDatabaseHandler
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.data.handlers.manga.AndroidMangaDatabaseHandler
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.data.handlers.novel.AndroidNovelDatabaseHandler
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.mi.`data`.AnimeDatabase
import tachiyomi.novel.`data`.NovelDatabase
import tachiyomi.source.local.entries.anime.LocalAnimeFetchTypeManager
import tachiyomi.source.local.image.anime.LocalAnimeBackgroundManager
import tachiyomi.source.local.image.anime.LocalAnimeCoverManager
import tachiyomi.source.local.image.anime.LocalEpisodeThumbnailManager
import tachiyomi.source.local.image.manga.LocalMangaCoverManager
import tachiyomi.source.local.image.novel.LocalNovelCoverManager
import tachiyomi.source.local.io.anime.LocalAnimeSourceFileSystem
import tachiyomi.source.local.io.manga.LocalMangaSourceFileSystem
import tachiyomi.source.local.io.novel.LocalNovelSourceFileSystem
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.io.File
import eu.kanade.tachiyomi.extension.novel.repo.NovelPluginStorage as NovelRepoPluginStorage

class AppModule(val app: Application) : InjektModule {
    companion object {
        private const val LOG_TAG = "AppModule"
    }

    /**
     * If a previous/dev build created an incompatible or partially initialized SQLite file
     * (e.g. correct user_version but missing tables after an interrupted migration), the app
     * will crash on the first query. Self-heal by deleting the file so SQLDelight recreates it.
     *
     * This helper is intentionally generic: pass the database file name and one required table
     * name. If the table is absent (or the file cannot be opened), the DB files are deleted.
     */
    private fun ensureDatabaseIsUsable(context: Context, dbName: String, requiredTable: String) {
        val dbFile = context.getDatabasePath(dbName)
        if (!dbFile.exists()) return

        val shouldDelete = runCatching {
            val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            val cursor = db.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1",
                arrayOf(requiredTable),
            )
            val hasRequiredTable = cursor.moveToFirst()
            cursor.close()
            db.close()
            !hasRequiredTable
        }.getOrElse { true }

        if (shouldDelete) {
            // `Context.deleteDatabase()` can fail if the DB is locked/open in another process.
            // Be aggressive: try deleteDatabase first, then delete DB/WAL/SHM files directly.
            Log.w(LOG_TAG, "DB '$dbName' missing required table '$requiredTable', recreating: ${dbFile.absolutePath}")

            runCatching { context.deleteDatabase(dbName) }

            // Defensive cleanup to avoid the "no such table" crash loop.
            runCatching { dbFile.delete() }
            runCatching { File(dbFile.absolutePath + "-wal").delete() }
            runCatching { File(dbFile.absolutePath + "-shm").delete() }

            if (dbFile.exists()) {
                Log.e(LOG_TAG, "Failed to delete DB file '$dbName'; app may crash. path=${dbFile.absolutePath}")
            }
        }
    }

    private fun ensureNovelDatabaseIsUsable(context: Context) =
        ensureDatabaseIsUsable(context, "tachiyomi.noveldb", "novelsources")

    private fun ensureMangaDatabaseIsUsable(context: Context) =
        ensureDatabaseIsUsable(context, "tachiyomi.db", "mangas")

    private fun ensureAnimeDatabaseIsUsable(context: Context) =
        ensureDatabaseIsUsable(context, "tachiyomi.animedb", "animes")

    private fun ensureAchievementsDatabaseIsUsable(context: Context) =
        ensureDatabaseIsUsable(context, AchievementsDatabase.NAME, "achievements")

    private fun ensureNotesSchema(
        db: SupportSQLiteDatabase,
        tableName: String,
        addColumnSql: String,
        triggerStatements: List<Pair<String, String>>,
    ) {
        val hasNotesColumn = runCatching {
            var found = false
            val cursor = db.query("PRAGMA table_info($tableName)")
            try {
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == "notes") {
                        found = true
                        break
                    }
                }
            } finally {
                cursor.close()
            }
            found
        }.getOrElse { error ->
            Log.e(LOG_TAG, "Failed to inspect '$tableName' schema for notes column, skip notes migration", error)
            return
        }

        if (!hasNotesColumn) {
            val addColumnResult = runCatching {
                db.execSQL(addColumnSql)
            }
            if (addColumnResult.isFailure) {
                Log.e(
                    LOG_TAG,
                    "Failed to add notes column for '$tableName', skip trigger recreation",
                    addColumnResult.exceptionOrNull(),
                )
                return
            }
        }

        // Recreate the triggers on every open so upgrades and fresh installs stay aligned.
        triggerStatements.forEach { (dropSql, createSql) ->
            runCatching {
                db.execSQL(dropSql)
                db.execSQL(createSql)
            }.onFailure { error ->
                Log.e(LOG_TAG, "Failed to recreate notes-related trigger for '$tableName'", error)
            }
        }
    }

    private fun migrateMangaNotesSchema(db: SupportSQLiteDatabase) {
        ensureNotesSchema(
            db = db,
            tableName = "mangas",
            addColumnSql = "ALTER TABLE mangas ADD COLUMN notes TEXT NOT NULL DEFAULT '';",
            triggerStatements = listOf(
                "DROP TRIGGER IF EXISTS update_last_modified_at_mangas;" to """
                    CREATE TRIGGER update_last_modified_at_mangas
                    AFTER UPDATE ON mangas
                    FOR EACH ROW
                    BEGIN
                      UPDATE mangas
                      SET last_modified_at = strftime('%s', 'now')
                      WHERE _id = new._id;
                    END;
                """.trimIndent(),
                "DROP TRIGGER IF EXISTS update_manga_version;" to """
                    CREATE TRIGGER update_manga_version AFTER UPDATE ON mangas
                    BEGIN
                        UPDATE mangas SET version = version + 1
                        WHERE _id = new._id AND new.is_syncing = 0 AND (
                        new.url != old.url OR
                        new.description != old.description OR
                        new.notes != old.notes OR
                        new.favorite != old.favorite
                    );
                    END;
                """.trimIndent(),
            ),
        )
    }

    private fun migrateAnimeNotesSchema(db: SupportSQLiteDatabase) {
        ensureNotesSchema(
            db = db,
            tableName = "animes",
            addColumnSql = "ALTER TABLE animes ADD COLUMN notes TEXT NOT NULL DEFAULT '';",
            triggerStatements = listOf(
                "DROP TRIGGER IF EXISTS update_last_modified_at_animes;" to """
                    CREATE TRIGGER update_last_modified_at_animes
                    AFTER UPDATE ON animes
                    FOR EACH ROW
                    BEGIN
                      UPDATE animes
                      SET last_modified_at = strftime('%s', 'now')
                      WHERE _id = new._id;
                    END;
                """.trimIndent(),
                "DROP TRIGGER IF EXISTS update_anime_version;" to """
                    CREATE TRIGGER update_anime_version AFTER UPDATE ON animes
                    BEGIN
                        UPDATE animes SET version = version + 1
                        WHERE _id = new._id AND new.is_syncing = 0 AND (
                        new.url != old.url OR
                        new.description != old.description OR
                        new.notes != old.notes OR
                        new.favorite != old.favorite
                    );
                    END;
                """.trimIndent(),
            ),
        )
    }

    private fun migrateNovelNotesSchema(db: SupportSQLiteDatabase) {
        ensureNotesSchema(
            db = db,
            tableName = "novels",
            addColumnSql = "ALTER TABLE novels ADD COLUMN notes TEXT NOT NULL DEFAULT '';",
            triggerStatements = listOf(
                "DROP TRIGGER IF EXISTS update_last_modified_at_novels;" to """
                    CREATE TRIGGER update_last_modified_at_novels
                    AFTER UPDATE OF source, url, author, description, notes, genre, title, status, thumbnail_url, favorite, last_update, next_update, initialized, viewer, chapter_flags, cover_last_modified, date_added, update_strategy, calculate_interval ON novels
                    FOR EACH ROW
                    BEGIN
                      UPDATE novels
                      SET last_modified_at = strftime('%s', 'now')
                      WHERE _id = new._id;
                    END;
                """.trimIndent(),
                "DROP TRIGGER IF EXISTS update_novel_version;" to """
                    CREATE TRIGGER update_novel_version AFTER UPDATE ON novels
                    BEGIN
                        UPDATE novels SET version = version + 1
                        WHERE _id = new._id AND new.is_syncing = 0 AND (
                            new.url != old.url OR
                            new.description != old.description OR
                            new.notes != old.notes OR
                            new.favorite != old.favorite
                        );
                    END;
                """.trimIndent(),
            ),
        )
    }

    private fun loadNovelPluginRuntimeOverrides(json: Json): NovelPluginRuntimeOverrides {
        val payload = runCatching {
            app.assets.open("novel-plugin-overrides.json").bufferedReader().use { it.readText() }
        }.getOrElse {
            Log.w(LOG_TAG, "Novel runtime overrides asset missing or invalid, fallback to empty")
            ""
        }
        return NovelPluginRuntimeOverrides.fromJson(json, payload)
    }

    override fun InjektRegistrar.registerInjectables() {
        addSingleton(app)

        ensureMangaDatabaseIsUsable(app)
        ensureAnimeDatabaseIsUsable(app)
        ensureNovelDatabaseIsUsable(app)
        ensureAchievementsDatabaseIsUsable(app)

        val sqlDriverManga = AndroidSqliteDriver(
            schema = Database.Schema,
            context = app,
            name = "tachiyomi.db",
            factory = if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Support database inspector in Android Studio
                FrameworkSQLiteOpenHelperFactory()
            } else {
                RequerySQLiteOpenHelperFactory()
            },
            callback = object : AndroidSqliteDriver.Callback(Database.Schema) {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    setPragma(db, "foreign_keys = ON")
                    setPragma(db, "journal_mode = WAL")
                    setPragma(db, "synchronous = NORMAL")
                    migrateMangaNotesSchema(db)
                }
                private fun setPragma(db: SupportSQLiteDatabase, pragma: String) {
                    val cursor = db.query("PRAGMA $pragma")
                    cursor.moveToFirst()
                    cursor.close()
                }
            },
        )

        val sqlDriverAnime = AndroidSqliteDriver(
            schema = AnimeDatabase.Schema,
            context = app,
            name = "tachiyomi.animedb",
            factory = if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Support database inspector in Android Studio
                FrameworkSQLiteOpenHelperFactory()
            } else {
                RequerySQLiteOpenHelperFactory()
            },
            callback = object : AndroidSqliteDriver.Callback(AnimeDatabase.Schema) {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    setPragma(db, "foreign_keys = ON")
                    setPragma(db, "journal_mode = WAL")
                    setPragma(db, "synchronous = NORMAL")
                    migrateAnimeNotesSchema(db)
                }
                private fun setPragma(db: SupportSQLiteDatabase, pragma: String) {
                    val cursor = db.query("PRAGMA $pragma")
                    cursor.moveToFirst()
                    cursor.close()
                }
            },
        )

        val sqlDriverAchievements = AndroidSqliteDriver(
            schema = tachiyomi.db.achievement.AchievementsDatabase.Schema,
            context = app,
            name = AchievementsDatabase.NAME,
            factory = if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Support database inspector in Android Studio
                FrameworkSQLiteOpenHelperFactory()
            } else {
                RequerySQLiteOpenHelperFactory()
            },
            callback = object : AndroidSqliteDriver.Callback(tachiyomi.db.achievement.AchievementsDatabase.Schema) {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    setPragma(db, "foreign_keys = ON")
                    setPragma(db, "journal_mode = WAL")
                    setPragma(db, "synchronous = NORMAL")
                }
                private fun setPragma(db: SupportSQLiteDatabase, pragma: String) {
                    val cursor = db.query("PRAGMA $pragma")
                    cursor.moveToFirst()
                    cursor.close()
                }
            },
        )

        val sqlDriverNovel = AndroidSqliteDriver(
            schema = NovelDatabase.Schema,
            context = app,
            name = "tachiyomi.noveldb",
            factory = if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Support database inspector in Android Studio
                FrameworkSQLiteOpenHelperFactory()
            } else {
                RequerySQLiteOpenHelperFactory()
            },
            callback = object : AndroidSqliteDriver.Callback(NovelDatabase.Schema) {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    setPragma(db, "foreign_keys = ON")
                    setPragma(db, "journal_mode = WAL")
                    setPragma(db, "synchronous = NORMAL")
                    migrateNovelNotesSchema(db)
                }
                private fun setPragma(db: SupportSQLiteDatabase, pragma: String) {
                    val cursor = db.query("PRAGMA $pragma")
                    cursor.moveToFirst()
                    cursor.close()
                }
            },
        )

        addSingletonFactory {
            Database(
                driver = sqlDriverManga,
                historyAdapter = History.Adapter(
                    last_readAdapter = DateColumnAdapter,
                ),
                chaptersAdapter = Chapters.Adapter(memoAdapter = MemoColumnAdapter),
                mangasAdapter = Mangas.Adapter(
                    memoAdapter = MemoColumnAdapter,
                    genreAdapter = StringListColumnAdapter,
                    custom_genreAdapter = StringListColumnAdapter,
                    update_strategyAdapter = MangaUpdateStrategyColumnAdapter,
                ),
            )
        }

        addSingletonFactory {
            NovelDatabase(
                driver = sqlDriverNovel,
                novel_historyAdapter = Novel_history.Adapter(
                    last_readAdapter = DateColumnAdapter,
                ),
                novel_chaptersAdapter = Novel_chapters.Adapter(memoAdapter = MemoColumnAdapter),
                novelsAdapter = Novels.Adapter(
                    memoAdapter = MemoColumnAdapter,
                    genreAdapter = StringListColumnAdapter,
                    custom_genreAdapter = StringListColumnAdapter,
                    update_strategyAdapter = MangaUpdateStrategyColumnAdapter,
                ),
            )
        }

        addSingletonFactory {
            AnimeDatabase(
                driver = sqlDriverAnime,
                animehistoryAdapter = Animehistory.Adapter(
                    last_seenAdapter = DateColumnAdapter,
                ),
                episodesAdapter = Episodes.Adapter(memoAdapter = MemoColumnAdapter),
                animesAdapter = Animes.Adapter(
                    memoAdapter = MemoColumnAdapter,
                    genreAdapter = StringListColumnAdapter,
                    custom_genreAdapter = StringListColumnAdapter,
                    update_strategyAdapter = AnimeUpdateStrategyColumnAdapter,
                    fetch_typeAdapter = FetchTypeColumnAdapter,
                ),
            )
        }

        addSingletonFactory {
            AchievementsDatabase(
                driver = sqlDriverAchievements,
            )
        }

        addSingletonFactory<NovelDatabaseHandler> {
            AndroidNovelDatabaseHandler(
                get(),
                sqlDriverNovel,
            )
        }

        addSingletonFactory<MangaDatabaseHandler> {
            AndroidMangaDatabaseHandler(
                get(),
                sqlDriverManga,
            )
        }

        addSingletonFactory<AnimeDatabaseHandler> {
            AndroidAnimeDatabaseHandler(
                get(),
                sqlDriverAnime,
            )
        }

        addSingletonFactory {
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                coerceInputValues = true
            }
        }
        addSingletonFactory {
            XML {
                defaultPolicy {
                    ignoreUnknownChildren()
                }
                autoPolymorphic = true
                xmlDeclMode = Charset
                indent = 2
                xmlVersion = XmlVersion.XML10
            }
        }
        addSingletonFactory<ProtoBuf> {
            ProtoBuf
        }

        addSingletonFactory {
            val readerPreferences = get<ReaderPreferences>()
            ChapterCache(
                context = app,
                json = get(),
                maxSizeBytes = readerPreferences.imageCacheSizeMb().get().toLong() * 1024L * 1024L,
            )
        }

        addSingletonFactory { MangaCoverCache(app) }
        addSingletonFactory { AnimeCoverCache(app) }
        addSingletonFactory { AnimeBackgroundCache(app) }
        addSingletonFactory { NovelCoverCache(app) }
        addSingletonFactory { SeriesCoverCache(app) }

        // Anime metadata caches
        addSingletonFactory { tachiyomi.data.shikimori.ShikimoriMetadataCache(get()) }
        addSingletonFactory { tachiyomi.data.anilist.AnilistMetadataCache(get()) }
        addSingletonFactory { tachiyomi.data.metadata.AnimeExternalMetadataCache(get(), get()) }
        addSingletonFactory { tachiyomi.data.metadata.MangaExternalMetadataCache(get()) }
        addSingletonFactory { eu.kanade.tachiyomi.data.coil.MetadataCoverResolver(get(), get(), get()) }

        addSingletonFactory { NetworkHelper(app, get()) }
        addSingletonFactory { eu.kanade.domain.easteregg.aurora.AuroraHeartManager.get(app) }
        addSingletonFactory { TorrentServerApi(get(), get()) }
        addSingletonFactory { TorrentServerUtils(get(), get()) }
        addSingletonFactory { JavaScriptEngine(app) }
        addSingletonFactory {
            eu.kanade.tachiyomi.ui.reader.novel.translation.GoogleTranslationService(get<NetworkHelper>().client)
        }
        addSingletonFactory {
            eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiTranslationService(
                client = get<NetworkHelper>().client.newBuilder()
                    .callTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
                    .build(),
                json = get(),
                promptResolver = eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiPromptResolver(app),
            )
        }
        addSingletonFactory {
            eu.kanade.tachiyomi.ui.reader.novel.translation.OpenRouterTranslationService(
                client = get<NetworkHelper>().client.newBuilder()
                    .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
                    .build(),
                json = get(),
            )
        }
        addSingletonFactory {
            eu.kanade.tachiyomi.ui.reader.novel.translation.DeepSeekTranslationService(
                client = get<NetworkHelper>().client.newBuilder()
                    .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
                    .build(),
                json = get(),
                resolveSystemPrompt = { mode, family ->
                    eu.kanade.tachiyomi.ui.reader.novel.translation.DeepSeekPromptResolver(app)
                        .resolveSystemPrompt(mode, family)
                },
            )
        }
        addSingletonFactory {
            eu.kanade.tachiyomi.ui.reader.novel.translation.MistralTranslationService(
                client = get<NetworkHelper>().client.newBuilder()
                    .callTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
                    .build(),
                json = get(),
                resolveSystemPrompt = { mode, family ->
                    eu.kanade.tachiyomi.ui.reader.novel.translation.MistralPromptResolver(app)
                        .resolveSystemPrompt(mode, family)
                },
            )
        }
        addSingletonFactory {
            eu.kanade.tachiyomi.ui.reader.novel.translation.NvidiaTranslationService(
                client = get<NetworkHelper>().client.newBuilder()
                    .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
                    .build(),
                json = get(),
            )
        }
        addSingletonFactory {
            eu.kanade.tachiyomi.ui.reader.novel.translation.OllamaCloudTranslationService(
                client = get<NetworkHelper>().client.newBuilder()
                    .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
                    .build(),
                json = get(),
            )
        }
        addSingletonFactory {
            NovelReaderAiSubtitleTranslationClient(
                preferences = get(),
                geminiTranslationService = get(),
                openRouterTranslationService = get(),
                deepSeekTranslationService = get(),
                mistralTranslationService = get(),
                nvidiaTranslationService = get(),
                ollamaCloudTranslationService = get(),
            )
        }
        addSingletonFactory {
            GoogleSubtitleTranslationProvider(get())
        }
        addSingletonFactory {
            AiSubtitleTranslationProvider(
                client = get<NovelReaderAiSubtitleTranslationClient>(),
            )
        }
        addSingletonFactory {
            SubtitleTranslationDiskCache(
                File(app.cacheDir, "subtitle_translations"),
            )
        }
        addSingletonFactory {
            SubtitleTranslationCoordinator(
                providers = mapOf(
                    SubtitleTranslationProviderId.Google to get<GoogleSubtitleTranslationProvider>(),
                    SubtitleTranslationProviderId.Ai to get<AiSubtitleTranslationProvider>(),
                ),
                cache = get(),
            )
        }
        addSingletonFactory { eu.kanade.tachiyomi.data.suggestions.SuggestionCoordinator() }
        addSingletonFactory { eu.kanade.tachiyomi.data.suggestions.novel.NovelRelatedSuggestionCoordinator() }
        addSingletonFactory { eu.kanade.tachiyomi.data.suggestions.novel.NovelSearchFallbackEngine() }
        addSingletonFactory { eu.kanade.tachiyomi.data.suggestions.manga.MangaSearchFallbackEngine() }
        addSingletonFactory { eu.kanade.tachiyomi.data.suggestions.anime.AnimeSearchFallbackEngine() }

        addSingletonFactory { NovelPluginIndexParser(get()) }
        addSingletonFactory<NovelPluginIndexFetcher> { NetworkNovelPluginIndexFetcher(get<NetworkHelper>().client) }
        addSingletonFactory<NovelPluginRepoProvider> {
            val getRepos: GetNovelExtensionRepo = get()
            object : NovelPluginRepoProvider {
                override suspend fun getAll() = getRepos.getAll()
            }
        }
        addSingletonFactory { NovelPluginApi(get(), get(), get()) }
        addSingletonFactory<NovelPluginApiFacade> { get<NovelPluginApi>() }
        addSingletonFactory {
            val filesDir = runCatching { app.filesDir }.getOrElse {
                File(System.getProperty("java.io.tmpdir"), "aniyomi_test_files_dir").also { it.mkdirs() }
            }
            NovelPluginStorage(File(filesDir, "novel_plugins"))
        }
        addSingletonFactory<NovelPluginDownloader> { NetworkNovelPluginDownloader(get<NetworkHelper>().client) }
        addSingletonFactory { NovelPluginInstaller(get(), get(), get()) }
        addSingletonFactory<NovelPluginInstallerFacade> { get<NovelPluginInstaller>() }
        addSingletonFactory<NovelPluginKeyValueStore> { AndroidNovelPluginKeyValueStore(app) }
        addSingletonFactory { loadNovelPluginRuntimeOverrides(get()) }
        addSingletonFactory { NovelDomainAliasResolver(get()) }
        addSingletonFactory { NovelJsRuntimeFactory(app, get(), get(), get(), get()) }
        addSingletonFactory { NovelPluginAssetBindings(get()) }
        addSingletonFactory { NovelPluginWebViewCoordinator(get()) }
        addSingletonFactory<NovelPluginSourceFactory> { NovelJsSourceFactory(get(), get(), get(), get(), get(), get()) }
        addSingletonFactory { TrustNovelExtension(get(), get()) }
        addSingletonFactory { ApkInstallStateStore() }
        addSingletonFactory { PendingApkInstallStore(get()) }
        addSingletonFactory<UnifiedApkExtensionInstaller> {
            UnifiedApkExtensionInstallCoordinator(
                stateStore = get(),
                backendAdapters = setOf(
                    PrivateApkInstallBackendAdapter(app),
                    SystemIntentApkInstallBackendAdapter(app, get(), ApkInstallBackend.PACKAGE_INSTALLER),
                    SystemIntentApkInstallBackendAdapter(app, get(), ApkInstallBackend.LEGACY),
                    ShizukuApkInstallBackendAdapter(app),
                    DhizukuApkInstallBackendAdapter(app),
                ),
            )
        }
        addSingletonFactory { KotlinNovelExtensionInstaller(app, get<NetworkHelper>().client, get(), get()) }

        addSingletonFactory<NovelExtensionManager> {
            DefaultNovelExtensionManager(app, get(), get(), get(), get(), get())
        }
        addSingletonFactory { NovelExtensionUpdateChecker() }
        addSingletonFactory { NovelPluginRepoParser(get()) }
        addSingletonFactory<NovelRepoPluginStorage> { InMemoryNovelPluginStorage() }
        addSingletonFactory { NovelPluginRepoService(get<NetworkHelper>().client, get()) }
        addSingletonFactory<NovelPluginRepoServiceContract> { get<NovelPluginRepoService>() }
        addSingletonFactory { NovelPluginRepoUpdateInteractor(get(), get(), get()) }

        addSingletonFactory<MangaSourceManager> { AndroidMangaSourceManager(app, get(), get()) }
        addSingletonFactory<AnimeSourceManager> { AndroidAnimeSourceManager(app, get(), get()) }
        addSingletonFactory<NovelSourceManager> { AndroidNovelSourceManager(app, get(), get()) }

        addSingletonFactory { MangaExtensionManager(app) }
        addSingletonFactory { AnimeExtensionManager(app) }

        addSingletonFactory { MangaDownloadProvider(app) }
        addSingletonFactory { MangaDownloadManager(app) }
        addSingletonFactory { MangaDownloadCache(app) }

        addSingletonFactory { AnimeDownloadProvider(app) }
        addSingletonFactory { AnimeDownloadManager(app) }
        addSingletonFactory { AnimeDownloadCache(app) }
        addSingletonFactory { NovelDownloadCache() }

        addSingletonFactory { TrackerManager(app) }
        addSingletonFactory { DelayedAnimeTrackingStore(app) }
        addSingletonFactory { DelayedMangaTrackingStore(app) }
        addSingletonFactory { DelayedNovelTrackingStore(app) }

        // Anime metadata integration
        addSingletonFactory {
            val trackerManager = get<TrackerManager>()
            eu.kanade.domain.shikimori.interactor.GetShikimoriMetadata(
                metadataCache = get(),
                shikimori = trackerManager.shikimori,
                shikimoriApi = trackerManager.shikimori.api,
                getAnimeTracks = get(),
                preferences = get(),
            )
        }
        addSingletonFactory {
            val trackerManager = get<TrackerManager>()
            eu.kanade.domain.anilist.interactor.GetAnilistMetadata(
                metadataCache = get(),
                anilistApi = trackerManager.aniList.api,
                getAnimeTracks = get(),
                preferences = get(),
            )
        }
        addSingletonFactory {
            val trackerManager = get<TrackerManager>()
            eu.kanade.domain.metadata.interactor.GetAnimeMetadata(
                metadataCache = get(),
                anilistApi = trackerManager.aniList.api,
                shikimori = trackerManager.shikimori,
                shikimoriApi = trackerManager.shikimori.api,
                getAnimeTracks = get(),
                preferences = get(),
            )
        }
        addSingletonFactory {
            val trackerManager = get<TrackerManager>()
            eu.kanade.domain.metadata.interactor.GetMangaMetadata(
                metadataCache = get(),
                anilistApi = trackerManager.aniList.api,
                shikimori = trackerManager.shikimori,
                shikimoriApi = trackerManager.shikimori.api,
                getMangaTracks = get(),
                preferences = get(),
            )
        }
        addSingletonFactory {
            eu.kanade.domain.entries.metadata.FetchEntryMetadataFromTracker(
                getMangaTracks = get(),
                getAnimeTracks = get(),
                getNovelTracks = get(),
                trackerManager = get(),
            )
        }

        addSingletonFactory { ImageSaver(app) }

        addSingletonFactory { AndroidStorageFolderProvider(app) }

        addSingletonFactory { LocalMangaSourceFileSystem(get()) }
        addSingletonFactory { LocalMangaCoverManager(app, get()) }

        addSingletonFactory { LocalAnimeSourceFileSystem(get()) }
        addSingletonFactory { LocalAnimeBackgroundManager(app, get()) }
        addSingletonFactory { LocalAnimeCoverManager(app, get()) }
        addSingletonFactory { LocalAnimeFetchTypeManager(app, get()) }
        addSingletonFactory { LocalEpisodeThumbnailManager(app, get()) }

        addSingletonFactory { LocalNovelSourceFileSystem(get()) }
        addSingletonFactory { LocalNovelCoverManager(app, get()) }

        addSingletonFactory { StorageManager(app, get()) }

        addSingletonFactory { ExternalIntents() }

        // Translation system
        addSingletonFactory { TranslationQueueManager() }
        addSingletonFactory { TranslationNotificationManager(app) }

        // Achievement system repositories
        addSingletonFactory<tachiyomi.domain.achievement.repository.AchievementRepository> {
            tachiyomi.data.achievement.repository.AchievementRepositoryImpl(get())
        }
        addSingletonFactory<tachiyomi.domain.achievement.repository.ActivityDataRepository> {
            tachiyomi.data.achievement.ActivityDataRepositoryImpl(get())
        }
        addSingletonFactory<tachiyomi.domain.achievement.repository.UserProfileRepository> {
            tachiyomi.data.achievement.UserProfileRepositoryImpl(get())
        }

        // Achievement system managers and handlers
        addSingletonFactory<tachiyomi.data.achievement.localization.AchievementTextResolver> {
            eu.kanade.tachiyomi.data.achievement.localization.AchievementTextResolverImpl(app)
        }
        addSingletonFactory { tachiyomi.data.achievement.loader.AchievementLoader(app, get(), get(), get()) }
        addSingletonFactory { tachiyomi.data.achievement.handler.PointsManager(get()) }
        addSingletonFactory { tachiyomi.data.achievement.UserProfileManager(get()) }
        addSingletonFactory {
            tachiyomi.data.achievement.UnlockableManager(
                app.getSharedPreferences("achievement_unlockables", Context.MODE_PRIVATE),
                get(),
            )
        }

        // Sync preferences and Google Drive service
        addSingletonFactory { SyncPreferences(get()) }
        addSingletonFactory { GoogleDriveService(app) }

        // Asynchronously init expensive components for a faster cold start
        java.util.concurrent.Executors.newSingleThreadExecutor().execute {
            get<NetworkHelper>()

            get<MangaSourceManager>()
            get<AnimeSourceManager>()
            get<NovelSourceManager>()

            get<Database>()
            get<AnimeDatabase>()
            get<NovelDatabase>()
            get<AchievementsDatabase>()

            get<MangaDownloadManager>()
            get<AnimeDownloadManager>()
        }
    }
}
