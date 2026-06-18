package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.LegacyBackup
import eu.kanade.tachiyomi.data.backup.models.MihonBackup
import eu.kanade.tachiyomi.data.backup.models.routeSharedMangaEntriesBySource
import kotlinx.serialization.SerializationException
import kotlinx.serialization.protobuf.ProtoBuf
import okio.buffer
import okio.gzip
import okio.source
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException

class BackupDecoder(
    private val context: Context,
    private val parser: ProtoBuf = Injekt.get(),
    private val mangaSourceManager: MangaSourceManager = Injekt.get(),
    private val novelSourceManager: NovelSourceManager = Injekt.get(),
    private val animeSourceManager: AnimeSourceManager = Injekt.get(),
) {
    /**
     * Decode a potentially-gzipped backup.
     */
    fun decode(uri: Uri): Backup {
        return context.contentResolver.openInputStream(uri)!!.use { inputStream ->
            val source = inputStream.source().buffer()

            val peeked = source.peek().apply {
                require(2)
            }
            val id1id2 = peeked.readShort()
            val backupString = when (id1id2.toInt()) {
                0x1f8b -> source.gzip().buffer() // 0x1f8b is gzip magic bytes
                MAGIC_JSON_SIGNATURE1, MAGIC_JSON_SIGNATURE2, MAGIC_JSON_SIGNATURE3 -> {
                    throw IOException(context.stringResource(MR.strings.invalid_backup_file_json))
                }
                else -> source
            }.use { it.readByteArray() }

            try {
                if (BackupDetector.isLegacyBackup(backupString)) {
                    parser.decodeFromByteArray(LegacyBackup.serializer(), backupString)
                        .toBackup()
                } else {
                    parser.decodeFromByteArray(Backup.serializer(), backupString)
                        .routeSharedMangaEntriesBySource(
                            mangaSourceClassifier = ::isMangaSource,
                            novelSourceClassifier = ::isNovelSource,
                            animeSourceClassifier = ::isAnimeSource,
                        )
                }
            } catch (e: SerializationException) {
                try {
                    parser.decodeFromByteArray(MihonBackup.serializer(), backupString)
                        .toTadamiBackup(mangaSourceManager, novelSourceManager, animeSourceManager)
                } catch (_: Exception) {
                    throw IOException(context.stringResource(MR.strings.invalid_backup_file_unknown))
                }
            }
        }
    }

    private fun isMangaSource(sourceId: Long): Boolean {
        return mangaSourceManager.get(sourceId) != null ||
            mangaSourceManager.getStubSources().any { it.id == sourceId }
    }

    private fun isNovelSource(sourceId: Long): Boolean {
        return novelSourceManager.get(sourceId) != null ||
            novelSourceManager.getStubSources().any { it.id == sourceId }
    }

    private fun isAnimeSource(sourceId: Long): Boolean {
        return animeSourceManager.get(sourceId) != null ||
            animeSourceManager.getStubSources().any { it.id == sourceId }
    }

    companion object {
        private const val MAGIC_JSON_SIGNATURE1 = 0x7b7d // `{}`
        private const val MAGIC_JSON_SIGNATURE2 = 0x7b22 // `{"`
        private const val MAGIC_JSON_SIGNATURE3 = 0x7b0a // `{\n`
    }
}
