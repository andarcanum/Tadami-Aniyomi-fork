package mihon.data.extension.mapper

import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.extension.manga.util.MangaExtensionLoader
import logcat.LogPriority
import mihon.data.extension.model.AvailableExtensionData
import mihon.domain.extensionstore.model.legacyBaseUrl
import tachiyomi.core.common.util.system.logcat

fun AvailableExtensionData.toMangaExtensionAvailable(): MangaExtension.Available? {
    val needsAppUpdate = libVersion !in MangaExtensionLoader.SUPPORTED_LIB_VERSIONS
    if (needsAppUpdate) {
        MangaExtensionStoreMapperLog.logcat(LogPriority.WARN) {
            "Keeping extension $pkgName $versionName from store '${store.name}' visible: " +
                "unsupported extensions-lib version $libVersion " +
                "(supported: ${MangaExtensionLoader.SUPPORTED_LIB_VERSIONS})"
        }
    }
    val repoBase = store.legacyBaseUrl()
    return MangaExtension.Available(
        name = name,
        pkgName = pkgName,
        versionName = versionName,
        versionCode = versionCode,
        libVersion = libVersion,
        lang = lang,
        isNsfw = isNsfw,
        sources = sources.map { source ->
            MangaExtension.Available.MangaSource(
                id = source.id,
                lang = source.lang,
                name = source.name,
                baseUrl = source.baseUrl,
            )
        },
        apkName = apkUrl,
        iconUrl = iconUrl,
        repoUrl = repoBase,
        repoName = store.name.ifBlank { store.badgeLabel },
        needsAppUpdate = needsAppUpdate,
    )
}

private object MangaExtensionStoreMapperLog
