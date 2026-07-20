package mihon.data.repository.manga

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import `data`.History
import `data`.Mangas
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.data.extension.service.ExtensionStoreService
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.GzipSink
import okio.buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.handlers.manga.AndroidMangaDatabaseHandler
import tachiyomi.data.Database as MangaDatabase

class MangaExtensionStoreRepositoryImplTest {

    private val server = MockWebServer()
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var handler: AndroidMangaDatabaseHandler
    private lateinit var repository: MangaExtensionStoreRepositoryImpl
    private lateinit var service: ExtensionStoreService

    @BeforeEach
    fun setUp() {
        Class.forName("org.sqlite.JDBC")
        server.start()

        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MangaDatabase.Schema.create(driver)
        val database = MangaDatabase(
            driver = driver,
            historyAdapter = History.Adapter(last_readAdapter = DateColumnAdapter),
            chaptersAdapter = data.Chapters.Adapter(memoAdapter = MemoColumnAdapter),
            mangasAdapter = Mangas.Adapter(
                memoAdapter = MemoColumnAdapter,
                genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = MangaUpdateStrategyColumnAdapter,
                custom_genreAdapter = StringListColumnAdapter,
            ),
        )
        handler = AndroidMangaDatabaseHandler(
            db = database,
            driver = driver,
            queryDispatcher = Dispatchers.Default,
            transactionDispatcher = Dispatchers.Default,
        )
        service = ExtensionStoreService(OkHttpClient(), Json { ignoreUnknownKeys = true }, ProtoBuf)
        repository = MangaExtensionStoreRepositoryImpl(handler, service)
    }

    @AfterEach
    fun tearDown() {
        driver.close()
        server.shutdown()
    }

    @Test
    fun `insertFromPreference stores legacy flag and loads gzip wrapped legacy index`() = runTest {
        val legacyIndex = """
            [
              {
                "name": "Tachiyomi: Offline Legacy",
                "pkg": "offline.legacy.pkg",
                "apk": "offline.legacy.pkg.apk",
                "lang": "en",
                "code": 12,
                "version": "1.6.0",
                "nsfw": 0,
                "sources": []
              }
            ]
        """.trimIndent()
        server.enqueue(MockResponse().setBody(Buffer().write(gzip(legacyIndex))))

        val repoJsonUrl = server.url("/repo/repo.json").toString()
        repository.insertFromPreference(repoJsonUrl, "Offline Repo")

        val store = repository.getAll().single()
        store.isLegacy shouldBe true
        store.indexUrl shouldBe repoJsonUrl

        val extensions = service.getExtensions(store)
        extensions.isSuccess.shouldBeTrue()
        extensions.getOrThrow().single().pkgName shouldBe "offline.legacy.pkg"
    }

    private fun gzip(body: String): ByteArray {
        val buffer = Buffer()
        GzipSink(buffer).buffer().use { it.writeUtf8(body) }
        return buffer.readByteArray()
    }
}
