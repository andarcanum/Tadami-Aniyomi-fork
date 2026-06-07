package eu.kanade.tachiyomi.ui.reader.novel.translation

import android.app.Application
import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationStylePreset
import eu.kanade.tachiyomi.util.safeCacheDir
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.concurrent.ConcurrentHashMap

internal class NovelReaderTranslationDiskCache(
    private val directory: File,
    private val json: Json,
) {
    internal val cacheDir: File = directory
    private val lock = Any()

    // In-memory metadata index: chapterId -> lightweight metadata
    // Avoids O(n) JSON parsing in chapterIds() scans
    private data class IndexEntry(
        val targetLang: String,
        val provider: NovelTranslationProvider,
        val model: String,
        val sourceLang: String,
        val promptMode: GeminiPromptMode,
        val stylePreset: NovelTranslationStylePreset,
        val hasTranslatedContent: Boolean,
    )

    private val index = ConcurrentHashMap<Long, IndexEntry>()

    @Volatile
    private var indexBuilt = false

    @Volatile
    private var indexBuilding = false

    @Serializable
    private data class IndexEntryDiskModel(
        val targetLang: String,
        val provider: NovelTranslationProvider,
        val model: String,
        val sourceLang: String,
        val promptMode: GeminiPromptMode,
        val stylePreset: NovelTranslationStylePreset,
        val hasTranslatedContent: Boolean,
    )

    private fun getOrLoadEntry(chapterId: Long): IndexEntry? {
        val entry = index[chapterId] ?: return null
        if (entry.targetLang.isNotEmpty()) return entry

        // Try reading companion meta file first
        val metaFile = File(directory, "$chapterId.meta")
        if (metaFile.isFile) {
            val loaded = runCatching {
                val model = json.decodeFromString<IndexEntryDiskModel>(metaFile.readText(Charsets.UTF_8))
                IndexEntry(
                    targetLang = model.targetLang,
                    provider = model.provider,
                    model = model.model,
                    sourceLang = model.sourceLang,
                    promptMode = model.promptMode,
                    stylePreset = model.stylePreset,
                    hasTranslatedContent = model.hasTranslatedContent,
                )
            }.getOrNull()
            if (loaded != null) {
                index[chapterId] = loaded
                return loaded
            }
        }

        // Fallback: read main JSON disk model
        val diskModel = readEntryDiskModel(chapterId)
        if (diskModel == null) {
            index.remove(chapterId)
            return null
        }
        val loaded = IndexEntry(
            targetLang = diskModel.targetLang,
            provider = diskModel.provider,
            model = diskModel.model,
            sourceLang = diskModel.sourceLang,
            promptMode = diskModel.promptMode,
            stylePreset = diskModel.stylePreset,
            hasTranslatedContent = diskModel.translatedByIndex.isNotEmpty(),
        )

        // Write the meta companion file so future reads are fast
        runCatching {
            val metaModel = IndexEntryDiskModel(
                targetLang = loaded.targetLang,
                provider = loaded.provider,
                model = loaded.model,
                sourceLang = loaded.sourceLang,
                promptMode = loaded.promptMode,
                stylePreset = loaded.stylePreset,
                hasTranslatedContent = loaded.hasTranslatedContent,
            )
            metaFile.writeText(json.encodeToString(metaModel), Charsets.UTF_8)
        }

        index[chapterId] = loaded
        return loaded
    }

    private fun ensureIndex() {
        if (indexBuilt) return
        if (indexBuilding) return // background rebuild already in progress, fallback to file-scan

        synchronized(lock) {
            if (indexBuilt || indexBuilding) return
            indexBuilding = true
            Thread {
                try {
                    rebuildIndex()
                } finally {
                    indexBuilt = true
                    indexBuilding = false
                }
            }.apply {
                name = "novel-translation-cache-index-rebuild"
                isDaemon = true
                start()
            }
        }
    }

    private fun rebuildIndex() {
        val rebuilt = linkedMapOf<Long, IndexEntry>()
        if (directory.exists()) {
            directory.listFiles()?.filter { it.isFile && it.extension == "json" }?.forEach { file ->
                val chapterId = file.nameWithoutExtension.toLongOrNull() ?: return@forEach
                // Placeholder: targetLang is set to empty to trigger lazy load on demand.
                rebuilt[chapterId] = IndexEntry(
                    targetLang = "",
                    provider = NovelTranslationProvider.GEMINI,
                    model = "",
                    sourceLang = "",
                    promptMode = GeminiPromptMode.CLASSIC,
                    stylePreset = NovelTranslationStylePreset.PROFESSIONAL,
                    hasTranslatedContent = true,
                )
            }
        }
        index.clear()
        index.putAll(rebuilt)
    }

    private fun readEntryDiskModel(chapterId: Long): GeminiTranslationCacheDiskModel? {
        val file = fileFor(chapterId)
        if (!file.isFile) return null
        return runCatching {
            json.decodeFromString<GeminiTranslationCacheDiskModel>(file.readText(Charsets.UTF_8))
        }.onFailure { error ->
            logcat(LogPriority.WARN, error) { "Failed to parse novel translation cache for chapter=$chapterId" }
            file.delete()
        }.getOrNull()
    }

    fun get(chapterId: Long): GeminiTranslationCacheEntry? {
        synchronized(lock) {
            return readEntryLocked(chapterId)
        }
    }

    fun put(entry: GeminiTranslationCacheEntry) {
        synchronized(lock) {
            runCatching {
                if (!directory.exists()) directory.mkdirs()
                val file = fileFor(entry.chapterId)
                file.writeText(json.encodeToString(GeminiTranslationCacheDiskModel.fromDomain(entry)), Charsets.UTF_8)

                // Write companion meta file
                val metaFile = File(directory, "${entry.chapterId}.meta")
                val metaModel = IndexEntryDiskModel(
                    targetLang = entry.targetLang,
                    provider = entry.provider,
                    model = entry.model,
                    sourceLang = entry.sourceLang,
                    promptMode = entry.promptMode,
                    stylePreset = entry.stylePreset,
                    hasTranslatedContent = entry.translatedByIndex.isNotEmpty(),
                )
                metaFile.writeText(json.encodeToString(metaModel), Charsets.UTF_8)

                // Maintain in-memory index
                index[entry.chapterId] = IndexEntry(
                    targetLang = entry.targetLang,
                    provider = entry.provider,
                    model = entry.model,
                    sourceLang = entry.sourceLang,
                    promptMode = entry.promptMode,
                    stylePreset = entry.stylePreset,
                    hasTranslatedContent = entry.translatedByIndex.isNotEmpty(),
                )
            }.onFailure { error ->
                logcat(LogPriority.WARN, error) {
                    "Failed to write novel translation cache for chapter=${entry.chapterId}"
                }
            }
        }
    }

    fun remove(chapterId: Long) {
        synchronized(lock) {
            fileFor(chapterId).delete()
            File(directory, "$chapterId.meta").delete()
            index.remove(chapterId)
        }
    }

    private fun indexReady(): Boolean = indexBuilt && !indexBuilding

    fun has(chapterId: Long): Boolean {
        ensureIndex()
        if (!indexReady()) return hasFallback(chapterId)
        return getOrLoadEntry(chapterId)?.hasTranslatedContent == true
    }

    fun has(
        chapterId: Long,
        requirements: NovelReaderTranslationCacheRequirements,
    ): Boolean {
        ensureIndex()
        if (!indexReady()) return hasFallback(chapterId, requirements)
        val entry = getOrLoadEntry(chapterId) ?: return false
        if (!entry.hasTranslatedContent) return false
        return entry.provider == requirements.translationProvider &&
            entry.model == requirements.modelId &&
            entry.sourceLang == requirements.sourceLang &&
            entry.targetLang == requirements.targetLang &&
            entry.promptMode == requirements.promptMode &&
            entry.stylePreset == requirements.stylePreset
    }

    fun has(
        chapterId: Long,
        targetLang: String,
    ): Boolean {
        ensureIndex()
        if (!indexReady()) return hasFallback(chapterId, targetLang)
        return getOrLoadEntry(chapterId)?.takeIf { it.hasTranslatedContent }?.targetLang == targetLang
    }

    // -- Fallback methods (file-scan, used while index builds in background) --

    private fun hasFallback(chapterId: Long): Boolean {
        return readEntryLocked(chapterId)?.translatedByIndex?.isNotEmpty() == true
    }

    private fun hasFallback(chapterId: Long, requirements: NovelReaderTranslationCacheRequirements): Boolean {
        return NovelReaderTranslationCacheResolver.matches(
            cached = readEntryLocked(chapterId),
            requirements = requirements,
        )
    }

    private fun hasFallback(chapterId: Long, targetLang: String): Boolean {
        return readEntryLocked(chapterId)?.targetLang == targetLang
    }

    private fun chapterIdsFallback(): Set<Long> {
        if (!directory.exists()) return emptySet()
        return directory.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.extension == "json" }
            ?.mapNotNull { file ->
                val chapterId = file.nameWithoutExtension.toLongOrNull() ?: return@mapNotNull null
                readEntryLocked(chapterId)
                    ?.takeIf { it.translatedByIndex.isNotEmpty() }
                    ?.chapterId
            }
            ?.toSet()
            ?: emptySet()
    }

    private fun chapterIdsFallback(targetLang: String): Set<Long> {
        if (!directory.exists()) return emptySet()
        return directory.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.extension == "json" }
            ?.mapNotNull { file ->
                val chapterId = file.nameWithoutExtension.toLongOrNull() ?: return@mapNotNull null
                readEntryLocked(chapterId)
                    ?.takeIf { it.targetLang == targetLang && it.translatedByIndex.isNotEmpty() }
                    ?.chapterId
            }
            ?.toSet()
            ?: emptySet()
    }

    private fun chapterIdsFallback(chapterIds: Collection<Long>, targetLang: String): Set<Long> {
        if (!directory.exists() || chapterIds.isEmpty()) return emptySet()
        return chapterIds.filter { chapterId ->
            val file = fileFor(chapterId)
            file.isFile && readEntryLocked(chapterId)?.targetLang == targetLang
        }.toSet()
    }

    private fun chapterIdsFallback(requirements: NovelReaderTranslationCacheRequirements): Set<Long> {
        if (!directory.exists()) return emptySet()
        return directory.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.extension == "json" }
            ?.mapNotNull { file ->
                val chapterId = file.nameWithoutExtension.toLongOrNull() ?: return@mapNotNull null
                readEntryLocked(chapterId)
                    ?.takeIf { cached ->
                        NovelReaderTranslationCacheResolver.matches(
                            cached = cached,
                            requirements = requirements,
                        )
                    }
                    ?.chapterId
            }
            ?.toSet()
            ?: emptySet()
    }

    fun chapterIds(): Set<Long> {
        ensureIndex()
        if (!indexReady()) return chapterIdsFallback()
        return index.keys.filter { getOrLoadEntry(it)?.hasTranslatedContent == true }.toSet()
    }

    fun chapterIds(targetLang: String): Set<Long> {
        ensureIndex()
        if (!indexReady()) return chapterIdsFallback(targetLang)
        return index.entries
            .filter { getOrLoadEntry(it.key)?.takeIf { it.hasTranslatedContent }?.targetLang == targetLang }
            .map { it.key }
            .toSet()
    }

    fun chapterIds(chapterIds: Collection<Long>, targetLang: String): Set<Long> {
        ensureIndex()
        if (!indexReady()) return chapterIdsFallback(chapterIds, targetLang)
        return chapterIds.filter { chapterId ->
            getOrLoadEntry(chapterId)?.takeIf { it.targetLang == targetLang && it.hasTranslatedContent } != null
        }.toSet()
    }

    fun chapterIds(requirements: NovelReaderTranslationCacheRequirements): Set<Long> {
        ensureIndex()
        if (!indexReady()) return chapterIdsFallback(requirements)
        return index.entries
            .filter { (chapterId, _) ->
                val entry = getOrLoadEntry(chapterId) ?: return@filter false
                entry.hasTranslatedContent &&
                    entry.provider == requirements.translationProvider &&
                    entry.model == requirements.modelId &&
                    entry.sourceLang == requirements.sourceLang &&
                    entry.targetLang == requirements.targetLang &&
                    entry.promptMode == requirements.promptMode &&
                    entry.stylePreset == requirements.stylePreset
            }
            .map { it.key }
            .toSet()
    }

    fun clear() {
        synchronized(lock) {
            if (!directory.exists()) return
            directory.listFiles()?.forEach { file ->
                if (file.isFile) file.delete()
            }
            index.clear()
            indexBuilt = false
        }
    }

    private fun fileFor(chapterId: Long): File = File(directory, "$chapterId.json")

    private fun readEntryLocked(chapterId: Long): GeminiTranslationCacheEntry? {
        val file = fileFor(chapterId)
        if (!file.isFile) return null
        return runCatching {
            json.decodeFromString<GeminiTranslationCacheDiskModel>(file.readText(Charsets.UTF_8)).toDomain()
        }.onFailure { error ->
            logcat(LogPriority.WARN, error) { "Failed to parse novel translation cache for chapter=$chapterId" }
            file.delete()
        }.getOrNull()
    }
}

@Serializable
private data class GeminiTranslationCacheDiskModel(
    val chapterId: Long,
    val translatedByIndex: Map<Int, String>,
    val provider: NovelTranslationProvider = NovelTranslationProvider.GEMINI,
    val model: String,
    val sourceLang: String,
    val targetLang: String,
    val promptMode: GeminiPromptMode,
    val stylePreset: NovelTranslationStylePreset = NovelTranslationStylePreset.PROFESSIONAL,
) {
    fun toDomain(): GeminiTranslationCacheEntry {
        return GeminiTranslationCacheEntry(
            chapterId = chapterId,
            translatedByIndex = translatedByIndex,
            provider = provider,
            model = model,
            sourceLang = sourceLang,
            targetLang = targetLang,
            promptMode = promptMode,
            stylePreset = stylePreset,
        )
    }

    companion object {
        fun fromDomain(entry: GeminiTranslationCacheEntry): GeminiTranslationCacheDiskModel {
            return GeminiTranslationCacheDiskModel(
                chapterId = entry.chapterId,
                translatedByIndex = entry.translatedByIndex,
                provider = entry.provider,
                model = entry.model,
                sourceLang = entry.sourceLang,
                targetLang = entry.targetLang,
                promptMode = entry.promptMode,
                stylePreset = entry.stylePreset,
            )
        }
    }
}

internal object NovelReaderTranslationDiskCacheStore {
    private val json by lazy { Injekt.get<Json>() }
    private val cache by lazy {
        val app = Injekt.get<Application>()
        val cacheRoot = app.safeCacheDir()
        NovelReaderTranslationDiskCache(
            directory = File(cacheRoot, "novel_reader_translation_cache"),
            json = json,
        )
    }

    init {
        registerWithGlobalCoordinator()
    }

    private fun registerWithGlobalCoordinator() {
        eu.kanade.tachiyomi.ui.reader.novel.GlobalCacheCoordinator.instance.register(
            object : eu.kanade.tachiyomi.ui.reader.novel.cache.NovelReaderCacheReporter {
                override fun cacheId(): String = "novel-reader-translation-disk"
                override fun currentBytes(): Long {
                    return cache.cacheDir.listFiles()
                        ?.filter { it.isFile }
                        ?.sumOf { it.length() }
                        ?: 0L
                }
                override fun trimToTargetBytes(targetBytes: Long) {
                    val files = cache.cacheDir.listFiles()
                        ?.filter { it.isFile }
                        ?.sortedBy { it.lastModified() }
                        .orEmpty()
                    var total = files.sumOf { it.length() }
                    for (file in files) {
                        if (total <= targetBytes) break
                        total -= file.length()
                        file.delete()
                    }
                }
            },
        )
    }

    fun get(chapterId: Long): GeminiTranslationCacheEntry? = cache.get(chapterId)

    fun put(entry: GeminiTranslationCacheEntry) = cache.put(entry)

    fun has(chapterId: Long): Boolean = cache.has(chapterId)

    fun has(
        chapterId: Long,
        requirements: NovelReaderTranslationCacheRequirements,
    ): Boolean = cache.has(chapterId, requirements)

    fun has(
        chapterId: Long,
        targetLang: String,
    ): Boolean = cache.has(chapterId, targetLang)

    fun chapterIds(): Set<Long> = cache.chapterIds()

    fun chapterIds(targetLang: String): Set<Long> = cache.chapterIds(targetLang)

    fun chapterIds(chapterIds: Collection<Long>, targetLang: String): Set<Long> = cache.chapterIds(
        chapterIds,
        targetLang,
    )

    fun chapterIds(requirements: NovelReaderTranslationCacheRequirements): Set<Long> = cache.chapterIds(requirements)

    fun remove(chapterId: Long) = cache.remove(chapterId)

    fun clear() = cache.clear()
}
