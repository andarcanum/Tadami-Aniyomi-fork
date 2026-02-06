package tachiyomi.data.extension.novel

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.extension.novel.model.NovelPlugin
import tachiyomi.domain.extension.novel.repository.NovelPluginRepository

class NovelPluginRepositoryImpl(
    private val handler: NovelDatabaseHandler,
) : NovelPluginRepository {
    override fun subscribeAll(): Flow<List<NovelPlugin.Installed>> {
        return handler.subscribeToList { novel_pluginsQueries.findAll(::mapPlugin) }
    }

    override suspend fun getAll(): List<NovelPlugin.Installed> {
        return handler.awaitList { novel_pluginsQueries.findAll(::mapPlugin) }
    }

    override suspend fun getById(id: String): NovelPlugin.Installed? {
        return handler.awaitOneOrNull { novel_pluginsQueries.findOne(id, ::mapPlugin) }
    }

    override suspend fun upsert(plugin: NovelPlugin.Installed) {
        handler.await {
            novel_pluginsQueries.upsert(
                id = plugin.id,
                name = plugin.name,
                site = plugin.site,
                lang = plugin.lang,
                version = plugin.version.toLong(),
                url = plugin.url,
                icon_url = plugin.iconUrl,
                custom_js = plugin.customJs,
                custom_css = plugin.customCss,
                has_settings = if (plugin.hasSettings) 1L else 0L,
                sha256 = plugin.sha256,
                repo_url = plugin.repoUrl,
            )
        }
    }

    override suspend fun delete(id: String) {
        handler.await { novel_pluginsQueries.delete(id) }
    }

    private fun mapPlugin(
        id: String,
        name: String,
        site: String,
        lang: String,
        version: Long,
        url: String,
        icon_url: String?,
        custom_js: String?,
        custom_css: String?,
        has_settings: Long,
        sha256: String,
        repo_url: String,
    ): NovelPlugin.Installed {
        return NovelPlugin.Installed(
            id = id,
            name = name,
            site = site,
            lang = lang,
            version = version.toInt(),
            url = url,
            iconUrl = icon_url,
            customJs = custom_js,
            customCss = custom_css,
            hasSettings = has_settings != 0L,
            sha256 = sha256,
            repoUrl = repo_url,
        )
    }
}
