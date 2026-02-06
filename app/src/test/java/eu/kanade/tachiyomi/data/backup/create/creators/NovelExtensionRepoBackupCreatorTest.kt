package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupExtensionRepos
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionrepo.novel.interactor.GetNovelExtensionRepo
import org.junit.jupiter.api.Test

class NovelExtensionRepoBackupCreatorTest {

    @Test
    fun `invoke maps novel extension repos`() {
        runTest {
            val repo = ExtensionRepo(
            baseUrl = "https://example.org",
            name = "Example",
            shortName = "ex",
            website = "https://example.org",
            signingKeyFingerprint = "ABC",
            )
            val getRepos = mockk<GetNovelExtensionRepo>()
            coEvery { getRepos.getAll() } returns listOf(repo)

            val creator = NovelExtensionRepoBackupCreator(getRepos)

            val result = creator()
            result.size shouldBe 1
            result.single().apply {
            baseUrl shouldBe "https://example.org"
            name shouldBe "Example"
            shortName shouldBe "ex"
            website shouldBe "https://example.org"
            signingKeyFingerprint shouldBe "ABC"
            }
        }
    }
    }
