package eu.kanade.tachiyomi.source.anime

import android.content.Context
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
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
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.domain.source.anime.repository.AnimeStubSourceRepository
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.source.local.entries.anime.LocalAnimeSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.ConcurrentHashMap

class AndroidAnimeSourceManager(
    private val context: Context,
    private val extensionManager: AnimeExtensionManager,
    private val sourceRepository: AnimeStubSourceRepository,
) : AnimeSourceManager {

    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val downloadManager: AnimeDownloadManager by injectLazy()

    // Source registration is best-effort: a failure inside one collector must be logged
    // instead of crashing the process via the default uncaught handler or leaking into
    // unrelated threads.
    private val scope = CoroutineScope(
        SupervisorJob() +
            Dispatchers.IO +
            CoroutineExceptionHandler { _, error ->
                logcat(LogPriority.ERROR, error) { "Anime source registration failed" }
            },
    )

    private val sourcesMapFlow = MutableStateFlow(ConcurrentHashMap<Long, AnimeSource>())

    private val stubSourcesMap = ConcurrentHashMap<Long, StubAnimeSource>()

    override val sources: Flow<List<AnimeSource>> = sourcesMapFlow.map {
        it.values.toList()
    }

    override val catalogueSources: Flow<List<AnimeCatalogueSource>> = sourcesMapFlow.map {
        it.values.filterIsInstance<AnimeCatalogueSource>()
    }

    init {
        scope.launch {
            extensionManager.installedExtensionsFlow
                .collectLatest { extensions ->
                    val mutableMap = ConcurrentHashMap<Long, AnimeSource>(
                        mapOf(
                            LocalAnimeSource.ID to LocalAnimeSource(
                                context,
                                Injekt.get(),
                                Injekt.get(),
                                Injekt.get(),
                                Injekt.get(),
                                Injekt.get(),
                            ),
                        ),
                    )
                    extensions.forEach { extension ->
                        extension.sources.forEach {
                            mutableMap[it.id] = it
                            registerStubSource(StubAnimeSource.from(it))
                        }
                    }
                    sourcesMapFlow.value = mutableMap
                    _isInitialized.value = true
                }
        }

        scope.launch {
            sourceRepository.subscribeAllAnime()
                .collectLatest { sources ->
                    stubSourcesMap.clear()
                    sources.forEach {
                        stubSourcesMap[it.id] = it
                    }
                }
        }
    }

    override fun get(sourceKey: Long): AnimeSource? {
        return sourcesMapFlow.value[sourceKey]
    }

    override fun getOrStub(sourceKey: Long): AnimeSource {
        return sourcesMapFlow.value[sourceKey]
            ?: stubSourcesMap[sourceKey]
            ?: run {
                val stub = StubAnimeSource(id = sourceKey, lang = "", name = "")
                registerStubSourceAsync(sourceKey)
                stubSourcesMap[sourceKey] = stub
                stub
            }
    }

    override fun getOnlineSources() = sourcesMapFlow.value.values.filterIsInstance<AnimeHttpSource>()

    override fun getCatalogueSources() = sourcesMapFlow.value.values.filterIsInstance<AnimeCatalogueSource>()

    override fun getStubSources(): List<StubAnimeSource> {
        val onlineSourceIds = getOnlineSources().map { it.id }
        return stubSourcesMap.values.filterNot { it.id in onlineSourceIds }
    }

    private fun registerStubSource(source: StubAnimeSource) {
        scope.launch {
            val dbSource = sourceRepository.getStubAnimeSource(source.id)
            if (dbSource == source) return@launch
            sourceRepository.upsertStubAnimeSource(source.id, source.lang, source.name)
            if (dbSource != null) {
                downloadManager.renameSource(dbSource, source)
            }
        }
    }

    private fun registerStubSourceAsync(id: Long) {
        scope.launch {
            val dbSource = sourceRepository.getStubAnimeSource(id)
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
