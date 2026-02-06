package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupExtensionRepos
import mihon.domain.extensionrepo.novel.interactor.GetNovelExtensionRepo
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelExtensionRepoRestorer(
    private val novelHandler: NovelDatabaseHandler = Injekt.get(),
    private val getExtensionRepos: GetNovelExtensionRepo = Injekt.get(),
) {

    suspend operator fun invoke(
        backupRepo: BackupExtensionRepos,
    ) {
        val dbRepos = getExtensionRepos.getAll()
        val existingReposBySHA = dbRepos.associateBy { it.signingKeyFingerprint }
        val existingReposByUrl = dbRepos.associateBy { it.baseUrl }
        val urlExists = existingReposByUrl[backupRepo.baseUrl]
        val shaExists = existingReposBySHA[backupRepo.signingKeyFingerprint]
        if (urlExists != null && urlExists.signingKeyFingerprint != backupRepo.signingKeyFingerprint) {
            error("Already Exists with different signing key fingerprint")
        } else if (shaExists != null) {
            error("${shaExists.name} has the same signing key fingerprint")
        } else {
            novelHandler.await {
                novel_extension_reposQueries.insert(
                    backupRepo.baseUrl,
                    backupRepo.name,
                    backupRepo.shortName,
                    backupRepo.website,
                    backupRepo.signingKeyFingerprint,
                )
            }
        }
    }
}
