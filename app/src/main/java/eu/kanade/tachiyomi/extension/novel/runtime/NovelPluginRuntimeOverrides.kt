package eu.kanade.tachiyomi.extension.novel.runtime

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class NovelPluginRuntimeOverrides(
    val entries: List<NovelPluginRuntimeOverride> = emptyList(),
) {
    private val byPluginId: Map<String, NovelPluginRuntimeOverride> by lazy {
        entries
            .groupBy { it.pluginId.lowercase() }
            .mapValues { (_, overrides) -> overrides.maxByOrNull { it.version } ?: overrides.first() }
    }

    fun forPlugin(pluginId: String): NovelPluginRuntimeOverride {
        return byPluginId[pluginId.lowercase()] ?: NovelPluginRuntimeOverride(pluginId = pluginId)
    }

    companion object {
        fun fromJson(json: Json, payload: String?): NovelPluginRuntimeOverrides {
            if (payload.isNullOrBlank()) return NovelPluginRuntimeOverrides()
            return runCatching { json.decodeFromString(serializer(), payload) }
                .getOrElse { NovelPluginRuntimeOverrides() }
        }
    }
}

@Serializable
data class NovelPluginRuntimeOverride(
    val pluginId: String,
    val version: Int = 1,
    val domainAliases: Map<String, String> = emptyMap(),
    val scriptPatches: List<NovelScriptPatch> = emptyList(),
    val chapterFallbackPolicy: NovelChapterFallbackPolicy = NovelChapterFallbackPolicy(),
)

@Serializable
data class NovelScriptPatch(
    val pattern: String,
    val replacement: String,
    val regex: Boolean = false,
    val ignoreCase: Boolean = false,
)

@Serializable
data class NovelChapterFallbackPolicy(
    val fillMissingChapterNames: Boolean = true,
    val dropDuplicateChapterPaths: Boolean = true,
    val chapterNamePrefix: String = "Chapter",
    @SerialName("stripFragmentFromChapterPath")
    val stripFragmentFromChapterPath: Boolean = true,
)
