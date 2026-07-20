package mihon.data.extension.model

import android.annotation.SuppressLint
import kotlinx.serialization.Serializable
import mihon.domain.extensionstore.model.ExtensionStore

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class NetworkLegacyExtension(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String,
    val code: Long,
    val version: String,
    val nsfw: Int = 0,
    val sources: List<Source>? = null,
) {
    @Serializable
    data class Source(
        val id: Long,
        val lang: String,
        val name: String,
        val baseUrl: String,
    )

    fun toAvailableExtensionData(
        store: ExtensionStore,
        storeBaseUrl: String,
    ): AvailableExtensionData {
        return AvailableExtensionData(
            name = name.substringAfter("Tachiyomi: "),
            pkgName = pkg,
            apkUrl = "$storeBaseUrl/apk/$apk",
            iconUrl = "$storeBaseUrl/icon/$pkg.png",
            libVersion = version.substringBeforeLast('.').toDouble(),
            versionCode = code,
            versionName = version,
            lang = lang,
            isNsfw = nsfw == 1,
            sources = if (sources.isNullOrEmpty()) {
                listOf(
                    AvailableExtensionData.Source(
                        id = 0,
                        name = name,
                        lang = lang,
                        baseUrl = "",
                    ),
                )
            } else {
                sources.map { source ->
                    AvailableExtensionData.Source(
                        id = source.id,
                        name = source.name,
                        lang = source.lang,
                        baseUrl = source.baseUrl,
                    )
                }
            },
            store = store,
        )
    }
}
