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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Serializes saved-search filter states for the anime/novel sources (manga uses the vendored
 * xyz.nulldev ts FilterSerializer).
 *
 * PARENT-3/BRA-2/BFEED-7/BRN-5: deserialization used to be strictly positional with unchecked
 * `json[STATE]!!` primitives - after an extension update reshaped its filter list (routine), the
 * states were applied to the WRONG filters and a type/shape mismatch threw straight out of the
 * screen model coroutines (process crash on opening the saved search). Now: the top-level parse
 * is guarded, each filter is restored only when its serialized TYPE and NAME still match (a
 * renamed filter is intentionally skipped - restoring it blindly is what corrupted searches),
 * every filter is individually exception-guarded (parity with the manga ts serializer, which
 * swallows per element), and Group children are aligned by the RAW child index so JsonNull
 * placeholders for non-filter children no longer shift the following children (BRA-13, the ts
 * GroupSerializer is the etalon).
 */
internal object SavedSearchFilterSerializer {
    fun serialize(filters: AnimeFilterList): String {
        return serializeAnimeFilters(filters.list).toString()
    }

    fun serialize(filters: NovelFilterList): String {
        return serializeNovelFilters(filters.list).toString()
    }

    fun deserialize(filtersJson: String, filters: AnimeFilterList) {
        val array = parseOrNull(filtersJson) ?: return
        deserializeAnimeFilters(array, filters.list)
    }

    fun deserialize(filtersJson: String, filters: NovelFilterList) {
        val array = parseOrNull(filtersJson) ?: return
        deserializeNovelFilters(array, filters.list)
    }

    private fun parseOrNull(filtersJson: String): JsonArray? {
        return try {
            Json.parseToJsonElement(filtersJson).jsonArray
        } catch (e: Exception) {
            // Corrupted/foreign JSON: degrade to "no saved filters" instead of crashing the
            // screen model coroutine that called us (BRM-7).
            null
        }
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

    private fun deserializeAnimeFilters(jsonArray: JsonArray, filters: List<*>) {
        filters.forEachIndexed { index, filter ->
            // Group children can contain non-filter entries (serialized as JsonNull
            // placeholders); aligning by the RAW index keeps the following children in sync.
            if (filter !is AnimeFilter<*>) return@forEachIndexed
            if (index >= jsonArray.size) return@forEachIndexed
            val jsonElement = jsonArray[index]
            if (jsonElement is JsonNull) return@forEachIndexed
            try {
                deserializeAnimeFilter(jsonElement.jsonObject, filter)
            } catch (e: Exception) {
                // Extension filter drift: skip this filter instead of crashing (manga ts
                // serializer etalon, FilterSerializer:67-73).
            }
        }
    }

    private fun deserializeAnimeFilter(json: JsonObject, filter: AnimeFilter<*>) {
        if (!matchesSerialized(json, filter.typeName(), filter.name)) return
        val stateElement = json[Keys.STATE] ?: return
        when (filter) {
            is AnimeFilter.Select<*> -> filter.state = stateElement.jsonPrimitive.int
            is AnimeFilter.Text -> filter.state = stateElement.jsonPrimitive.content
            is AnimeFilter.CheckBox -> filter.state = stateElement.jsonPrimitive.boolean
            is AnimeFilter.TriState -> filter.state = stateElement.jsonPrimitive.int
            is AnimeFilter.Group<*> -> {
                val childJson = (stateElement as? JsonArray) ?: return
                deserializeAnimeFilters(childJson, filter.state)
            }
            is AnimeFilter.Sort -> {
                filter.state = (stateElement as? JsonObject)?.let {
                    val selectionIndex = it[Keys.INDEX]?.jsonPrimitive?.int ?: return@let null
                    val ascending = it[Keys.ASCENDING]?.jsonPrimitive?.boolean ?: return@let null
                    AnimeFilter.Sort.Selection(selectionIndex, ascending)
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
                // Picker/XCheckBox had their own branches here - dead code: Picker extends
                // Select and XCheckBox extends TriState, so the parent branches always matched
                // first (identical Int state); removed (control NEW-2).
                is NovelFilter.Select<*> -> put(Keys.STATE, filter.state)
                is NovelFilter.Text -> put(Keys.STATE, filter.state)
                is NovelFilter.CheckBox -> put(Keys.STATE, filter.state)
                is NovelFilter.Switch -> put(Keys.STATE, filter.state)
                is NovelFilter.TriState -> put(Keys.STATE, filter.state)
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

    private fun deserializeNovelFilters(jsonArray: JsonArray, filters: List<*>) {
        filters.forEachIndexed { index, filter ->
            if (filter !is NovelFilter<*>) return@forEachIndexed
            if (index >= jsonArray.size) return@forEachIndexed
            val jsonElement = jsonArray[index]
            if (jsonElement is JsonNull) return@forEachIndexed
            try {
                deserializeNovelFilter(jsonElement.jsonObject, filter)
            } catch (e: Exception) {
                // See deserializeAnimeFilters.
            }
        }
    }

    private fun deserializeNovelFilter(json: JsonObject, filter: NovelFilter<*>) {
        if (!matchesSerialized(json, filter.typeName(), filter.name)) return
        val stateElement = json[Keys.STATE] ?: return
        when (filter) {
            is NovelFilter.Select<*> -> filter.state = stateElement.jsonPrimitive.int
            is NovelFilter.Text -> filter.state = stateElement.jsonPrimitive.content
            is NovelFilter.CheckBox -> filter.state = stateElement.jsonPrimitive.boolean
            is NovelFilter.Switch -> filter.state = stateElement.jsonPrimitive.boolean
            is NovelFilter.TriState -> filter.state = stateElement.jsonPrimitive.int
            is NovelFilter.Group<*> -> {
                val childJson = (stateElement as? JsonArray) ?: return
                deserializeNovelFilters(childJson, filter.state)
            }
            is NovelFilter.Sort -> {
                filter.state = (stateElement as? JsonObject)?.let {
                    val selectionIndex = it[Keys.INDEX]?.jsonPrimitive?.int ?: return@let null
                    val ascending = it[Keys.ASCENDING]?.jsonPrimitive?.boolean ?: return@let null
                    NovelFilter.Sort.Selection(selectionIndex, ascending)
                }
            }
            else -> Unit
        }
    }

    private fun matchesSerialized(json: JsonObject, typeName: String, name: String): Boolean {
        return json[Keys.TYPE]?.jsonPrimitive?.contentOrNull == typeName &&
            json[Keys.NAME]?.jsonPrimitive?.contentOrNull == name
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
