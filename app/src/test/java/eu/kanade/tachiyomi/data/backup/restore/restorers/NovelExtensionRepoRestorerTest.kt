package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupExtensionRepos
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import mihon.domain.extensionrepo.novel.interactor.GetNovelExtensionRepo
import org.junit.jupiter.api.Test
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.novel.data.NovelDatabase

class NovelExtensionRepoRestorerTest {

    @Test
    fun `restore inserts novel repo when no conflicts`() {
        runTest {
            val handler = mockk<NovelDatabaseHandler>()
            val getRepos = mockk<GetNovelExtensionRepo>()
            coEvery { getRepos.getAll() } returns emptyList()
            coEvery { handler.await(any(), any<suspend NovelDatabase.() -> Unit>()) } returns Unit

            val restorer = NovelExtensionRepoRestorer(handler, getRepos)

            restorer(
            BackupExtensionRepos(
            baseUrl = "https://example.org",
            name = "Example",
            shortName = null,
            website = "https://example.org",
            signingKeyFingerprint = "ABC",
            ),
            )

            coVerify(exactly = 1) { handler.await(any(), any<suspend NovelDatabase.() -> Unit>()) }
        }
    }
    }
