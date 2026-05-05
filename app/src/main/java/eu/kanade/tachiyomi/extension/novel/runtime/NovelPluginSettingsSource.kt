package eu.kanade.tachiyomi.extension.novel.runtime

import eu.kanade.tachiyomi.novelsource.ConfigurableNovelSource
import eu.kanade.tachiyomi.novelsource.NovelSource

interface NovelPluginSettingsSource {
    fun hasPluginSettings(discoverRuntime: Boolean = false): Boolean
}

internal fun NovelSource.hasVisiblePluginSettings(): Boolean {
    return when (this) {
        is NovelPluginSettingsSource -> hasPluginSettings(discoverRuntime = false)
        is ConfigurableNovelSource -> true
        else -> false
    }
}

internal fun NovelSource.hasVisiblePluginSettingsByDiscovery(): Boolean {
    return when (this) {
        is NovelPluginSettingsSource -> hasPluginSettings(discoverRuntime = true)
        is ConfigurableNovelSource -> true
        else -> false
    }
}
