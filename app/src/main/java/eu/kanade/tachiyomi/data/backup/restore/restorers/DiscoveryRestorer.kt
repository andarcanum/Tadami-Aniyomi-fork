package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupDiscoveryHidden
import eu.kanade.tachiyomi.data.backup.models.BackupDiscoverySignal
import eu.kanade.tachiyomi.data.backup.models.BackupDiscoveryTag
import eu.kanade.tachiyomi.data.backup.models.BackupDiscoveryTasteResetEpoch
import eu.kanade.tachiyomi.data.backup.models.BackupDiscoveryTitleMemory
import eu.kanade.tachiyomi.data.backup.models.toDomainEntry
import eu.kanade.tachiyomi.data.backup.models.toDomainSignal
import eu.kanade.tachiyomi.data.backup.models.toDomainTitleMemory
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.repository.DiscoveryRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Restore скрытых тайтлов, тегов, сигнал-лога вкусов и v2-состояния «Для тебя»:
 * один батч (транзакция) на медиатип, insertOrIgnore/upsert-merge — идемпотентно,
 * существующие локальные записи не затираются.
 */
class DiscoveryRestorer(
    private val repository: DiscoveryRepository = Injekt.get(),
) {
    suspend fun restoreDiscovery(
        hidden: List<BackupDiscoveryHidden>,
        tags: List<BackupDiscoveryTag>,
        signals: List<BackupDiscoverySignal> = emptyList(),
        titleMemory: List<BackupDiscoveryTitleMemory> = emptyList(),
        tasteResetEpochs: List<BackupDiscoveryTasteResetEpoch> = emptyList(),
    ) {
        if (tasteResetEpochs.isNotEmpty()) {
            val epochs = buildMap {
                tasteResetEpochs.forEach { entry ->
                    val media = DiscoveryMediaType.fromKey(entry.mediaType) ?: return@forEach
                    if (entry.tasteResetAt > 0L) {
                        val prev = this[media] ?: 0L
                        if (entry.tasteResetAt > prev) put(media, entry.tasteResetAt)
                    }
                }
            }
            if (epochs.isNotEmpty()) {
                repository.restoreTasteResetEpochs(epochs)
            }
        }
        hidden.groupBy { it.mediaType }.forEach { (mediaKey, items) ->
            val media = DiscoveryMediaType.fromKey(mediaKey) ?: return@forEach
            repository.restoreHiddenEntries(media, items.map { it.toDomainEntry() })
        }
        tags.groupBy { it.mediaType }.forEach { (mediaKey, items) ->
            val media = DiscoveryMediaType.fromKey(mediaKey) ?: return@forEach
            repository.restoreBlacklistEntries(media, items.map { it.toDomainEntry() })
        }
        if (titleMemory.isNotEmpty()) {
            val domainMemory = titleMemory.mapNotNull { it.toDomainTitleMemory() }
            if (domainMemory.isNotEmpty()) {
                repository.restoreTitleMemory(domainMemory)
            }
        }
        if (signals.isNotEmpty()) {
            // Битые ключи (мусорные media/signal) молча пропускаются на маппинге.
            val domainSignals = signals.mapNotNull { it.toDomainSignal() }
            if (domainSignals.isNotEmpty()) {
                repository.restoreSignals(domainSignals)
            }
        }
    }
}
