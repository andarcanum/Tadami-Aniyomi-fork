package eu.kanade.presentation.browse.novel

import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.ui.browse.novel.extension.NovelExtensionItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.extension.novel.model.NovelPlugin

class NovelExtensionRowActionTest {

    @Test
    fun `available idle row installs`() {
        assertEquals(
            NovelExtensionRowAction.Install,
            resolveNovelExtensionRowAction(item(available(), NovelExtensionItem.Status.Available)),
        )
    }

    @Test
    fun `installed regular update row updates`() {
        assertEquals(
            NovelExtensionRowAction.Update,
            resolveNovelExtensionRowAction(
                item(
                    installed(),
                    NovelExtensionItem.Status.UpdateAvailable,
                    hasUpdate = true,
                ),
            ),
        )
    }

    @Test
    fun `installed repo only update row opens reinstall`() {
        assertEquals(
            NovelExtensionRowAction.Reinstall,
            resolveNovelExtensionRowAction(
                item(
                    installed(),
                    NovelExtensionItem.Status.UpdateAvailable,
                    hasRepoUpdate = true,
                ),
            ),
        )
    }

    @Test
    fun `repo only reinstall error retries reinstall`() {
        assertEquals(
            NovelExtensionRowAction.Reinstall,
            resolveNovelExtensionRowAction(
                item(
                    installed(),
                    NovelExtensionItem.Status.UpdateAvailable,
                    installStep = InstallStep.Error,
                    hasRepoUpdate = true,
                ),
            ),
        )
    }

    @Test
    fun `regular update error retries update`() {
        assertEquals(
            NovelExtensionRowAction.Update,
            resolveNovelExtensionRowAction(
                item(
                    installed(),
                    NovelExtensionItem.Status.UpdateAvailable,
                    installStep = InstallStep.Error,
                    hasUpdate = true,
                ),
            ),
        )
    }

    @Test
    fun `installed idle row opens details`() {
        assertEquals(
            NovelExtensionRowAction.Open,
            resolveNovelExtensionRowAction(item(installed(), NovelExtensionItem.Status.Installed)),
        )
    }

    @Test
    fun `active install row does nothing`() {
        assertEquals(
            NovelExtensionRowAction.None,
            resolveNovelExtensionRowAction(
                item(
                    available(),
                    NovelExtensionItem.Status.Available,
                    installStep = InstallStep.Installing,
                ),
            ),
        )
    }

    private fun item(
        plugin: NovelPlugin,
        status: NovelExtensionItem.Status,
        installStep: InstallStep = InstallStep.Idle,
        hasUpdate: Boolean = false,
        hasRepoUpdate: Boolean = false,
    ) = NovelExtensionItem(
        plugin = plugin,
        status = status,
        installStep = installStep,
        hasSettings = false,
        hasUpdate = hasUpdate,
        hasRepoUpdate = hasRepoUpdate,
    )

    private fun available() = NovelPlugin.Available(
        id = "plugin.id",
        name = "Plugin",
        site = "https://example.org",
        lang = "en",
        versionCode = 1,
        versionName = "1",
        url = "https://example.org/plugin.js",
        iconUrl = null,
        customJs = null,
        customCss = null,
        hasSettings = false,
        sha256 = "sha",
        repoUrl = "https://repo.example/index.json",
    )

    private fun installed() = NovelPlugin.Installed(
        id = "plugin.id",
        name = "Plugin",
        site = "https://example.org",
        lang = "en",
        versionCode = 1,
        versionName = "1",
        url = "https://example.org/plugin.js",
        iconUrl = null,
        customJs = null,
        customCss = null,
        hasSettings = false,
        sha256 = "sha",
        repoUrl = "https://repo.example/index.json",
    )
}
