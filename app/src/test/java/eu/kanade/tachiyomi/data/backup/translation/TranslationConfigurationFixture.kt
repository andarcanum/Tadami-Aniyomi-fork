package eu.kanade.tachiyomi.data.backup.translation

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import datanovel.Novel_chapters
import datanovel.Novel_history
import datanovel.Novels
import io.mockk.mockk
import kotlinx.serialization.json.Json
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.entries.novel.NovelRepositoryImpl
import tachiyomi.data.handlers.novel.AndroidNovelDatabaseHandler
import tachiyomi.data.translation.profile.TranslationProfileRepositoryImpl
import tachiyomi.domain.translation.credentials.CredentialRead
import tachiyomi.domain.translation.credentials.CredentialSecret
import tachiyomi.domain.translation.credentials.CredentialStore
import tachiyomi.domain.translation.profile.ConnectionProfile
import tachiyomi.domain.translation.profile.CredentialBinding
import tachiyomi.domain.translation.profile.CredentialMetadata
import tachiyomi.domain.translation.profile.GenerationSettings
import tachiyomi.domain.translation.profile.LegacyCredentialReference
import tachiyomi.domain.translation.profile.LegacyCredentialScope
import tachiyomi.domain.translation.profile.ModelSelection
import tachiyomi.domain.translation.profile.OutputTokenField
import tachiyomi.domain.translation.profile.ProfileAssignment
import tachiyomi.domain.translation.profile.ProfileAssignmentScope
import tachiyomi.domain.translation.profile.ProfileAuth
import tachiyomi.domain.translation.profile.ProfileCatalog
import tachiyomi.domain.translation.profile.PromptSettings
import tachiyomi.domain.translation.profile.ReasoningChoice
import tachiyomi.domain.translation.profile.ReasoningContract
import tachiyomi.domain.translation.profile.TranslationLanguages
import tachiyomi.domain.translation.profile.TranslationProfile
import tachiyomi.domain.translation.profile.TranslationProtocol
import tachiyomi.novel.data.NovelDatabase

internal const val CONNECTION = "00000000-0000-0000-0000-000000000001"
internal const val PROFILE = "00000000-0000-0000-0000-000000000002"
internal const val KEY_A = "00000000-0000-0000-0000-000000000011"
internal const val KEY_B = "00000000-0000-0000-0000-000000000012"
internal const val LOCAL_KEY = "00000000-0000-0000-0000-000000000099"
internal const val SOURCE = -42L
internal const val NOVEL_URL = "/novel/fixture"
internal const val VAULT_VALUE = "test-only-vault-value-never-export"

internal fun translationCatalog(novelId: Long? = null) = ProfileCatalog(
    connections = listOf(
        ConnectionProfile(
            CONNECTION,
            "Fixture connection",
            TranslationProtocol.OPENAI_CHAT,
            "https://example.test/custom/v1",
            ProfileAuth.BEARER,
            credentialBindings = listOf(CredentialBinding(KEY_A), CredentialBinding(KEY_B)),
        ),
    ),
    profiles = listOf(
        TranslationProfile(
            PROFILE,
            "Fixture profile",
            CONNECTION,
            ModelSelection(
                "exact/vendor-model",
                ReasoningContract.Levels(listOf("low", "high")),
                GenerationSettings(temperature = 0.4, maxOutputTokens = 128, reasoning = ReasoningChoice.Level("high")),
                OutputTokenField.MAX_COMPLETION_TOKENS,
            ),
            TranslationLanguages("English", "Russian"),
            PromptSettings(customText = "Retain this public prompt."),
        ),
    ),
    credentials = listOf(
        CredentialMetadata(KEY_A, "First alias", "1234", 1),
        CredentialMetadata(KEY_B, "Second alias", "5678", 2),
    ),
    assignments = listOf(
        ProfileAssignment(ProfileAssignmentScope.GLOBAL, 0, PROFILE),
        ProfileAssignment(ProfileAssignmentScope.SOURCE, SOURCE, PROFILE),
    ) + listOfNotNull(novelId?.let { ProfileAssignment(ProfileAssignmentScope.NOVEL, it, PROFILE) }),
    legacyReferences = listOf(
        LegacyCredentialReference("GEMINI", LegacyCredentialScope.GLOBAL, 0, KEY_A),
        LegacyCredentialReference("GEMINI", LegacyCredentialScope.SOURCE, SOURCE, KEY_B),
    ),
)

internal class TranslationBackupFixture : AutoCloseable {
    init {
        Class.forName("org.sqlite.JDBC", true, JdbcSqliteDriver::class.java.classLoader)
    }

    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    val database: NovelDatabase
    val handler: AndroidNovelDatabaseHandler
    val profiles: TranslationProfileRepositoryImpl
    val novels: NovelRepositoryImpl

    init {
        NovelDatabase.Schema.create(driver)
        database = NovelDatabase(
            driver,
            Novel_chapters.Adapter(memoAdapter = MemoColumnAdapter),
            Novel_history.Adapter(last_readAdapter = DateColumnAdapter),
            Novels.Adapter(
                memoAdapter = MemoColumnAdapter,
                genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = MangaUpdateStrategyColumnAdapter,
                custom_genreAdapter = StringListColumnAdapter,
            ),
        )
        handler = AndroidNovelDatabaseHandler(database, driver)
        profiles = TranslationProfileRepositoryImpl(handler, Json)
        novels = NovelRepositoryImpl(handler, mockk(relaxed = true))
    }

    fun insertNovel(id: Long, source: Long = SOURCE, url: String = NOVEL_URL) {
        driver.execute(
            null,
            """
            INSERT INTO novels (_id, source, url, title, status, favorite, initialized,
                viewer, chapter_flags, cover_last_modified, date_added)
            VALUES (?, ?, ?, 'Fixture novel', 0, 1, 1, 0, 0, 0, 0)
            """.trimIndent(),
            3,
        ) {
            bindLong(0, id)
            bindLong(1, source)
            bindString(2, url)
        }
    }

    override fun close() = driver.close()
}

internal class RecordingTranslationVault : CredentialStore {
    val values = mutableMapOf<String, String>()
    val states = mutableMapOf<String, CredentialRead>()
    val released = mutableListOf<CredentialSecret>()
    var reads = 0
    var puts = 0
    var removals = 0

    override suspend fun read(id: String): CredentialRead {
        reads++
        states[id]?.let { return it }
        val value = values[id] ?: return CredentialRead.Missing
        val secret = CredentialSecret(value).also { released += it }
        return CredentialRead.Available(secret)
    }

    override suspend fun put(id: String, secret: String) {
        puts++
        values[id] = secret
    }
    override suspend fun remove(id: String) {
        removals++
        values.remove(id)
    }
}
