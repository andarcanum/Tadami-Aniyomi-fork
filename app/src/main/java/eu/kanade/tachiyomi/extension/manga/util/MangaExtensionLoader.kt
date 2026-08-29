package eu.kanade.tachiyomi.extension.manga.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import dalvik.system.PathClassLoader
import eu.kanade.domain.extension.manga.interactor.TrustMangaExtension
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.canReplacePrivateExtension
import eu.kanade.tachiyomi.extension.installer.PrivateExtensionInstallResult
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.extension.manga.model.MangaLoadResult
import eu.kanade.tachiyomi.extension.matchesExtensionFeature
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.util.lang.Hash
import eu.kanade.tachiyomi.util.storage.copyAndSetReadOnlyTo
import eu.kanade.tachiyomi.util.system.ChildFirstPathClassLoader
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy
import java.io.File

/**
 * Class that handles the loading of the extensions. Supports two kinds of extensions:
 *
 * 1. Shared extension: This extension is installed to the system with package
 * installer, so other variants of Tachiyomi/Aniyomi and its forks can also use this extension.
 *
 * 2. Private extension: This extension is put inside private data directory of the
 * running app, so this extension can only be used by the running app and not shared
 * with other apps.
 *
 * When both kinds of extensions are installed with a same package name, shared
 * extension will be used unless the version codes are different. In that case the
 * one with higher version code will be used.
 */
@SuppressLint("PackageManagerGetSignatures")
internal object MangaExtensionLoader {

    private val preferences: SourcePreferences by injectLazy()
    private val trustExtension: TrustMangaExtension by injectLazy()
    private val loadNsfwSource by lazy {
        preferences.showNsfwSource().get()
    }

    private const val EXTENSION_FEATURE = "tachiyomi.extension"
    private const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"
    private const val METADATA_SOURCE_FACTORY = "tachiyomi.extension.factory"
    private const val METADATA_NSFW = "tachiyomi.extension.nsfw"
    private const val METADATA_NAME = "tachiyomix.name"
    private const val METADATA_CONTENT_WARNING = "tachiyomix.contentWarning"
    private const val METADATA_EXTENSION_LIB = "tachiyomix.extensionLib"
    const val LIB_VERSION_MIN = 1.4
    const val LIB_VERSION_MAX = 1.6

    /**
     * Accepted extensions-lib generations. Every published manga extension is still on 1.4 - the
     * keiyoushi build plugin whitelists exactly that value - so 1.5/1.6 are headroom, not something
     * in use. The 1.6-only combined update API (getMangaUpdate) is deliberately NOT routed here:
     * no extension implements it, and the library update path would gain nothing but risk.
     */
    val SUPPORTED_LIB_VERSIONS = listOf(1.4, 1.5, 1.6)

    @Suppress("DEPRECATION")
    private val PACKAGE_FLAGS = PackageManager.GET_CONFIGURATIONS or
        PackageManager.GET_META_DATA or
        PackageManager.GET_SIGNATURES or
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)

    private const val PRIVATE_EXTENSION_EXTENSION = "ext"

    private var isMigrated = false

    private fun getPrivateExtensionDir(context: Context): File {
        val targetDir = File(context.filesDir, "manga_exts")
        if (!isMigrated) {
            synchronized(this) {
                if (!isMigrated) {
                    migrateLegacyPrivateExtensions(context, targetDir)
                    isMigrated = true
                }
            }
        }
        return targetDir
    }

    private fun migrateLegacyPrivateExtensions(context: Context, targetDir: File) {
        val legacyDir = File(context.filesDir, "exts")
        if (!legacyDir.isDirectory) return

        legacyDir.listFiles()?.forEach { file ->
            if (file.isFile && file.extension == PRIVATE_EXTENSION_EXTENSION) {
                val pkgName = file.nameWithoutExtension
                if (pkgName.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)+$"))) {
                    // Classify by the manifest feature, not by the file name: manga extensions such
                    // as ...extension.fr.animesama contain ".anime" and were moved into the anime
                    // directory, where nothing ever loads them again.
                    val archiveInfo = context.packageManager.getPackageArchiveInfo(file.absolutePath, PACKAGE_FLAGS)
                    if (matchesExtensionFeature(archiveInfo, EXTENSION_FEATURE) == true) {
                        targetDir.mkdirs()
                        val targetFile = File(targetDir, file.name)
                        file.renameTo(targetFile)
                    }
                }
            }
        }

        if (legacyDir.listFiles().isNullOrEmpty()) {
            legacyDir.delete()
        }
    }

    fun installPrivateExtensionFile(context: Context, file: File): PrivateExtensionInstallResult {
        val extension = context.packageManager.getPackageArchiveInfo(
            file.absolutePath,
            PACKAGE_FLAGS,
        )
            ?.takeIf { isPackageAnExtension(it) } ?: return PrivateExtensionInstallResult.InvalidApk

        val pkgName = extension.packageName
        if (!pkgName.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)+$"))) {
            logcat(LogPriority.ERROR) { "Invalid package name: $pkgName" }
            return PrivateExtensionInstallResult.InvalidApk
        }
        val currentExtension = getMangaExtensionPackageInfoFromPkgName(
            context,
            extension.packageName,
        )
        val newSignatures = getSignatures(extension)

        if (currentExtension != null) {
            if (PackageInfoCompat.getLongVersionCode(extension) <
                PackageInfoCompat.getLongVersionCode(currentExtension)
            ) {
                logcat(LogPriority.ERROR) { "Installed extension version is higher. Downgrading is not allowed." }
                return PrivateExtensionInstallResult.Downgrade
            }

            val extensionSignatures = newSignatures
            if (extensionSignatures.isNullOrEmpty()) {
                logcat(LogPriority.ERROR) { "Extension to be installed is not signed." }
                return PrivateExtensionInstallResult.InvalidApk
            }

            // Cross-store re-publication is handled by the reinstall path (uninstall first), so a
            // signature change here means the replacement is not from the installed publisher.
            if (!canReplacePrivateExtension(
                    installedVersionCode = PackageInfoCompat.getLongVersionCode(currentExtension),
                    newVersionCode = PackageInfoCompat.getLongVersionCode(extension),
                    installedSignatures = getSignatures(currentExtension).orEmpty(),
                    newSignatures = extensionSignatures,
                )
            ) {
                logcat(LogPriority.ERROR) { "Installed extension signature is not matched." }
                return PrivateExtensionInstallResult.SignatureMismatch
            }
        }

        val privateExtensionDir = getPrivateExtensionDir(context)
        if (!privateExtensionDir.exists() && !privateExtensionDir.mkdirs()) {
            logcat(LogPriority.ERROR) { "Failed to create private extension directory." }
            return PrivateExtensionInstallResult.Error
        }

        val target = File(
            privateExtensionDir,
            "${extension.packageName}.$PRIVATE_EXTENSION_EXTENSION",
        )
        // Write to a .part file and rename only after a complete copy: dying between the old
        // delete->copy steps would leave the extension without any loadable file.
        val part = File(privateExtensionDir, "${extension.packageName}.$PRIVATE_EXTENSION_EXTENSION.part")
        // Set once the previous good file has been removed for the swap: staging failures
        // before that point must never destroy a working extension.
        var previousFileRemoved = false
        return try {
            part.delete()
            file.copyAndSetReadOnlyTo(part, overwrite = true)
            if (target.exists()) {
                if (!target.delete()) {
                    logcat(LogPriority.ERROR) {
                        "Failed to replace existing private extension file: ${target.absolutePath}"
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
            if (currentExtension != null) {
                MangaExtensionInstallReceiver.notifyReplaced(context, extension.packageName)
                // Keep the user's trust across the update when the signing key is unchanged.
                newSignatures?.lastOrNull()?.let { signatureHash ->
                    trustExtension.trustIfSameSigner(
                        extension.packageName,
                        PackageInfoCompat.getLongVersionCode(extension),
                        signatureHash,
                    )
                }
            } else {
                MangaExtensionInstallReceiver.notifyAdded(context, extension.packageName)
            }
            PrivateExtensionInstallResult.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to copy extension file." }
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

    /**
     * Return a list of all the available extensions initialized concurrently.
     *
     * @param context The application context.
     */
    fun loadMangaExtensions(context: Context): List<MangaLoadResult> {
        val pkgManager = context.packageManager

        val installedPkgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pkgManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong()),
            )
        } else {
            pkgManager.getInstalledPackages(PACKAGE_FLAGS)
        }

        val sharedExtPkgs = installedPkgs
            .asSequence()
            .filter { isPackageAnExtension(it) }
            .map { MangaExtensionInfo(packageInfo = it, isShared = true) }

        val privateExtPkgs = getPrivateExtensionDir(context)
            .listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.extension == PRIVATE_EXTENSION_EXTENSION }
            ?.mapNotNull {
                // Just in case, since Android 14+ requires them to be read-only
                if (it.canWrite()) {
                    it.setReadOnly()
                }

                val path = it.absolutePath
                pkgManager.getPackageArchiveInfo(path, PACKAGE_FLAGS)
                    ?.apply { applicationInfo!!.fixBasePaths(path) }
            }
            ?.filter { isPackageAnExtension(it) }
            ?.map { MangaExtensionInfo(packageInfo = it, isShared = false) }
            ?.toList()
            .orEmpty()
        val privateExtPkgsByPkgName = privateExtPkgs.associateBy { it.packageInfo.packageName }

        val extPkgs = (sharedExtPkgs + privateExtPkgs.asSequence())
            // Remove duplicates. Shared takes priority than private by default
            .distinctBy { it.packageInfo.packageName }
            // Compare version number
            .mapNotNull { sharedPkg ->
                val privatePkg = privateExtPkgsByPkgName[sharedPkg.packageInfo.packageName]
                selectExtensionPackage(sharedPkg, privatePkg)
            }
            .toList()

        if (extPkgs.isEmpty()) return emptyList()

        val trustedFingerprints = runBlocking {
            trustExtension.getTrustedFingerprints()
        }

        // Load each extension concurrently and wait for completion
        return runBlocking {
            val deferred = extPkgs.map {
                async { loadMangaExtension(context, it, trustedFingerprints) }
            }
            deferred.awaitAll()
        }
    }

    /**
     * Attempts to load an extension from the given package name. It checks if the extension
     * contains the required feature flag before trying to load it.
     */
    suspend fun loadMangaExtensionFromPkgName(context: Context, pkgName: String): MangaLoadResult {
        val extensionPackage = getMangaExtensionInfoFromPkgName(context, pkgName)
        if (extensionPackage == null) {
            logcat(LogPriority.ERROR) { "Extension package is not found ($pkgName)" }
            return MangaLoadResult.Error
        }
        return loadMangaExtension(context, extensionPackage)
    }

    fun getMangaExtensionPackageInfoFromPkgName(context: Context, pkgName: String): PackageInfo? {
        return getMangaExtensionInfoFromPkgName(context, pkgName)?.packageInfo
    }

    private fun getMangaExtensionInfoFromPkgName(context: Context, pkgName: String): MangaExtensionInfo? {
        val privateExtensionFile = File(
            getPrivateExtensionDir(context),
            "$pkgName.$PRIVATE_EXTENSION_EXTENSION",
        )
        val privatePkg = if (privateExtensionFile.isFile) {
            context.packageManager.getPackageArchiveInfo(
                privateExtensionFile.absolutePath,
                PACKAGE_FLAGS,
            )
                ?.takeIf { isPackageAnExtension(it) }
                ?.let {
                    it.applicationInfo!!.fixBasePaths(privateExtensionFile.absolutePath)
                    MangaExtensionInfo(
                        packageInfo = it,
                        isShared = false,
                    )
                }
        } else {
            null
        }

        val sharedPkg = try {
            context.packageManager.getPackageInfo(pkgName, PACKAGE_FLAGS)
                .takeIf { isPackageAnExtension(it) }
                ?.let {
                    MangaExtensionInfo(
                        packageInfo = it,
                        isShared = true,
                    )
                }
        } catch (error: PackageManager.NameNotFoundException) {
            null
        }

        return selectExtensionPackage(sharedPkg, privatePkg)
    }

    /**
     * Loads an extension
     *
     * @param context The application context.
     * @param extensionInfo The extension to load.
     */
    private suspend fun loadMangaExtension(
        context: Context,
        extensionInfo: MangaExtensionInfo,
        trustedFingerprints: Set<String>? = null,
    ): MangaLoadResult {
        val pkgManager = context.packageManager
        val pkgInfo = extensionInfo.packageInfo
        val appInfo = pkgInfo.applicationInfo!!
        val pkgName = pkgInfo.packageName

        val extName = appInfo.metaData?.getString(METADATA_NAME)
            ?: pkgManager.getApplicationLabel(appInfo).toString().substringAfter(
                "Tachiyomi: ",
            )
        val versionName = pkgInfo.versionName
        val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)

        if (versionName.isNullOrEmpty()) {
            logcat(LogPriority.WARN) { "Missing versionName for extension $extName" }
            return MangaLoadResult.Error
        }

        // Validate lib version. MetaData values can be stored as Float, Double or String
        // depending on how the extension manifest declares them; typed Bundle getters throw
        // (and log a warning) on a mismatch, so read the raw value and convert explicitly.
        @Suppress("DEPRECATION") // Bundle.get(String) is deprecated in API 33; no raw-value replacement exists
        val rawLibVersion = when (val value = appInfo.metaData?.get(METADATA_EXTENSION_LIB)) {
            is Number -> value.toDouble().takeUnless { it == 0.0 }
            is String -> value.toDoubleOrNull()?.takeUnless { it == 0.0 }
            else -> null
        } ?: versionName.substringBeforeLast('.').toDoubleOrNull()
        val libVersion = if (rawLibVersion != null) kotlin.math.round(rawLibVersion * 100.0) / 100.0 else null
        if (libVersion == null || libVersion !in SUPPORTED_LIB_VERSIONS) {
            logcat(LogPriority.WARN) {
                "Lib version is $libVersion, while only versions " +
                    "$SUPPORTED_LIB_VERSIONS are allowed"
            }
            return MangaLoadResult.Error
        }

        val signatures = getSignatures(pkgInfo)
        val isTrusted = if (signatures.isNullOrEmpty()) {
            false
        } else if (trustedFingerprints != null) {
            trustExtension.isTrusted(pkgInfo, signatures, trustedFingerprints)
        } else {
            trustExtension.isTrusted(pkgInfo, signatures)
        }

        if (signatures.isNullOrEmpty()) {
            logcat(LogPriority.WARN) { "Package $pkgName isn't signed" }
            return MangaLoadResult.Error
        } else if (!isTrusted) {
            val extension = MangaExtension.Untrusted(
                extName,
                pkgName,
                versionName,
                versionCode,
                libVersion,
                signatures.last(),
            )
            logcat(LogPriority.WARN) { "Extension $pkgName isn't trusted" }
            return MangaLoadResult.Untrusted(extension)
        }

        val isNsfw = (appInfo.metaData?.getInt(METADATA_CONTENT_WARNING) ?: 0) > 0 ||
            appInfo.metaData?.getInt(METADATA_NSFW) == 1
        if (!loadNsfwSource && isNsfw) {
            logcat(LogPriority.WARN) { "NSFW extension $pkgName not allowed" }
            return MangaLoadResult.Error
        }

        val classLoader = try {
            ChildFirstPathClassLoader(appInfo.sourceDir, null, context.classLoader)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Extension load error: $extName ($pkgName)" }
            return MangaLoadResult.Error
        }

        val sourceMeta = appInfo.metaData?.getString(METADATA_SOURCE_CLASS)
        if (sourceMeta.isNullOrEmpty()) {
            logcat(LogPriority.WARN) { "Missing source class for extension $extName" }
            return MangaLoadResult.Error
        }
        val sources = sourceMeta
            .split(";")
            .map {
                val sourceClass = it.trim()
                if (sourceClass.startsWith(".")) {
                    pkgInfo.packageName + sourceClass
                } else {
                    sourceClass
                }
            }
            .flatMap {
                try {
                    when (val obj = Class.forName(it, false, classLoader).getDeclaredConstructor().newInstance()) {
                        is MangaSource -> listOf(obj)
                        is SourceFactory -> obj.createSources()
                        else -> throw Exception("Unknown source class type: ${obj.javaClass}")
                    }
                } catch (e: LinkageError) {
                    try {
                        val fallBackClassLoader = PathClassLoader(appInfo.sourceDir, null, context.classLoader)
                        when (
                            val obj = Class.forName(
                                it,
                                false,
                                fallBackClassLoader,
                            ).getDeclaredConstructor().newInstance()
                        ) {
                            is MangaSource -> {
                                listOf(obj)
                            }
                            is SourceFactory -> obj.createSources()
                            else -> throw Exception("Unknown source class type: ${obj.javaClass}")
                        }
                    } catch (e: Throwable) {
                        logcat(LogPriority.ERROR, e) { "Extension load error: $extName ($it)" }
                        return MangaLoadResult.Error
                    }
                } catch (e: Throwable) {
                    logcat(LogPriority.ERROR, e) { "Extension load error: $extName ($it)" }
                    return MangaLoadResult.Error
                }
            }

        val langs = sources.filterIsInstance<CatalogueSource>()
            .map { it.lang }
            .toSet()
        val lang = when (langs.size) {
            0 -> ""
            1 -> langs.first()
            else -> "all"
        }

        val extension = MangaExtension.Installed(
            name = extName,
            pkgName = pkgName,
            versionName = versionName,
            versionCode = versionCode,
            libVersion = libVersion,
            lang = lang,
            isNsfw = isNsfw,
            sources = sources,
            pkgFactory = appInfo.metaData.getString(METADATA_SOURCE_FACTORY),
            icon = appInfo.loadIcon(pkgManager),
            isShared = extensionInfo.isShared,
        )
        return MangaLoadResult.Success(extension)
    }

    /**
     * Choose which extension package to use based on version code
     *
     * @param shared extension installed to system
     * @param private extension installed to data directory
     */
    private fun selectExtensionPackage(shared: MangaExtensionInfo?, private: MangaExtensionInfo?): MangaExtensionInfo? {
        when {
            private == null && shared != null -> return shared
            shared == null && private != null -> return private
            shared == null && private == null -> return null
        }

        return if (PackageInfoCompat.getLongVersionCode(shared!!.packageInfo) >=
            PackageInfoCompat.getLongVersionCode(private!!.packageInfo)
        ) {
            shared
        } else {
            private
        }
    }

    /**
     * Returns true if the given package is an extension.
     *
     * @param pkgInfo The package info of the application.
     */
    private fun isPackageAnExtension(pkgInfo: PackageInfo): Boolean {
        val hasFeature = pkgInfo.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE }
        if (!hasFeature) return false

        val appInfo = pkgInfo.applicationInfo ?: return true
        val isNovel = appInfo.metaData?.run {
            getBoolean("tachiyomi.extension.novel", false) ||
                getInt("tachiyomi.extension.novel", 0) == 1 ||
                getBoolean("tachiyomi.novelextension.novel", false) ||
                getInt("tachiyomi.novelextension.novel", 0) == 1
        } ?: false

        return !isNovel
    }

    /**
     * Returns the signatures of the package or null if it's not signed.
     *
     * @param pkgInfo The package info of the application.
     * @return List SHA256 digest of the signatures
     */
    private fun getSignatures(pkgInfo: PackageInfo): List<String>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = pkgInfo.signingInfo!!
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.signatures
        }
            ?.map { Hash.sha256(it.toByteArray()) }
            ?.toList()
    }

    /**
     * On Android 13+ the ApplicationInfo generated by getPackageArchiveInfo doesn't
     * have sourceDir which breaks assets loading (used for getting icon here).
     */
    private fun ApplicationInfo.fixBasePaths(apkPath: String) {
        if (sourceDir == null) {
            sourceDir = apkPath
        }
        if (publicSourceDir == null) {
            publicSourceDir = apkPath
        }
    }

    private data class MangaExtensionInfo(
        val packageInfo: PackageInfo,
        val isShared: Boolean,
    )
}
