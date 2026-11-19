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

private const val INDEX_META_SCHEMA_VERSION = 1

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
        val extractorVersion: Int = 0,
        val promptModifiersFingerprint: String = "",
        val replaceRulesFingerprint: String = "",
        val sourceSegmentCount: Int = 0,
        val translatedCount: Int = 0,
    ) {
        val isComplete: Boolean
            get() = sourceSegmentCount > 0 && translatedCount >= sourceSegmentCount
    }

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
        // Legacy .meta files predate these fields and decode to the "legacy/unknown" defaults,
        // which never match current requirements - exactly the intended invalidation.
        val extractorVersion: Int = 0,
        val promptModifiersFingerprint: String = "",
        val replaceRulesFingerprint: String = "",
        val sourceSegmentCount: Int = 0,
        val translatedCount: Int = 0,
        val schemaVersion: Int = 0,
    )

    private fun getOrLoadEntry(chapterId: Long): IndexEntry? {
        val entry = index[chapterId] ?: return null
        if (entry.targetLang.isNotEmpty()) return entry

        // Try reading companion meta file first
        val metaFile = File(directory, "$chapterId.meta")
        if (metaFile.isFile) {
            val loaded = runCatching {
                val model = json.decodeFromString<IndexEntryDiskModel>(metaFile.readText(Charsets.UTF_8))
                if (model.schemaVersion != INDEX_META_SCHEMA_VERSION) return@runCatching null
                IndexEntry(
                    targetLang = model.targetLang,
                    provider = model.provider,
                    model = model.model,
                    sourceLang = model.sourceLang,
                    promptMode = model.promptMode,
                    stylePreset = model.stylePreset,
                    hasTranslatedContent = model.hasTranslatedContent,
                    extractorVersion = model.extractorVersion,
                    promptModifiersFingerprint = model.promptModifiersFingerprint,
                    replaceRulesFingerprint = model.replaceRulesFingerprint,
                    sourceSegmentCount = model.sourceSegmentCount,
                    translatedCount = model.translatedCount,
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
        val complete = diskModel.toDomain().isTranslationComplete
        val loaded = IndexEntry(
            targetLang = diskModel.targetLang,
            provider = diskModel.provider,
            model = diskModel.model,
            sourceLang = diskModel.sourceLang,
            promptMode = diskModel.promptMode,
            stylePreset = diskModel.stylePreset,
            hasTranslatedContent = diskModel.translatedByIndex.values.any { it.isNotBlank() },
            extractorVersion = diskModel.extractorVersion,
            promptModifiersFingerprint = diskModel.promptModifiersFingerprint,
            replaceRulesFingerprint = diskModel.replaceRulesFingerprint,
            sourceSegmentCount = diskModel.sourceSegmentCount,
            translatedCount = if (complete) diskModel.translatedByIndex.size else -1,
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
                extractorVersion = loaded.extractorVersion,
                promptModifiersFingerprint = loaded.promptModifiersFingerprint,
                replaceRulesFingerprint = loaded.replaceRulesFingerprint,
                sourceSegmentCount = loaded.sourceSegmentCount,
                translatedCount = loaded.translatedCount,
                schemaVersion = INDEX_META_SCHEMA_VERSION,
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
        // Merge placeholders per key instead of clear()+putAll(): a put() landing while the file
        // scan runs would otherwise be wiped from the in-memory index even though its file exists
        // on disk, giving has()/chapterIds() false negatives until the next rebuild. Stale ids
        // (files deleted externally) self-heal in getOrLoadEntry's disk fallback.
        rebuilt.forEach { (chapterId, placeholder) ->
            index.putIfAbsent(chapterId, placeholder)
        }
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
            if (!directory.isDirectory &&
                !directory.mkdirs()
            ) {
                throw java.io.IOException("Cannot create translation cache directory")
            }
            val existing = readEntryLocked(entry.chapterId)
            // A failed replacement may leave checkpoints, but must never replace the last full artifact.
            if (!entry.isTranslationComplete && existing?.isTranslationComplete == true) return
            val encoded = json.encodeToString(GeminiTranslationCacheDiskModel.fromDomain(entry))
            val temporary = File.createTempFile(".${entry.chapterId}-", ".tmp", directory)
            try {
                java.io.FileOutputStream(temporary).use { output ->
                    output.write(encoded.toByteArray(Charsets.UTF_8))
                    output.fd.sync()
                }
                java.nio.file.Files.move(
                    temporary.toPath(),
                    fileFor(entry.chapterId).toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                )
                // A sidecar is never authoritative for a newly published artifact.
                File(directory, "${entry.chapterId}.meta").delete()
                index.remove(entry.chapterId)
                indexBuilt = false
            } finally {
                temporary.delete()
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

    fun has(chapterId: Long, requirements: NovelReaderTranslationCacheRequirements): Boolean = synchronized(lock) {
        val entry = readEntryLocked(chapterId) ?: return@synchronized false
        entry.isTranslationComplete && NovelReaderTranslationCacheResolver.matches(entry, requirements)
    }

    fun has(chapterId: Long, targetLang: String): Boolean {
        ensureIndex()
        if (!indexReady()) {
            return synchronized(lock) {
                val entry = readEntryLocked(chapterId) ?: return@synchronized false
                entry.targetLang == targetLang && entry.isTranslationComplete
            }
        }
        val entry = getOrLoadEntry(chapterId) ?: return false
        return entry.targetLang == targetLang && entry.hasTranslatedContent && entry.isComplete
    }

    // -- Fallback methods (file-scan, used while index builds in background) --

    private fun hasFallback(chapterId: Long): Boolean {
        return readEntryLocked(chapterId)?.translatedByIndex?.isNotEmpty() == true
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

    fun chapterIds(): Set<Long> {
        ensureIndex()
        if (!indexReady()) return chapterIdsFallback()
        return index.keys.filter { getOrLoadEntry(it)?.hasTranslatedContent == true }.toSet()
    }

    fun chapterIds(targetLang: String): Set<Long> = chapterIds().filter { has(it, targetLang) }.toSet()

    fun chapterIds(chapterIds: Collection<Long>, targetLang: String): Set<Long> =
        chapterIds.filter { has(it, targetLang) }.toSet()

    fun chapterIds(requirements: NovelReaderTranslationCacheRequirements): Set<Long> =
        chapterIds().filter { has(it, requirements) }.toSet()

    fun chapterIds(
        chapterIds: Collection<Long>,
        requirements: NovelReaderTranslationCacheRequirements,
    ): Set<Long> = chapterIds.filter { has(it, requirements) }.toSet()

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
    // Legacy cache files predate extractor versioning and decode to 0, which never matches the
    // current requirements - they are retranslated instead of overlaid onto shifted blocks.
    val extractorVersion: Int = 0,
    val promptModifiersFingerprint: String = "",
    val replaceRulesFingerprint: String = "",
    val sourceSegmentCount: Int = 0,
    val sourceFingerprint: String = "",
    val generationFingerprint: String = "",
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
            extractorVersion = extractorVersion,
            promptModifiersFingerprint = promptModifiersFingerprint,
            replaceRulesFingerprint = replaceRulesFingerprint,
            sourceSegmentCount = sourceSegmentCount,
            sourceFingerprint = sourceFingerprint,
            generationFingerprint = generationFingerprint,
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
                extractorVersion = entry.extractorVersion,
                promptModifiersFingerprint = entry.promptModifiersFingerprint,
                replaceRulesFingerprint = entry.replaceRulesFingerprint,
                sourceSegmentCount = entry.sourceSegmentCount,
                sourceFingerprint = entry.sourceFingerprint,
                generationFingerprint = entry.generationFingerprint,
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

    fun chapterIds(
        chapterIds: Collection<Long>,
        requirements: NovelReaderTranslationCacheRequirements,
    ): Set<Long> = cache.chapterIds(chapterIds, requirements)

    fun remove(chapterId: Long) = cache.remove(chapterId)

    fun clear() = cache.clear()
}
