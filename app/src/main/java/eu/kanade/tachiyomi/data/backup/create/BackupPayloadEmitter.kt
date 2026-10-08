package eu.kanade.tachiyomi.data.backup.create

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupAchievement
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupAnimeSeries
import eu.kanade.tachiyomi.data.backup.models.BackupAnimeSource
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupCustomButtons
import eu.kanade.tachiyomi.data.backup.models.BackupDayActivity
import eu.kanade.tachiyomi.data.backup.models.BackupDiscoveryHidden
import eu.kanade.tachiyomi.data.backup.models.BackupDiscoverySignal
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
import eu.kanade.tachiyomi.data.backup.models.MihonBackupManga
import eu.kanade.tachiyomi.data.backup.models.TadamiMediaType
import eu.kanade.tachiyomi.data.backup.models.TadamiMediaTypeHint
import eu.kanade.tachiyomi.data.backup.models.TadamiSisterManifest
import eu.kanade.tachiyomi.data.backup.models.toBackupManga
import eu.kanade.tachiyomi.data.backup.models.toMihonBackupManga
import kotlinx.serialization.KSerializer
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.OutputStream

/**
 * Streams the top level protobuf fields of a backup into an [OutputStream] one entry at a time,
 * so the uncompressed payload never exists as a single array in RAM (see [BackupWriter.writeStreamed]).
 *
 * Field numbers AND their order mirror the declaration order of
 * [eu.kanade.tachiyomi.data.backup.models.Backup] and
 * [eu.kanade.tachiyomi.data.backup.models.MihonBackup]: kotlinx encodes declared fields in order,
 * so the streamed bytes are identical to [ProtoBuf.encodeToByteArray] for the same object. The
 * byte-equivalence test pins that property; a schema edit that forgets this file fails it loudly.
 */
internal object BackupPayloadEmitter {

    private const val WIRE_VARINT = 0L
    private const val WIRE_LENGTH_DELIMITED = 2L

    fun emitNative(backup: Backup, proto: ProtoBuf, out: OutputStream) {
        with(out) {
            messageList(1, backup.backupManga, BackupManga.serializer(), proto)
            messageList(2, backup.backupCategories, BackupCategory.serializer(), proto)
            messageList(101, backup.backupSources, BackupSource.serializer(), proto)
            messageList(104, backup.backupPreferences, BackupPreference.serializer(), proto)
            messageList(105, backup.backupSourcePreferences, BackupSourcePreferences.serializer(), proto)
            messageList(106, backup.backupMangaExtensionRepo, BackupExtensionRepos.serializer(), proto)
            boolField(500, backup.isLegacy, default = true)
            messageList(501, backup.backupAnime, BackupAnime.serializer(), proto)
            messageList(502, backup.backupAnimeCategories, BackupCategory.serializer(), proto)
            messageList(503, backup.backupAnimeSources, BackupAnimeSource.serializer(), proto)
            messageList(504, backup.backupExtensions, BackupExtension.serializer(), proto)
            messageList(505, backup.backupAnimeExtensionRepo, BackupExtensionRepos.serializer(), proto)
            messageList(506, backup.backupCustomButton, BackupCustomButtons.serializer(), proto)
            messageList(507, backup.backupNovelExtensionRepo, BackupExtensionRepos.serializer(), proto)
            messageList(650, backup.backupAnimeExtensionStore, BackupExtensionStore.serializer(), proto)
            messageList(651, backup.backupMangaExtensionStore, BackupExtensionStore.serializer(), proto)
            messageList(652, backup.backupNovelExtensionStore, BackupExtensionStore.serializer(), proto)
            messageList(508, backup.backupNovel, BackupNovel.serializer(), proto)
            messageList(509, backup.backupNovelCategories, BackupCategory.serializer(), proto)
            messageList(510, backup.backupNovelSources, BackupSource.serializer(), proto)
            messageList(600, backup.backupAchievements, BackupAchievement.serializer(), proto)
            backup.backupUserProfile?.let {
                messageField(601, proto.encodeToByteArray(BackupUserProfile.serializer(), it))
            }
            messageList(602, backup.backupActivityLog, BackupDayActivity.serializer(), proto)
            backup.backupStats?.let {
                messageField(603, proto.encodeToByteArray(BackupStats.serializer(), it))
            }
            messageList(620, backup.backupMangaSeries, BackupMangaSeries.serializer(), proto)
            messageList(621, backup.backupNovelSeries, BackupNovelSeries.serializer(), proto)
            messageList(622, backup.backupFeeds, BackupFeed.serializer(), proto)
            messageList(623, backup.backupReelsFavorites, BackupReelsFavorite.serializer(), proto)
            messageList(626, backup.backupReelsFollows, BackupReelsFollow.serializer(), proto)
            messageList(624, backup.backupDiscoveryHidden, BackupDiscoveryHidden.serializer(), proto)
            messageList(625, backup.backupDiscoveryBlacklistTags, BackupDiscoveryTag.serializer(), proto)
            messageList(627, backup.backupDiscoverySignals, BackupDiscoverySignal.serializer(), proto)
            messageList(628, backup.backupAnimeSeries, BackupAnimeSeries.serializer(), proto)
        }
    }

    fun emitSister(backup: Backup, proto: ProtoBuf, out: OutputStream) {
        val hints = backup.backupManga.map {
            TadamiMediaTypeHint(sourceId = it.source, url = it.url, mediaType = TadamiMediaType.MANGA)
        } + backup.backupNovel.map {
            TadamiMediaTypeHint(sourceId = it.source, url = it.url, mediaType = TadamiMediaType.NOVEL)
        }
        with(out) {
            // Mihon has a single library section: manga and flattened novels share field 1, in
            // exactly the order the combined list declares.
            backup.backupManga.forEach {
                messageField(1, proto.encodeToByteArray(MihonBackupManga.serializer(), it.toMihonBackupManga()))
            }
            backup.backupNovel.forEach {
                messageField(
                    1,
                    proto.encodeToByteArray(MihonBackupManga.serializer(), it.toBackupManga().toMihonBackupManga()),
                )
            }
            messageList(2, backup.backupCategories, BackupCategory.serializer(), proto)
            messageList(101, backup.backupSources, BackupSource.serializer(), proto)
            messageList(104, backup.backupPreferences, BackupPreference.serializer(), proto)
            messageList(105, backup.backupSourcePreferences, BackupSourcePreferences.serializer(), proto)
            messageList(106, backup.backupMangaExtensionRepo, BackupExtensionRepos.serializer(), proto)
            messageList(501, backup.backupAnime, BackupAnime.serializer(), proto)
            messageList(502, backup.backupAnimeCategories, BackupCategory.serializer(), proto)
            messageList(503, backup.backupAnimeSources, BackupAnimeSource.serializer(), proto)
            messageField(
                TadamiSisterManifest.PROTO_FIELD,
                proto.encodeToByteArray(
                    TadamiSisterManifest.serializer(),
                    TadamiSisterManifest(entries = hints),
                ),
            )
        }
    }

    private fun <T> OutputStream.messageList(
        number: Int,
        items: List<T>,
        serializer: KSerializer<T>,
        proto: ProtoBuf,
    ) {
        for (item in items) {
            messageField(number, proto.encodeToByteArray(serializer, item))
        }
    }

    private fun OutputStream.messageField(number: Int, payload: ByteArray) {
        writeVarint(this, (number.toLong() shl 3) or WIRE_LENGTH_DELIMITED)
        writeVarint(this, payload.size.toLong())
        write(payload)
    }

    /** kotlinx omits a boolean that equals its declared default; mirror that exactly. */
    private fun OutputStream.boolField(number: Int, value: Boolean, default: Boolean) {
        if (value == default) return
        writeVarint(this, (number.toLong() shl 3) or WIRE_VARINT)
        writeVarint(this, if (value) 1L else 0L)
    }

    private fun writeVarint(out: OutputStream, value: Long) {
        var remaining = value
        while (true) {
            val bits = (remaining and 0x7F).toInt()
            remaining = remaining ushr 7
            if (remaining == 0L) {
                out.write(bits)
                return
            }
            out.write(bits or 0x80)
        }
    }
}
