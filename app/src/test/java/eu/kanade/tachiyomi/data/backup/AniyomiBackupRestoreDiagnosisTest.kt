package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupAnimeSource
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.LegacyBackup
import eu.kanade.tachiyomi.data.backup.models.MihonBackup
import eu.kanade.tachiyomi.data.backup.models.mergeLegacyPayloadIfPresent
import eu.kanade.tachiyomi.data.backup.models.routeSharedMangaEntriesBySource
import eu.kanade.tachiyomi.data.backup.models.toMihonBackupManga
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AniyomiBackupRestoreDiagnosisTest {

    private val parser = ProtoBuf

    @Test
    fun `legacy Aniyomi backup with anime at field 3 restores anime via LegacyBackup path`() {
        val legacy = LegacyBackup(
            backupManga = listOf(sampleManga("Manga A")),
            backupAnime = listOf(sampleAnime("Anime A")),
            backupAnimeSources = listOf(BackupAnimeSource(name = "AnimeSrc", sourceId = 42)),
        )
        val bytes = parser.encodeToByteArray(LegacyBackup.serializer(), legacy)

        assertTrue(BackupDetector.isLegacyBackup(bytes))

        val decoded = parser.decodeFromByteArray(LegacyBackup.serializer(), bytes).toBackup()
        assertEquals(listOf("Manga A"), decoded.backupManga.map { it.title })
        assertEquals(listOf("Anime A"), decoded.backupAnime.map { it.title })
    }

    @Test
    fun `legacy Aniyomi backup with anime but empty animeSources is detected as legacy`() {
        val legacy = LegacyBackup(
            backupManga = listOf(sampleManga("Manga A")),
            backupAnime = listOf(sampleAnime("Anime A")),
            backupAnimeSources = emptyList(),
        )
        val bytes = parser.encodeToByteArray(LegacyBackup.serializer(), legacy)

        assertTrue(BackupDetector.isLegacyBackup(bytes))
        assertFalse(BackupDetector.isMihonBackup(bytes))

        val decoded = parser.decodeFromByteArray(LegacyBackup.serializer(), bytes).toBackup()
        assertEquals(listOf("Anime A"), decoded.backupAnime.map { it.title })
    }

    @Test
    fun `legacy-shaped backup without categories is not misdetected as Mihon`() {
        val legacy = LegacyBackup(
            backupManga = listOf(sampleManga("Manga A")),
            backupAnime = listOf(sampleAnime("Anime A")),
            backupAnimeSources = emptyList(),
        )
        val bytes = parser.encodeToByteArray(LegacyBackup.serializer(), legacy)

        assertFalse(BackupDetector.isMihonBackup(bytes))

        val decoded = parser.decodeFromByteArray(LegacyBackup.serializer(), bytes).toBackup()
        assertEquals(listOf("Manga A"), decoded.backupManga.map { it.title })
        assertEquals(listOf("Anime A"), decoded.backupAnime.map { it.title })
    }

    @Test
    fun `native backup decode merges legacy anime from field 3 when field 501 is empty`() {
        val legacy = LegacyBackup(
            backupManga = listOf(sampleManga("Manga A")),
            backupAnime = listOf(sampleAnime("Anime A")),
            backupAnimeCategories = listOf(BackupCategory(name = "Watching", order = 0)),
            backupAnimeSources = emptyList(),
        )
        val bytes = parser.encodeToByteArray(LegacyBackup.serializer(), legacy)

        val decoded = parser.decodeFromByteArray(Backup.serializer(), bytes)
            .mergeLegacyPayloadIfPresent(parser.decodeFromByteArray(LegacyBackup.serializer(), bytes))

        assertEquals(listOf("Manga A"), decoded.backupManga.map { it.title })
        assertEquals(listOf("Anime A"), decoded.backupAnime.map { it.title })
        assertEquals(listOf("Watching"), decoded.backupAnimeCategories.map { it.name })
    }

    @Test
    fun `modern Aniyomi backup with anime at field 501 restores correctly`() {
        val modern = Backup(
            backupManga = listOf(sampleManga("Manga A")),
            backupAnime = listOf(sampleAnime("Anime A")),
            isLegacy = false,
        )
        val bytes = parser.encodeToByteArray(Backup.serializer(), modern)

        assertFalse(BackupDetector.isMihonBackup(bytes))
        assertFalse(BackupDetector.isLegacyBackup(bytes))

        val decoded = parser.decodeFromByteArray(Backup.serializer(), bytes)
        assertEquals(listOf("Manga A"), decoded.backupManga.map { it.title })
        assertEquals(listOf("Anime A"), decoded.backupAnime.map { it.title })
    }

    @Test
    fun `shared manga entries route to anime when source id is listed in backupAnimeSources`() {
        val backup = Backup(
            backupManga = listOf(sampleManga("Anime entry").copy(source = 99)),
            backupAnimeSources = listOf(BackupAnimeSource(name = "AnimeSrc", sourceId = 99)),
        )

        val routed = backup.routeSharedMangaEntriesBySource(
            mangaSourceClassifier = { false },
            novelSourceClassifier = { false },
            animeSourceClassifier = { false },
        )

        assertEquals(emptyList(), routed.backupManga.map { it.title })
        assertEquals(listOf("Anime entry"), routed.backupAnime.map { it.title })
    }

    @Test
    fun `Mihon backup anime entries route when anime source is installed`() {
        val mihon = MihonBackup(
            backupManga = listOf(
                sampleManga("Manga A").copy(source = 1).toMihonBackupManga(),
                sampleManga("Anime posing as manga").copy(source = 99).toMihonBackupManga(),
            ),
            backupSources = listOf(
                eu.kanade.tachiyomi.data.backup.models.BackupSource(name = "M", sourceId = 1),
                eu.kanade.tachiyomi.data.backup.models.BackupSource(name = "A", sourceId = 99),
            ),
        )
        val bytes = parser.encodeToByteArray(MihonBackup.serializer(), mihon)

        val withAnimeExt = decodeViaMihon(
            bytes,
            mangaSourceClassifier = { it == 1L },
            novelSourceClassifier = { false },
            animeSourceClassifier = { it == 99L },
        )
        assertEquals(listOf("Manga A"), withAnimeExt.backupManga.map { it.title })
        assertEquals(listOf("Anime posing as manga"), withAnimeExt.backupAnime.map { it.title })
    }

    private fun decodeViaMihon(
        bytes: ByteArray,
        mangaSourceClassifier: (Long) -> Boolean,
        novelSourceClassifier: (Long) -> Boolean,
        animeSourceClassifier: (Long) -> Boolean,
    ): Backup {
        val mihon = parser.decodeFromByteArray(MihonBackup.serializer(), bytes)
        return Backup(
            backupManga = mihon.backupManga.map { it.toBackupManga() },
            backupCategories = mihon.backupCategories,
            backupSources = mihon.backupSources,
            backupPreferences = mihon.backupPreferences,
            backupSourcePreferences = mihon.backupSourcePreferences,
            backupMangaExtensionRepo = mihon.backupExtensionRepo,
            isLegacy = false,
        ).routeSharedMangaEntriesBySource(
            mangaSourceClassifier = mangaSourceClassifier,
            novelSourceClassifier = novelSourceClassifier,
            animeSourceClassifier = animeSourceClassifier,
        )
    }

    private fun sampleManga(title: String): BackupManga {
        return BackupManga(source = 1, url = "/manga", title = title)
    }

    private fun sampleAnime(title: String): BackupAnime {
        return BackupAnime(source = 42, url = "/anime", title = title)
    }
}
