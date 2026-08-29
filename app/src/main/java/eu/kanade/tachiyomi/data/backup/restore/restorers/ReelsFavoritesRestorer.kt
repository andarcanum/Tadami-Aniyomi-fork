package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupReelsFavorite
import eu.kanade.tachiyomi.data.backup.models.toReelsFavorite
import tachiyomi.domain.reels.anime.repository.ReelsFavoriteRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ReelsFavoritesRestorer(
    private val repository: ReelsFavoriteRepository = Injekt.get(),
) {
    // INSERT OR REPLACE keeps restore idempotent and never drops existing likes. One batched
    // write: the repository persists the whole list in a single transaction, so a large
    // backup does not pay a transaction per row.
    suspend fun restoreReelsFavorites(backup: List<BackupReelsFavorite>) {
        if (backup.isEmpty()) return
        repository.insertAll(backup.map { it.toReelsFavorite() })
    }
}
