package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupNovel
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.novelsource.NovelSource
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelSourcesBackupCreator(
    private val novelSourceManager: NovelSourceManager = Injekt.get(),
) {

    operator fun invoke(novels: List<BackupNovel>): List<BackupSource> {
        return novels
            .asSequence()
            .map(BackupNovel::source)
            .distinct()
            .map(novelSourceManager::getOrStub)
            .map { it.toBackupSource() }
            .toList()
    }
}

private fun NovelSource.toBackupSource() =
    BackupSource(
        name = this.name,
        sourceId = this.id,
    )
