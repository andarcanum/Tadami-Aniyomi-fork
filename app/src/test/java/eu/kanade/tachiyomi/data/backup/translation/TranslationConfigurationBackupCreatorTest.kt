package eu.kanade.tachiyomi.data.backup.translation

import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.create.creators.TranslationConfigurationBackupCreator
import eu.kanade.tachiyomi.data.backup.models.BackupTranslationNovelAssignment
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.translation.profile.ProfileAssignmentScope
import tachiyomi.domain.translation.profile.TranslationProfileRepository

class TranslationConfigurationBackupCreatorTest {
    @Test
    fun `app settings off and sister export do not read the translation catalog`() = runBlocking<Unit> {
        val profiles = mockk<TranslationProfileRepository>()
        val handler = mockk<NovelDatabaseHandler>()
        val creator = TranslationConfigurationBackupCreator(profiles, handler)
        creator(BackupOptions(appSettings = false)) shouldBe null
        TranslationConfigurationBackupCreator()(BackupOptions(appSettings = false)) shouldBe null
        creator(BackupOptions(sisterAppCompatible = true)) shouldBe null
        coVerify(exactly = 0) { profiles.getCatalog() }
    }

    @Test
    fun `global source aliases and references survive omitted novel library and source preferences`() =
        runBlocking<Unit> {
            TranslationBackupFixture().use { fixture ->
                fixture.profiles.importConfiguration(translationCatalog())
                val creator = TranslationConfigurationBackupCreator(fixture.profiles, fixture.handler)
                val payload = creator(
                    BackupOptions(libraryEntries = false, backupNovel = false, sourceSettings = false),
                )!!
                payload.decode().toCatalog() shouldBe translationCatalog()
                payload.decode().assignments.map { it.scope }.toSet() shouldBe
                    setOf(ProfileAssignmentScope.GLOBAL, ProfileAssignmentScope.SOURCE)
            }
        }

    @Test
    fun `creator exports stable novel references and private option never adds vault values`() = runBlocking<Unit> {
        TranslationBackupFixture().use { fixture ->
            fixture.insertNovel(123)
            fixture.profiles.importConfiguration(translationCatalog(123))
            val creator = TranslationConfigurationBackupCreator(fixture.profiles, fixture.handler)
            val public = creator(BackupOptions(libraryEntries = false, backupNovel = false))!!
            val private = creator(BackupOptions(privateSettings = true, libraryEntries = false, backupNovel = false))!!
            public shouldBe private
            public.decode().novelAssignments shouldBe
                listOf(BackupTranslationNovelAssignment(SOURCE, NOVEL_URL, PROFILE))
            public.decode().assignments.none { it.scope == ProfileAssignmentScope.NOVEL } shouldBe true
            public.configurationJson.contains(VAULT_VALUE) shouldBe false
        }
    }

    @Test
    fun `orphan numeric novel assignment fails export rather than inventing or dropping identity`() =
        runBlocking<Unit> {
            TranslationBackupFixture().use { fixture ->
                fixture.profiles.importConfiguration(translationCatalog(123))
                val creator = TranslationConfigurationBackupCreator(fixture.profiles, fixture.handler)
                var failure: IllegalStateException? = null
                try {
                    creator(BackupOptions())
                } catch (error: IllegalStateException) {
                    failure = error
                }
                (failure != null) shouldBe true
                failure!!.message!!.contains("123") shouldBe false
                failure!!.cause shouldBe null
            }
        }
}
