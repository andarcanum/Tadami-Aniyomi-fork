package eu.kanade.tachiyomi.extension.novel.repo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class NovelPluginRepoEntry(
    val id: String,
    val name: String,
    val site: String,
    val lang: String,
    val version: Int,
    val url: String,
    val iconUrl: String?,
    val customJsUrl: String?,
    val customCssUrl: String?,
    val hasSettings: Boolean,
    val sha256: String,
)

@Serializable
internal data class NovelPluginRepoEntryDto(
    val id: String,
    val name: String,
    val site: String,
    val lang: String,
    val version: Int,
    val url: String,
    val iconUrl: String? = null,
    @SerialName("customJS") val customJsUrl: String? = null,
    @SerialName("customCSS") val customCssUrl: String? = null,
    val hasSettings: Boolean = false,
    val sha256: String,
) {
    fun toModel(): NovelPluginRepoEntry = NovelPluginRepoEntry(
        id = id,
        name = name,
        site = site,
        lang = lang,
        version = version,
        url = url,
        iconUrl = iconUrl,
        customJsUrl = customJsUrl,
        customCssUrl = customCssUrl,
        hasSettings = hasSettings,
        sha256 = sha256,
    )
}
