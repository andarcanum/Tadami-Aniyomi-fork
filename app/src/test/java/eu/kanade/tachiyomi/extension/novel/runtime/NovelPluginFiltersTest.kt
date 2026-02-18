package eu.kanade.tachiyomi.extension.novel.runtime

import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

class NovelPluginFiltersTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val mapper = NovelPluginFilterMapper(json)

    @Test
    fun `uses cached defaults when active filter list is empty`() {
        val cachedPayload = """
            {
              "sort": {
                "type": "Picker",
                "label": "Sort",
                "value": "popular",
                "options": [
                  { "label": "Popular", "value": "popular" },
                  { "label": "Latest", "value": "latest" }
                ]
              },
              "order": {
                "type": "Picker",
                "label": "Order",
                "value": "desc",
                "options": [
                  { "label": "Desc", "value": "desc" },
                  { "label": "Asc", "value": "asc" }
                ]
              }
            }
        """.trimIndent()

        val values = NovelPluginFilters.toFilterValuesWithDefaults(
            filters = NovelFilterList(),
            cachedFiltersPayload = cachedPayload,
            filterMapper = mapper,
        )

        values["sort"]!!.jsonObject["value"]!!.jsonPrimitive.content shouldBe "popular"
        values["order"]!!.jsonObject["value"]!!.jsonPrimitive.content shouldBe "desc"
    }

    @Test
    fun `prefers explicit filters over cached defaults`() {
        val cachedPayload = """
            {
              "sort": {
                "type": "Picker",
                "label": "Sort",
                "value": "popular",
                "options": [
                  { "label": "Popular", "value": "popular" },
                  { "label": "Latest", "value": "latest" }
                ]
              }
            }
        """.trimIndent()
        val explicitPayload = """
            {
              "sort": {
                "type": "Picker",
                "label": "Sort",
                "value": "latest",
                "options": [
                  { "label": "Popular", "value": "popular" },
                  { "label": "Latest", "value": "latest" }
                ]
              }
            }
        """.trimIndent()

        val explicitFilters = mapper.toFilterList(explicitPayload)
        val values = NovelPluginFilters.toFilterValuesWithDefaults(
            filters = explicitFilters,
            cachedFiltersPayload = cachedPayload,
            filterMapper = mapper,
        )

        values["sort"]!!.jsonObject["value"]!!.jsonPrimitive.content shouldBe "latest"
    }

    @Test
    fun `does not merge stale cached keys when explicit filters are present`() {
        val cachedPayload = """
            {
              "sort": {
                "type": "Picker",
                "label": "Sort",
                "value": "popular",
                "options": [
                  { "label": "Popular", "value": "popular" },
                  { "label": "Latest", "value": "latest" }
                ]
              },
              "order": {
                "type": "Picker",
                "label": "Order",
                "value": "desc",
                "options": [
                  { "label": "Desc", "value": "desc" },
                  { "label": "Asc", "value": "asc" }
                ]
              }
            }
        """.trimIndent()
        val explicitPartialPayload = """
            {
              "sort": {
                "type": "Picker",
                "label": "Sort",
                "value": "latest",
                "options": [
                  { "label": "Popular", "value": "popular" },
                  { "label": "Latest", "value": "latest" }
                ]
              }
            }
        """.trimIndent()

        val explicitPartial = mapper.toFilterList(explicitPartialPayload)
        val values = NovelPluginFilters.toFilterValuesWithDefaults(
            filters = explicitPartial,
            cachedFiltersPayload = cachedPayload,
            filterMapper = mapper,
        )

        values["sort"]!!.jsonObject["value"]!!.jsonPrimitive.content shouldBe "latest"
        values.containsKey("order") shouldBe false
    }
}
