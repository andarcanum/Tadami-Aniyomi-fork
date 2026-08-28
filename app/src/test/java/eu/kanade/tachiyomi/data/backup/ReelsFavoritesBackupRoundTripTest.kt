package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupReelsFavorite
import eu.kanade.tachiyomi.data.backup.models.toBackupReelsFavorite
import eu.kanade.tachiyomi.data.backup.models.toReelsFavorite
import eu.kanade.tachiyomi.data.backup.restore.restorers.ReelsFavoritesRestorer
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Test
import tachiyomi.domain.reels.anime.model.ReelsFavorite
import tachiyomi.domain.reels.anime.repository.ReelsFavoriteRepository
import java.util.Date

class ReelsFavoritesBackupRoundTripTest {

    private val favorite = ReelsFavorite(
        videoId = "vid-1",
        sourceId = 101L,
        title = "Saved reel",
        author = "alice",
        videoUrl = "https://cdn.example/sd.mp4",
        videoUrlHd = "https://cdn.example/hd.mp4",
        posterUrl = "https://cdn.example/p.jpg",
        posterUrlVertical = "https://cdn.example/vp.jpg",
        webUrl = "https://example.example/watch/vid-1",
        durationSec = 12.5,
        hasAudio = true,
        addedAt = Date(1_700_000_000_000L),
    )

    @Test
    fun `reels favorite survives proto round trip`() {
        val backup = Backup(backupReelsFavorites = listOf(favorite.toBackupReelsFavorite()))

        val bytes = ProtoBuf.encodeToByteArray(Backup.serializer(), backup)
        val decoded = ProtoBuf.decodeFromByteArray(Backup.serializer(), bytes)

        decoded.backupReelsFavorites.size shouldBe 1
        decoded.backupReelsFavorites.first().toReelsFavorite() shouldBe favorite
    }

    @Test
    fun `empty backup decodes without reels favorites`() {
        val bytes = ProtoBuf.encodeToByteArray(Backup.serializer(), Backup())
        val decoded = ProtoBuf.decodeFromByteArray(Backup.serializer(), bytes)
        decoded.backupReelsFavorites shouldBe emptyList()
    }

    @Test
    fun `mapper preserves all fields`() {
        val round = favorite.toBackupReelsFavorite().toReelsFavorite()
        round shouldBe favorite
    }

    @Test
    fun `restore writes all favorites in one transactional batch`() {
        val repo = RecordingReelsRepository()
        val restorer = ReelsFavoritesRestorer(repo)
        val other = favorite.copy(videoId = "vid-2", sourceId = 202L)

        runBlocking {
            restorer.restoreReelsFavorites(listOf(favorite.toBackupReelsFavorite(), other.toBackupReelsFavorite()))
        }

        repo.insertAllCalls shouldBe 1
        repo.singleInserts shouldBe 0
        repo.favorites.size shouldBe 2
    }

    @Test
    fun `restore keeps local favorites missing from the backup`() {
        val repo = RecordingReelsRepository()
        val restorer = ReelsFavoritesRestorer(repo)
        val local = favorite.copy(videoId = "local-1")
        repo.favorites["local-1" to 101L] = local

        val backupFavorite = favorite.copy(videoId = "backup-1")
        runBlocking {
            restorer.restoreReelsFavorites(listOf(backupFavorite.toBackupReelsFavorite()))
        }

        repo.favorites.keys shouldBe setOf("local-1" to 101L, "backup-1" to 101L)
    }

    @Test
    fun `restore replaces same-key favorites instead of duplicating`() {
        val repo = RecordingReelsRepository()
        val restorer = ReelsFavoritesRestorer(repo)
        val staleTs = Date(1_000L)
        val freshTs = Date(2_000L)
        repo.favorites["dup" to 101L] = favorite.copy(videoId = "dup", addedAt = staleTs)

        val backupFavorite = favorite.copy(videoId = "dup", addedAt = freshTs)
        runBlocking {
            restorer.restoreReelsFavorites(listOf(backupFavorite.toBackupReelsFavorite()))
        }

        repo.favorites.size shouldBe 1
        repo.favorites["dup" to 101L]!!.addedAt shouldBe freshTs
    }
}

private class RecordingReelsRepository : ReelsFavoriteRepository {
    val favorites = mutableMapOf<Pair<String, Long>, ReelsFavorite>()
    var insertAllCalls = 0
    var singleInserts = 0

    override fun subscribeAll(): Flow<List<ReelsFavorite>> = MutableStateFlow(favorites.values.toList())

    override suspend fun getAll(): List<ReelsFavorite> = favorites.values.toList()

    override suspend fun getBySource(sourceId: Long): List<ReelsFavorite> =
        favorites.values.filter { it.sourceId == sourceId }

    override suspend fun getIdsBySource(sourceId: Long): List<String> =
        favorites.values.filter { it.sourceId == sourceId }.map { it.videoId }

    override suspend fun insert(favorite: ReelsFavorite) {
        singleInserts++
        favorites[favorite.videoId to favorite.sourceId] = favorite
    }

    override suspend fun insertAll(favorites: List<ReelsFavorite>) {
        insertAllCalls++
        favorites.forEach { this.favorites[it.videoId to it.sourceId] = it }
    }

    override suspend fun delete(videoId: String, sourceId: Long) {
        favorites.remove(videoId to sourceId)
    }
}
