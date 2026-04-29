package eu.kanade.tachiyomi.ui.browse.search

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.novelsource.model.NovelFilter
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

internal object SavedSearchFilterSerializer {
    fun serialize(filters: AnimeFilterList): String {
        return serializeAnimeFilters(filters.list).toString()
    }

    fun serialize(filters: NovelFilterList): String {
        return serializeNovelFilters(filters.list).toString()
    }

    fun deserialize(filtersJson: String, filters: AnimeFilterList) {
        deserializeAnimeFilters(Json.parseToJsonElement(filtersJson).jsonArray, filters.list)
    }

    fun deserialize(filtersJson: String, filters: NovelFilterList) {
        deserializeNovelFilters(Json.parseToJsonElement(filtersJson).jsonArray, filters.list)
    }

    private fun serializeAnimeFilters(filters: List<AnimeFilter<*>>): JsonArray {
        return buildJsonArray {
            filters.forEach { add(serializeAnimeFilter(it)) }
        }
    }

    private fun serializeAnimeFilter(filter: AnimeFilter<*>): JsonObject {
        return buildJsonObject {
            put(Keys.TYPE, filter.typeName())
            put(Keys.NAME, filter.name)
            when (filter) {
                is AnimeFilter.Select<*> -> put(Keys.STATE, filter.state)
                is AnimeFilter.Text -> put(Keys.STATE, filter.state)
                is AnimeFilter.CheckBox -> put(Keys.STATE, filter.state)
                is AnimeFilter.TriState -> put(Keys.STATE, filter.state)
                is AnimeFilter.Group<*> -> putJsonArray(Keys.STATE) {
                    filter.state.forEach {
                        add(if (it is AnimeFilter<*>) serializeAnimeFilter(it) else JsonNull)
                    }
                }
                is AnimeFilter.Sort -> {
                    put(
                        Keys.STATE,
                        filter.state?.let { selection ->
                            buildJsonObject {
                                put(Keys.INDEX, selection.index)
                                put(Keys.ASCENDING, selection.ascending)
                            }
                        } ?: JsonNull,
                    )
                }
                else -> Unit
            }
        }
    }

    private fun deserializeAnimeFilters(jsonArray: JsonArray, filters: List<AnimeFilter<*>>) {
        filters.forEachIndexed { index, filter ->
            if (index >= jsonArray.size) return@forEachIndexed
            val jsonElement = jsonArray[index]
            if (jsonElement is JsonNull) return@forEachIndexed
            deserializeAnimeFilter(jsonElement.jsonObject, filter)
        }
    }

    private fun deserializeAnimeFilter(json: JsonObject, filter: AnimeFilter<*>) {
        when (filter) {
            is AnimeFilter.Select<*> -> filter.state = json[Keys.STATE]!!.jsonPrimitive.int
            is AnimeFilter.Text -> filter.state = json[Keys.STATE]!!.jsonPrimitive.content
            is AnimeFilter.CheckBox -> filter.state = json[Keys.STATE]!!.jsonPrimitive.boolean
            is AnimeFilter.TriState -> filter.state = json[Keys.STATE]!!.jsonPrimitive.int
            is AnimeFilter.Group<*> -> {
                val childFilters = filter.state.filterIsInstance<AnimeFilter<*>>()
                val childJson = json[Keys.STATE]?.jsonArray ?: return
                deserializeAnimeFilters(childJson, childFilters)
            }
            is AnimeFilter.Sort -> {
                filter.state = (json[Keys.STATE] as? JsonObject)?.let {
                    AnimeFilter.Sort.Selection(
                        it[Keys.INDEX]!!.jsonPrimitive.int,
                        it[Keys.ASCENDING]!!.jsonPrimitive.boolean,
                    )
                }
            }
            else -> Unit
        }
    }

    private fun serializeNovelFilters(filters: List<NovelFilter<*>>): JsonArray {
        return buildJsonArray {
            filters.forEach { add(serializeNovelFilter(it)) }
        }
    }

    private fun serializeNovelFilter(filter: NovelFilter<*>): JsonObject {
        return buildJsonObject {
            put(Keys.TYPE, filter.typeName())
            put(Keys.NAME, filter.name)
            when (filter) {
                is NovelFilter.Select<*> -> put(Keys.STATE, filter.state)
                is NovelFilter.Picker<*> -> put(Keys.STATE, filter.state)
                is NovelFilter.Text -> put(Keys.STATE, filter.state)
                is NovelFilter.CheckBox -> put(Keys.STATE, filter.state)
                is NovelFilter.Switch -> put(Keys.STATE, filter.state)
                is NovelFilter.TriState -> put(Keys.STATE, filter.state)
                is NovelFilter.XCheckBox -> put(Keys.STATE, filter.state)
                is NovelFilter.Group<*> -> putJsonArray(Keys.STATE) {
                    filter.state.forEach {
                        add(if (it is NovelFilter<*>) serializeNovelFilter(it) else JsonNull)
                    }
                }
                is NovelFilter.Sort -> {
                    put(
                        Keys.STATE,
                        filter.state?.let { selection ->
                            buildJsonObject {
                                put(Keys.INDEX, selection.index)
                                put(Keys.ASCENDING, selection.ascending)
                            }
                        } ?: JsonNull,
                    )
                }
                else -> Unit
            }
        }
    }

    private fun deserializeNovelFilters(jsonArray: JsonArray, filters: List<NovelFilter<*>>) {
        filters.forEachIndexed { index, filter ->
            if (index >= jsonArray.size) return@forEachIndexed
            val jsonElement = jsonArray[index]
            if (jsonElement is JsonNull) return@forEachIndexed
            deserializeNovelFilter(jsonElement.jsonObject, filter)
        }
    }

    private fun deserializeNovelFilter(json: JsonObject, filter: NovelFilter<*>) {
        when (filter) {
            is NovelFilter.Select<*> -> filter.state = json[Keys.STATE]!!.jsonPrimitive.int
            is NovelFilter.Picker<*> -> filter.state = json[Keys.STATE]!!.jsonPrimitive.int
            is NovelFilter.Text -> filter.state = json[Keys.STATE]!!.jsonPrimitive.content
            is NovelFilter.CheckBox -> filter.state = json[Keys.STATE]!!.jsonPrimitive.boolean
            is NovelFilter.Switch -> filter.state = json[Keys.STATE]!!.jsonPrimitive.boolean
            is NovelFilter.TriState -> filter.state = json[Keys.STATE]!!.jsonPrimitive.int
            is NovelFilter.XCheckBox -> filter.state = json[Keys.STATE]!!.jsonPrimitive.int
            is NovelFilter.Group<*> -> {
                val childFilters = filter.state.filterIsInstance<NovelFilter<*>>()
                val childJson = json[Keys.STATE]?.jsonArray ?: return
                deserializeNovelFilters(childJson, childFilters)
            }
            is NovelFilter.Sort -> {
                filter.state = (json[Keys.STATE] as? JsonObject)?.let {
                    NovelFilter.Sort.Selection(
                        it[Keys.INDEX]!!.jsonPrimitive.int,
                        it[Keys.ASCENDING]!!.jsonPrimitive.boolean,
                    )
                }
            }
            else -> Unit
        }
    }

    private fun AnimeFilter<*>.typeName(): String = when (this) {
        is AnimeFilter.Header -> Keys.HEADER
        is AnimeFilter.Separator -> Keys.SEPARATOR
        is AnimeFilter.Select<*> -> Keys.SELECT
        is AnimeFilter.Text -> Keys.TEXT
        is AnimeFilter.CheckBox -> Keys.CHECKBOX
        is AnimeFilter.TriState -> Keys.TRISTATE
        is AnimeFilter.Group<*> -> Keys.GROUP
        is AnimeFilter.Sort -> Keys.SORT
    }

    private fun NovelFilter<*>.typeName(): String = when (this) {
        is NovelFilter.Header -> Keys.HEADER
        is NovelFilter.Separator -> Keys.SEPARATOR
        is NovelFilter.Select<*> -> Keys.SELECT
        is NovelFilter.Picker<*> -> Keys.SELECT
        is NovelFilter.Text -> Keys.TEXT
        is NovelFilter.CheckBox -> Keys.CHECKBOX
        is NovelFilter.Switch -> Keys.SWITCH
        is NovelFilter.TriState -> Keys.TRISTATE
        is NovelFilter.XCheckBox -> Keys.TRISTATE
        is NovelFilter.Group<*> -> Keys.GROUP
        is NovelFilter.Sort -> Keys.SORT
    }

    private object Keys {
        const val TYPE = "type"
        const val NAME = "name"
        const val STATE = "state"
        const val INDEX = "index"
        const val ASCENDING = "ascending"
        const val HEADER = "HEADER"
        const val SEPARATOR = "SEPARATOR"
        const val SELECT = "SELECT"
        const val TEXT = "TEXT"
        const val CHECKBOX = "CHECKBOX"
        const val SWITCH = "SWITCH"
        const val TRISTATE = "TRISTATE"
        const val GROUP = "GROUP"
        const val SORT = "SORT"
    }
}
