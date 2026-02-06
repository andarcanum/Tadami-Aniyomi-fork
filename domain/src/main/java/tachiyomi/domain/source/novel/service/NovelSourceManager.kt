package tachiyomi.domain.source.novel.service

import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.novelsource.online.NovelHttpSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import tachiyomi.domain.source.novel.model.StubNovelSource

interface NovelSourceManager {

    val isInitialized: StateFlow<Boolean>

    val catalogueSources: Flow<List<NovelCatalogueSource>>

    fun get(sourceKey: Long): NovelSource?

    fun getOrStub(sourceKey: Long): NovelSource

    fun getOnlineSources(): List<NovelHttpSource>

    fun getCatalogueSources(): List<NovelCatalogueSource>

    fun getStubSources(): List<StubNovelSource>
}
