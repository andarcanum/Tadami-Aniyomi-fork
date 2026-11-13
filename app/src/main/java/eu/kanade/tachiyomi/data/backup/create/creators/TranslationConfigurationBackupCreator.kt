package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.BackupTranslationConfiguration
import eu.kanade.tachiyomi.data.backup.models.BackupTranslationNovelAssignment
import eu.kanade.tachiyomi.data.translation.profile.TranslationProfileServices
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.translation.profile.ProfileAssignmentScope
import tachiyomi.domain.translation.profile.TranslationProfileRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TranslationConfigurationBackupCreator(
    private val profiles: TranslationProfileRepository? = null,
    private val handler: NovelDatabaseHandler? = null,
) {
    suspend operator fun invoke(options: BackupOptions): BackupTranslationConfiguration? {
        if (!options.appSettings || options.sisterAppCompatible) return null
        val catalog = (profiles ?: TranslationProfileServices.repository).getCatalog()
        val references = catalog.assignments.filter { it.scope == ProfileAssignmentScope.NOVEL }.map { assignment ->
            val novel = (handler ?: Injekt.get<NovelDatabaseHandler>()).awaitOneOrNull {
                it.novelsQueries.getNovelById(assignment.scopeId)
            }
                ?: throw IllegalStateException("Cannot back up a translation assignment for a missing novel")
            BackupTranslationNovelAssignment(novel.source, novel.url, assignment.profileId)
        }
        return BackupTranslationConfiguration.fromCatalog(catalog, references)
    }
}
