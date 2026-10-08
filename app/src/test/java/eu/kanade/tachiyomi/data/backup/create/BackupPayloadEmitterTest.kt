package eu.kanade.tachiyomi.data.backup.create

import eu.kanade.tachiyomi.data.backup.BackupOrigin
import eu.kanade.tachiyomi.data.backup.contentSummary
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupAchievement
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupAnimeSeries
import eu.kanade.tachiyomi.data.backup.models.BackupAnimeSource
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupCustomButtons
import eu.kanade.tachiyomi.data.backup.models.BackupDayActivity
import eu.kanade.tachiyomi.data.backup.models.BackupDiscoveryHidden
import eu.kanade.tachiyomi.data.backup.models.BackupDiscoveryTag
import eu.kanade.tachiyomi.data.backup.models.BackupExtension
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionRepos
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import eu.kanade.tachiyomi.data.backup.models.BackupFeed
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupMangaSeries
import eu.kanade.tachiyomi.data.backup.models.BackupNovel
import eu.kanade.tachiyomi.data.backup.models.BackupNovelSeries
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupReelsFavorite
import eu.kanade.tachiyomi.data.backup.models.BackupReelsFollow
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.models.BackupStats
import eu.kanade.tachiyomi.data.backup.models.BackupUserProfile
import eu.kanade.tachiyomi.data.backup.models.BooleanPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.MihonBackup
import eu.kanade.tachiyomi.data.backup.models.toMihonBackup
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream

/**
 * The streaming emitter replaces ProtoBuf.encodeToByteArray for the whole backup, so its wire
 * output must stay identical to the kotlinx encoder for both formats: field numbers, field order
 * and default omission all pinned by byte equality. A schema edit that forgets the emitter fails
 * here instead of silently dropping a section from every future backup.
 */
class BackupPayloadEmitterTest {

    @Test
    fun `native stream is byte identical to the kotlinx encoder`() {
        val backup = richBackup()
        val streamed = ByteArrayOutputStream()
            .also { BackupPayloadEmitter.emitNative(backup, ProtoBuf, it) }
            .toByteArray()

        assertArrayEquals(ProtoBuf.encodeToByteArray(Backup.serializer(), backup), streamed)
    }

    @Test
    fun `sister stream is byte identical to the kotlinx encoder`() {
        val backup = richBackup()
        val streamed = ByteArrayOutputStream()
            .also { BackupPayloadEmitter.emitSister(backup, ProtoBuf, it) }
            .toByteArray()

        assertArrayEquals(
            ProtoBuf.encodeToByteArray(MihonBackup.serializer(), backup.toMihonBackup()),
            streamed,
        )
    }

    @Test
    fun `streamed payload passes the staged verification`(@TempDir dir: File) {
        val backup = richBackup()
        val streamed = ByteArrayOutputStream()
            .also { BackupPayloadEmitter.emitNative(backup, ProtoBuf, it) }
            .toByteArray()
        val staged = File(dir, "staged.tachibk")
        FileOutputStream(staged).use { out -> GZIPOutputStream(out).use { it.write(streamed) } }

        verifyStagedStream(
            staged = staged,
            payloadDigest = MessageDigest.getInstance("SHA-256").digest(streamed),
            expected = backup.contentSummary(),
            expectedOrigin = BackupOrigin.TADAMI,
        )
    }

    private fun store(prefix: String) = BackupExtensionStore(
        indexUrl = "https://$prefix.invalid",
        name = prefix,
        badgeLabel = "badge",
        signingKey = "key",
        contactWebsite = "https://$prefix.invalid",
        contactDiscord = null,
        isLegacy = false,
        extensionListUrl = null,
    )

    private fun repo(prefix: String) = BackupExtensionRepos(
        baseUrl = "https://$prefix.invalid",
        name = prefix,
        shortName = null,
        website = "https://$prefix.invalid",
        signingKeyFingerprint = "fingerprint",
    )

    /** Every top level section populated, so every emitted field number is covered. */
    private fun richBackup(): Backup = Backup(
        backupManga = listOf(
            BackupManga(
                source = 1,
                url = "/manga",
                title = "Manga title",
                chapters = listOf(BackupChapter(url = "/chapter", name = "Chapter 1")),
            ),
        ),
        backupCategories = listOf(BackupCategory(name = "cat")),
        backupSources = listOf(BackupSource(name = "src", sourceId = 1)),
        backupPreferences = listOf(BackupPreference(key = "key", value = BooleanPreferenceValue(true))),
        backupSourcePreferences = listOf(BackupSourcePreferences(sourceKey = "src", prefs = emptyList())),
        backupMangaExtensionRepo = listOf(repo("manga")),
        isLegacy = false,
        backupAnime = listOf(BackupAnime(source = 2, url = "/anime", title = "Anime title")),
        backupAnimeCategories = listOf(BackupCategory(name = "anime-cat")),
        backupAnimeSources = listOf(BackupAnimeSource(name = "asrc", sourceId = 2)),
        backupExtensions = listOf(BackupExtension(pkgName = "pkg", apk = byteArrayOf(1))),
        backupAnimeExtensionRepo = listOf(repo("anime")),
        backupCustomButton = listOf(
            BackupCustomButtons(
                name = "btn",
                isFavorite = false,
                sortIndex = 0,
                content = "content",
                longPressContent = "long",
                onStartup = "startup",
            ),
        ),
        backupNovelExtensionRepo = listOf(repo("novel")),
        backupAnimeExtensionStore = listOf(store("anime")),
        backupMangaExtensionStore = listOf(store("manga")),
        backupNovelExtensionStore = listOf(store("novel")),
        backupNovel = listOf(BackupNovel(source = 3, url = "/novel", title = "Novel title")),
        backupNovelCategories = listOf(BackupCategory(name = "novel-cat")),
        backupNovelSources = listOf(BackupSource(name = "nsrc", sourceId = 3)),
        backupAchievements = listOf(
            BackupAchievement(id = "id", title = "t", description = "d", category = 0, type = 0),
        ),
        backupUserProfile = BackupUserProfile(joinDate = 0L),
        backupActivityLog = listOf(BackupDayActivity()),
        backupStats = BackupStats(),
        backupMangaSeries = listOf(BackupMangaSeries(title = "manga series")),
        backupNovelSeries = listOf(BackupNovelSeries(title = "novel series")),
        backupFeeds = listOf(BackupFeed()),
        backupReelsFavorites = listOf(
            BackupReelsFavorite(videoId = "v", sourceId = 1, videoUrl = "u", posterUrl = "p"),
        ),
        backupReelsFollows = listOf(BackupReelsFollow(sourceId = 1, creator = "creator")),
        backupDiscoveryHidden = listOf(BackupDiscoveryHidden(mediaType = "MANGA", cleanTitle = "title")),
        backupDiscoveryBlacklistTags = listOf(BackupDiscoveryTag(mediaType = "MANGA", tag = "tag")),
        backupAnimeSeries = listOf(BackupAnimeSeries(title = "anime series")),
    )
}
