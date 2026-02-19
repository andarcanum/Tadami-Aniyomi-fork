package eu.kanade.tachiyomi.extension.novel.api

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class NovelExtensionUpdateRunnerTest {

    @Test
    fun `run triggers novel extension update check`() {
        runTest {
            val api = mockk<NovelExtensionApi>()
            coEvery { api.checkForUpdates() } returns emptyList()

            val runner = NovelExtensionUpdateRunner(api)

            runner.run()

            coVerify(exactly = 1) { api.checkForUpdates() }
        }
    }
}
