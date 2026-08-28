package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupReelsFavorite
import eu.kanade.tachiyomi.data.backup.models.toBackupReelsFavorite
import tachiyomi.domain.reels.anime.repository.ReelsFavoriteRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ReelsFavoritesBackupCreator(
    private val repository: ReelsFavoriteRepository = Injekt.get(),
) {
    suspend operator fun invoke(): List<BackupReelsFavorite> {
        return repository.getAll().map { it.toBackupReelsFavorite() }
    }
}
