package tachiyomi.domain.release.interactor

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AppUpdateVersionComparatorTest {

    @Test
    fun `preview build detects newer commit count`() {
        AppUpdateVersionComparator.isUpdateAvailable(
            isPreview = true,
            installedCommitCount = 1000,
            installedVersionName = "",
            availableVersionTag = "r1001",
        ) shouldBe true
    }

    @Test
    fun `stable build treats matching version as already installed`() {
        AppUpdateVersionComparator.hasInstalledOrNewer(
            isPreview = false,
            installedCommitCount = 0,
            installedVersionName = "0.34",
            targetVersionTag = "v0.34",
        ) shouldBe true
    }
}
