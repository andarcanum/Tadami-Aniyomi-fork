package mihon.data.extension.model

import mihon.data.extension.model.NetworkExtensionStore.ContentWarning
import mihon.domain.extensionstore.model.ExtensionStore

data class AvailableExtensionData(
    val name: String,
    val pkgName: String,
    val apkUrl: String,
    val iconUrl: String,
    val libVersion: Double,
    val versionCode: Long,
    val versionName: String,
    val lang: String,
    val isNsfw: Boolean,
    val isTorrent: Boolean = false,
    val sources: List<Source>,
    val store: ExtensionStore,
) {
    data class Source(
        val id: Long,
        val lang: String,
        val name: String,
        val baseUrl: String,
    )
}

fun NetworkExtensionStore.ExtensionList.toAvailableExtensionData(store: ExtensionStore): List<AvailableExtensionData> {
    return extensions.map { extension ->
        val langs = extension.sources.map { it.language }.toSet()
        AvailableExtensionData(
            name = extension.name,
            pkgName = extension.packageName,
            apkUrl = extension.resources.apkUrl,
            iconUrl = extension.resources.iconUrl,
            libVersion = extension.extensionLib.toDouble(),
            versionCode = extension.versionCode,
            versionName = extension.versionName,
            lang = if (langs.size == 1) langs.first() else "all",
            isNsfw = extension.contentWarning >= ContentWarning.MIXED,
            sources = extension.sources.map { source ->
                AvailableExtensionData.Source(
                    id = source.id,
                    name = source.name,
                    lang = source.language,
                    baseUrl = source.homeUrl,
                )
            },
            store = store,
        )
    }
}
