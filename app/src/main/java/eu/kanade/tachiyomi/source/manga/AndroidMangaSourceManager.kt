package eu.kanade.tachiyomi.source.manga

import android.content.Context
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.manga.model.StubMangaSource
import tachiyomi.domain.source.manga.repository.MangaStubSourceRepository
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.source.local.entries.manga.LocalMangaSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.ConcurrentHashMap

class AndroidMangaSourceManager(
    private val context: Context,
    private val extensionManager: MangaExtensionManager,
    private val sourceRepository: MangaStubSourceRepository,
) : MangaSourceManager {

    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val downloadManager: MangaDownloadManager by injectLazy()

    // Source registration is best-effort: a failure inside one collector must be logged
    // instead of crashing the process via the default uncaught handler or leaking into
    // unrelated threads.
    private val scope = CoroutineScope(
        SupervisorJob() +
            Dispatchers.IO +
            CoroutineExceptionHandler { _, error ->
                logcat(LogPriority.ERROR, error) { "Manga source registration failed" }
            },
    )

    private val sourcesMapFlow = MutableStateFlow(ConcurrentHashMap<Long, MangaSource>())

    private val stubSourcesMap = ConcurrentHashMap<Long, StubMangaSource>()

    override val catalogueSources: Flow<List<CatalogueSource>> = sourcesMapFlow.map {
        it.values.filterIsInstance<CatalogueSource>()
    }

    init {
        scope.launch {
            extensionManager.installedExtensionsFlow
                .collectLatest { extensions ->
                    val mutableMap = ConcurrentHashMap<Long, MangaSource>(
                        mapOf(
                            LocalMangaSource.ID to LocalMangaSource(
                                context,
                                Injekt.get(),
                                Injekt.get(),
                            ),
                        ),
                    )
                    extensions.forEach { extension ->
                        extension.sources.forEach {
                            mutableMap[it.id] = it
                            registerStubSource(StubMangaSource.from(it))
                        }
                    }
                    sourcesMapFlow.value = mutableMap
                    _isInitialized.value = true
                }
        }

        scope.launch {
            sourceRepository.subscribeAllManga()
                .collectLatest { sources ->
                    stubSourcesMap.clear()
                    sources.forEach {
                        stubSourcesMap[it.id] = it
                    }
                }
        }
    }

    override fun get(sourceKey: Long): MangaSource? {
        return sourcesMapFlow.value[sourceKey]
    }

    override fun getOrStub(sourceKey: Long): MangaSource {
        return sourcesMapFlow.value[sourceKey]
            ?: stubSourcesMap[sourceKey]
            ?: run {
                val stub = StubMangaSource(id = sourceKey, lang = "", name = "")
                registerStubSourceAsync(sourceKey)
                stubSourcesMap[sourceKey] = stub
                stub
            }
    }

    override fun getOnlineSources() = sourcesMapFlow.value.values.filterIsInstance<HttpSource>()

    override fun getCatalogueSources() = sourcesMapFlow.value.values.filterIsInstance<CatalogueSource>()

    override fun getStubSources(): List<StubMangaSource> {
        val onlineSourceIds = getOnlineSources().map { it.id }
        return stubSourcesMap.values.filterNot { it.id in onlineSourceIds }
    }

    private fun registerStubSource(source: StubMangaSource) {
        scope.launch {
            val dbSource = sourceRepository.getStubMangaSource(source.id)
            if (dbSource == source) return@launch
            sourceRepository.upsertStubMangaSource(source.id, source.lang, source.name)
            if (dbSource != null) {
                downloadManager.renameSource(dbSource, source)
            }
        }
    }

    private fun registerStubSourceAsync(id: Long) {
        scope.launch {
            val dbSource = sourceRepository.getStubMangaSource(id)
            if (dbSource != null) {
                stubSourcesMap[id] = dbSource
                return@launch
            }
            extensionManager.getSourceData(id)?.let { extensionStub ->
                registerStubSource(extensionStub)
                stubSourcesMap[id] = extensionStub
            }
        }
    }
}
