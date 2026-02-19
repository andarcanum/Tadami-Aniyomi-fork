package tachiyomi.data.extension.novel

import eu.kanade.tachiyomi.util.lang.Hash
import logcat.LogPriority
import logcat.logcat
import tachiyomi.domain.extension.novel.model.NovelPlugin
import tachiyomi.domain.extension.novel.repository.NovelPluginRepository

class NovelPluginInstaller(
    private val downloader: NovelPluginDownloader,
    private val repository: NovelPluginRepository,
    private val storage: NovelPluginStorage,
) : NovelPluginInstallerFacade {
    override suspend fun install(plugin: NovelPlugin.Available): NovelPlugin.Installed {
        logcat(LogPriority.INFO) { "Novel plugin install start id=${plugin.id} url=${plugin.url}" }
        val scriptBytes = downloader.download(plugin.url)
        logcat(LogPriority.INFO) {
            "Novel plugin download ok id=${plugin.id} bytes=${scriptBytes.size}"
        }
        if (plugin.sha256.isNotBlank()) {
            val checksum = Hash.sha256(scriptBytes)
            if (!checksum.equals(plugin.sha256, ignoreCase = true)) {
                throw NovelPluginInstallException.ChecksumMismatch(plugin.id, plugin.sha256, checksum)
            }
        }

        val customJsBytes = plugin.customJs?.let { downloader.download(it) }
        val customCssBytes = plugin.customCss?.let { downloader.download(it) }

        storage.writePluginFiles(
            pluginId = plugin.id,
            script = scriptBytes,
            customJs = customJsBytes,
            customCss = customCssBytes,
        )

        val installed = plugin.toInstalled()
        repository.upsert(installed)
        return installed
    }

    override suspend fun uninstall(pluginId: String) {
        storage.deletePluginFiles(pluginId)
        repository.delete(pluginId)
    }
}

interface NovelPluginInstallerFacade {
    suspend fun install(plugin: NovelPlugin.Available): NovelPlugin.Installed

    suspend fun uninstall(pluginId: String)
}

interface NovelPluginDownloader {
    suspend fun download(url: String): ByteArray
}

sealed class NovelPluginInstallException(message: String) : RuntimeException(message) {
    class ChecksumMismatch(
        val pluginId: String,
        val expected: String,
        val actual: String,
    ) : NovelPluginInstallException(
        "Checksum mismatch for $pluginId. Expected $expected but got $actual.",
    )
}

fun NovelPlugin.Available.toInstalled(): NovelPlugin.Installed {
    return NovelPlugin.Installed(
        id = id,
        name = name,
        site = site,
        lang = lang,
        version = version,
        url = url,
        iconUrl = iconUrl,
        customJs = customJs,
        customCss = customCss,
        hasSettings = hasSettings,
        sha256 = sha256,
        repoUrl = repoUrl,
    )
}
