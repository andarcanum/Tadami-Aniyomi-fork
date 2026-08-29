package eu.kanade.tachiyomi.extension.novel.kotlin

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import androidx.preference.PreferenceScreen
import dalvik.system.PathClassLoader
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.extension.novel.interactor.TrustNovelExtension
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.canReplacePrivateExtension
import eu.kanade.tachiyomi.extension.installer.ApkExtensionKind
import eu.kanade.tachiyomi.extension.installer.ApkInstallBackend
import eu.kanade.tachiyomi.extension.installer.ApkInstallRequest
import eu.kanade.tachiyomi.extension.installer.ApkInstallResult
import eu.kanade.tachiyomi.extension.installer.ApkUninstallRequest
import eu.kanade.tachiyomi.extension.installer.ExtensionSignatureComparison
import eu.kanade.tachiyomi.extension.installer.PendingApkInstallStore
import eu.kanade.tachiyomi.extension.installer.PrivateExtensionInstallResult
import eu.kanade.tachiyomi.extension.installer.UnifiedApkExtensionInstaller
import eu.kanade.tachiyomi.extension.installer.toApkInstallBackend
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import eu.kanade.tachiyomi.extension.novel.runtime.NovelPluginIdentitySource
import eu.kanade.tachiyomi.extension.util.OkHttpExtensionApkDownloader
import eu.kanade.tachiyomi.novelsource.ConfigurableNovelSource
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.novelsource.NovelSourceFactory
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.NovelsPage
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import eu.kanade.tachiyomi.novelsource.online.NovelHttpSource
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.novel.NovelImageRequestSource
import eu.kanade.tachiyomi.source.novel.NovelSiteSource
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.lang.Hash
import eu.kanade.tachiyomi.util.storage.copyAndSetReadOnlyTo
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.ChildFirstPathClassLoader
import eu.kanade.tachiyomi.util.system.isPackageInstalled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import okhttp3.OkHttpClient
import rx.Observable
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.extension.novel.model.NovelPlugin
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.security.MessageDigest
import eu.kanade.tachiyomi.source.Source as TachiyomiSource

private const val APK_MIME = "application/vnd.android.package-archive"

/** Must match OkHttpExtensionApkDownloader.DOWNLOAD_DIR — that is where orphaned parts land. */
private const val EXTENSION_APK_DOWNLOAD_DIR = "extension_apks"

/** Cached novel-plugin APKs and partial downloads older than this are swept as orphans. */
private const val ORPHAN_DOWNLOAD_MAX_AGE_MS = 48L * 60 * 60 * 1000

/**
 * Deletes truncated downloads ("*.apk.part") and stale cached plugin APKs left behind by dead
 * processes; everything in that cache directory is re-downloadable from the plugin repo.
 *
 * @return absolute paths of the deleted files, for logging at the call site.
 */
fun sweepOrphanedNovelPluginDownloads(context: Context, nowMs: Long = System.currentTimeMillis()): List<String> {
    val children = File(context.cacheDir, EXTENSION_APK_DOWNLOAD_DIR).listFiles() ?: return emptyList()
    val deleted = mutableListOf<String>()
    for (child in children) {
        val expired = nowMs - child.lastModified() > ORPHAN_DOWNLOAD_MAX_AGE_MS
        if (!child.isFile || (!child.name.endsWith(".apk.part") && !expired)) continue
        if (child.delete()) deleted += child.absolutePath
    }
    return deleted
}

private const val INSTALL_TIMEOUT_MS = 5 * 60 * 1000L

/** Backend-reported removal should land quickly; anything longer smells like a no-op uninstall. */
private const val UNINSTALL_VERIFY_TIMEOUT_MS = 15_000L

/** The ACTION_DELETE fallback is user-driven: give the system dialog a generous budget. */
private const val UNINSTALL_FALLBACK_TIMEOUT_MS = 120_000L

private const val UNINSTALL_POLL_INTERVAL_MS = 250L

class KotlinNovelExtensionInstaller(
    private val context: Context,
    private val client: OkHttpClient,
    private val basePreferences: BasePreferences,
    private val unifiedInstaller: UnifiedApkExtensionInstaller,
) {
    private val pendingInstallStore = PendingApkInstallStore(basePreferences)
    private val trustExtension: TrustNovelExtension by injectLazy()

    suspend fun install(plugin: NovelPlugin.Available): NovelPlugin.Installed {
        val apkUrl = plugin.apkUrl ?: plugin.url
        val pkgName = plugin.pkgName ?: plugin.id
        // Reuse the shared downloader: it writes to "<name>.apk.part" and renames only after a
        // complete download, so a dead process cannot leave a truncated APK that looks final.
        val apkFile = OkHttpExtensionApkDownloader(context, client, basePreferences)
            .download(
                url = apkUrl,
                packageName = pkgName,
                displayName = plugin.name,
                kind = ApkExtensionKind.NOVEL_KOTLIN,
            )

        // Validate before handing the APK to a system installer: the index checksum and the
        // package identity are the only gates a shared backend has (a private install re-checks
        // signatures in the loader).
        if (plugin.sha256.isNotBlank() && sha256Of(apkFile) != plugin.sha256) {
            apkFile.delete()
            error("Checksum mismatch for Kotlin novel extension $pkgName")
        }
        val archiveInfo = runCatching {
            context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, packageInfoFlags)
        }.getOrNull()
        if (archiveInfo == null || !isNovelExtensionApk(archiveInfo)) {
            apkFile.delete()
            error("Downloaded file is not a Kotlin novel extension APK: $pkgName")
        }
        if (archiveInfo.packageName != pkgName) {
            apkFile.delete()
            error("Downloaded APK package mismatch: expected=$pkgName actual=${archiveInfo.packageName}")
        }

        val installer = basePreferences.extensionInstaller().get()
        // Keep a private-only copy private: updating it through a system backend would add a
        // second install of the same package next to the private file (mirrors the manga/anime
        // isUpdateForPrivatelyInstalled routing).
        val keepPrivate = !context.isPackageInstalled(pkgName) &&
            KotlinNovelExtensionLoader.hasPrivateExtensionFile(context, pkgName)
        val backend = if (keepPrivate) ApkInstallBackend.PRIVATE else installer.toApkInstallBackend()
        val terminalStep = unifiedInstaller.install(
            ApkInstallRequest(
                id = pkgName,
                packageName = pkgName,
                displayName = plugin.name,
                uri = apkFile.getUriCompat(context),
                file = apkFile,
                backend = backend,
                kind = ApkExtensionKind.NOVEL_KOTLIN,
            ),
        ).first { it.isCompleted() }
        if (terminalStep != InstallStep.Installed) {
            // The backend reported a generic failure — check whether the signing key changed,
            // so the UI can offer reinstall-with-uninstall instead of a bare error.
            if (ExtensionSignatureComparison.signaturesDiffer(context, apkFile, pkgName) == true) {
                novelManager()?.reportSignatureMismatch(plugin.id)
            }
            error("Failed to install Kotlin novel extension $pkgName using ${installer.name}: $terminalStep")
        }
        // A system install finished — carry the user's trust to the new version when the signing
        // key is unchanged, so an update does not silently flip the extension back to Untrusted
        // (mirrors the manga/anime manager's carryTrustToNewVersion; the private path handles this
        // inside installPrivateExtensionFile).
        carryTrustToNewVersion(pkgName)
        return plugin.toInstalledKotlin()
    }

    private fun carryTrustToNewVersion(pkgName: String) {
        val info = runCatching {
            context.packageManager.getPackageInfo(pkgName, 0)
        }.getOrNull() ?: return
        val signatures = ExtensionSignatureComparison.installedSignatures(context, pkgName) ?: return
        signatures.lastOrNull()?.let { signatureHash ->
            runCatching {
                trustExtension.trustIfSameSigner(
                    pkgName,
                    PackageInfoCompat.getLongVersionCode(info),
                    signatureHash,
                )
            }.onFailure {
                logcat(LogPriority.WARN, it) { "Failed to carry trust to new version for $pkgName" }
            }
        }
    }

    private fun novelManager(): NovelExtensionManager? =
        runCatching { Injekt.get<NovelExtensionManager>() }.getOrNull()

    @Suppress("DEPRECATION")
    private val packageInfoFlags = PackageManager.GET_CONFIGURATIONS or
        PackageManager.GET_META_DATA or
        PackageManager.GET_SIGNATURES or
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)

    private fun isNovelExtensionApk(pkgInfo: PackageInfo): Boolean {
        return pkgInfo.reqFeatures.orEmpty().any { it.name == "tachiyomi.novelextension" }
    }

    /** SHA-256 hex of a file without loading it fully into memory. */
    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    /** Best-effort cancel of an in-flight install: stops awaiting the result and clears the
     *  pending-permission entry. A system dialog that is already visible cannot be aborted. */
    fun cancelInstall(pkgName: String) {
        unifiedInstaller.cancel(pkgName)
    }

    suspend fun uninstall(plugin: NovelPlugin.Installed) {
        val pkgName = plugin.pkgName ?: plugin.id
        if (context.isPackageInstalled(pkgName)) {
            // Route through the backend that installed the plugin so the uninstall is performed by
            // the same mechanism (Shizuku/Dhizuku do not need the system dialog this way).
            val result = unifiedInstaller.uninstall(
                ApkUninstallRequest(
                    packageName = pkgName,
                    kind = ApkExtensionKind.NOVEL_KOTLIN,
                    backend = basePreferences.extensionInstaller().get().toApkInstallBackend(),
                ),
            )
            when (result) {
                is ApkInstallResult.Installed -> {
                    // Never trust the result blindly (B1): a PRIVATE-backend "success" may have
                    // removed only the private copy while the system package survives, which
                    // would strand replacePluginFromRepo's removal poll until its timeout.
                    if (!awaitPackageRemoved(pkgName, UNINSTALL_VERIFY_TIMEOUT_MS)) {
                        logcat(LogPriority.WARN) {
                            "Kotlin novel extension $pkgName still installed after backend uninstall; requesting system uninstall"
                        }
                        runCatching {
                            Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkgName"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                .let(context::startActivity)
                        }.onFailure {
                            logcat(LogPriority.WARN, it) {
                                "Failed to launch system uninstall for Kotlin novel extension $pkgName"
                            }
                        }
                        if (!awaitPackageRemoved(pkgName, UNINSTALL_FALLBACK_TIMEOUT_MS)) {
                            error("Kotlin novel extension $pkgName was not uninstalled; keeping private copy")
                        }
                    }
                    withContext(Dispatchers.IO) {
                        // Delete only after confirmed removal: cancelling the dialog must keep
                        // the private copy, otherwise the extension would lose all its files.
                        KotlinNovelExtensionLoader.uninstallPrivateExtension(context, pkgName)
                    }
                }
                is ApkInstallResult.Cancelled -> Unit
                is ApkInstallResult.Error -> {
                    logcat(LogPriority.WARN) { "Unified uninstall failed for $pkgName: ${result.reason}" }
                    runCatching {
                        Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkgName"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .let(context::startActivity)
                    }.onFailure {
                        logcat(LogPriority.WARN, it) {
                            "Failed to launch system uninstall for Kotlin novel extension $pkgName"
                        }
                    }
                }
            }
        } else {
            // No system package: private-only copy, nothing to confirm.
            withContext(Dispatchers.IO) {
                KotlinNovelExtensionLoader.uninstallPrivateExtension(context, pkgName)
            }
        }
    }

    /** Polls until [pkgName] disappears; false when it is still installed after [timeoutMs]. */
    private suspend fun awaitPackageRemoved(pkgName: String, timeoutMs: Long): Boolean =
        awaitPollCondition(timeoutMs) { !context.isPackageInstalled(pkgName) }
}

/**
 * Polls [condition] every [UNINSTALL_POLL_INTERVAL_MS] until it holds or [timeoutMs] elapses.
 * Internal + parameterized so the polling contract stays unit-testable without Android.
 */
internal suspend fun awaitPollCondition(timeoutMs: Long, condition: suspend () -> Boolean): Boolean =
    withTimeoutOrNull(timeoutMs) {
        while (!condition()) {
            delay(UNINSTALL_POLL_INTERVAL_MS)
        }
        true
    } ?: false

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
    val plugin: NovelPlugin,
    val sources: List<NovelSource>,
)

@SuppressLint("PackageManagerGetSignatures")
object KotlinNovelExtensionLoader {
    private const val EXTENSION_FEATURE_NOVEL = "tachiyomi.novelextension"
    private const val METADATA_SOURCE_CLASS = "tachiyomi.novelextension.class"
    private const val METADATA_SOURCE_FACTORY = "tachiyomi.novelextension.factory"
    private const val METADATA_NSFW = "tachiyomi.novelextension.nsfw"
    private const val METADATA_NAME = "tachiyomix.name"
    private const val METADATA_CONTENT_WARNING = "tachiyomix.contentWarning"
    private const val METADATA_NOVEL = "tachiyomi.novelextension.novel"
    private const val METADATA_EXTENSION_LIB = "tachiyomix.extensionLib"
    private const val PRIVATE_EXTENSION_EXTENSION = "ext"
    private const val LIB_VERSION_MIN = 1.4
    private const val LIB_VERSION_MAX = 1.7

    val SUPPORTED_LIB_VERSIONS = listOf(1.4, 1.5, 1.6, 1.7)

    private val preferences: SourcePreferences by injectLazy()
    private val trustExtension: TrustNovelExtension by injectLazy()

    @Suppress("DEPRECATION")
    private val PACKAGE_FLAGS = PackageManager.GET_CONFIGURATIONS or
        PackageManager.GET_META_DATA or
        PackageManager.GET_SIGNATURES or
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)

    private fun getPrivateExtensionDir(context: Context) = File(context.filesDir, "novel_exts")

    fun installPrivateExtensionFile(
        context: Context,
        file: File,
        pkgName: String,
    ): PrivateExtensionInstallResult {
        if (!pkgName.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)+$"))) {
            logcat(LogPriority.ERROR) { "Invalid package name: $pkgName" }
            return PrivateExtensionInstallResult.InvalidApk
        }

        val pkgManager = context.packageManager
        val archiveInfo = pkgManager.getPackageArchiveInfo(file.absolutePath, PACKAGE_FLAGS)
            ?.takeIf { isPackageAnExtension(it) }
            ?: run {
                logcat(LogPriority.ERROR) { "File is not a Kotlin novel extension APK: ${file.absolutePath}" }
                return PrivateExtensionInstallResult.InvalidApk
            }
        if (archiveInfo.packageName != pkgName) {
            logcat(LogPriority.ERROR) {
                "Kotlin novel extension package mismatch: expected=$pkgName actual=${archiveInfo.packageName}"
            }
            return PrivateExtensionInstallResult.InvalidApk
        }

        val privateExtensionDir = getPrivateExtensionDir(context)
        if (!privateExtensionDir.exists() && !privateExtensionDir.mkdirs()) {
            logcat(LogPriority.ERROR) { "Failed to create private Kotlin novel extension directory." }
            return PrivateExtensionInstallResult.Error
        }

        val target = File(privateExtensionDir, "$pkgName.$PRIVATE_EXTENSION_EXTENSION")
        // A private extension is loaded without the system verifying anything, so the signature is
        // what ties an update to the publisher of the installed copy. Cross-store re-publication
        // goes through uninstall + install, where no installed copy is left to replace.
        val installedInfo = target.takeIf { it.exists() }
            ?.let { pkgManager.getPackageArchiveInfo(it.absolutePath, PACKAGE_FLAGS) }
        if (installedInfo != null) {
            if (PackageInfoCompat.getLongVersionCode(archiveInfo) <
                PackageInfoCompat.getLongVersionCode(installedInfo)
            ) {
                logcat(LogPriority.ERROR) {
                    "Refusing to downgrade private Kotlin novel extension $pkgName."
                }
                return PrivateExtensionInstallResult.Downgrade
            }
            if (!canReplacePrivateExtension(
                    installedVersionCode = PackageInfoCompat.getLongVersionCode(installedInfo),
                    newVersionCode = PackageInfoCompat.getLongVersionCode(archiveInfo),
                    installedSignatures = getSignatures(installedInfo).orEmpty(),
                    newSignatures = getSignatures(archiveInfo).orEmpty(),
                )
            ) {
                logcat(LogPriority.ERROR) {
                    "Refusing to replace private Kotlin novel extension $pkgName: signature mismatch."
                }
                return PrivateExtensionInstallResult.SignatureMismatch
            }
        }
        val part = File(privateExtensionDir, "$pkgName.$PRIVATE_EXTENSION_EXTENSION.part")
        // Set once the previous good file has been removed for the swap: staging failures
        // before that point must never destroy a working extension.
        var previousFileRemoved = false
        return try {
            part.delete()
            file.copyAndSetReadOnlyTo(part, overwrite = true)
            if (target.exists()) {
                if (!target.delete()) {
                    logcat(LogPriority.ERROR) {
                        "Failed to replace existing private Kotlin novel extension file: ${target.absolutePath}"
                    }
                    part.delete()
                    return PrivateExtensionInstallResult.Error
                }
                previousFileRemoved = true
            }
            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true)
                target.setReadOnly()
                part.delete()
            }
            // Keep the user's trust across the update when the signing key is unchanged.
            if (installedInfo != null) {
                getSignatures(archiveInfo)?.lastOrNull()?.let { signatureHash ->
                    trustExtension.trustIfSameSigner(
                        pkgName,
                        PackageInfoCompat.getLongVersionCode(archiveInfo),
                        signatureHash,
                    )
                }
            }
            PrivateExtensionInstallResult.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to install private Kotlin novel extension file for $pkgName." }
            // Best-effort restore when the swap had already removed the previous good file.
            if (previousFileRemoved && !target.exists() && part.exists()) {
                runCatching { part.renameTo(target) }
            }
            part.delete()
            PrivateExtensionInstallResult.Error
        }
    }

    fun uninstallPrivateExtension(context: Context, pkgName: String) {
        File(getPrivateExtensionDir(context), "$pkgName.$PRIVATE_EXTENSION_EXTENSION").delete()
    }

    /** Whether the private store holds a copy of [pkgName] (no system package needed). */
    fun hasPrivateExtensionFile(context: Context, pkgName: String): Boolean {
        return File(getPrivateExtensionDir(context), "$pkgName.$PRIVATE_EXTENSION_EXTENSION").isFile
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

        val trustedFingerprints = runBlocking {
            trustExtension.getTrustedFingerprints()
        }

        return runBlocking {
            extPkgs.map { extensionInfo ->
                async { loadExtension(context, extensionInfo, trustedFingerprints) }
            }.awaitAll().filterNotNull()
        }
    }

    suspend fun isExtensionPackage(context: Context, pkgName: String): Boolean {
        return getExtensionPackageInfoFromPkgName(context, pkgName) != null
    }

    private suspend fun loadExtension(
        context: Context,
        extensionInfo: ExtensionInfo,
        trustedFingerprints: Set<String>,
    ): KotlinNovelExtensionLoadResult? {
        val pkgManager = context.packageManager
        val pkgInfo = extensionInfo.packageInfo
        val appInfo = pkgInfo.applicationInfo ?: return null
        val pkgName = pkgInfo.packageName
        val appLabel = pkgManager.getApplicationLabel(appInfo).toString()
        val extName = appInfo.metaData?.getString(METADATA_NAME)
            ?: appLabel.substringAfter("Tsundoku: ").substringAfter("NovelApp: ")
        val versionName = pkgInfo.versionName ?: return null
        val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo).toInt()
        val rawLibVersion = appInfo.metaData?.getDouble(METADATA_EXTENSION_LIB)?.takeUnless { it == 0.0 }
            ?: appInfo.metaData?.getFloat(METADATA_EXTENSION_LIB)?.toDouble()?.takeUnless { it == 0.0 }
            ?: versionName.substringBeforeLast('.').toDoubleOrNull()
        val libVersion = if (rawLibVersion != null) kotlin.math.round(rawLibVersion * 100.0) / 100.0 else null
        if (libVersion == null || libVersion !in SUPPORTED_LIB_VERSIONS) {
            logcat(LogPriority.WARN) { "Kotlin novel extension $pkgName has unsupported lib version $libVersion" }
            return null
        }
        val signatures = getSignatures(pkgInfo)
        if (signatures.isNullOrEmpty()) {
            logcat(LogPriority.WARN) { "Kotlin novel extension $pkgName is not signed" }
            return null
        }
        val isNsfw = (appInfo.metaData?.getInt(METADATA_CONTENT_WARNING) ?: 0) > 0 ||
            appInfo.metaData?.getInt(METADATA_NSFW) == 1
        if (!preferences.showNsfwSource().get() && isNsfw) {
            logcat(LogPriority.WARN) { "NSFW Kotlin novel extension $pkgName not allowed" }
            return null
        }
        val iconUrl = runCatching { saveIcon(context, pkgName, appInfo.loadIcon(pkgManager)) }
            .onFailure { logcat(LogPriority.WARN, it) { "Failed to save Kotlin novel extension icon for $pkgName" } }
            .getOrNull()
        if (!trustExtension.isTrusted(pkgInfo, signatures, trustedFingerprints)) {
            logcat(LogPriority.WARN) { "Kotlin novel extension $pkgName is not trusted" }
            return KotlinNovelExtensionLoadResult(
                plugin = NovelPlugin.Untrusted(
                    id = pkgName,
                    name = extName,
                    site = "",
                    lang = "",
                    versionCode = versionCode,
                    versionName = versionName,
                    url = "",
                    iconUrl = iconUrl,
                    customJs = null,
                    customCss = null,
                    hasSettings = false,
                    sha256 = "",
                    repoUrl = "",
                    pkgName = pkgName,
                    signatureHash = signatures.last(),
                    isKotlinExtension = true,
                    isNsfw = isNsfw,
                ),
                sources = emptyList(),
            )
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
            isNsfw = isNsfw,
            isShared = extensionInfo.isShared,
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
) : NovelSource, NovelSiteSource, NovelPluginIdentitySource, NovelImageRequestSource {
    override val id: Long = source.id
    override val name: String = source.name
    override val lang: String = source.lang
    override val isKotlinExtension: Boolean = true
    override val siteUrl: String? = (source as? NovelSiteSource)?.siteUrl ?: (source as? NovelHttpSource)?.baseUrl

    override suspend fun getImageRequestHeaders(): Map<String, String> {
        val headers = (source as? NovelHttpSource)?.headers
            ?: (source as? HttpSource)?.headers
            ?: return emptyMap()
        return (0 until headers.size).associate { headers.name(it) to headers.value(it) }
    }

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
) : KotlinIdentityBasicNovelSourceAdapter(catalogueSource, pluginId), NovelHttpSource, NovelCatalogueSource {
    override val baseUrl: String get() = (catalogueSource as? NovelHttpSource)?.baseUrl ?: siteUrl.orEmpty()
    override val headers: okhttp3.Headers get() = (catalogueSource as? NovelHttpSource)?.headers
        ?: okhttp3.Headers.Builder().build()
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
) : NovelSource, NovelSiteSource, NovelPluginIdentitySource, NovelImageRequestSource {
    override val id: Long = source.id
    override val name: String = source.name
    override val lang: String = source.lang
    override val isKotlinExtension: Boolean = true
    override val siteUrl: String? = (source as? HttpSource)?.baseUrl

    override suspend fun getImageRequestHeaders(): Map<String, String> {
        val headers = (source as? HttpSource)?.headers ?: return emptyMap()
        return (0 until headers.size).associate { headers.name(it) to headers.value(it) }
    }

    override suspend fun getNovelDetails(novel: SNovel): SNovel {
        return source.getMangaDetails(novel.toManga()).toNovel(source)
    }

    override suspend fun getChapterList(novel: SNovel): List<SNovelChapter> {
        return source.getChapterList(novel.toManga()).map { it.toNovelChapter(source) }
    }

    override suspend fun getChapterText(chapter: SNovelChapter): String {
        val pages = runCatching { source.getPageList(chapter.toChapter()) }.getOrNull()
        if (pages.isNullOrEmpty()) {
            val fallbackPage = Page(0, chapter.url)
            return source.fetchPageText(fallbackPage)
        }
        val textBlocks = pages.map { page ->
            runCatching { source.fetchPageText(page) }.getOrDefault("")
        }.filter { it.isNotBlank() }
        return if (textBlocks.isNotEmpty()) {
            textBlocks.joinToString("\n\n")
        } else {
            source.fetchPageText(pages.first())
        }
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

internal fun CatalogueSource.asKotlinNovelCatalogueSource(pluginId: String): NovelCatalogueSource {
    return KotlinCatalogueNovelSourceAdapter(this, pluginId)
}

private val filterAdapter: KotlinNovelFilterAdapter = KotlinNovelFilterAdapterImpl()

private open class KotlinCatalogueNovelSourceAdapter(
    source: CatalogueSource,
    pluginId: String,
) : KotlinMangaNovelSourceAdapter(source, pluginId), NovelHttpSource, NovelCatalogueSource {
    private val catalogueSource: CatalogueSource = source

    override val baseUrl: String get() = (source as? HttpSource)?.baseUrl.orEmpty()
    override val headers: okhttp3.Headers get() = (source as? HttpSource)?.headers ?: okhttp3.Headers.Builder().build()
    override val supportsLatest: Boolean = catalogueSource.supportsLatest

    override suspend fun getPopularNovels(page: Int): NovelsPage {
        return catalogueSource.getPopularManga(page).toNovelsPage(source)
    }

    override suspend fun getPopularNovels(page: Int, filters: NovelFilterList): NovelsPage {
        return when {
            filters.isEmpty() -> getPopularNovels(page)
            else -> getSearchNovels(page, query = "", filters = filters)
        }
    }

    override suspend fun getSearchNovels(page: Int, query: String, filters: NovelFilterList): NovelsPage {
        return catalogueSource.getSearchManga(
            page,
            query,
            filterAdapter.toMangaFilterList(filters, catalogueSource),
        ).toNovelsPage(source)
    }

    override suspend fun getLatestUpdates(page: Int): NovelsPage {
        return catalogueSource.getLatestUpdates(page).toNovelsPage(source)
    }

    override suspend fun getLatestUpdates(page: Int, filters: NovelFilterList): NovelsPage {
        return when {
            filters.isEmpty() -> getLatestUpdates(page)
            else -> getSearchNovels(page, query = "", filters = filters)
        }
    }

    override fun getFilterList(): NovelFilterList = filterAdapter.toNovelFilterList(catalogueSource.getFilterList())

    @Deprecated("Use the non-RxJava API instead.")
    @Suppress("DEPRECATION")
    override fun fetchPopularNovels(page: Int): Observable<NovelsPage> {
        return catalogueSource.fetchPopularManga(page).map { it.toNovelsPage(source) }
    }

    @Deprecated("Use the non-RxJava API instead.")
    @Suppress("DEPRECATION")
    override fun fetchSearchNovels(page: Int, query: String, filters: NovelFilterList): Observable<NovelsPage> {
        return catalogueSource.fetchSearchManga(
            page,
            query,
            filterAdapter.toMangaFilterList(filters, catalogueSource),
        ).map {
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
        it.status = runCatching { status }.getOrDefault(5).toNovelStatus()
        it.thumbnail_url = runCatching { thumbnail_url }.getOrNull()?.let { resolveSourceUrl(source, it) }
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
        it.status = runCatching { status }.getOrDefault(SNovel.UNKNOWN).toMangaStatus()
        it.thumbnail_url = runCatching { thumbnail_url }.getOrNull()
        it.update_strategy = runCatching { update_strategy }.getOrDefault(it.update_strategy)
        it.initialized = runCatching { initialized }.getOrDefault(false)
    }
}

private fun Int.toNovelStatus(): Int = when (this) {
    0 -> SNovel.ONGOING
    1 -> SNovel.COMPLETED
    2 -> SNovel.LICENSED
    3 -> SNovel.PUBLISHING_FINISHED
    4 -> SNovel.CANCELLED
    6 -> SNovel.ON_HIATUS
    else -> SNovel.UNKNOWN
}

private fun Int.toMangaStatus(): Int = when (this) {
    SNovel.ONGOING -> 0
    SNovel.COMPLETED -> 1
    SNovel.LICENSED -> 2
    SNovel.PUBLISHING_FINISHED -> 3
    SNovel.CANCELLED -> 4
    SNovel.ON_HIATUS -> 6
    else -> 5
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
