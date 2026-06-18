package eu.kanade.tachiyomi.extension.novel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.novel.api.NovelPluginApiFacade
import eu.kanade.tachiyomi.extension.novel.kotlin.KotlinNovelExtensionInstaller
import eu.kanade.tachiyomi.extension.novel.kotlin.KotlinNovelExtensionLoadResult
import eu.kanade.tachiyomi.extension.novel.kotlin.KotlinNovelExtensionLoader
import eu.kanade.tachiyomi.extension.novel.runtime.NovelPluginCapabilities
import eu.kanade.tachiyomi.extension.novel.runtime.NovelPluginCapabilitySource
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.util.system.isPackageInstalled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.data.extension.novel.NovelPluginInstallerFacade
import tachiyomi.domain.extension.novel.model.NovelPlugin
import tachiyomi.domain.extension.novel.repository.NovelPluginRepository
import tachiyomi.domain.source.novel.model.StubNovelSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

internal fun removeNovelInstalledRepoEntries(
    entries: Set<String>,
    key: String,
    alternateKey: String? = null,
): Set<String> {
    return entries.filterNot { entry ->
        val entryKey = entry.substringBefore('|')
        entryKey == key || entryKey == alternateKey
    }.toSet()
}

class DefaultNovelExtensionManager(
    private val context: Context?,
    private val repository: NovelPluginRepository,
    private val api: NovelPluginApiFacade,
    private val installer: NovelPluginInstallerFacade,
    private val sourceFactory: NovelPluginSourceFactory,
    private val kotlinInstaller: KotlinNovelExtensionInstaller?,
) : NovelExtensionManager {
    constructor(
        repository: NovelPluginRepository,
        api: NovelPluginApiFacade,
        installer: NovelPluginInstallerFacade,
        sourceFactory: NovelPluginSourceFactory,
    ) : this(null, repository, api, installer, sourceFactory, null)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val installedSources = MutableStateFlow<List<NovelSource>>(emptyList())
    private val installedPlugins = MutableStateFlow<List<NovelPlugin.Installed>>(emptyList())
    private val availablePlugins = MutableStateFlow<List<NovelPlugin.Available>>(emptyList())
    private val updates = MutableStateFlow<List<NovelPlugin.Installed>>(emptyList())
    private val installedPluginIconUrls = ConcurrentHashMap<Long, String?>()
    private val pendingInstallRepos = ConcurrentHashMap<String, InstalledRepo>()
    private val sourcePreferences by lazy(LazyThreadSafetyMode.NONE) {
        runCatching { Injekt.get<SourcePreferences>() }.getOrNull()
    }

    @Volatile
    private var installedJsPluginsSnapshot: List<NovelPlugin.Installed> = emptyList()

    @Volatile
    private var installedKotlinExtensionsSnapshot: List<KotlinNovelExtensionLoadResult> = emptyList()

    override val installedSourcesFlow: Flow<List<NovelSource>> = installedSources.asStateFlow()
    override val installedPluginsFlow: Flow<List<NovelPlugin.Installed>> = installedPlugins.asStateFlow()
    override val availablePluginsFlow: Flow<List<NovelPlugin.Available>> = availablePlugins.asStateFlow()
    override val updatesFlow: Flow<List<NovelPlugin.Installed>> = updates.asStateFlow()

    init {
        if (context != null) {
            registerKotlinPackageReceiver(context)
            reloadInstalledKotlinExtensions()
        }
        scope.launch {
            repository.subscribeAll().collect {
                installedJsPluginsSnapshot = it
                applyInstalledSnapshots()
            }
        }
    }

    private fun registerKotlinPackageReceiver(context: Context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            context,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent?) {
                    if (intent?.getBooleanExtra(Intent.EXTRA_REPLACING, false) == true &&
                        intent.action == Intent.ACTION_PACKAGE_REMOVED
                    ) {
                        return
                    }
                    val pkgName = intent?.data?.encodedSchemeSpecificPart
                    val knownInstalled = pkgName != null &&
                        installedKotlinExtensionsSnapshot.any {
                            it.plugin.pkgName == pkgName || it.plugin.id == pkgName
                        }
                    scope.launch {
                        val shouldReload = when {
                            pkgName == null -> true
                            knownInstalled -> true
                            intent.action == Intent.ACTION_PACKAGE_REMOVED -> false
                            else -> KotlinNovelExtensionLoader.isExtensionPackage(context, pkgName)
                        }
                        if (shouldReload) {
                            reloadInstalledKotlinExtensions()
                        }
                    }
                }
            },
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override suspend fun refreshAvailablePlugins() {
        availablePlugins.value = api.fetchAvailablePlugins().map { it.withNormalizedLang() }
        if (context != null) {
            reloadInstalledKotlinExtensions()
        } else {
            updatePendingUpdates()
        }
    }

    override suspend fun installPlugin(plugin: NovelPlugin.Available): NovelPlugin.Installed {
        if (plugin.isKotlinExtension) {
            pendingInstallRepos[plugin.pkgName ?: plugin.id] = InstalledRepo(plugin.repoUrl, plugin.repoName)
            val installed = requireNotNull(kotlinInstaller) { "Kotlin novel extension installer is not available" }
                .install(plugin)
                .withNormalizedLang()
            reloadInstalledKotlinExtensions()
            return installed
        }

        val installed = installer.install(plugin).withNormalizedLang()
        saveInstalledRepo(installed)
        installedJsPluginsSnapshot = installedJsPluginsSnapshot
            .filterNot { it.id == installed.id } + installed
        applyInstalledSnapshots()
        return installed
    }

    override suspend fun uninstallPlugin(plugin: NovelPlugin.Installed) {
        if (plugin.isKotlinExtension) {
            requireNotNull(kotlinInstaller) { "Kotlin novel extension installer is not available" }.uninstall(plugin)
            removeSavedInstalledRepo(plugin.pkgName ?: plugin.id, plugin.id)
            reloadInstalledKotlinExtensions()
            return
        }

        removeSavedInstalledRepo(plugin.id, plugin.pkgName)
        installer.uninstall(plugin.id)
        installedJsPluginsSnapshot = installedJsPluginsSnapshot.filterNot { it.id == plugin.id }
        applyInstalledSnapshots()
    }

    override suspend fun replacePluginFromRepo(
        installed: NovelPlugin.Installed,
        replacement: NovelPlugin.Available,
    ): NovelPlugin.Installed {
        uninstallPlugin(installed)

        if (installed.isKotlinExtension && context != null) {
            val pkgName = installed.pkgName ?: installed.id
            repeat(REPLACE_UNINSTALL_WAIT_SECONDS) {
                if (!context.isPackageInstalled(pkgName)) {
                    return installPlugin(replacement)
                }
                delay(1.seconds)
            }
            error("Timed out waiting for Kotlin novel extension $pkgName to uninstall")
        }

        return installPlugin(replacement)
    }

    override suspend fun getSourceData(id: Long): StubNovelSource? {
        installedSources.value.firstOrNull { it.id == id }?.let { source ->
            return StubNovelSource.from(source)
        }

        return repository.getAll()
            .firstOrNull { plugin -> NovelPluginId.toSourceId(plugin.id) == id }
            ?.let { plugin -> StubNovelSource(id = id, lang = plugin.lang, name = plugin.name) }
    }

    override fun getPluginIconUrlForSource(sourceId: Long): String? {
        return installedPluginIconUrls[sourceId]
    }

    override fun getCapabilitiesForSource(sourceId: Long): NovelPluginCapabilities? {
        val source = installedSources.value.firstOrNull { it.id == sourceId } ?: return null
        return (source as? NovelPluginCapabilitySource)?.pluginCapabilities
    }

    private fun reloadInstalledKotlinExtensions() {
        val appContext = context ?: return
        installedKotlinExtensionsSnapshot = KotlinNovelExtensionLoader.loadExtensions(appContext)
        applyInstalledSnapshots()
    }

    private fun applyInstalledSnapshots() {
        val availableById = availablePlugins.value.groupBy { it.id }
        val normalizedJs = installedJsPluginsSnapshot
            .map { it.withNormalizedLang().withPendingSavedOrInferredRepo(availableById[it.id].orEmpty()) }
        val normalizedKotlin = installedKotlinExtensionsSnapshot
            .map {
                it.plugin.withNormalizedLang().withPendingSavedOrInferredRepo(availableById[it.plugin.id].orEmpty())
            }
        (normalizedJs + normalizedKotlin).forEach(::saveInstalledRepo)
        installedPlugins.value = normalizedJs + normalizedKotlin
        installedSources.value =
            normalizedJs.mapNotNull { plugin -> sourceFactory.create(plugin) } +
            installedKotlinExtensionsSnapshot.flatMap { it.sources }

        installedPluginIconUrls.clear()
        normalizedJs.forEach { plugin ->
            plugin.iconUrl?.let { iconUrl ->
                installedPluginIconUrls[NovelPluginId.toSourceId(plugin.id)] = iconUrl
            }
        }
        installedKotlinExtensionsSnapshot.forEach { result ->
            result.plugin.iconUrl?.let { iconUrl ->
                result.sources.forEach { source ->
                    installedPluginIconUrls[source.id] = iconUrl
                }
            }
        }
        updatePendingUpdates()
    }

    private fun NovelPlugin.Installed.withPendingSavedOrInferredRepo(
        variants: List<NovelPlugin.Available>,
    ): NovelPlugin.Installed {
        val key = pkgName ?: id
        val pendingRepo = pendingInstallRepos.remove(key) ?: pendingInstallRepos.remove(id)
        return when {
            pendingRepo != null -> copy(
                repoUrl = pendingRepo.url,
                repoName = pendingRepo.name.takeIf { it.isNotBlank() },
            )
            repoUrl.isNotBlank() -> this
            else -> withSavedRepo(key) ?: withInferredRepo(variants)
        }
    }

    private fun NovelPlugin.Installed.withSavedRepo(key: String): NovelPlugin.Installed? {
        val savedRepo = getSavedInstalledRepo(key) ?: getSavedInstalledRepo(id) ?: return null
        return copy(repoUrl = savedRepo.url, repoName = savedRepo.name.takeIf { it.isNotBlank() })
    }

    private fun NovelPlugin.Installed.withInferredRepo(
        variants: List<NovelPlugin.Available>,
    ): NovelPlugin.Installed {
        if (repoUrl.isNotBlank() || variants.isEmpty()) return this

        val exactVersionMatches = variants.filter { it.versionCode == versionCode }
        val repoCandidate = exactVersionMatches.singleOrNull()
            ?: variants.singleOrNull()
            ?: variants
                .map { it.repoUrl }
                .distinct()
                .singleOrNull()
                ?.let { repoUrl -> variants.first { it.repoUrl == repoUrl } }
            ?: return this

        return copy(
            repoUrl = repoCandidate.repoUrl,
            repoName = repoCandidate.repoName.takeIf { it.isNotBlank() },
            apkUrl = apkUrl ?: repoCandidate.apkUrl,
        )
    }

    private fun getSavedInstalledRepo(key: String): InstalledRepo? {
        return sourcePreferences?.novelInstalledExtensionRepos()?.get()
            ?.firstOrNull { it.substringBefore('|') == key }
            ?.let { entry ->
                val parts = entry.split('|', limit = 3)
                val url = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@let null
                InstalledRepo(url = url, name = parts.getOrNull(2).orEmpty())
            }
    }

    private fun saveInstalledRepo(plugin: NovelPlugin.Installed) {
        val repoUrl = plugin.repoUrl.takeIf { it.isNotBlank() } ?: return
        val repoName = plugin.repoName.orEmpty()
        val key = plugin.pkgName ?: plugin.id
        sourcePreferences?.novelInstalledExtensionRepos()?.getAndSet { entries ->
            entries.filterNot { entry ->
                val entryKey = entry.substringBefore('|')
                entryKey == key || entryKey == plugin.id
            }.toSet() + "$key|$repoUrl|$repoName"
        }
    }

    private fun removeSavedInstalledRepo(
        key: String,
        alternateKey: String? = null,
    ) {
        sourcePreferences?.novelInstalledExtensionRepos()?.getAndSet { entries ->
            removeNovelInstalledRepoEntries(entries, key, alternateKey)
        }
    }

    private data class InstalledRepo(
        val url: String,
        val name: String,
    )

    private fun updatePendingUpdates() {
        val bestAvailableByIdVersion = availablePlugins.value
            .groupBy { it.id }
            .mapValues { (_, plugins) -> plugins.maxByOrNull { it.versionCode } }
        updates.value = installedPlugins.value.filter { installed ->
            val best = bestAvailableByIdVersion[installed.id] ?: return@filter false
            best.versionCode > installed.versionCode
        }
    }

    private companion object {
        const val REPLACE_UNINSTALL_WAIT_SECONDS = 120
    }
}
