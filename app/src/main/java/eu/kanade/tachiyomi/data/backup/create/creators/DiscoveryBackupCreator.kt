package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupDiscoveryHidden
import eu.kanade.tachiyomi.data.backup.models.BackupDiscoverySignal
import eu.kanade.tachiyomi.data.backup.models.BackupDiscoveryTag
import eu.kanade.tachiyomi.data.backup.models.BackupDiscoveryTasteResetEpoch
import eu.kanade.tachiyomi.data.backup.models.BackupDiscoveryTitleMemory
import eu.kanade.tachiyomi.data.backup.models.toBackupDiscoveryHidden
import eu.kanade.tachiyomi.data.backup.models.toBackupDiscoverySignal
import eu.kanade.tachiyomi.data.backup.models.toBackupDiscoveryTag
import eu.kanade.tachiyomi.data.backup.models.toBackupDiscoveryTitleMemory
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.repository.DiscoveryRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class DiscoveryBackupPayload(
    val hidden: List<BackupDiscoveryHidden> = emptyList(),
    val tags: List<BackupDiscoveryTag> = emptyList(),
    val signals: List<BackupDiscoverySignal> = emptyList(),
    val titleMemory: List<BackupDiscoveryTitleMemory> = emptyList(),
    val tasteResetEpochs: List<BackupDiscoveryTasteResetEpoch> = emptyList(),
)

/**
 * Собирает скрытые тайтлы, теговый блэклист, сигнал-лог вкусов и v2-состояние
 * «Для тебя» по всем медиатипам. Кэш подборок не собирается — он регенерируем (решение A-спека).
 */
class DiscoveryBackupCreator(
    private val repository: DiscoveryRepository = Injekt.get(),
) {
    suspend operator fun invoke(): DiscoveryBackupPayload {
        val hidden = mutableListOf<BackupDiscoveryHidden>()
        val tags = mutableListOf<BackupDiscoveryTag>()
        val signals = mutableListOf<BackupDiscoverySignal>()
        val titleMemory = mutableListOf<BackupDiscoveryTitleMemory>()
        DiscoveryMediaType.entries.forEach { media ->
            repository.getHiddenEntries(media).forEach { hidden += it.toBackupDiscoveryHidden(media.key) }
            repository.getBlacklistEntries(media).forEach { tags += it.toBackupDiscoveryTag(media.key) }
            repository.getSignals(media).forEach { signals += it.toBackupDiscoverySignal() }
            repository.getTitleMemory(media).forEach { titleMemory += it.toBackupDiscoveryTitleMemory() }
        }
        val epochs = repository.getTasteResetEpochs().map { (media, epoch) ->
            BackupDiscoveryTasteResetEpoch(mediaType = media.key, tasteResetAt = epoch)
        }
        return DiscoveryBackupPayload(
            hidden = hidden,
            tags = tags,
            signals = signals,
            titleMemory = titleMemory,
            tasteResetEpochs = epochs,
        )
    }
}
