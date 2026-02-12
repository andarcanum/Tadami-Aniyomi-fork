package eu.kanade.tachiyomi.di

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import data.History
import data.Mangas
import dataanime.Animehistory
import dataanime.Animes
import datanovel.Novel_history
import datanovel.Novels
import eu.kanade.domain.track.anime.store.DelayedAnimeTrackingStore
import eu.kanade.domain.track.manga.store.DelayedMangaTrackingStore
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.cache.AnimeBackgroundCache
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.MangaCoverCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadProvider
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadProvider
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
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
import eu.kanade.tachiyomi.extension.novel.repo.InMemoryNovelPluginStorage
import eu.kanade.tachiyomi.extension.novel.repo.NovelPluginRepoParser
import eu.kanade.tachiyomi.extension.novel.repo.NovelPluginRepoService
import eu.kanade.tachiyomi.extension.novel.repo.NovelPluginRepoServiceContract
import eu.kanade.tachiyomi.extension.novel.repo.NovelPluginRepoUpdateInteractor
import eu.kanade.tachiyomi.extension.novel.runtime.NovelJsRuntimeFactory
import eu.kanade.tachiyomi.extension.novel.runtime.NovelJsSourceFactory
import eu.kanade.tachiyomi.extension.novel.runtime.NovelDomainAliasResolver
import eu.kanade.tachiyomi.extension.novel.runtime.NovelPluginRuntimeOverrides
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.anime.AndroidAnimeSourceManager
import eu.kanade.tachiyomi.source.manga.AndroidMangaSourceManager
import eu.kanade.tachiyomi.source.novel.AndroidNovelSourceManager
import eu.kanade.tachiyomi.ui.player.ExternalIntents
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.domain.extensionrepo.novel.interactor.GetNovelExtensionRepo
import nl.adaptivity.xmlutil.XmlDeclMode.Charset
import nl.adaptivity.xmlutil.core.XmlVersion
import nl.adaptivity.xmlutil.serialization.XML
import tachiyomi.core.common.storage.AndroidStorageFolderProvider
import tachiyomi.data.AnimeUpdateStrategyColumnAdapter
import tachiyomi.data.Database
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.FetchTypeColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
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
import tachiyomi.mi.data.AnimeDatabase
import tachiyomi.novel.data.NovelDatabase
import tachiyomi.source.local.entries.anime.LocalAnimeFetchTypeManager
import tachiyomi.source.local.image.anime.LocalAnimeBackgroundManager
import tachiyomi.source.local.image.anime.LocalAnimeCoverManager
import tachiyomi.source.local.image.anime.LocalEpisodeThumbnailManager
import tachiyomi.source.local.image.manga.LocalMangaCoverManager
import tachiyomi.source.local.io.anime.LocalAnimeSourceFileSystem
import tachiyomi.source.local.io.manga.LocalMangaSourceFileSystem
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
     * We use a separate SqlDelight database for ranobe. If a previous/dev build created an
     * incompatible or partially initialized file (e.g. correct user_version but missing tables),
     * the app will crash on first query. Self-heal by deleting the file so it is recreated.
     */
    private fun ensureNovelDatabaseIsUsable(context: Context) {
        val dbName = "tachiyomi.noveldb"
        val dbFile = context.getDatabasePath(dbName)
        if (!dbFile.exists()) return

        val shouldDelete = runCatching {
            val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            val cursor = db.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1",
                arrayOf("novelsources"),
            )
            val hasRequiredTable = cursor.moveToFirst()
            cursor.close()
            db.close()
            !hasRequiredTable
        }.getOrElse { true }

        if (shouldDelete) {
            // `Context.deleteDatabase()` can fail if the DB is locked/open in another process.
            // Be aggressive: try deleteDatabase first, then delete DB/WAL/SHM files directly.
            Log.w(LOG_TAG, "Novel DB missing required tables, recreating: ${dbFile.absolutePath}")

            runCatching { context.deleteDatabase(dbName) }

            // Defensive cleanup to avoid the "no such table" crash loop.
            runCatching { dbFile.delete() }
            runCatching { File(dbFile.absolutePath + "-wal").delete() }
            runCatching { File(dbFile.absolutePath + "-shm").delete() }

            if (dbFile.exists()) {
                Log.e(LOG_TAG, "Failed to delete novel DB file; app may crash. path=${dbFile.absolutePath}")
            }
        }
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
                }
                private fun setPragma(db: SupportSQLiteDatabase, pragma: String) {
                    val cursor = db.query("PRAGMA $pragma")
                    cursor.moveToFirst()
                    cursor.close()
                }
            },
        )

        ensureNovelDatabaseIsUsable(app)

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
                }
                private fun setPragma(db: SupportSQLiteDatabase, pragma: String) {
                    val cursor = db.query("PRAGMA $pragma")
                    cursor.moveToFirst()
                    cursor.close()
                }
            },
        )

        val sqlDriverAchievements = AndroidSqliteDriver(
            schema = tachiyomi.data.achievement.AchievementsDatabase.Schema,
            context = app,
            name = AchievementsDatabase.NAME,
            factory = if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Support database inspector in Android Studio
                FrameworkSQLiteOpenHelperFactory()
            } else {
                RequerySQLiteOpenHelperFactory()
            },
            callback = object : AndroidSqliteDriver.Callback(tachiyomi.data.achievement.AchievementsDatabase.Schema) {
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
                mangasAdapter = Mangas.Adapter(
                    genreAdapter = StringListColumnAdapter,
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
                novelsAdapter = Novels.Adapter(
                    genreAdapter = StringListColumnAdapter,
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
                animesAdapter = Animes.Adapter(
                    genreAdapter = StringListColumnAdapter,
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

        addSingletonFactory { ChapterCache(app, get()) }

        addSingletonFactory { MangaCoverCache(app) }
        addSingletonFactory { AnimeCoverCache(app) }
        addSingletonFactory { AnimeBackgroundCache(app) }

        // Anime metadata caches
        addSingletonFactory { tachiyomi.data.shikimori.ShikimoriMetadataCache(get()) }
        addSingletonFactory { tachiyomi.data.anilist.AnilistMetadataCache(get()) }

        addSingletonFactory { NetworkHelper(app, get()) }
        addSingletonFactory { JavaScriptEngine(app) }

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
        addSingletonFactory { NovelPluginStorage(File(app.filesDir, "novel_plugins")) }
        addSingletonFactory<NovelPluginDownloader> { NetworkNovelPluginDownloader(get<NetworkHelper>().client) }
        addSingletonFactory { NovelPluginInstaller(get(), get(), get()) }
        addSingletonFactory<NovelPluginInstallerFacade> { get<NovelPluginInstaller>() }
        addSingletonFactory<NovelPluginKeyValueStore> { AndroidNovelPluginKeyValueStore(app) }
        addSingletonFactory { loadNovelPluginRuntimeOverrides(get()) }
        addSingletonFactory { NovelDomainAliasResolver(get()) }
        addSingletonFactory { NovelJsRuntimeFactory(app, get(), get(), get(), get()) }
        addSingletonFactory<NovelPluginSourceFactory> { NovelJsSourceFactory(get(), get(), get(), get()) }

        addSingletonFactory<NovelExtensionManager> { DefaultNovelExtensionManager(get(), get(), get(), get()) }
        addSingletonFactory { NovelExtensionUpdateChecker() }
        addSingletonFactory { NovelPluginRepoParser(get()) }
        addSingletonFactory<NovelRepoPluginStorage> { InMemoryNovelPluginStorage() }
        addSingletonFactory { NovelPluginRepoService(get<NetworkHelper>().client, get()) }
        addSingletonFactory<NovelPluginRepoServiceContract> { get<NovelPluginRepoService>() }
        addSingletonFactory { NovelPluginRepoUpdateInteractor(get(), get(), get()) }

        addSingletonFactory<MangaSourceManager> { AndroidMangaSourceManager(app, get(), get()) }
        addSingletonFactory<AnimeSourceManager> { AndroidAnimeSourceManager(app, get(), get()) }
        addSingletonFactory<NovelSourceManager> { AndroidNovelSourceManager(get(), get()) }

        addSingletonFactory { MangaExtensionManager(app) }
        addSingletonFactory { AnimeExtensionManager(app) }

        addSingletonFactory { MangaDownloadProvider(app) }
        addSingletonFactory { MangaDownloadManager(app) }
        addSingletonFactory { MangaDownloadCache(app) }

        addSingletonFactory { AnimeDownloadProvider(app) }
        addSingletonFactory { AnimeDownloadManager(app) }
        addSingletonFactory { AnimeDownloadCache(app) }

        addSingletonFactory { TrackerManager(app) }
        addSingletonFactory { DelayedAnimeTrackingStore(app) }
        addSingletonFactory { DelayedMangaTrackingStore(app) }

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

        addSingletonFactory { ImageSaver(app) }

        addSingletonFactory { AndroidStorageFolderProvider(app) }

        addSingletonFactory { LocalMangaSourceFileSystem(get()) }
        addSingletonFactory { LocalMangaCoverManager(app, get()) }

        addSingletonFactory { LocalAnimeSourceFileSystem(get()) }
        addSingletonFactory { LocalAnimeBackgroundManager(app, get()) }
        addSingletonFactory { LocalAnimeCoverManager(app, get()) }
        addSingletonFactory { LocalAnimeFetchTypeManager(app, get()) }
        addSingletonFactory { LocalEpisodeThumbnailManager(app, get()) }

        addSingletonFactory { StorageManager(app, get()) }

        addSingletonFactory { ExternalIntents() }

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
        addSingletonFactory { tachiyomi.data.achievement.loader.AchievementLoader(app, get(), get()) }
        addSingletonFactory { tachiyomi.data.achievement.handler.PointsManager(get()) }
        addSingletonFactory { tachiyomi.data.achievement.UserProfileManager(get()) }
        addSingletonFactory {
            tachiyomi.data.achievement.UnlockableManager(
                app.getSharedPreferences("achievement_unlockables", Context.MODE_PRIVATE),
            )
        }

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
