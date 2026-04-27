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

internal class NovelReaderTranslationDiskCache(
    private val directory: File,
    private val json: Json,
) {
    private val lock = Any()

    fun get(chapterId: Long): GeminiTranslationCacheEntry? {
        synchronized(lock) {
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

    fun put(entry: GeminiTranslationCacheEntry) {
        synchronized(lock) {
            runCatching {
                if (!directory.exists()) directory.mkdirs()
                val file = fileFor(entry.chapterId)
                file.writeText(json.encodeToString(GeminiTranslationCacheDiskModel.fromDomain(entry)), Charsets.UTF_8)
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
        }
    }

    fun has(chapterId: Long): Boolean {
        synchronized(lock) {
            return fileFor(chapterId).isFile
        }
    }

    fun chapterIds(): Set<Long> {
        synchronized(lock) {
            if (!directory.exists()) return emptySet()
            return directory.listFiles()
                ?.asSequence()
                ?.filter { it.isFile }
                ?.mapNotNull { file ->
                    file.nameWithoutExtension.toLongOrNull()
                }
                ?.toSet()
                ?: emptySet()
        }
    }

    fun clear() {
        synchronized(lock) {
            if (!directory.exists()) return
            directory.listFiles()?.forEach { file ->
                if (file.isFile) file.delete()
            }
        }
    }

    private fun fileFor(chapterId: Long): File = File(directory, "$chapterId.json")
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

    fun get(chapterId: Long): GeminiTranslationCacheEntry? = cache.get(chapterId)

    fun put(entry: GeminiTranslationCacheEntry) = cache.put(entry)

    fun has(chapterId: Long): Boolean = cache.has(chapterId)

    fun chapterIds(): Set<Long> = cache.chapterIds()

    fun remove(chapterId: Long) = cache.remove(chapterId)

    fun clear() = cache.clear()
}
