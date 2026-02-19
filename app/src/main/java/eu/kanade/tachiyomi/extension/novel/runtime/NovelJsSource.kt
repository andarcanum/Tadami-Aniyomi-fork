package eu.kanade.tachiyomi.extension.novel.runtime

import android.util.Log
import eu.kanade.tachiyomi.extension.novel.NovelPluginId
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilter
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.NovelsPage
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import eu.kanade.tachiyomi.source.novel.NovelPluginImagePayload
import eu.kanade.tachiyomi.source.novel.NovelPluginImageSource
import eu.kanade.tachiyomi.source.novel.NovelSiteSource
import eu.kanade.tachiyomi.source.novel.NovelWebUrlSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import rx.Observable
import tachiyomi.domain.extension.novel.model.NovelPlugin
import java.net.URLDecoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Base64

class NovelJsSource internal constructor(
    private val plugin: NovelPlugin.Installed,
    private val script: String,
    private val runtimeFactory: NovelJsRuntimeFactory,
    private val json: Json,
    private val scriptBuilder: NovelPluginScriptBuilder,
    private val filterMapper: NovelPluginFilterMapper,
    private val resultNormalizer: NovelPluginResultNormalizer,
    private val runtimeOverride: NovelPluginRuntimeOverride,
) : NovelCatalogueSource, NovelSiteSource, NovelWebUrlSource, NovelPluginImageSource {
    override val id: Long = NovelPluginId.toSourceId(plugin.id)
    override val name: String = plugin.name
    override val lang: String = plugin.lang
    override val supportsLatest: Boolean = true
    override val siteUrl: String? = plugin.site

    private val mutex = Mutex()
    private var runtime: NovelJsRuntime? = null
    private var hasParsePage: Boolean? = null
    private var hasResolveUrl: Boolean? = null
    private var hasFetchImage: Boolean? = null
    private var cachedFiltersPayload: String? = null

    override fun getFilterList(): NovelFilterList {
        NovelPluginFilters.decodeFilterList(
            payload = cachedFiltersPayload,
            filterMapper = filterMapper,
        ).takeIf { it.isNotEmpty() }?.let { cached ->
            return cached
        }

        if (isWuxiaworldPlugin()) {
            return wuxiaworldFilterList()
        }

        return runBlocking {
            runPluginSafe(
                operation = "filters",
                defaultValue = NovelFilterList(),
            ) {
                withTimeoutOrNull(3_500) {
                    mutex.withLock { loadFiltersLocked() }
                } ?: loadFiltersWithIsolatedRuntime()
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
        return runPluginSafe(
            operation = "popularNovels(page=$page)",
            defaultValue = NovelsPage(emptyList(), hasNextPage = false),
        ) {
            getPopularNovels(page, getFilterList())
        }
    }

    override suspend fun getPopularNovels(page: Int, filters: NovelFilterList): NovelsPage {
        return runPluginSafe(
            operation = "popularNovels(page=$page, filters)",
            defaultValue = NovelsPage(emptyList(), hasNextPage = false),
        ) {
            if (isWuxiaworldPlugin()) {
                val options = resolveWuxiaworldCatalogOptions(
                    filters = filters,
                    defaultSort = WuxiaworldSort.Popular,
                )
                val items = mutex.withLock {
                    val runtime = ensureRuntimeLocked()
                    fetchWuxiaworldCatalog(runtime, page, options)
                }
                return@runPluginSafe items.toNovelsPage()
            }
            val items = mutex.withLock {
                val runtime = ensureRuntimeLocked()
                val options = buildPopularOptionsJson(
                    filters = resolveFilterValues(runtime, filters),
                    showLatest = false,
                )
                val payload = callPlugin(runtime, "popularNovels", page.toString(), options)
                parseNovelItems(payload)
            }
            items.toNovelsPage()
        }
    }

    override suspend fun getSearchNovels(page: Int, query: String, filters: NovelFilterList): NovelsPage {
        return runPluginSafe(
            operation = "searchNovels(page=$page)",
            defaultValue = NovelsPage(emptyList(), hasNextPage = false),
        ) {
            val items = mutex.withLock {
                val runtime = ensureRuntimeLocked()
                val filtersJson = resolveFilterValues(runtime, filters).toString()
                val payload = callPlugin(runtime, "searchNovels", toJsString(query), page.toString(), filtersJson)
                parseNovelItems(payload)
            }
            items.toNovelsPage()
        }
    }

    override suspend fun getLatestUpdates(page: Int): NovelsPage {
        return runPluginSafe(
            operation = "latestUpdates(page=$page)",
            defaultValue = NovelsPage(emptyList(), hasNextPage = false),
        ) {
            getLatestUpdates(page, getFilterList())
        }
    }

    override suspend fun getLatestUpdates(page: Int, filters: NovelFilterList): NovelsPage {
        return runPluginSafe(
            operation = "latestUpdates(page=$page, filters)",
            defaultValue = NovelsPage(emptyList(), hasNextPage = false),
        ) {
            if (isWuxiaworldPlugin()) {
                val options = resolveWuxiaworldCatalogOptions(
                    filters = filters,
                    defaultSort = WuxiaworldSort.Newest,
                )
                val items = mutex.withLock {
                    val runtime = ensureRuntimeLocked()
                    fetchWuxiaworldCatalog(runtime, page, options)
                }
                return@runPluginSafe items.toNovelsPage()
            }
            val items = mutex.withLock {
                val runtime = ensureRuntimeLocked()
                val options = buildPopularOptionsJson(
                    filters = resolveFilterValues(runtime, filters),
                    showLatest = true,
                )
                val payload = callPlugin(runtime, "popularNovels", page.toString(), options)
                parseNovelItems(payload)
            }
            items.toNovelsPage()
        }
    }

    override suspend fun getNovelDetails(novel: SNovel): SNovel {
        val sourceNovel = runPluginSafe(
            operation = "parseNovel(url=${novel.url})",
            defaultValue = null,
        ) {
            mutex.withLock {
                val runtime = ensureRuntimeLocked()
                val payload = callPlugin(runtime, "parseNovel", toJsString(novel.url))
                val parsed = NovelJsPayloadParser.parseNovel(json, payload)
                parsed?.let { enrichNovelDetails(runtime, novel.url, it) }
            }
        } ?: return novel

        return novel.apply {
            sourceNovel.path?.takeIf { it.isNotBlank() }?.let { url = it }
            sourceNovel.name?.takeIf { it.isNotBlank() }?.let { title = it }
            author = sourceNovel.author
            description = sourceNovel.summary
            genre = sourceNovel.genres
            status = mapStatus(sourceNovel.status)
            thumbnail_url = normalizeCoverUrl(sourceNovel.cover)
            initialized = true
        }
    }

    override suspend fun getChapterList(novel: SNovel): List<SNovelChapter> {
        return runPluginSafe(
            operation = "chapterList(url=${novel.url})",
            defaultValue = emptyList(),
        ) {
            val runtime = mutex.withLock { ensureRuntimeLocked() }
            val sourceNovel = runCatching {
                mutex.withLock {
                    val payload = callPlugin(runtime, "parseNovel", toJsString(novel.url))
                    NovelJsPayloadParser.parseNovel(json, payload)
                }
            }.getOrNull()

            if (sourceNovel == null) {
                val novelUpdatesChapters = fetchNovelUpdatesChapterFallback(runtime, novel.url)
                if (novelUpdatesChapters.isNotEmpty()) {
                    return@runPluginSafe normalizeChapters(novelUpdatesChapters).mapNotNull { it.toSChapterOrNull() }
                }

                val endpointChapters = fetchRulateFamilyChapterEndpointFallback(runtime, novel.url)
                if (endpointChapters.isNotEmpty()) {
                    return@runPluginSafe normalizeChapters(endpointChapters).mapNotNull { it.toSChapterOrNull() }
                }

                val htmlFallbackChapters = fetchHtmlChapterListFallback(runtime, novel.url)
                return@runPluginSafe normalizeChapters(htmlFallbackChapters).mapNotNull { it.toSChapterOrNull() }
            }

            val directChapters = sourceNovel.chapters ?: emptyList()
            if (directChapters.isNotEmpty()) {
                return@runPluginSafe normalizeChapters(directChapters).mapNotNull { it.toSChapterOrNull() }
            }

            val novelUpdatesChapters = fetchNovelUpdatesChapterFallback(runtime, sourceNovel.path ?: novel.url)
            if (novelUpdatesChapters.isNotEmpty()) {
                return@runPluginSafe normalizeChapters(novelUpdatesChapters).mapNotNull { it.toSChapterOrNull() }
            }

            val endpointChapters = fetchRulateFamilyChapterEndpointFallback(runtime, novel.url)
            if (endpointChapters.isNotEmpty()) {
                return@runPluginSafe normalizeChapters(endpointChapters).mapNotNull { it.toSChapterOrNull() }
            }

            val htmlFallbackChapters = fetchHtmlChapterListFallback(runtime, novel.url)
            if (htmlFallbackChapters.isNotEmpty()) {
                return@runPluginSafe normalizeChapters(htmlFallbackChapters).mapNotNull { it.toSChapterOrNull() }
            }

            val totalPages = sourceNovel.totalPages ?: return@runPluginSafe emptyList()
            if (hasParsePage != true) return@runPluginSafe emptyList()

            val collected = mutableListOf<ParsedPluginChapter>()
            for (page in 1..totalPages) {
                val payload = mutex.withLock {
                    callPlugin(runtime, "parsePage", toJsString(novel.url), toJsString(page.toString()))
                }
                val pageResult = NovelJsPayloadParser.parsePage(json, payload) ?: continue
                collected.addAll(pageResult.chapters)
            }
            normalizeChapters(collected).mapNotNull { it.toSChapterOrNull() }
        }
    }

    override suspend fun getChapterText(chapter: SNovelChapter): String {
        return runPluginSafe(
            operation = "chapterText(url=${chapter.url})",
            defaultValue = "",
        ) {
            mutex.withLock {
                val runtime = ensureRuntimeLocked()
                val payload = callPlugin(runtime, "parseChapter", toJsString(chapter.url))
                if (payload.isBlank() || payload == "null") "" else json.decodeFromString<String>(payload)
            }
        }
    }

    override suspend fun getNovelWebUrl(novelPath: String): String? {
        return runPluginSafe(
            operation = "resolveNovelUrl(path=$novelPath)",
            defaultValue = null,
        ) {
            mutex.withLock {
                val runtime = ensureRuntimeLocked()
                if (hasResolveUrl != true) return@withLock null
                decodeResolvedUrl(
                    callPlugin(
                        runtime = runtime,
                        functionName = "resolveUrl",
                        toJsString(novelPath),
                        "true",
                    ),
                )
            }
        }
    }

    override suspend fun getChapterWebUrl(chapterPath: String, novelPath: String?): String? {
        return runPluginSafe(
            operation = "resolveChapterUrl(path=$chapterPath)",
            defaultValue = null,
        ) {
            mutex.withLock {
                val runtime = ensureRuntimeLocked()
                if (hasResolveUrl != true) return@withLock null
                decodeResolvedUrl(
                    callPlugin(
                        runtime = runtime,
                        functionName = "resolveUrl",
                        toJsString(chapterPath),
                        "false",
                    ),
                )
            }
        }
    }

    override suspend fun fetchImage(imageRef: String): NovelPluginImagePayload? {
        return runPluginSafe(
            operation = "fetchImage(ref=$imageRef)",
            defaultValue = null,
        ) {
            mutex.withLock {
                val runtime = ensureRuntimeLocked()
                if (hasFetchImage != true) return@withLock null
                val payload = callPluginWithTimeout(
                    runtime = runtime,
                    functionName = "fetchImage",
                    timeoutMs = FETCH_IMAGE_RUNTIME_TIMEOUT_MS,
                    toJsString(imageRef),
                )
                decodePluginImagePayload(payload)
            }
        }
    }

    internal fun clearInMemoryCaches() {
        cachedFiltersPayload = null
    }

    private fun loadFiltersLocked(): NovelFilterList {
        val payload = cachedFiltersPayload ?: run {
            val runtime = ensureRuntimeLocked()
            callPlugin(runtime, "filters")
        }.also { cachedFiltersPayload = it }
        return NovelPluginFilters.decodeFilterList(payload, filterMapper)
    }

    private fun loadFiltersWithIsolatedRuntime(): NovelFilterList {
        return runCatching {
            runtimeFactory.create(plugin.id).use { isolatedRuntime ->
                val moduleName = plugin.id
                val moduleLiteral = toJsString(moduleName)
                val wrappedScript = scriptBuilder.wrap(script, moduleName)
                isolatedRuntime.evaluate(wrappedScript, "${plugin.id}.js")
                isolatedRuntime.evaluate(
                    """
                    var __plugin = require($moduleLiteral);
                    if (__plugin && __plugin.default) { __plugin = __plugin.default; }
                    """.trimIndent(),
                    "novel-plugin-init.js",
                )
                val payload = callPlugin(isolatedRuntime, "filters")
                if (payload.isNotBlank() && payload != "null") {
                    cachedFiltersPayload = payload
                }
                NovelPluginFilters.decodeFilterList(payload, filterMapper)
            }
        }.getOrDefault(NovelFilterList())
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
        hasResolveUrl = (instance.evaluate("typeof __plugin.resolveUrl === \"function\"") as? Boolean) == true
        hasFetchImage = (instance.evaluate("typeof __plugin.fetchImage === \"function\"") as? Boolean) == true
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

    private fun callPluginWithTimeout(
        runtime: NovelJsRuntime,
        functionName: String,
        timeoutMs: Long,
        vararg args: String,
    ): String {
        val call = when (functionName) {
            "filters" -> "JSON.stringify(__plugin && __plugin.filters ? __plugin.filters : {})"
            else -> {
                val joinedArgs = args.joinToString(", ")
                "JSON.stringify(__resolve(__plugin.$functionName($joinedArgs)))"
            }
        }
        val result = runtime.evaluate(
            script = call,
            fileName = "novel-plugin-call.js",
            timeoutMs = timeoutMs,
        )
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

    private fun decodeResolvedUrl(payload: String): String? {
        if (payload.isBlank() || payload == "null") return null
        return runCatching { json.decodeFromString<String>(payload) }
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun decodePluginImagePayload(payload: String): NovelPluginImagePayload? {
        if (payload.isBlank() || payload == "null") return null
        val element = runCatching { json.decodeFromString<JsonElement>(payload) }.getOrNull() ?: return null

        return when (element) {
            is JsonPrimitive -> {
                val rawValue = element.contentOrNull?.trim().orEmpty()
                decodeImagePayloadFromDataUrl(rawValue)
            }
            is JsonObject -> {
                val cacheKey = element["cacheKey"]?.asStringOrNull()
                    ?: element["key"]?.asStringOrNull()
                val dataUrl = element["dataUrl"]?.asStringOrNull()
                    ?: element["data"]?.asStringOrNull()
                if (!dataUrl.isNullOrBlank()) {
                    decodeImagePayloadFromDataUrl(dataUrl)?.let { decoded ->
                        return decoded.copy(cacheKey = cacheKey ?: decoded.cacheKey)
                    }
                }

                val base64Value = element["base64"]?.asStringOrNull()
                    ?: element["bytesBase64"]?.asStringOrNull()
                    ?: element["bodyBase64"]?.asStringOrNull()
                val mimeType = element["mimeType"]?.asStringOrNull()
                    ?: element["contentType"]?.asStringOrNull()
                    ?: "application/octet-stream"

                decodeImagePayloadFromBase64(base64Value = base64Value, mimeType = mimeType)
                    ?.copy(cacheKey = cacheKey)
            }
            else -> null
        }
    }

    private fun decodeImagePayloadFromDataUrl(dataUrl: String): NovelPluginImagePayload? {
        if (!dataUrl.startsWith("data:", ignoreCase = true)) return null
        val commaIndex = dataUrl.indexOf(',')
        if (commaIndex <= 5) return null

        val metadata = dataUrl.substring(5, commaIndex)
        val mimeType = metadata.substringBefore(';')
            .trim()
            .ifBlank { "application/octet-stream" }
        val isBase64 = metadata.contains(";base64", ignoreCase = true)
        val payloadBody = dataUrl.substring(commaIndex + 1)

        val bytes = if (isBase64) {
            runCatching { Base64.getDecoder().decode(payloadBody) }.getOrNull()
        } else {
            runCatching {
                URLDecoder.decode(payloadBody, Charsets.UTF_8.name()).toByteArray(Charsets.UTF_8)
            }.getOrNull()
        } ?: return null

        return NovelPluginImagePayload(
            bytes = bytes,
            mimeType = mimeType,
            cacheKey = null,
        )
    }

    private fun decodeImagePayloadFromBase64(base64Value: String?, mimeType: String): NovelPluginImagePayload? {
        val normalizedBase64 = base64Value?.trim().orEmpty()
        if (normalizedBase64.isBlank()) return null

        val bytes = runCatching { Base64.getDecoder().decode(normalizedBase64) }
            .getOrNull()
            ?: return null

        return NovelPluginImagePayload(
            bytes = bytes,
            mimeType = mimeType,
            cacheKey = null,
        )
    }

    private fun JsonElement.asStringOrNull(): String? {
        return (this as? JsonPrimitive)?.contentOrNull
    }

    private fun List<PluginNovelItem>.toNovelsPage(): NovelsPage {
        val novels = asSequence()
            .map { it.toSNovel() }
            .filter { it.url.isNotBlank() && it.title.isNotBlank() }
            .distinctBy { it.url.trim() }
            .toList()
        val hasNextPage = novels.isNotEmpty()
        return NovelsPage(novels, hasNextPage)
    }

    private fun PluginNovelItem.toSNovel(): SNovel {
        return SNovel.create().also {
            it.url = path
            it.title = name
            it.thumbnail_url = normalizeCoverUrl(cover)
        }
    }

    private fun ParsedPluginChapter.toSChapterOrNull(): SNovelChapter? {
        val resolvedPath = path?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val resolvedName = name?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return SNovelChapter.create().also {
            it.url = resolvedPath
            it.name = resolvedName
            it.date_upload = parseChapterDate(releaseTime)
            it.chapter_number = chapterNumber?.toFloat() ?: -1f
            it.scanlator = resolveScanlatorLabel(scanlator, page)
        }
    }

    private fun resolveScanlatorLabel(
        scanlator: String?,
        page: String?,
    ): String? {
        val explicitScanlator = scanlator?.trim()?.takeIf { it.isNotEmpty() }
        if (explicitScanlator != null) return explicitScanlator

        val pageLabel = page?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (pageLabel.all { it.isDigit() }) return null
        return pageLabel
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

    private fun resolveFilterValues(runtime: NovelJsRuntime, filters: NovelFilterList): JsonObject {
        if (cachedFiltersPayload.isNullOrBlank()) {
            val payload = runCatching { callPlugin(runtime, "filters") }.getOrNull()
            if (!payload.isNullOrBlank() && payload != "null") {
                cachedFiltersPayload = payload
            }
        }
        return NovelPluginFilters.toFilterValuesWithDefaults(
            filters = filters,
            cachedFiltersPayload = cachedFiltersPayload,
            filterMapper = filterMapper,
        )
    }

    private fun resolveWuxiaworldCatalogOptions(
        filters: NovelFilterList,
        defaultSort: WuxiaworldSort,
    ): WuxiaworldCatalogOptions {
        val sort = filters.filterIsInstance<WuxiaworldSortFilter>()
            .firstOrNull()
            ?.selectedSort()
            ?: defaultSort
        val direction = filters.filterIsInstance<WuxiaworldDirectionFilter>()
            .firstOrNull()
            ?.selectedDirection()
            ?: WuxiaworldDirection.Descending
        val status = filters.filterIsInstance<WuxiaworldStatusFilter>()
            .firstOrNull()
            ?.selectedStatus()
            ?: WuxiaworldStatus.All
        return WuxiaworldCatalogOptions(
            sort = sort,
            direction = direction,
            status = status,
        )
    }

    private fun fetchWuxiaworldCatalog(
        runtime: NovelJsRuntime,
        page: Int,
        options: WuxiaworldCatalogOptions,
    ): List<PluginNovelItem> {
        val siteLiteral = toJsString(siteUrl.orEmpty())
        val sortLiteral = toJsString(options.sort.value)
        val directionLiteral = toJsString(options.direction.value)
        val statusLiteral = toJsString(options.status.value)
        val pageNumber = page.coerceAtLeast(1)
        val script = """
            (function() {
              var fetchApi = require("@libs/fetch").fetchApi;
              var base = (__plugin && __plugin.site) ? __plugin.site : $siteLiteral;
              if (!base) return "[]";
              var url = String(base).replace(/\/+$/, "") + "/api/novels";
              var response = __resolve(fetchApi(url).catch(function() { return null; }));
              if (!response || (typeof response.status === "number" && response.status >= 400)) return "[]";
              var payload = __resolve(response.json().catch(function() { return null; }));
              var items = payload && Array.isArray(payload.items) ? payload.items.slice() : [];
              var sort = $sortLiteral;
              var direction = $directionLiteral;
              var status = $statusLiteral;

              if (status === "active") {
                items = items.filter(function(item) { return !!(item && item.active); });
              } else if (status === "finished") {
                items = items.filter(function(item) { return !!(item && item.active === false); });
              }

              function numberValue(value, fallback) {
                return (typeof value === "number" && !isNaN(value)) ? value : fallback;
              }
              function compareByName(a, b) {
                var left = String((a && a.name) || "");
                var right = String((b && b.name) || "");
                if (left < right) return -1;
                if (left > right) return 1;
                return 0;
              }

              if (sort === "popular") {
                items.sort(function(a, b) {
                  var scoreDiff = numberValue((b || {}).reviewScore, -1) - numberValue((a || {}).reviewScore, -1);
                  if (scoreDiff !== 0) return scoreDiff;
                  return numberValue((b || {}).id, 0) - numberValue((a || {}).id, 0);
                });
              } else if (sort === "trending") {
                items.sort(function(a, b) {
                  var trendDiff = numberValue((b || {}).trendingScore, -1) - numberValue((a || {}).trendingScore, -1);
                  if (trendDiff !== 0) return trendDiff;
                  return numberValue((b || {}).reviewScore, -1) - numberValue((a || {}).reviewScore, -1);
                });
              } else if (sort === "name") {
                items.sort(compareByName);
              } else {
                items.sort(function(a, b) {
                  return numberValue((b || {}).id, 0) - numberValue((a || {}).id, 0);
                });
              }

              if (direction === "asc") {
                items.reverse();
              }

              var page = $pageNumber;
              var perPage = 30;
              var start = (page - 1) * perPage;
              var selected = items.slice(start, start + perPage);
              var mapped = selected.map(function(item) {
                var cover = item ? item.coverUrl : null;
                if (cover && typeof cover === "object" && typeof cover.value === "string") {
                  cover = cover.value;
                }
                return {
                  name: String((item && item.name) || ""),
                  path: "/novel/" + String((item && item.slug) || "") + "/",
                  cover: cover
                };
              }).filter(function(item) {
                return !!item.path && item.path !== "/novel//" && !!item.name;
              });
              return JSON.stringify(mapped);
            })();
        """.trimIndent()
        val payload = runtime.evaluate(script, "novel-plugin-wuxiaworld-catalog.js") as? String ?: return emptyList()
        return parseNovelItems(payload)
    }

    private fun enrichNovelDetails(
        runtime: NovelJsRuntime,
        novelPath: String,
        parsed: ParsedPluginNovel,
    ): ParsedPluginNovel {
        val normalized = parsed.copy(cover = normalizeCoverUrl(parsed.cover))
        if (!isRulateFamilyPlugin()) return normalized
        if (!needsNovelDetailsFallback(normalized)) return normalized

        val fallback = fetchNovelDetailsFallback(runtime, novelPath) ?: return normalized
        return normalized.copy(
            name = normalized.name.takeIf { !it.isNullOrBlank() } ?: fallback.name,
            cover = normalizeCoverUrl(normalized.cover.takeIf { !it.isNullOrBlank() } ?: fallback.cover),
            summary = normalized.summary.takeIf { !it.isNullOrBlank() } ?: fallback.summary,
            genres = normalized.genres.takeIf { !it.isNullOrBlank() } ?: fallback.genres,
            author = normalized.author.takeIf { !it.isNullOrBlank() } ?: fallback.author,
            status = normalized.status.takeIf { !it.isNullOrBlank() } ?: fallback.status,
        )
    }

    private fun needsNovelDetailsFallback(novel: ParsedPluginNovel): Boolean {
        return novel.summary.isNullOrBlank() || novel.genres.isNullOrBlank() || novel.cover.isNullOrBlank()
    }

    private fun fetchNovelDetailsFallback(
        runtime: NovelJsRuntime,
        novelPath: String,
    ): ParsedPluginNovel? {
        val pluginIdLiteral = toJsString(plugin.id)
        val siteLiteral = toJsString(siteUrl.orEmpty())
        val pathLiteral = toJsString(novelPath)
        val script = """
            (function() {
              var fetchApi = require("@libs/fetch").fetchApi;
              function toAbsolute(base, path) {
                var baseValue = String(base || "");
                var pathValue = String(path || "");
                if (!pathValue) return baseValue;
                if (/^https?:\/\//i.test(pathValue)) return pathValue;
                if (!baseValue) return pathValue;
                if (baseValue.slice(-1) === "/" && pathValue.charAt(0) === "/") return baseValue + pathValue.slice(1);
                if (baseValue.slice(-1) !== "/" && pathValue.charAt(0) !== "/") return baseValue + "/" + pathValue;
                return baseValue + pathValue;
              }
              function firstNode(selector, html) {
                var nodes = JSON.parse(__native.select(html, selector));
                return nodes && nodes.length ? (nodes[0] || {}) : null;
              }
              function firstText(selector, html) {
                var node = firstNode(selector, html);
                if (!node) return null;
                var text = String(node.text || "").replace(/\s+/g, " ").trim();
                return text || null;
              }
              function firstAttr(selector, attr, html) {
                var node = firstNode(selector, html);
                if (!node || !node.attrs) return null;
                var value = node.attrs[attr];
                if (value == null || value === "") return null;
                return String(value).trim();
              }
              function collectText(selector, html) {
                var nodes = JSON.parse(__native.select(html, selector));
                var seen = {};
                var values = [];
                for (var i = 0; i < nodes.length; i++) {
                  var node = nodes[i] || {};
                  var value = String(node.text || "").replace(/\s+/g, " ").trim();
                  if (!value) continue;
                  if (seen[value]) continue;
                  seen[value] = true;
                  values.push(value);
                }
                return values;
              }

              var pluginId = String($pluginIdLiteral || "").toLowerCase();
              var base = (__plugin && __plugin.baseUrl) ? __plugin.baseUrl : $siteLiteral;
              var url = toAbsolute(base, $pathLiteral);
              var headers = (__plugin && __plugin.headers) ? __plugin.headers : {};
              var response = __resolve(fetchApi(url, { headers: headers }).catch(function() { return null; }));
              if (!response || (typeof response.status === "number" && response.status >= 400)) return "{}";
              var html = __resolve(response.text().catch(function() { return ""; }));
              if (!html) return "{}";

              var cover = firstAttr('meta[property="og:image"]', 'content', html) ||
                firstAttr('.book__cover img, .images img, .images .slick-slide img', 'data-src', html) ||
                firstAttr('.book__cover img, .images img, .images .slick-slide img', 'src', html);
              var summary = firstText('.book__description, #Info > div p, #editdescription, .wi_fic_desc', html) ||
                firstAttr('meta[property="og:description"], meta[name="description"]', 'content', html);
              var genres = collectText('#seriesgenre a, .fic_genre, div.span5 p em a, .book__genres a', html).join(',');
              var author = firstText('#authtag a, #authtag, .auth_name_fic, div.span5 p:contains("Автор") em', html);
              var status = firstText('#editstatus, .rnd_stats, div.span5 p:contains("Выпуск") em', html);
              var name = firstText('.seriestitlenu, .fic_title, .book__title, h1', html) ||
                firstAttr('meta[property="og:title"]', 'content', html);

              if (pluginId !== 'rulate' && pluginId !== 'erolate') {
                return "{}";
              }

              return JSON.stringify({
                name: name || null,
                cover: cover || null,
                summary: summary || null,
                genres: genres || null,
                author: author || null,
                status: status || null
              });
            })();
        """.trimIndent()
        val payload = runtime.evaluate(script, "novel-plugin-details-fallback.js") as? String ?: return null
        if (payload.isBlank() || payload == "null" || payload == "{}") return null
        return NovelJsPayloadParser.parseNovel(json, payload)
    }

    private fun normalizeCoverUrl(value: String?): String? {
        val input = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (!isRulateFamilyPlugin()) return input

        var normalized = input
        normalized = normalized.replace(Regex("([?&])(w|h|width|height)=\\d+"), "")
        normalized = normalized.replace("?&", "?").trimEnd('?', '&')
        normalized = normalized.replace("/thumbs/", "/")
        normalized = normalized.replace("/thumb/", "/")
        return normalized
    }

    private fun wuxiaworldFilterList(): NovelFilterList {
        return NovelFilterList(
            WuxiaworldSortFilter(),
            WuxiaworldDirectionFilter(),
            WuxiaworldStatusFilter(),
        )
    }

    private fun toJsString(value: String): String = json.encodeToString(value)

    private fun normalizeChapters(chapters: List<ParsedPluginChapter>): List<ParsedPluginChapter> {
        return resultNormalizer.normalize(
            pluginId = plugin.id,
            chapters = chapters,
            policy = runtimeOverride.chapterFallbackPolicy,
        )
    }

    private fun fetchRulateFamilyChapterEndpointFallback(
        runtime: NovelJsRuntime,
        novelPath: String,
    ): List<ParsedPluginChapter> {
        if (!isRulateFamilyPlugin()) return emptyList()
        val bookId = extractBookIdFromPath(novelPath) ?: return emptyList()

        val siteLiteral = toJsString(siteUrl.orEmpty())
        val bookIdLiteral = toJsString(bookId)
        val script = """
            (function() {
              var fetchApi = require("@libs/fetch").fetchApi;
              var base = (__plugin && __plugin.baseUrl) ? __plugin.baseUrl : $siteLiteral;
              if (!base) return "[]";
              var endpoint = base.replace(/\/+$/, "") + "/book/" + $bookIdLiteral + "/chapters";
              var headers = (__plugin && __plugin.headers) ? __plugin.headers : {};
              var data = __resolve(fetchApi(endpoint, { headers: headers })
                .then(function(res) {
                  if (!res) return [];
                  if (typeof res.status === "number" && res.status >= 400) return [];
                  if (typeof res.json !== "function") return [];
                  return res.json().catch(function() { return []; });
                })
                .catch(function() { return []; }));
              return JSON.stringify(Array.isArray(data) ? data : []);
            })();
        """.trimIndent()

        val payload = runtime.evaluate(
            script,
            "novel-plugin-rulate-chapters-fallback.js",
        ) as? String ?: return emptyList()
        if (payload.isBlank() || payload == "null") return emptyList()
        return NovelJsPayloadParser.parseRulateFamilyChapterEndpoint(json, payload, bookId)
    }

    private fun fetchNovelUpdatesChapterFallback(
        runtime: NovelJsRuntime,
        novelPath: String,
    ): List<ParsedPluginChapter> {
        if (!isNovelUpdatesPlugin()) return emptyList()

        val novelHtml = fetchNovelUpdatesNovelPageHtml(runtime, novelPath) ?: return emptyList()
        val novelId = extractNovelUpdatesPostId(novelHtml)
        if (!novelId.isNullOrBlank()) {
            val chaptersHtml = fetchNovelUpdatesAjaxHtml(runtime, novelId)
            val ajaxChapters = parseNovelUpdatesChaptersHtml(
                chaptersHtml = chaptersHtml.orEmpty(),
                siteUrl = siteUrl,
            )
            if (ajaxChapters.isNotEmpty()) {
                return ajaxChapters
            }
        }

        return parseNovelUpdatesChaptersHtml(
            chaptersHtml = novelHtml,
            siteUrl = siteUrl,
        )
    }

    private fun fetchNovelUpdatesNovelPageHtml(
        runtime: NovelJsRuntime,
        novelPath: String,
    ): String? {
        val siteLiteral = toJsString(siteUrl.orEmpty())
        val novelPathLiteral = toJsString(novelPath)
        val script = """
            (function() {
              var fetchApi = require("@libs/fetch").fetchApi;
              function toAbsolute(base, path) {
                var baseValue = String(base || "");
                var pathValue = String(path || "");
                if (!pathValue) return baseValue;
                if (/^https?:\/\//i.test(pathValue)) return pathValue;
                if (!baseValue) return pathValue;
                if (baseValue.slice(-1) === "/" && pathValue.charAt(0) === "/") return baseValue + pathValue.slice(1);
                if (baseValue.slice(-1) !== "/" && pathValue.charAt(0) !== "/") return baseValue + "/" + pathValue;
                return baseValue + pathValue;
              }
              var base = (__plugin && __plugin.baseUrl) ? __plugin.baseUrl : $siteLiteral;
              var url = toAbsolute(base, $novelPathLiteral);
              var headers = (__plugin && __plugin.headers) ? __plugin.headers : {};
              var response = __resolve(fetchApi(url, { headers: headers }).catch(function() { return null; }));
              if (!response || (typeof response.status === "number" && response.status >= 400)) return "";
              return __resolve(response.text().catch(function() { return ""; })) || "";
            })();
        """.trimIndent()
        return (runtime.evaluate(script, "novelupdates-page-fallback.js") as? String)
            ?.takeIf { it.isNotBlank() }
    }

    private fun fetchNovelUpdatesAjaxHtml(
        runtime: NovelJsRuntime,
        novelId: String,
    ): String? {
        val siteLiteral = toJsString(siteUrl.orEmpty())
        val novelIdLiteral = toJsString(novelId)
        val script = """
            (function() {
              var fetchApi = require("@libs/fetch").fetchApi;
              var base = (__plugin && __plugin.baseUrl) ? __plugin.baseUrl : $siteLiteral;
              if (!base) return "";
              var endpoint = base.replace(/\/+$/, "") + "/wp-admin/admin-ajax.php";
              var headers = (__plugin && __plugin.headers) ? __plugin.headers : {};
              var form = new FormData();
              form.append("action", "nd_getchapters");
              form.append("mygrr", "0");
              form.append("mypostid", $novelIdLiteral);
              var response = __resolve(fetchApi(endpoint, {
                method: "POST",
                body: form,
                headers: headers
              }).catch(function() { return null; }));
              if (!response || (typeof response.status === "number" && response.status >= 400)) return "";
              return __resolve(response.text().catch(function() { return ""; })) || "";
            })();
        """.trimIndent()
        return (runtime.evaluate(script, "novelupdates-ajax-fallback.js") as? String)
            ?.takeIf { it.isNotBlank() }
    }

    private fun fetchHtmlChapterListFallback(
        runtime: NovelJsRuntime,
        novelPath: String,
    ): List<ParsedPluginChapter> {
        if (!isHtmlChapterFallbackPlugin()) return emptyList()

        val pluginIdLiteral = toJsString(plugin.id)
        val siteLiteral = toJsString(siteUrl.orEmpty())
        val novelPathLiteral = toJsString(novelPath)
        val script = """
            (function() {
              var fetchApi = require("@libs/fetch").fetchApi;
              function toAbsolute(base, path) {
                var baseValue = String(base || "");
                var pathValue = String(path || "");
                if (!pathValue) return baseValue;
                if (/^https?:\/\//i.test(pathValue)) return pathValue;
                if (!baseValue) return pathValue;
                if (baseValue.slice(-1) === "/" && pathValue.charAt(0) === "/") return baseValue + pathValue.slice(1);
                if (baseValue.slice(-1) !== "/" && pathValue.charAt(0) !== "/") return baseValue + "/" + pathValue;
                return baseValue + pathValue;
              }
              function normalizePath(href) {
                if (href == null) return null;
                var value = String(href).trim();
                if (!value) return null;
                if (/^https?:\/\//i.test(value)) {
                  value = __native.getPathname(value) || value;
                }
                if (value.charAt(0) !== "/") {
                  value = "/" + value.replace(/^\/+/, "");
                }
                return value;
              }
              function isCandidate(path, pluginId) {
                if (!path) return false;
                if (/\/chapter\//i.test(path)) return true;
                if (pluginId === "scribblehub" && /\/read\//i.test(path)) return true;
                return false;
              }
              function extract(html, selector) {
                var nodes = JSON.parse(__native.select(html, selector));
                var output = [];
                for (var i = 0; i < nodes.length; i++) {
                  var node = nodes[i] || {};
                  var attrs = node.attrs || {};
                  var path = normalizePath(attrs.href || attrs["data-href"]);
                  if (!path) continue;
                  output.push({
                    name: String(node.text || "").replace(/\s+/g, " ").trim(),
                    path: path
                  });
                }
                return output;
              }

              var pluginId = String($pluginIdLiteral || "").toLowerCase();
              var selectors = pluginId === "scribblehub"
                ? ["a.toc_a", ".toc_w a", "a[href*='/read/']", "a[href*='/chapter/']"]
                : ["table#chapters a[href*='/chapter/']", ".chapter-row a[href*='/chapter/']", "a[href*='/chapter/']"];
              var base = (__plugin && __plugin.baseUrl) ? __plugin.baseUrl : $siteLiteral;
              var url = toAbsolute(base, $novelPathLiteral);
              var headers = (__plugin && __plugin.headers) ? __plugin.headers : {};
              var response = __resolve(fetchApi(url, { headers: headers }).catch(function() { return null; }));
              if (!response || (typeof response.status === "number" && response.status >= 400)) return "[]";
              var html = __resolve(response.text().catch(function() { return ""; }));
              if (!html) return "[]";

              var seen = {};
              var chapters = [];
              for (var s = 0; s < selectors.length; s++) {
                var extracted = extract(html, selectors[s]);
                for (var i = 0; i < extracted.length; i++) {
                  var item = extracted[i];
                  if (!isCandidate(item.path, pluginId)) continue;
                  if (seen[item.path]) continue;
                  seen[item.path] = true;
                  if (!item.name) item.name = "Chapter " + (chapters.length + 1);
                  chapters.push(item);
                }
                if (chapters.length >= 10) break;
              }

              return JSON.stringify(chapters);
            })();
        """.trimIndent()

        val payload = runtime.evaluate(
            script,
            "novel-plugin-html-chapters-fallback.js",
        ) as? String ?: return emptyList()
        if (payload.isBlank() || payload == "null") return emptyList()
        return NovelJsPayloadParser.parseChaptersArray(json, payload)
    }

    private fun isRulateFamilyPlugin(): Boolean {
        return plugin.id.equals("rulate", ignoreCase = true) ||
            plugin.id.equals("erolate", ignoreCase = true)
    }

    private fun isWuxiaworldPlugin(): Boolean {
        return plugin.id.equals("wuxiaworld", ignoreCase = true)
    }

    private fun isNovelUpdatesPlugin(): Boolean {
        return plugin.id.equals("novelupdates", ignoreCase = true)
    }

    private fun isHtmlChapterFallbackPlugin(): Boolean {
        return plugin.id.equals("royalroad", ignoreCase = true) ||
            plugin.id.equals("scribblehub", ignoreCase = true)
    }

    private fun extractBookIdFromPath(path: String?): String? {
        val value = path?.trim().orEmpty()
        if (value.isBlank()) return null
        val match = Regex("/book/(\\d+)").find(value) ?: return null
        return match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }
    }

    private suspend inline fun <T> runPluginSafe(
        operation: String,
        defaultValue: T,
        crossinline block: suspend () -> T,
    ): T {
        return try {
            block()
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            Log.e(LOG_TAG, "Novel plugin error during $operation for ${plugin.id}", error)
            defaultValue
        }
    }

    @Serializable
    private data class PluginNovelItem(
        val name: String,
        val path: String,
        val cover: String? = null,
    )

    companion object {
        private const val LOG_TAG = "NovelJsSource"
        private const val FETCH_IMAGE_RUNTIME_TIMEOUT_MS = 15_000L
    }

    private enum class WuxiaworldSort(val value: String) {
        Popular("popular"),
        Newest("new"),
        Trending("trending"),
        Name("name"),
    }

    private enum class WuxiaworldDirection(val value: String) {
        Descending("desc"),
        Ascending("asc"),
    }

    private enum class WuxiaworldStatus(val value: String) {
        All("all"),
        Active("active"),
        Finished("finished"),
    }

    private data class WuxiaworldCatalogOptions(
        val sort: WuxiaworldSort,
        val direction: WuxiaworldDirection,
        val status: WuxiaworldStatus,
    )

    private class WuxiaworldSortFilter(state: Int = 0) :
        NovelFilter.Select<String>(
            name = "Sort Results By",
            values = arrayOf("Popular", "Newest", "Trending", "Name"),
            state = state,
        ) {
        fun selectedSort(): WuxiaworldSort {
            return when (state) {
                1 -> WuxiaworldSort.Newest
                2 -> WuxiaworldSort.Trending
                3 -> WuxiaworldSort.Name
                else -> WuxiaworldSort.Popular
            }
        }
    }

    private class WuxiaworldDirectionFilter(state: Int = 0) :
        NovelFilter.Select<String>(
            name = "Order By",
            values = arrayOf("Descending", "Ascending"),
            state = state,
        ) {
        fun selectedDirection(): WuxiaworldDirection {
            return if (state == 1) WuxiaworldDirection.Ascending else WuxiaworldDirection.Descending
        }
    }

    private class WuxiaworldStatusFilter(state: Int = 0) :
        NovelFilter.Select<String>(
            name = "Story Status",
            values = arrayOf("All", "Active", "Finished"),
            state = state,
        ) {
        fun selectedStatus(): WuxiaworldStatus {
            return when (state) {
                1 -> WuxiaworldStatus.Active
                2 -> WuxiaworldStatus.Finished
                else -> WuxiaworldStatus.All
            }
        }
    }
}

internal fun parseNovelUpdatesChaptersHtml(
    chaptersHtml: String,
    siteUrl: String?,
): List<ParsedPluginChapter> {
    if (chaptersHtml.isBlank()) return emptyList()
    val document = Jsoup.parseBodyFragment(chaptersHtml)
    val chapterRows = document.select("li.sp_li_chp")
        .ifEmpty {
            document.select(
                "#myTable tr:has(a[href]), #myTable tr:has(a[data-href]), " +
                    "tr:has(a[href]), tr:has(a[data-href]), " +
                    "li:has(a[href]), li:has(a[data-href])",
            )
        }
    if (chapterRows.isEmpty()) return emptyList()

    return chapterRows.mapIndexedNotNull { index, row ->
        val href = selectNovelUpdatesChapterHref(row) ?: return@mapIndexedNotNull null
        val normalizedPath = normalizeNovelUpdatesChapterPath(href, siteUrl) ?: return@mapIndexedNotNull null
        val chapterName = row.text()
            .replace("v", "volume ")
            .replace("c", " chapter ")
            .replace("part", "part ")
            .replace("ss", "SS")
            .replace(Regex("\\b\\w")) { it.value.uppercase() }
            .trim()
            .ifBlank { "Chapter ${index + 1}" }

        ParsedPluginChapter(
            name = chapterName,
            path = normalizedPath,
            chapterNumber = (index + 1).toDouble(),
        )
    }
}

internal fun extractNovelUpdatesPostId(html: String): String? {
    if (html.isBlank()) return null
    val document = Jsoup.parseBodyFragment(html)
    document.selectFirst("input#mypostid")
        ?.attr("value")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }

    val patterns = listOf(
        Regex("""\bmypostid\b["']?\s*[:=]\s*["']?(\d+)""", RegexOption.IGNORE_CASE),
        Regex("""\bpostid\b["']?\s*[:=]\s*["']?(\d+)""", RegexOption.IGNORE_CASE),
    )

    return patterns.asSequence()
        .mapNotNull { regex -> regex.find(html)?.groupValues?.getOrNull(1) }
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
}

internal fun normalizeNovelUpdatesChapterPath(
    href: String,
    siteUrl: String?,
): String? {
    var value = href.trim()
    if (value.isBlank()) return null

    if (value.startsWith("//")) {
        value = "https:$value"
    }

    if (value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)) {
        val site = siteUrl?.trim()?.trimEnd('/')
        if (!site.isNullOrBlank() && value.startsWith(site, ignoreCase = true)) {
            val stripped = value.substring(site.length).trim()
            if (stripped.isBlank()) return null
            return if (stripped.startsWith("/")) stripped else "/$stripped"
        }
        return value
    }

    if (!value.startsWith("/")) {
        value = "/$value"
    }
    return value
}

private fun selectNovelUpdatesChapterHref(row: Element): String? {
    val hrefs = row.select("a[href], a[data-href]")
        .mapNotNull { anchor ->
            val href = anchor.attr("href").trim()
            val dataHref = anchor.attr("data-href").trim()
            when {
                href.isNotBlank() -> href
                dataHref.isNotBlank() -> dataHref
                else -> null
            }
        }
    if (hrefs.isEmpty()) return null

    return hrefs.firstOrNull { isLikelyNovelUpdatesChapterHref(it) }
        ?: hrefs.lastOrNull()
}

private fun isLikelyNovelUpdatesChapterHref(href: String): Boolean {
    val value = href.lowercase()
    return value.contains("/extnu/") ||
        value.contains("/go-to/") ||
        value.contains("chapter") ||
        value.contains("/read") ||
        value.startsWith("//") ||
        value.startsWith("http://") ||
        value.startsWith("https://")
}

internal data class ParsedPluginChapter(
    val name: String? = null,
    val path: String? = null,
    val releaseTime: String? = null,
    val chapterNumber: Double? = null,
    val scanlator: String? = null,
    val page: String? = null,
)

internal data class ParsedPluginNovel(
    val name: String? = null,
    val path: String? = null,
    val cover: String? = null,
    val genres: String? = null,
    val summary: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val status: String? = null,
    val rating: Double? = null,
    val chapters: List<ParsedPluginChapter>? = null,
    val totalPages: Int? = null,
)

internal data class ParsedPluginPage(
    val chapters: List<ParsedPluginChapter> = emptyList(),
)

internal object NovelJsPayloadParser {
    fun parseNovel(json: Json, payload: String): ParsedPluginNovel? {
        val root = decodeRootObject(json, payload) ?: return null
        return ParsedPluginNovel(
            name = root.string("name", "title"),
            path = root.string("path", "url"),
            cover = root.string("cover", "thumbnail_url", "thumbnail"),
            genres = root.string("genres"),
            summary = root.string("summary", "description"),
            author = root.string("author"),
            artist = root.string("artist"),
            status = root.string("status"),
            rating = root.number("rating"),
            chapters = parseChapters(root["chapters"]),
            totalPages = root.integer("totalPages"),
        )
    }

    fun parsePage(json: Json, payload: String): ParsedPluginPage? {
        val element = decodeElement(json, payload) ?: return null
        val chapters = when (element) {
            is JsonArray -> parseChapters(element)
            is JsonObject -> parseChapters(element["chapters"])
            else -> emptyList()
        }
        return ParsedPluginPage(chapters = chapters)
    }

    fun parseChaptersArray(json: Json, payload: String): List<ParsedPluginChapter> {
        val element = decodeElement(json, payload) ?: return emptyList()
        return parseChapters(element)
    }

    fun parseRulateFamilyChapterEndpoint(
        json: Json,
        payload: String,
        bookId: String,
    ): List<ParsedPluginChapter> {
        if (bookId.isBlank()) return emptyList()
        val items = decodeElement(json, payload) as? JsonArray ?: return emptyList()
        return items.mapIndexedNotNull { index, entry ->
            val chapter = entry as? JsonObject ?: return@mapIndexedNotNull null
            val chapterId = chapter.string("id") ?: return@mapIndexedNotNull null
            ParsedPluginChapter(
                name = chapter.string("title", "name") ?: "Chapter ${index + 1}",
                path = "/book/$bookId/$chapterId",
                chapterNumber = (index + 1).toDouble(),
            )
        }
    }

    private fun decodeRootObject(json: Json, payload: String): JsonObject? {
        val element = decodeElement(json, payload)
        return element as? JsonObject
    }

    private fun decodeElement(json: Json, payload: String): JsonElement? {
        if (payload.isBlank() || payload == "null") return null
        return runCatching { json.decodeFromString<JsonElement>(payload) }.getOrNull()
    }

    private fun parseChapters(element: JsonElement?): List<ParsedPluginChapter> {
        val items = element as? JsonArray ?: return emptyList()
        return items.mapNotNull { entry ->
            val chapter = entry as? JsonObject ?: return@mapNotNull null
            ParsedPluginChapter(
                name = chapter.string("name", "title", "chapterName", "chapterTitle"),
                path = chapter.string("path", "url", "href", "link", "chapterPath"),
                releaseTime = chapter.string("releaseTime"),
                chapterNumber = chapter.number("chapterNumber", "number"),
                scanlator = chapter.string("scanlator", "translator", "team", "group", "branch", "branchName"),
                page = chapter.string("page"),
            )
        }
    }

    private fun JsonObject.string(vararg keys: String): String? {
        for (key in keys) {
            val value = this[key] ?: continue
            val primitive = value as? JsonPrimitive ?: continue
            val content = primitive.content.trim()
            if (content.isNotBlank()) return content
        }
        return null
    }

    private fun JsonObject.integer(vararg keys: String): Int? {
        for (key in keys) {
            val value = this[key] ?: continue
            val primitive = value as? JsonPrimitive ?: continue
            val asInt = primitive.intOrNull ?: primitive.content.toIntOrNull()
            if (asInt != null) return asInt
        }
        return null
    }

    private fun JsonObject.number(vararg keys: String): Double? {
        for (key in keys) {
            val value = this[key] ?: continue
            val primitive = value as? JsonPrimitive ?: continue
            val asDouble = primitive.doubleOrNull ?: primitive.content.toDoubleOrNull()
            if (asDouble != null) return asDouble
        }
        return null
    }
}
