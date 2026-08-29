package eu.kanade.tachiyomi.source.novel

import android.content.Context
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.novelsource.online.NovelHttpSource
import kotlinx.coroutines.CoroutineDispatcher
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
import tachiyomi.domain.source.novel.model.StubNovelSource
import tachiyomi.domain.source.novel.repository.NovelStubSourceRepository
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.source.local.entries.novel.LocalNovelSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap

class AndroidNovelSourceManager(
    private val context: Context,
    private val extensionManager: NovelExtensionManager,
    private val sourceRepository: NovelStubSourceRepository,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val omniSourceFactory: () -> OmniSource = { OmniSource() },
) : NovelSourceManager {

    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    // Source registration is best-effort: a failure inside one collector must be logged
    // instead of crashing the process via the default uncaught handler or leaking into
    // unrelated threads.
    private val scope = CoroutineScope(
        SupervisorJob() +
            dispatcher +
            CoroutineExceptionHandler { _, error ->
                logcat(LogPriority.ERROR, error) { "Novel source registration failed" }
            },
    )

    private val sourcesMapFlow = MutableStateFlow(ConcurrentHashMap<Long, NovelSource>())

    private val stubSourcesMap = ConcurrentHashMap<Long, StubNovelSource>()

    override val catalogueSources: Flow<List<NovelCatalogueSource>> = sourcesMapFlow.map {
        it.values.filterIsInstance<NovelCatalogueSource>()
    }

    init {
        scope.launch {
            extensionManager.installedSourcesFlow
                .collectLatest { sources ->
                    val mutableMap = ConcurrentHashMap<Long, NovelSource>()
                    sources.forEach {
                        mutableMap[it.id] = it
                        registerStubSource(StubNovelSource.from(it))
                    }
                    // Add built-in local source
                    val localNovelSource = LocalNovelSource(
                        context,
                        Injekt.get(),
                        Injekt.get(),
                    )
                    mutableMap[localNovelSource.id] = localNovelSource
                    // Add built-in OmniResolver source
                    runCatching {
                        omniSourceFactory()
                    }.onSuccess { omniSource ->
                        mutableMap[omniSource.id] = omniSource
                    }
                    sourcesMapFlow.value = mutableMap
                    _isInitialized.value = true
                }
        }

        scope.launch {
            sourceRepository.subscribeAllNovel()
                .collectLatest { sources ->
                    stubSourcesMap.clear()
                    sources.forEach {
                        stubSourcesMap[it.id] = it
                    }
                }
        }
    }

    override fun get(sourceKey: Long): NovelSource? {
        return sourcesMapFlow.value[sourceKey]
    }

    override fun getOrStub(sourceKey: Long): NovelSource {
        return sourcesMapFlow.value[sourceKey]
            ?: stubSourcesMap[sourceKey]
            ?: run {
                val stub = StubNovelSource(id = sourceKey, lang = "", name = "")
                registerStubSourceAsync(sourceKey)
                stubSourcesMap[sourceKey] = stub
                stub
            }
    }

    override fun getOnlineSources() = sourcesMapFlow.value.values.filterIsInstance<NovelHttpSource>()

    override fun getCatalogueSources() = sourcesMapFlow.value.values.filterIsInstance<NovelCatalogueSource>()

    override fun getStubSources(): List<StubNovelSource> {
        val onlineSourceIds = getOnlineSources().map { it.id }
        return stubSourcesMap.values.filterNot {
            it.id in onlineSourceIds ||
                it.id == LocalNovelSource.ID
        }
    }

    private fun registerStubSource(source: StubNovelSource) {
        scope.launch {
            val dbSource = sourceRepository.getStubNovelSource(source.id)
            if (dbSource == source) return@launch
            sourceRepository.upsertStubNovelSource(source.id, source.lang, source.name)
        }
    }

    private fun registerStubSourceAsync(id: Long) {
        scope.launch {
            val dbSource = sourceRepository.getStubNovelSource(id)
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
