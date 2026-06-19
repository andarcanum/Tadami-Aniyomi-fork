package eu.kanade.tachiyomi.extension.novel.kotlin

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.preference.PreferenceScreen
import dalvik.system.PathClassLoader
import eu.kanade.domain.extension.novel.interactor.TrustNovelExtension
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.novel.runtime.NovelPluginIdentitySource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.novelsource.ConfigurableNovelSource
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.novelsource.NovelSourceFactory
import eu.kanade.tachiyomi.novelsource.model.NovelFilter
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.NovelsPage
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.novel.NovelSiteSource
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.lang.Hash
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.ChildFirstPathClassLoader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import logcat.LogPriority
import okhttp3.OkHttpClient
import rx.Observable
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.extension.novel.model.NovelPlugin
import uy.kohesive.injekt.injectLazy
import java.io.File
import eu.kanade.tachiyomi.source.Source as TachiyomiSource

private const val APK_MIME = "application/vnd.android.package-archive"
private const val INSTALL_TIMEOUT_MS = 5 * 60 * 1000L

class KotlinNovelExtensionInstaller(
    private val context: Context,
    private val client: OkHttpClient,
) {
    suspend fun install(plugin: NovelPlugin.Available): NovelPlugin.Installed {
        val apkUrl = plugin.apkUrl ?: plugin.url
        val apkFile = withContext(Dispatchers.IO) {
            val safeName = plugin.id.replace(Regex("[^A-Za-z0-9_.-]"), "_")
            val dir = File(context.externalCacheDir ?: context.cacheDir, "novel_kotlin_extensions")
            dir.mkdirs()
            val file = File(dir, "$safeName.apk")
            client.newCall(GET(apkUrl)).awaitSuccess().use { response ->
                file.outputStream().use { output ->
                    response.body.byteStream().use { input -> input.copyTo(output) }
                }
            }
            file
        }
        awaitSystemInstall(plugin.pkgName ?: plugin.id, apkFile)
        return plugin.toInstalledKotlin()
    }

    suspend fun uninstall(plugin: NovelPlugin.Installed) {
        val pkgName = plugin.pkgName ?: plugin.id
        withContext(Dispatchers.IO) {
            KotlinNovelExtensionLoader.uninstallPrivateExtension(context, pkgName)
        }
        runCatching {
            Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkgName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .let(context::startActivity)
        }.onFailure {
            logcat(LogPriority.WARN, it) { "Failed to launch system uninstall for Kotlin novel extension $pkgName" }
        }
    }

    private suspend fun awaitSystemInstall(pkgName: String, apkFile: File) {
        val installResult = CompletableDeferred<Unit>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                val action = intent?.action ?: return
                if (action != Intent.ACTION_PACKAGE_ADDED && action != Intent.ACTION_PACKAGE_REPLACED) return
                val installedPkgName = intent.data?.encodedSchemeSpecificPart ?: return
                if (installedPkgName == pkgName) {
                    installResult.complete(Unit)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            installApk(apkFile)
            withTimeout(INSTALL_TIMEOUT_MS) { installResult.await() }
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    @Suppress("DEPRECATION")
    private fun installApk(apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .let(context::startActivity)
            throw IllegalStateException("Package install permission is required")
        }

        Intent(Intent.ACTION_INSTALL_PACKAGE)
            .setDataAndType(apkFile.getUriCompat(context), APK_MIME)
            .putExtra(Intent.EXTRA_RETURN_RESULT, false)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .let(context::startActivity)
    }
}

fun NovelPlugin.Available.toInstalledKotlin(): NovelPlugin.Installed {
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
        repoName = repoName.takeIf { it.isNotBlank() },
        pkgName = pkgName,
        apkUrl = apkUrl,
        isKotlinExtension = true,
    )
}

data class KotlinNovelExtensionLoadResult(
    val plugin: NovelPlugin.Installed,
    val sources: List<NovelSource>,
)

@SuppressLint("PackageManagerGetSignatures")
object KotlinNovelExtensionLoader {
    private const val EXTENSION_FEATURE_NOVEL = "tachiyomi.novelextension"
    private const val METADATA_SOURCE_CLASS = "tachiyomi.novelextension.class"
    private const val METADATA_SOURCE_FACTORY = "tachiyomi.novelextension.factory"
    private const val METADATA_NSFW = "tachiyomi.novelextension.nsfw"
    private const val METADATA_NOVEL = "tachiyomi.novelextension.novel"
    private const val PRIVATE_EXTENSION_EXTENSION = "ext"
    private const val LIB_VERSION_MIN = 1.4
    private const val LIB_VERSION_MAX = 1.5

    private val preferences: SourcePreferences by injectLazy()
    private val trustExtension: TrustNovelExtension by injectLazy()

    @Suppress("DEPRECATION")
    private val PACKAGE_FLAGS = PackageManager.GET_CONFIGURATIONS or
        PackageManager.GET_META_DATA or
        PackageManager.GET_SIGNATURES or
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)

    private fun getPrivateExtensionDir(context: Context) = File(context.filesDir, "novel_exts")

    fun uninstallPrivateExtension(context: Context, pkgName: String) {
        File(getPrivateExtensionDir(context), "$pkgName.$PRIVATE_EXTENSION_EXTENSION").delete()
    }

    fun loadExtensions(context: Context): List<KotlinNovelExtensionLoadResult> {
        val pkgManager = context.packageManager
        val installedPkgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pkgManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong()))
        } else {
            pkgManager.getInstalledPackages(PACKAGE_FLAGS)
        }

        val sharedExtPkgs = installedPkgs
            .asSequence()
            .filter { isPackageAnExtension(it) }
            .map { ExtensionInfo(packageInfo = it, isShared = true) }

        val privateExtPkgs = getPrivateExtensionDir(context)
            .listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.extension == PRIVATE_EXTENSION_EXTENSION }
            ?.mapNotNull {
                if (it.canWrite()) it.setReadOnly()
                val path = it.absolutePath
                pkgManager.getPackageArchiveInfo(path, PACKAGE_FLAGS)
                    ?.apply { applicationInfo!!.fixBasePaths(path) }
            }
            ?.filter { isPackageAnExtension(it) }
            ?.map { ExtensionInfo(packageInfo = it, isShared = false) }
            ?.toList()
            .orEmpty()

        val extPkgs = (sharedExtPkgs.toList() + privateExtPkgs)
            .distinctBy { it.packageInfo.packageName }
            .mapNotNull { sharedPkg ->
                val privatePkg = privateExtPkgs.singleOrNull {
                    it.packageInfo.packageName ==
                        sharedPkg.packageInfo.packageName
                }
                selectExtensionPackage(sharedPkg, privatePkg)
            }

        return runBlocking {
            extPkgs.map { extensionInfo ->
                async { loadExtension(context, extensionInfo) }
            }.awaitAll().filterNotNull()
        }
    }

    suspend fun isExtensionPackage(context: Context, pkgName: String): Boolean {
        return getExtensionPackageInfoFromPkgName(context, pkgName) != null
    }

    private suspend fun loadExtension(context: Context, extensionInfo: ExtensionInfo): KotlinNovelExtensionLoadResult? {
        val pkgManager = context.packageManager
        val pkgInfo = extensionInfo.packageInfo
        val appInfo = pkgInfo.applicationInfo ?: return null
        val pkgName = pkgInfo.packageName
        val appLabel = pkgManager.getApplicationLabel(appInfo).toString()
        val extName = appLabel
            .substringAfter("Tsundoku: ")
            .substringAfter("NovelApp: ")
        val versionName = pkgInfo.versionName ?: return null
        val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo).toInt()
        val libVersion = versionName.substringBeforeLast('.').toDoubleOrNull()
        if (libVersion == null || libVersion < LIB_VERSION_MIN || libVersion > LIB_VERSION_MAX) {
            logcat(LogPriority.WARN) { "Kotlin novel extension $pkgName has unsupported lib version $libVersion" }
            return null
        }
        val signatures = getSignatures(pkgInfo)
        if (signatures.isNullOrEmpty()) {
            logcat(LogPriority.WARN) { "Kotlin novel extension $pkgName is not signed" }
            return null
        }
        val isPackageExtension = pkgName.contains(".novelextension.") ||
            pkgName.startsWith("eu.kanade.tachiyomi.novelextension") ||
            pkgName.startsWith("app.tsundoku.extension.")
        if (!isPackageExtension && !trustExtension.isTrusted(pkgInfo, signatures)) {
            logcat(LogPriority.WARN) { "Kotlin novel extension $pkgName is not trusted" }
            return null
        }
        val isNsfw = appInfo.metaData?.getInt(METADATA_NSFW) == 1
        if (!preferences.showNsfwSource().get() && isNsfw) {
            logcat(LogPriority.WARN) { "NSFW Kotlin novel extension $pkgName not allowed" }
            return null
        }

        val classLoader = try {
            ChildFirstPathClassLoader(appInfo.sourceDir, null, context.classLoader)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Kotlin novel extension classloader error: $extName ($pkgName)" }
            return null
        }

        val sourceClasses = appInfo.metaData?.getString(METADATA_SOURCE_CLASS)
            ?: run {
                logcat(LogPriority.WARN) { "Missing source class for Kotlin novel extension $extName ($pkgName)" }
                return null
            }

        val sources = sourceClasses
            .split(";")
            .map { sourceClass ->
                sourceClass.trim().let {
                    if (it.startsWith(".")) pkgName + it else it
                }
            }
            .flatMap { className ->
                instantiateSources(classLoader, appInfo.sourceDir, className, extName)
                    ?: return null
            }
            .mapNotNull { it.asNovelSource(pkgName) }

        if (sources.isEmpty()) {
            logcat(LogPriority.WARN) { "Kotlin novel extension $pkgName did not expose any compatible novel sources" }
            return null
        }

        val lang = sources.map { it.lang }.toSet().let { langs ->
            when (langs.size) {
                0 -> ""
                1 -> langs.first()
                else -> "all"
            }
        }
        val iconUrl = runCatching { saveIcon(context, pkgName, appInfo.loadIcon(pkgManager)) }
            .onFailure { logcat(LogPriority.WARN, it) { "Failed to save Kotlin novel extension icon for $pkgName" } }
            .getOrNull()
        val pluginSite = sources.asSequence()
            .mapNotNull { (it as? NovelSiteSource)?.siteUrl }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
        val plugin = NovelPlugin.Installed(
            id = pkgName,
            name = extName,
            site = pluginSite,
            lang = lang,
            versionCode = versionCode,
            versionName = versionName,
            url = "",
            iconUrl = iconUrl,
            customJs = null,
            customCss = null,
            hasSettings = sources.any { it is ConfigurableNovelSource },
            sha256 = "",
            repoUrl = "",
            pkgName = pkgName,
            apkUrl = null,
            isKotlinExtension = true,
        )
        return KotlinNovelExtensionLoadResult(plugin, sources)
    }

    private fun saveIcon(context: Context, pkgName: String, drawable: Drawable): String? {
        val iconDir = File(context.cacheDir, "novel_kotlin_extension_icons")
        if (!iconDir.exists() && !iconDir.mkdirs()) return null
        val file = File(iconDir, "$pkgName.png")
        val bitmap = when (drawable) {
            is BitmapDrawable -> drawable.bitmap
            else -> Bitmap.createBitmap(
                drawable.intrinsicWidth.takeIf { it > 0 } ?: 96,
                drawable.intrinsicHeight.takeIf { it > 0 } ?: 96,
                Bitmap.Config.ARGB_8888,
            ).also { bitmap ->
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
            }
        }
        file.outputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) }
        return file.toURI().toString()
    }

    private fun instantiateSources(
        classLoader: ClassLoader,
        sourceDir: String,
        className: String,
        extName: String,
    ): List<Any>? {
        return try {
            instantiateSourceWith(classLoader, className)
        } catch (e: LinkageError) {
            runCatching {
                instantiateSourceWith(
                    PathClassLoader(sourceDir, null, KotlinNovelExtensionLoader::class.java.classLoader),
                    className,
                )
            }.getOrElse { error ->
                logcat(LogPriority.ERROR, error) { "Kotlin novel extension fallback load error: $extName ($className)" }
                null
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Kotlin novel extension load error: $extName ($className)" }
            null
        }
    }

    private fun instantiateSourceWith(classLoader: ClassLoader, className: String): List<Any> {
        return when (val obj = Class.forName(className, false, classLoader).getDeclaredConstructor().newInstance()) {
            is NovelSource -> listOf(obj)
            is NovelSourceFactory -> obj.createSources()
            is MangaSource -> listOf(obj)
            is TachiyomiSource -> listOf(obj)
            is SourceFactory -> obj.createSources()
            else -> throw Exception("Unknown source class type: ${obj.javaClass}")
        }
    }

    private fun Any.asNovelSource(pluginId: String): NovelSource? {
        return when (this) {
            is NovelSource -> this.withKotlinPluginIdentity(pluginId)
            is CatalogueSource -> if (this is ConfigurableSource) {
                KotlinConfigurableCatalogueNovelSourceAdapter(this, this, pluginId)
            } else {
                KotlinCatalogueNovelSourceAdapter(this, pluginId)
            }
            is TachiyomiSource -> if (this is ConfigurableSource) {
                KotlinConfigurableMangaNovelSourceAdapter(this, pluginId)
            } else {
                KotlinMangaNovelSourceAdapter(this, pluginId)
            }
            else -> null
        }
    }

    private fun selectExtensionPackage(shared: ExtensionInfo?, private: ExtensionInfo?): ExtensionInfo? {
        return when {
            private == null && shared != null -> shared
            shared == null && private != null -> private
            shared == null && private == null -> null
            PackageInfoCompat.getLongVersionCode(shared!!.packageInfo) >=
                PackageInfoCompat.getLongVersionCode(private!!.packageInfo) -> shared
            else -> private
        }
    }

    fun getExtensionPackageInfoFromPkgName(context: Context, pkgName: String): PackageInfo? {
        val privateExtensionFile = File(getPrivateExtensionDir(context), "$pkgName.$PRIVATE_EXTENSION_EXTENSION")
        val privatePkg = if (privateExtensionFile.isFile) {
            context.packageManager.getPackageArchiveInfo(privateExtensionFile.absolutePath, PACKAGE_FLAGS)
                ?.takeIf { isPackageAnExtension(it) }
                ?.let {
                    it.applicationInfo!!.fixBasePaths(privateExtensionFile.absolutePath)
                    ExtensionInfo(it, isShared = false)
                }
        } else {
            null
        }
        val sharedPkg = try {
            context.packageManager.getPackageInfo(pkgName, PACKAGE_FLAGS)
                .takeIf { isPackageAnExtension(it) }
                ?.let { ExtensionInfo(it, isShared = true) }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        return selectExtensionPackage(sharedPkg, privatePkg)?.packageInfo
    }

    private fun isPackageAnExtension(pkgInfo: PackageInfo): Boolean {
        return pkgInfo.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE_NOVEL }
    }

    private fun getSignatures(pkgInfo: PackageInfo): List<String>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = pkgInfo.signingInfo ?: return null
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.signatures
        }?.map { Hash.sha256(it.toByteArray()) }?.toList()
    }

    private fun ApplicationInfo.fixBasePaths(apkPath: String) {
        sourceDir = apkPath
        publicSourceDir = apkPath
    }

    private data class ExtensionInfo(
        val packageInfo: PackageInfo,
        val isShared: Boolean,
    )
}

private fun NovelSource.withKotlinPluginIdentity(pluginId: String): NovelSource {
    if (this is NovelPluginIdentitySource) return this
    return when {
        this is NovelCatalogueSource && this is ConfigurableNovelSource -> {
            KotlinIdentityConfigurableCatalogueNovelSourceAdapter(this, this, pluginId)
        }
        this is NovelCatalogueSource -> KotlinIdentityCatalogueNovelSourceAdapter(this, pluginId)
        this is ConfigurableNovelSource -> KotlinIdentityConfigurableNovelSourceAdapter(this, pluginId)
        else -> KotlinIdentityBasicNovelSourceAdapter(this, pluginId)
    }
}

private open class KotlinIdentityBasicNovelSourceAdapter(
    protected val source: NovelSource,
    override val pluginId: String,
) : NovelSource, NovelSiteSource, NovelPluginIdentitySource {
    override val id: Long = source.id
    override val name: String = source.name
    override val lang: String = source.lang
    override val siteUrl: String? = (source as? NovelSiteSource)?.siteUrl

    override suspend fun getNovelDetails(novel: SNovel): SNovel = source.getNovelDetails(novel)

    override suspend fun getChapterList(novel: SNovel): List<SNovelChapter> = source.getChapterList(novel)

    override suspend fun getChapterText(chapter: SNovelChapter): String = source.getChapterText(chapter)

    @Deprecated("Use the non-RxJava API instead.")
    @Suppress("DEPRECATION")
    override fun fetchNovelDetails(novel: SNovel): Observable<SNovel> = source.fetchNovelDetails(novel)

    @Deprecated("Use the non-RxJava API instead.")
    @Suppress("DEPRECATION")
    override fun fetchChapterList(novel: SNovel): Observable<List<SNovelChapter>> = source.fetchChapterList(novel)

    @Deprecated("Use the non-RxJava API instead.")
    @Suppress("DEPRECATION")
    override fun fetchChapterText(chapter: SNovelChapter): Observable<String> = source.fetchChapterText(chapter)
}

private open class KotlinIdentityCatalogueNovelSourceAdapter(
    protected val catalogueSource: NovelCatalogueSource,
    pluginId: String,
) : KotlinIdentityBasicNovelSourceAdapter(catalogueSource, pluginId), NovelCatalogueSource {
    override val supportsLatest: Boolean = catalogueSource.supportsLatest

    override suspend fun getPopularNovels(page: Int): NovelsPage = catalogueSource.getPopularNovels(page)

    override suspend fun getPopularNovels(page: Int, filters: NovelFilterList): NovelsPage {
        return catalogueSource.getPopularNovels(page, filters)
    }

    override suspend fun getSearchNovels(page: Int, query: String, filters: NovelFilterList): NovelsPage {
        return catalogueSource.getSearchNovels(page, query, filters)
    }

    override suspend fun getLatestUpdates(page: Int): NovelsPage = catalogueSource.getLatestUpdates(page)

    override suspend fun getLatestUpdates(page: Int, filters: NovelFilterList): NovelsPage {
        return catalogueSource.getLatestUpdates(page, filters)
    }

    override fun getFilterList(): NovelFilterList = catalogueSource.getFilterList()

    @Deprecated("Use the non-RxJava API instead.")
    @Suppress("DEPRECATION")
    override fun fetchPopularNovels(page: Int): Observable<NovelsPage> {
        return catalogueSource.fetchPopularNovels(page)
    }

    @Deprecated("Use the non-RxJava API instead.")
    @Suppress("DEPRECATION")
    override fun fetchSearchNovels(page: Int, query: String, filters: NovelFilterList): Observable<NovelsPage> {
        return catalogueSource.fetchSearchNovels(page, query, filters)
    }

    @Deprecated("Use the non-RxJava API instead.")
    @Suppress("DEPRECATION")
    override fun fetchLatestUpdates(page: Int): Observable<NovelsPage> {
        return catalogueSource.fetchLatestUpdates(page)
    }
}

private class KotlinIdentityConfigurableNovelSourceAdapter(
    source: ConfigurableNovelSource,
    pluginId: String,
) : KotlinIdentityBasicNovelSourceAdapter(source, pluginId), ConfigurableNovelSource {
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        (source as ConfigurableNovelSource).setupPreferenceScreen(screen)
    }
}

private class KotlinIdentityConfigurableCatalogueNovelSourceAdapter(
    catalogueSource: NovelCatalogueSource,
    private val configurableSource: ConfigurableNovelSource,
    pluginId: String,
) : KotlinIdentityCatalogueNovelSourceAdapter(catalogueSource, pluginId), ConfigurableNovelSource {
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        configurableSource.setupPreferenceScreen(screen)
    }
}

private open class KotlinMangaNovelSourceAdapter(
    protected val source: TachiyomiSource,
    override val pluginId: String,
) : NovelSource, NovelSiteSource, NovelPluginIdentitySource {
    override val id: Long = source.id
    override val name: String = source.name
    override val lang: String = source.lang
    override val siteUrl: String? = (source as? HttpSource)?.baseUrl

    override suspend fun getNovelDetails(novel: SNovel): SNovel {
        return source.getMangaDetails(novel.toManga()).toNovel(source)
    }

    override suspend fun getChapterList(novel: SNovel): List<SNovelChapter> {
        return source.getChapterList(novel.toManga()).map { it.toNovelChapter(source) }
    }

    override suspend fun getChapterText(chapter: SNovelChapter): String {
        val page = runCatching { source.getPageList(chapter.toChapter()).firstOrNull() }
            .getOrNull()
            ?: Page(0, chapter.url)
        return source.fetchPageText(page)
    }
}

private open class KotlinConfigurableMangaNovelSourceAdapter(
    source: ConfigurableSource,
    pluginId: String,
) : KotlinMangaNovelSourceAdapter(source, pluginId), ConfigurableNovelSource {
    private val configurableSource: ConfigurableSource = source

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        configurableSource.setupPreferenceScreen(screen)
    }
}

private open class KotlinCatalogueNovelSourceAdapter(
    source: CatalogueSource,
    pluginId: String,
) : KotlinMangaNovelSourceAdapter(source, pluginId), NovelCatalogueSource {
    private val catalogueSource: CatalogueSource = source

    override val supportsLatest: Boolean = catalogueSource.supportsLatest

    override suspend fun getPopularNovels(page: Int): NovelsPage {
        return catalogueSource.getPopularManga(page).toNovelsPage(source)
    }

    override suspend fun getPopularNovels(page: Int, filters: NovelFilterList): NovelsPage {
        return catalogueSource.getPopularManga(page).toNovelsPage(source)
    }

    override suspend fun getSearchNovels(page: Int, query: String, filters: NovelFilterList): NovelsPage {
        return catalogueSource.getSearchManga(page, query, filters.toMangaFilterList()).toNovelsPage(source)
    }

    override suspend fun getLatestUpdates(page: Int): NovelsPage {
        return catalogueSource.getLatestUpdates(page).toNovelsPage(source)
    }

    override suspend fun getLatestUpdates(page: Int, filters: NovelFilterList): NovelsPage {
        return getLatestUpdates(page)
    }

    override fun getFilterList(): NovelFilterList = catalogueSource.getFilterList().toNovelFilterList()

    @Deprecated("Use the non-RxJava API instead.")
    @Suppress("DEPRECATION")
    override fun fetchPopularNovels(page: Int): Observable<NovelsPage> {
        return catalogueSource.fetchPopularManga(page).map { it.toNovelsPage(source) }
    }

    @Deprecated("Use the non-RxJava API instead.")
    @Suppress("DEPRECATION")
    override fun fetchSearchNovels(page: Int, query: String, filters: NovelFilterList): Observable<NovelsPage> {
        return catalogueSource.fetchSearchManga(page, query, filters.toMangaFilterList()).map {
            it.toNovelsPage(source)
        }
    }

    @Deprecated("Use the non-RxJava API instead.")
    @Suppress("DEPRECATION")
    override fun fetchLatestUpdates(page: Int): Observable<NovelsPage> {
        return catalogueSource.fetchLatestUpdates(page).map { it.toNovelsPage(source) }
    }
}

private class KotlinConfigurableCatalogueNovelSourceAdapter(
    source: CatalogueSource,
    private val configurableSource: ConfigurableSource,
    pluginId: String,
) : KotlinCatalogueNovelSourceAdapter(source, pluginId), ConfigurableNovelSource {

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        configurableSource.setupPreferenceScreen(screen)
    }
}

private fun FilterList.toNovelFilterList(): NovelFilterList {
    return NovelFilterList(map { it.toNovelFilter() })
}

private fun NovelFilterList.toMangaFilterList(): FilterList {
    return FilterList(map { it.toMangaFilter() })
}

@Suppress("UNCHECKED_CAST")
private fun Filter<*>.toNovelFilter(): NovelFilter<*> {
    return when (this) {
        is Filter.Header -> NovelFilter.Header(name)
        is Filter.Separator -> NovelFilter.Separator(name)
        is Filter.CheckBox -> object : NovelFilter.CheckBox(name, state) {}
        is Filter.TriState -> object : NovelFilter.TriState(name, state) {}
        is Filter.Text -> object : NovelFilter.Text(name, state) {}
        is Filter.Select<*> -> object : NovelFilter.Select<Any?>(name, values as Array<Any?>, state) {}
        is Filter.Group<*> -> object : NovelFilter.Group<NovelFilter<*>>(
            name,
            state.filterIsInstance<Filter<*>>().map { it.toNovelFilter() },
        ) {}
        is Filter.Sort -> object : NovelFilter.Sort(
            name,
            values,
            state?.let { NovelFilter.Sort.Selection(it.index, it.ascending) },
        ) {}
    }
}

@Suppress("UNCHECKED_CAST")
private fun NovelFilter<*>.toMangaFilter(): Filter<*> {
    return when (this) {
        is NovelFilter.Header -> Filter.Header(name)
        is NovelFilter.Separator -> Filter.Separator(name)
        is NovelFilter.CheckBox -> object : Filter.CheckBox(name, state) {}
        is NovelFilter.Switch -> object : Filter.CheckBox(name, state) {}
        is NovelFilter.TriState -> object : Filter.TriState(name, state) {}
        is NovelFilter.Text -> object : Filter.Text(name, state) {}
        is NovelFilter.Select<*> -> object : Filter.Select<Any?>(name, values as Array<Any?>, state) {}
        is NovelFilter.Group<*> -> object : Filter.Group<Filter<*>>(
            name,
            state.filterIsInstance<NovelFilter<*>>().map { it.toMangaFilter() },
        ) {}
        is NovelFilter.Sort -> object : Filter.Sort(
            name,
            values,
            state?.let { Filter.Sort.Selection(it.index, it.ascending) },
        ) {}
        is NovelFilter.Picker<*> -> object : Filter.Select<Any?>(name, values as Array<Any?>, state) {}
        is NovelFilter.XCheckBox -> object : Filter.TriState(name, state) {}
    }
}

private fun MangasPage.toNovelsPage(source: TachiyomiSource): NovelsPage {
    return NovelsPage(mangas.map { it.toNovel(source) }, hasNextPage)
}

private fun SManga.toNovel(source: TachiyomiSource): SNovel {
    val safeTitle = safeTitle().ifBlank { safeUrl() }
    return SNovel.create().also {
        it.url = safeUrl().ifBlank { safeTitle }
        it.title = safeTitle.ifBlank { "Untitled" }
        it.author = runCatching { author }.getOrNull() ?: runCatching { artist }.getOrNull()
        it.description = runCatching { description }.getOrNull()
        it.genre = runCatching { genre }.getOrNull()
        it.status = runCatching { status }.getOrDefault(SNovel.UNKNOWN)
        it.thumbnail_url = runCatching { thumbnail_url }.getOrNull()
        it.update_strategy = runCatching { update_strategy }.getOrDefault(it.update_strategy)
        it.initialized = runCatching { initialized }.getOrDefault(false)
    }
}

private fun SNovel.toManga(): SManga {
    val safeTitle = safeTitle().ifBlank { safeUrl() }
    return SManga.create().also {
        it.url = safeUrl().ifBlank { safeTitle }
        it.title = safeTitle.ifBlank { "Untitled" }
        it.artist = runCatching { author }.getOrNull()
        it.author = runCatching { author }.getOrNull()
        it.description = runCatching { description }.getOrNull()
        it.genre = runCatching { genre }.getOrNull()
        it.status = runCatching { status }.getOrDefault(SManga.UNKNOWN)
        it.thumbnail_url = runCatching { thumbnail_url }.getOrNull()
        it.update_strategy = runCatching { update_strategy }.getOrDefault(it.update_strategy)
        it.initialized = runCatching { initialized }.getOrDefault(false)
    }
}

private fun SChapter.toNovelChapter(source: TachiyomiSource): SNovelChapter {
    val safeName = safeName().ifBlank { safeUrl() }
    return SNovelChapter.create().also {
        it.url = safeUrl().ifBlank { safeName }
        it.name = safeName.ifBlank { "Chapter" }
        it.date_upload = runCatching { date_upload }.getOrDefault(0L)
        it.date_upload_raw = null
        it.chapter_number = runCatching { chapter_number }.getOrDefault(-1f)
        it.scanlator = runCatching { scanlator }.getOrNull()
    }
}

private fun SNovelChapter.toChapter(): SChapter {
    val safeName = safeName().ifBlank { safeUrl() }
    return SChapter.create().also {
        it.url = safeUrl().ifBlank { safeName }
        it.name = safeName.ifBlank { "Chapter" }
        it.date_upload = runCatching { date_upload }.getOrDefault(0L)
        it.chapter_number = runCatching { chapter_number }.getOrDefault(-1f)
        it.scanlator = runCatching { scanlator }.getOrNull()
    }
}

private fun resolveSourceUrl(source: TachiyomiSource, url: String): String {
    val raw = url.trim()
    if (raw.isBlank()) return raw
    if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
        return raw
    }
    val baseUrl = (source as? HttpSource)?.baseUrl?.trimEnd('/') ?: return raw
    return if (raw.startsWith('/')) {
        "$baseUrl$raw"
    } else {
        "$baseUrl/$raw"
    }
}

private fun SManga.safeUrl(): String = runCatching { url }.getOrDefault("")

private fun SManga.safeTitle(): String = runCatching { title }.getOrDefault("")

private fun SNovel.safeUrl(): String = runCatching { url }.getOrDefault("")

private fun SNovel.safeTitle(): String = runCatching { title }.getOrDefault("")

private fun SChapter.safeUrl(): String = runCatching { url }.getOrDefault("")

private fun SChapter.safeName(): String = runCatching { name }.getOrDefault("")

private fun SNovelChapter.safeUrl(): String = runCatching { url }.getOrDefault("")

private fun SNovelChapter.safeName(): String = runCatching { name }.getOrDefault("")
