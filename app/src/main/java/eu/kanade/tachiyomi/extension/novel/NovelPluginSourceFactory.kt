package eu.kanade.tachiyomi.extension.novel

import eu.kanade.tachiyomi.novelsource.NovelSource
import tachiyomi.domain.extension.novel.model.NovelPlugin

interface NovelPluginSourceFactory {
    fun create(plugin: NovelPlugin.Installed): NovelSource?

    fun clearRuntimeCaches() = Unit
}
