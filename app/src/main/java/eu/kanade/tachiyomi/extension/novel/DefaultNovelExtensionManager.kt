package eu.kanade.tachiyomi.extension.novel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import eu.kanade.domain.extension.novel.interactor.TrustNovelExtension
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.installer.ExtensionSignatureComparison
import eu.kanade.tachiyomi.extension.novel.api.NovelPluginApiFacade
import eu.kanade.tachiyomi.extension.novel.kotlin.KotlinNovelExtensionInstaller
import eu.kanade.tachiyomi.extension.novel.kotlin.KotlinNovelExtensionLoadResult
import eu.kanade.tachiyomi.extension.novel.kotlin.KotlinNovelExtensionLoader
import eu.kanade.tachiyomi.extension.novel.runtime.NovelPluginCapabilities
import eu.kanade.tachiyomi.extension.novel.runtime.NovelPluginCapabilitySource
import eu.kanade.tachiyomi.extension.novel.runtime.NovelPluginIdentitySource
import eu.kanade.tachiyomi.extension.novel.runtime.NovelPluginWebViewCoordinator
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.util.system.isPackageInstalled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.extension.novel.NovelPluginInstallerFacade
import tachiyomi.data.extension.novel.NovelPluginKeyValueStore
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
    private val trustExtension: TrustNovelExtension? = runCatching {
        Injekt.get<TrustNovelExtension>()
    }.getOrNull(),
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
    private val untrustedPlugins = MutableStateFlow<List<NovelPlugin.Untrusted>>(emptyList())
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
    override val untrustedPluginsFlow: Flow<List<NovelPlugin.Untrusted>> = untrustedPlugins.asStateFlow()
    override val updatesFlow: Flow<List<NovelPlugin.Installed>> = updates.asStateFlow()
    override val repoFetchErrors: Flow<Map<String, String>> = api.repoFetchErrors

    private val _signatureMismatchEvents =
        MutableSharedFlow<NovelExtensionManager.SignatureMismatchEvent>(extraBufferCapacity = 8)
    override val signatureMismatchEvents: SharedFlow<NovelExtensionManager.SignatureMismatchEvent> =
        _signatureMismatchEvents.asSharedFlow()

    override fun reportSignatureMismatch(pluginId: String) {
        val installed = installedPlugins.value.firstOrNull { it.id == pluginId }
        val displayName = installed?.name ?: pluginId
        // The offered replacement must never be older than the installed copy: a mismatch
        // reinstall uninstalls first, so the JS downgrade guard would not apply (parity with the
        // manga/anime managers, which hand out the newest variant per package).
        val candidate = availablePlugins.value
            .filter { it.id == pluginId }
            .filter { installed == null || it.versionCode >= installed.versionCode }
            .maxByOrNull { it.versionCode }
        _signatureMismatchEvents.tryEmit(
            NovelExtensionManager.SignatureMismatchEvent(
                pluginId = pluginId,
                displayName = displayName,
                candidate = candidate,
            ),
        )
    }

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
        // The NSFW toggle gates which sources are exposed; re-apply immediately on change instead
        // of waiting for the next refresh.
        sourcePreferences?.showNsfwSource()?.changes()?.onEach {
            applyInstalledSnapshots()
        }?.launchIn(scope)
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
                            it.plugin.packageNameOrId() == pkgName || it.plugin.id == pkgName
                        }
                    scope.launch {
                        val shouldReload = when {
                            pkgName == null -> true
                            knownInstalled -> true
                            intent.action == Intent.ACTION_PACKAGE_REMOVED -> false
                            else -> KotlinNovelExtensionLoader.isExtensionPackage(context, pkgName)
                        }
                        if (shouldReload) {
                            // An add/replace finished outside our installer paths (e.g. the user
                            // installed the extension APK from a file manager) — keep the user's
                            // trust across the new version when the signing key is unchanged.
                            if (pkgName != null && intent.action != Intent.ACTION_PACKAGE_REMOVED) {
                                carryTrustToNewVersion(pkgName)
                            }
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
            enableLanguageOf(installed)
            reloadInstalledKotlinExtensions()
            return installed
        }

        // Downgrading a JS plugin would silently revert user-facing behavior; the Kotlin path
        // refuses downgrades, so keep the semantics identical across plugin types.
        val currentInstalled = installedPlugins.value.firstOrNull { it.id == plugin.id }
        if (currentInstalled != null && plugin.versionCode < currentInstalled.versionCode) {
            throw IllegalArgumentException("Refusing to downgrade JS novel plugin ${plugin.id}")
        }
        if (currentInstalled != null) {
            // The plugin is being replaced: drop stale runtimes/script cache before reloading.
            sourceFactory.clearRuntimeCaches()
        }

        val installed = installer.install(plugin).withNormalizedLang()
        saveInstalledRepo(installed)
        enableLanguageOf(installed)
        installedJsPluginsSnapshot = installedJsPluginsSnapshot
            .filterNot { it.id == installed.id } + installed
        applyInstalledSnapshots()
        return installed
    }

    override fun cancelPluginInstall(plugin: NovelPlugin.Available) {
        if (!plugin.isKotlinExtension) return
        val pkgName = plugin.pkgName ?: plugin.id
        pendingInstallRepos.remove(pkgName)
        kotlinInstaller?.cancelInstall(pkgName)
    }

    override fun cancelPluginInstall(plugin: NovelPlugin.Installed) {
        if (!plugin.isKotlinExtension) return
        val pkgName = plugin.pkgName ?: plugin.id
        pendingInstallRepos.remove(pkgName)
        kotlinInstaller?.cancelInstall(pkgName)
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
        // Free plugin-owned state so a reinstall cannot pick up stale settings or web storage.
        runCatching { Injekt.get<NovelPluginKeyValueStore>().clear(plugin.id) }
        runCatching { Injekt.get<NovelPluginWebViewCoordinator>().cleanup(plugin.id) }
        sourceFactory.clearRuntimeCaches()
    }

    override suspend fun uninstallPlugin(plugin: NovelPlugin.Untrusted) {
        requireNotNull(kotlinInstaller) { "Kotlin novel extension installer is not available" }
            .uninstall(plugin.toInstalledForUninstall())
        untrustedPlugins.value = untrustedPlugins.value.filterNot { it.pkgName == plugin.pkgName }
        reloadInstalledKotlinExtensions()
    }

    override suspend fun replacePluginFromRepo(
        installed: NovelPlugin.Installed,
        replacement: NovelPlugin.Available,
    ): NovelPlugin.Installed = withContext(NonCancellable) {
        // BEXT-2: ran in the caller's (screen model) cancellable scope - leaving the extensions
        // screen between the uninstall and the install left the plugin REMOVED with no
        // replacement. The manga/anime sides moved the work to the manager scope; a suspend API
        // cannot detach from its caller, so it is made NonCancellable instead (X3 parity).
        uninstallPlugin(installed)

        if (installed.isKotlinExtension && context != null) {
            val pkgName = installed.pkgName ?: installed.id
            repeat(REPLACE_UNINSTALL_WAIT_SECONDS) {
                if (!context.isPackageInstalled(pkgName)) {
                    return@withContext installPlugin(replacement)
                }
                delay(1.seconds)
            }
            error("Timed out waiting for Kotlin novel extension $pkgName to uninstall")
        }

        installPlugin(replacement)
    }

    override suspend fun trustPlugin(plugin: NovelPlugin.Untrusted) {
        requireNotNull(trustExtension) { "Novel extension trust service is not available" }
            .trust(plugin.pkgName, plugin.versionCode.toLong(), plugin.signatureHash)
        enableLanguageOf(plugin)
        reloadInstalledKotlinExtensions()
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

    override fun getPluginId(sourceId: Long): String? {
        installedSources.value
            .firstOrNull { it.id == sourceId }
            ?.let { source -> (source as? NovelPluginIdentitySource)?.pluginId }
            ?.let { return it }

        return installedPlugins.value
            .firstOrNull { NovelPluginId.toSourceId(it.id) == sourceId }
            ?.id
    }

    override fun getPluginIdAsFlow(sourceId: Long): Flow<String?> {
        return combine(installedSourcesFlow, installedPluginsFlow) { sources, plugins ->
            sources.firstOrNull { it.id == sourceId }
                ?.let { source -> (source as? NovelPluginIdentitySource)?.pluginId }
                ?: plugins.firstOrNull { NovelPluginId.toSourceId(it.id) == sourceId }?.id
        }
            .distinctUntilChanged()
    }

    override fun isNsfwForSource(sourceId: Long): Boolean {
        val pluginId = getPluginId(sourceId) ?: return false
        return resolvePluginNsfw(pluginId)
    }

    override fun isNsfwForSourceAsFlow(sourceId: Long): Flow<Boolean> {
        return combine(
            getPluginIdAsFlow(sourceId),
            installedPluginsFlow,
            availablePluginsFlow,
        ) { pluginId, installed, available ->
            if (pluginId == null) return@combine false
            installed.firstOrNull { it.id == pluginId }?.isNsfw
                ?: available.firstOrNull { it.id == pluginId }?.isNsfw
                ?: false
        }
            .distinctUntilChanged()
    }

    private fun resolvePluginNsfw(pluginId: String): Boolean {
        return installedPlugins.value.firstOrNull { it.id == pluginId }?.isNsfw
            ?: availablePlugins.value.firstOrNull { it.id == pluginId }?.isNsfw
            ?: false
    }

    private fun reloadInstalledKotlinExtensions() {
        val appContext = context ?: return
        installedKotlinExtensionsSnapshot = KotlinNovelExtensionLoader.loadExtensions(appContext)
        applyInstalledSnapshots()
    }

    /**
     * Carries the user's trust to the newly installed (shared) version when the signing key is
     * unchanged, so an update does not silently flip the extension back to Untrusted. No-op for
     * private-only copies (handled inside the loader) and fresh installs.
     */
    private fun carryTrustToNewVersion(pkgName: String) {
        val appContext = context ?: return
        val trust = trustExtension ?: return
        val info = runCatching {
            appContext.packageManager.getPackageInfo(pkgName, 0)
        }.getOrNull() ?: return
        val signatures = ExtensionSignatureComparison.installedSignatures(appContext, pkgName) ?: return
        signatures.lastOrNull()?.let { signatureHash ->
            runCatching {
                trust.trustIfSameSigner(pkgName, PackageInfoCompat.getLongVersionCode(info), signatureHash)
            }.onFailure { e ->
                logcat(LogPriority.WARN, e) { "Failed to carry trust to new version for $pkgName" }
            }
        }
    }

    private fun applyInstalledSnapshots() {
        val availableById = availablePlugins.value.groupBy { it.id }
        val normalizedJs = installedJsPluginsSnapshot
            .map {
                it.withNormalizedLang()
                    .withPendingSavedOrInferredRepo(availableById[it.id].orEmpty())
                    .withNsfwFromAvailable(availableById[it.id].orEmpty())
            }
        val normalizedKotlin = installedKotlinExtensionsSnapshot
            .mapNotNull { result ->
                (result.plugin as? NovelPlugin.Installed)
                    ?.withNormalizedLang()
                    ?.withPendingSavedOrInferredRepo(availableById[result.plugin.id].orEmpty())
            }
        val normalizedUntrusted = installedKotlinExtensionsSnapshot
            .mapNotNull { result ->
                (result.plugin as? NovelPlugin.Untrusted)?.withNormalizedLang()
            }
        (normalizedJs + normalizedKotlin).forEach(::saveInstalledRepo)
        installedPlugins.value = normalizedJs + normalizedKotlin
        untrustedPlugins.value = normalizedUntrusted
        // Parental control: an NSFW plugin must not register sources while the setting is off, the
        // same way the manga/anime loaders refuse to load NSFW extensions.
        val showNsfwSources = sourcePreferences?.showNsfwSource()?.get() ?: true
        installedSources.value =
            normalizedJs
                .filter { showNsfwSources || !it.isNsfw }
                .mapNotNull { plugin -> sourceFactory.create(plugin) } +
            installedKotlinExtensionsSnapshot
                .filter { it.plugin is NovelPlugin.Installed && (showNsfwSources || !it.plugin.isNsfw) }
                .flatMap { it.sources }

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

    private fun NovelPlugin.Installed.withNsfwFromAvailable(
        variants: List<NovelPlugin.Available>,
    ): NovelPlugin.Installed {
        if (isKotlinExtension) return this
        val nsfw = variants.firstOrNull { it.versionCode == versionCode }?.isNsfw
            ?: variants.firstOrNull()?.isNsfw
            ?: isNsfw
        return if (nsfw == isNsfw) this else copy(isNsfw = nsfw)
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

    private fun NovelPlugin.Untrusted.toInstalledForUninstall(): NovelPlugin.Installed {
        return NovelPlugin.Installed(
            id = id,
            name = name,
            site = site,
            lang = lang,
            versionCode = versionCode,
            versionName = versionName,
            url = url,
            iconUrl = iconUrl,
            customJs = customJs,
            customCss = customCss,
            hasSettings = hasSettings,
            sha256 = sha256,
            repoUrl = repoUrl,
            pkgName = pkgName,
            isKotlinExtension = true,
        )
    }

    // ponytail: Auto-enable languages of the newly installed novel plugin
    private fun enableLanguageOf(plugin: NovelPlugin) {
        val lang = plugin.lang
        if (lang.isNotBlank() && lang != "all") {
            sourcePreferences?.enabledLanguages()?.let { enabledLanguagesPref ->
                val currentLangs = enabledLanguagesPref.get()
                if (lang !in currentLangs) {
                    enabledLanguagesPref.set(currentLangs + lang)
                }
            }
        }
    }

    private fun NovelPlugin.packageNameOrId(): String {
        return when (this) {
            is NovelPlugin.Available -> pkgName ?: id
            is NovelPlugin.Installed -> pkgName ?: id
            is NovelPlugin.Untrusted -> pkgName
        }
    }

    private fun NovelPlugin.Untrusted.withNormalizedLang(): NovelPlugin.Untrusted {
        return copy(lang = lang.lowercase().takeIf { it.isNotBlank() } ?: "all")
    }

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
