package mihon.data.extension.mapper

import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.extension.anime.util.AnimeExtensionLoader
import logcat.LogPriority
import mihon.data.extension.model.AvailableExtensionData
import mihon.domain.extensionstore.model.legacyBaseUrl
import tachiyomi.core.common.util.system.logcat

fun AvailableExtensionData.toAnimeExtensionAvailable(): AnimeExtension.Available? {
    val needsAppUpdate = libVersion !in AnimeExtensionLoader.SUPPORTED_LIB_VERSIONS
    if (needsAppUpdate) {
        AnimeExtensionStoreMapperLog.logcat(LogPriority.WARN) {
            "Skipping extension $pkgName $versionName from store '${store.name}': " +
                "unsupported extensions-lib version $libVersion " +
                "(supported: ${AnimeExtensionLoader.SUPPORTED_LIB_VERSIONS})"
        }
    }
    val repoBase = store.legacyBaseUrl()
    return AnimeExtension.Available(
        name = name,
        pkgName = pkgName,
        versionName = versionName,
        versionCode = versionCode,
        libVersion = libVersion,
        lang = lang,
        isNsfw = isNsfw,
        sources = sources.map { source ->
            AnimeExtension.Available.AnimeSource(
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

private object AnimeExtensionStoreMapperLog
