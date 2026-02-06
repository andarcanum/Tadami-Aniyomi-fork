package eu.kanade.tachiyomi.extension.novel.repo

import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

class NovelPluginRepoParser(
    private val json: Json,
) {
    fun parse(payload: String): List<NovelPluginRepoEntry> {
        val entries = json.decodeFromString<List<NovelPluginRepoEntryDto>>(payload)
        return entries.map { it.toModel() }
    }
}
