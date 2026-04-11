package eu.kanade.tachiyomi.extension.novel.runtime

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.novelsource.ConfigurableNovelSource
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.source.novel.NovelImageRequestSource
import eu.kanade.tachiyomi.source.novel.NovelPluginImageSource
import eu.kanade.tachiyomi.source.novel.NovelSiteSource
import eu.kanade.tachiyomi.source.novel.NovelWebUrlSource

internal class NovelConfigurableJsSource(
    private val delegate: NovelJsSource,
) : NovelCatalogueSource by delegate,
    NovelSiteSource by delegate,
    NovelWebUrlSource by delegate,
    NovelPluginImageSource by delegate,
    NovelImageRequestSource by delegate,
    NovelPluginCapabilitySource by delegate,
    NovelPluginSettingsSource by delegate,
    NovelPluginIdentitySource by delegate,
    ConfigurableNovelSource {

    override val id: Long
        get() = delegate.id

    override val name: String
        get() = delegate.name

    override val lang: String
        get() = delegate.lang

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        delegate.setupPreferenceScreen(screen)
    }
}
