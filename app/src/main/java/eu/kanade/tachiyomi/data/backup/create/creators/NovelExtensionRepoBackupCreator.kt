package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupExtensionRepos
import eu.kanade.tachiyomi.data.backup.models.backupExtensionReposMapper
import mihon.domain.extensionrepo.novel.interactor.GetNovelExtensionRepo
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelExtensionRepoBackupCreator(
    private val getNovelExtensionRepos: GetNovelExtensionRepo = Injekt.get(),
) {

    suspend operator fun invoke(): List<BackupExtensionRepos> {
        return getNovelExtensionRepos.getAll()
            .map(backupExtensionReposMapper)
    }
}
