package eu.kanade.tachiyomi.extension.novel.runtime

import eu.kanade.tachiyomi.extension.novel.NovelPluginId
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.NovelsPage
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import rx.Observable
import tachiyomi.domain.extension.novel.model.NovelPlugin
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class NovelJsSource(
    private val plugin: NovelPlugin.Installed,
    private val script: String,
    private val runtimeFactory: NovelJsRuntimeFactory,
    private val json: Json,
    private val scriptBuilder: NovelPluginScriptBuilder,
    private val filterMapper: NovelPluginFilterMapper,
) : NovelCatalogueSource {
    override val id: Long = NovelPluginId.toSourceId(plugin.id)
    override val name: String = plugin.name
    override val lang: String = plugin.lang
    override val supportsLatest: Boolean = true

    private val mutex = Mutex()
    private var runtime: NovelJsRuntime? = null
    private var hasParsePage: Boolean? = null
    private var cachedFilterList: NovelFilterList? = null

    override fun getFilterList(): NovelFilterList {
        return runBlocking {
            mutex.withLock {
                cachedFilterList ?: loadFiltersLocked().also { cachedFilterList = it }
            }
        }
    }

    override fun fetchPopularNovels(page: Int): Observable<NovelsPage> {
        return Observable.fromCallable { runBlocking { getPopularNovels(page) } }
    }

    override fun fetchSearchNovels(page: Int, query: String, filters: NovelFilterList): Observable<NovelsPage> {
        return Observable.fromCallable { runBlocking { getSearchNovels(page, query, filters) } }
    }

    override fun fetchLatestUpdates(page: Int): Observable<NovelsPage> {
        return Observable.fromCallable { runBlocking { getLatestUpdates(page) } }
    }

    override suspend fun getPopularNovels(page: Int): NovelsPage {
        val items = mutex.withLock {
            val runtime = ensureRuntimeLocked()
            val options = buildPopularOptionsJson(filters = getFilterValuesLocked(), showLatest = false)
            val payload = callPlugin(runtime, "popularNovels", page.toString(), options)
            parseNovelItems(payload)
        }
        return items.toNovelsPage()
    }

    override suspend fun getSearchNovels(page: Int, query: String, filters: NovelFilterList): NovelsPage {
        val items = mutex.withLock {
            val runtime = ensureRuntimeLocked()
            val filtersJson = filterMapper.toFilterValues(filters).toString()
            val payload = callPlugin(runtime, "searchNovels", toJsString(query), page.toString(), filtersJson)
            parseNovelItems(payload)
        }
        return items.toNovelsPage()
    }

    override suspend fun getLatestUpdates(page: Int): NovelsPage {
        val items = mutex.withLock {
            val runtime = ensureRuntimeLocked()
            val options = buildPopularOptionsJson(filters = getFilterValuesLocked(), showLatest = true)
            val payload = callPlugin(runtime, "popularNovels", page.toString(), options)
            parseNovelItems(payload)
        }
        return items.toNovelsPage()
    }

    override suspend fun getNovelDetails(novel: SNovel): SNovel {
        val sourceNovel = mutex.withLock {
            val runtime = ensureRuntimeLocked()
            val payload = callPlugin(runtime, "parseNovel", toJsString(novel.url))
            if (payload.isBlank() || payload == "null") null else json.decodeFromString<PluginSourceNovel>(payload)
        } ?: return novel

        return novel.apply {
            url = sourceNovel.path
            title = sourceNovel.name
            author = sourceNovel.author
            description = sourceNovel.summary
            genre = sourceNovel.genres
            status = mapStatus(sourceNovel.status)
            thumbnail_url = sourceNovel.cover
            initialized = true
        }
    }

    override suspend fun getChapterList(novel: SNovel): List<SNovelChapter> {
        val runtime = mutex.withLock { ensureRuntimeLocked() }
        val sourceNovel = mutex.withLock {
            val payload = callPlugin(runtime, "parseNovel", toJsString(novel.url))
            if (payload.isBlank() || payload == "null") null else json.decodeFromString<PluginSourceNovel>(payload)
        } ?: return emptyList()

        val chapters = sourceNovel.chapters ?: emptyList()
        if (chapters.isNotEmpty()) return chapters.map { it.toSChapter() }

        val totalPages = sourceNovel.totalPages ?: return emptyList()
        if (hasParsePage != true) return emptyList()

        val collected = mutableListOf<PluginChapterItem>()
        for (page in 1..totalPages) {
            val payload = mutex.withLock {
                callPlugin(runtime, "parsePage", toJsString(novel.url), toJsString(page.toString()))
            }
            if (payload.isBlank() || payload == "null") continue
            val pageResult = json.decodeFromString<PluginSourcePage>(payload)
            collected.addAll(pageResult.chapters)
        }
        return collected.map { it.toSChapter() }
    }

    override suspend fun getChapterText(chapter: SNovelChapter): String {
        return mutex.withLock {
            val runtime = ensureRuntimeLocked()
            val payload = callPlugin(runtime, "parseChapter", toJsString(chapter.url))
            if (payload.isBlank() || payload == "null") "" else json.decodeFromString<String>(payload)
        }
    }

    private fun loadFiltersLocked(): NovelFilterList {
        val runtime = ensureRuntimeLocked()
        val payload = callPlugin(runtime, "filters")
        if (payload.isBlank() || payload == "null") return NovelFilterList()
        return filterMapper.toFilterList(payload)
    }

    private fun getFilterValuesLocked(): JsonObject {
        val filterList = cachedFilterList ?: loadFiltersLocked().also { cachedFilterList = it }
        return filterMapper.toFilterValues(filterList)
    }

    private fun ensureRuntimeLocked(): NovelJsRuntime {
        runtime?.let { return it }
        val instance = runtimeFactory.create(plugin.id)
        val moduleName = plugin.id
        val moduleLiteral = toJsString(moduleName)
        val wrappedScript = scriptBuilder.wrap(script, moduleName)
        instance.evaluate(wrappedScript, "${plugin.id}.js")
        instance.evaluate(
            """
            var __plugin = require($moduleLiteral);
            if (__plugin && __plugin.default) { __plugin = __plugin.default; }
            """.trimIndent(),
            "novel-plugin-init.js",
        )
        hasParsePage = (instance.evaluate("typeof __plugin.parsePage === \"function\"") as? Boolean) == true
        runtime = instance
        return instance
    }

    private fun callPlugin(runtime: NovelJsRuntime, functionName: String, vararg args: String): String {
        val call = when (functionName) {
            "filters" -> "JSON.stringify(__plugin && __plugin.filters ? __plugin.filters : {})"
            else -> {
                val joinedArgs = args.joinToString(", ")
                "JSON.stringify(__resolve(__plugin.$functionName($joinedArgs)))"
            }
        }
        val result = runtime.evaluate(call, "novel-plugin-call.js")
        return result as? String ?: ""
    }

    private fun parseNovelItems(payload: String): List<PluginNovelItem> {
        if (payload.isBlank() || payload == "null") return emptyList()
        val element = json.decodeFromString<JsonElement>(payload)
        return when (element) {
            is JsonArray -> json.decodeFromJsonElement(element)
            is JsonObject -> {
                val novelsElement = element["novels"] ?: return emptyList()
                if (novelsElement is JsonArray) json.decodeFromJsonElement(novelsElement) else emptyList()
            }
            else -> emptyList()
        }
    }

    private fun List<PluginNovelItem>.toNovelsPage(): NovelsPage {
        val novels = map { it.toSNovel() }
        val hasNextPage = isNotEmpty()
        return NovelsPage(novels, hasNextPage)
    }

    private fun PluginNovelItem.toSNovel(): SNovel {
        return SNovel.create().also {
            it.url = path
            it.title = name
            it.thumbnail_url = cover
        }
    }

    private fun PluginChapterItem.toSChapter(): SNovelChapter {
        return SNovelChapter.create().also {
            it.url = path
            it.name = name
            it.date_upload = parseChapterDate(releaseTime)
            it.chapter_number = chapterNumber?.toFloat() ?: -1f
        }
    }

    private fun parseChapterDate(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        return runCatching { Instant.parse(value).toEpochMilli() }
            .getOrElse {
                runCatching {
                    LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                }.getOrElse { 0L }
            }
    }

    private fun mapStatus(value: String?): Int {
        val normalized = value?.trim()?.lowercase().orEmpty()
        return when (normalized) {
            "ongoing" -> SNovel.ONGOING
            "completed" -> SNovel.COMPLETED
            "licensed" -> SNovel.LICENSED
            "publishing finished", "publishing_finished", "publishingfinished" -> SNovel.PUBLISHING_FINISHED
            "cancelled", "canceled", "dropped" -> SNovel.CANCELLED
            "on hiatus", "on_hiatus", "hiatus" -> SNovel.ON_HIATUS
            else -> SNovel.UNKNOWN
        }
    }

    private fun buildPopularOptionsJson(filters: JsonObject, showLatest: Boolean): String {
        return buildJsonObject {
            put("showLatestNovels", JsonPrimitive(showLatest))
            put("filters", filters)
        }.toString()
    }

    private fun toJsString(value: String): String = json.encodeToString(value)

    @Serializable
    private data class PluginNovelItem(
        val name: String,
        val path: String,
        val cover: String? = null,
    )

    @Serializable
    private data class PluginChapterItem(
        val name: String,
        val path: String,
        val releaseTime: String? = null,
        val chapterNumber: Double? = null,
        val page: String? = null,
    )

    @Serializable
    private data class PluginSourceNovel(
        val name: String,
        val path: String,
        val cover: String? = null,
        val genres: String? = null,
        val summary: String? = null,
        val author: String? = null,
        val artist: String? = null,
        val status: String? = null,
        val rating: Double? = null,
        val chapters: List<PluginChapterItem>? = null,
        val totalPages: Int? = null,
    )

    @Serializable
    private data class PluginSourcePage(
        val chapters: List<PluginChapterItem> = emptyList(),
    )
}
