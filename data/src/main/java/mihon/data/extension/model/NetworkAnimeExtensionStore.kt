package mihon.data.extension.model

import android.annotation.SuppressLint
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.protobuf.ProtoNumber
import mihon.domain.extensionstore.model.ExtensionStore

/**
 * Wire schema of an Aniyomi-style ANIME extension store index (index.json / index.pb).
 *
 * Field numbering differs from the manga-oriented [NetworkExtensionStore]: Extension.isTorrent
 * is proto 8 and Extension.sources is proto 9, and Source.message is proto 6 (manga has no
 * proto 8 and sources at 8). Anime repos published for Aniyomi (e.g. mojuru/anime-repo) follow
 * this schema; decoding them with the manga model fails with
 * "Expected wire type SIZE_DELIMITED(2), but found VARINT(0)" on the first isTorrent=true entry.
 */
@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class NetworkAnimeExtensionStore(
    @ProtoNumber(1) val name: String,
    @ProtoNumber(2) val badgeLabel: String,
    @ProtoNumber(3) val signingKey: String,
    @ProtoNumber(4) val contact: Contact,
    @ProtoNumber(101) val extensionList: ExtensionList? = null,
    @ProtoNumber(102) val extensionListUrl: String? = null,
) : BaseNetworkExtensionStore {
    @Serializable
    data class Contact(
        @ProtoNumber(1) val website: String,
        @ProtoNumber(2) val discord: String? = null,
    )

    @Serializable
    data class ExtensionList(@ProtoNumber(1) val extensions: List<Extension>)

    @Serializable
    data class Extension(
        @ProtoNumber(1) val name: String,
        @ProtoNumber(2) val packageName: String,
        @ProtoNumber(3) val resources: Resources,
        @ProtoNumber(4) val extensionLib: String,
        @ProtoNumber(5)
        @Serializable(with = Proto3JsonInt64Serializer::class)
        val versionCode: Long,
        @ProtoNumber(6) val versionName: String,
        @ProtoNumber(7) val contentWarning: ContentWarning = ContentWarning.UNSPECIFIED,
        @ProtoNumber(8) val isTorrent: Boolean = false,
        @ProtoNumber(9) val sources: List<Source>,
    )

    @Serializable
    data class Resources(
        @ProtoNumber(1) val apkUrl: String,
        @ProtoNumber(2) val iconUrl: String,
    )

    @Serializable
    data class Source(
        @ProtoNumber(1) @Serializable(with = Proto3JsonInt64Serializer::class) val id: Long,
        @ProtoNumber(2) val name: String,
        @ProtoNumber(3) val language: String,
        @ProtoNumber(4) val homeUrl: String = "",
        @ProtoNumber(5) val mirrorUrls: List<String> = emptyList(),
        @ProtoNumber(6) val message: String? = null,
    )

    @Suppress("Unused")
    enum class ContentWarning {
        @ProtoNumber(0)
        @JsonNames("CONTENT_WARNING_UNSPECIFIED")
        UNSPECIFIED,

        @ProtoNumber(1)
        @JsonNames("CONTENT_WARNING_SAFE")
        SAFE,

        @ProtoNumber(2)
        @JsonNames("CONTENT_WARNING_MIXED")
        MIXED,

        @ProtoNumber(3)
        @JsonNames("CONTENT_WARNING_NSFW")
        NSFW,
    }

    override fun toExtensionStore(indexUrl: String): ExtensionStore {
        return ExtensionStore(
            indexUrl = indexUrl,
            name = name,
            badgeLabel = badgeLabel,
            signingKey = signingKey,
            contact = ExtensionStore.Contact(
                website = contact.website,
                discord = contact.discord,
            ),
            isLegacy = false,
            extensionListUrl = extensionListUrl,
        )
    }
}

fun NetworkAnimeExtensionStore.ExtensionList.toAvailableExtensionData(
    store: ExtensionStore,
): List<AvailableExtensionData> {
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
            isNsfw = extension.contentWarning >= NetworkAnimeExtensionStore.ContentWarning.MIXED,
            isTorrent = extension.isTorrent,
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
