package eu.kanade.tachiyomi.extension.novel.runtime

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

class NovelPluginFilterMapperTest {

    @Test
    fun `maps plugin filters into filter list and values`() {
        val mapper = NovelPluginFilterMapper(Json { ignoreUnknownKeys = true })
        val filtersJson = """
            {
              "type": {
                "type": "Picker",
                "label": "Type",
                "value": "hot",
                "options": [
                  { "label": "Hot", "value": "hot" },
                  { "label": "New", "value": "new" }
                ]
              },
              "query": {
                "type": "Text",
                "label": "Query",
                "value": "abc"
              },
              "genres": {
                "type": "Checkbox",
                "label": "Genres",
                "value": ["fantasy"],
                "options": [
                  { "label": "Fantasy", "value": "fantasy" },
                  { "label": "Sci-fi", "value": "sci" }
                ]
              },
              "nsfw": {
                "type": "Switch",
                "label": "NSFW",
                "value": true
              },
              "tags": {
                "type": "XCheckbox",
                "label": "Tags",
                "value": { "include": ["a"], "exclude": ["b"] },
                "options": [
                  { "label": "A", "value": "a" },
                  { "label": "B", "value": "b" }
                ]
              }
            }
        """.trimIndent()

        val filterList = mapper.toFilterList(filtersJson)

        filterList.size shouldBe 5

        val values = mapper.toFilterValues(filterList)
        values.keys.toSet() shouldBe setOf("type", "query", "genres", "nsfw", "tags")

        values["query"]!!.jsonObject["value"]!!.jsonPrimitive.content shouldBe "abc"
        values["nsfw"]!!.jsonObject["value"]!!.jsonPrimitive.content.toBoolean() shouldBe true
        values["type"]!!.jsonObject["value"]!!.jsonPrimitive.content shouldBe "hot"

        val genres = values["genres"]!!.jsonObject["value"] as JsonArray
        genres.first().jsonPrimitive.content shouldBe "fantasy"

        val tags = values["tags"]!!.jsonObject["value"] as JsonObject
        (tags["include"] as JsonArray).first().jsonPrimitive.content shouldBe "a"
        (tags["exclude"] as JsonArray).first().jsonPrimitive.content shouldBe "b"
    }
}
