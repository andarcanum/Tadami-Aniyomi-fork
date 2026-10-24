package eu.kanade.tachiyomi.data.download.manga

import android.app.Application
import android.content.Context
import android.util.AtomicFile
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.util.size
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import logcat.asLog
import tachiyomi.core.common.storage.extension
import tachiyomi.core.common.storage.nameWithoutExtension
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.storage.service.StorageManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * Cache where we dump the downloads directory from the filesystem. This class is needed because
 * directory checking is expensive and it slows down the app. The cache is invalidated by the time
 * defined in [renewInterval] as we don't have any control over the filesystem and the user can
 * delete the folders at any time without the app noticing.
 */
class MangaDownloadCache(
    private val context: Context,
    private val provider: MangaDownloadProvider = Injekt.get(),
    private val sourceManager: MangaSourceManager = Injekt.get(),
    private val extensionManager: MangaExtensionManager = Injekt.get(),
    private val storageManager: StorageManager = Injekt.get(),
    private val scope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error ->
            logcat.logcat("MangaDownloadCache", LogPriority.ERROR) {
                "MangaDownloadCache: background operation failed\n${error.asLog()}"
            }
        },
    ),
) {

    private val restoreFinished = CompletableDeferred<Unit>()
    private val renewalLock = Any()
    private val storageGeneration = AtomicLong(0L)
    private val diskCacheMutex = Mutex()
    private val writerLock = Any()

    private val _changes = MutableSharedFlow<Unit>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val changes = _changes.asSharedFlow()

    /**
     * The interval after which this cache should be invalidated. 1 hour shouldn't cause major
     * issues, as the cache is only used for UI feedback.
     */
    private val renewInterval = 1.hours.inWholeMilliseconds
    private val failedRenewRetryInterval = 30.seconds.inWholeMilliseconds

    /**
     * The last time the cache was refreshed.
     */
    @Volatile
    private var lastRenew = 0L
    private var renewalJob: Job? = null

    private val _isInitializing = MutableStateFlow(false)
    val isInitializing = _isInitializing
        .debounce(1000L) // Don't notify if it finishes quickly enough
        .stateIn(scope, SharingStarted.WhileSubscribed(), false)

    private val diskCacheFile: File
        get() = File(context.cacheDir, "dl_index_cache_v3")

    private val rootDownloadsDirMutex = Mutex()

    // C-M6: read from many threads without the mutex; the fork replaced the baseline's
    // ConcurrentHashMap with a plain map - at minimum the root reference must be @Volatile so
    // readers see the swapped snapshot (writers only ever assign fully built objects).
    @Volatile
    private var rootDownloadsDir = RootDirectory(storageManager.getDownloadsDirectory())

    init {
        _changes.tryEmit(Unit)

        val restoreGeneration = storageGeneration.get()
        scope.launch {
            try {
                diskCacheMutex.withLock {
                    val atomicFile = AtomicFile(diskCacheFile)
                    if (diskCacheFile.exists() || File("${diskCacheFile.path}.bak").exists()) {
                        val restored = ProtoBuf.decodeFromByteArray<RootDirectory>(atomicFile.readFully())
                        rootDownloadsDirMutex.withLock {
                            synchronized(renewalLock) {
                                if (storageGeneration.get() == restoreGeneration &&
                                    restored.dir?.uri == rootDownloadsDir.dir?.uri
                                ) {
                                    rootDownloadsDir = restored
                                    lastRenew = restored.lastSuccessfulScanAt
                                        .takeIf { it in 1..System.currentTimeMillis() } ?: 0L
                                }
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to initialize from disk cache" }
            } finally {
                restoreFinished.complete(Unit)
                // Restore is a state transition, not a new filesystem validation.
                notifyChanges(persist = false)
            }
        }

        storageManager.changes
            .onEach { invalidateCache() }
            .launchIn(scope)
    }

    /**
     * Returns true if the chapter is downloaded.
     *
     * @param chapterName the name of the chapter to query.
     * @param chapterScanlator scanlator of the chapter to query
     * @param mangaTitle the title of the manga to query.
     * @param sourceId the id of the source of the chapter.
     * @param skipCache whether to skip the directory cache and check in the filesystem.
     */
    fun isChapterDownloaded(
        chapterName: String,
        chapterScanlator: String?,
        mangaTitle: String,
        sourceId: Long,
        skipCache: Boolean,
        mangaId: Long? = null,
        chapterId: Long? = null,
    ): Boolean {
        if (skipCache) {
            val source = sourceManager.getOrStub(sourceId)
            return provider.findChapterDir(
                chapterName,
                chapterScanlator,
                mangaTitle,
                mangaId,
                chapterId,
                source,
            ) != null
        }

        renewCache()

        val sourceDir = rootDownloadsDir.sourceDirs[sourceId]
        if (sourceDir != null) {
            val names = provider.getValidChapterDirNames(chapterName, chapterScanlator, chapterId)
            return mangaDirsFor(sourceDir, mangaTitle, mangaId)
                .any { mangaDir -> names.any { it in mangaDir.chapterDirs } }
        }
        return false
    }

    /**
     * DECISION-6/7: resolve the cached manga directories by the scoped name first plus the
     * legacy title-only name - during the compatibility window a manga's chapters can live in
     * either directory (new downloads scoped, old ones legacy).
     *
     * A folder moved or renamed on disk (e.g. downloads imported from another app, refreshed
     * title, sanitization drift) no longer matches either exact name; the mangaId suffix
     * " [id]" at the end of the directory name is still a stable anchor for such folders,
     * so fall back to matching any cached directory of this source whose name ends with the
     * suffix. Exact names keep priority.
     */
    private fun mangaDirsFor(sourceDir: SourceDirectory, mangaTitle: String, mangaId: Long?): List<MangaDirectory> {
        return cachedMangaDirectories(
            directories = sourceDir.mangaDirs,
            scopedName = mangaId?.let { provider.getMangaDirName(mangaTitle, it) },
            legacyName = provider.getLegacyMangaDirName(mangaTitle),
            mangaId = mangaId,
        )
    }

    /**
     * Returns the amount of downloaded chapters.
     */
    fun getTotalDownloadCount(): Int {
        renewCache()

        return rootDownloadsDir.sourceDirs.values.sumOf { sourceDir ->
            sourceDir.mangaDirs.values.sumOf { mangaDir ->
                mangaDir.chapterDirs.size
            }
        }
    }

    /**
     * Returns the amount of downloaded chapters for a manga.
     *
     * @param manga the manga to check.
     */
    fun getDownloadCount(manga: Manga): Int {
        renewCache()

        val sourceDir = rootDownloadsDir.sourceDirs[manga.source]
        if (sourceDir != null) {
            return mangaDirsFor(sourceDir, manga.title, manga.id).sumOf { it.chapterDirs.size }
        }
        return 0
    }

    /**
     * Returns the total size of downloaded chapters.
     */
    fun getTotalDownloadSize(): Long {
        renewCache()

        return rootDownloadsDir.sourceDirs.values.sumOf { sourceDir ->
            sourceDir.dir?.size() ?: 0L
        }
    }

    /**
     * Returns the total size of downloaded chapters for a manga.
     *
     * @param manga the manga to check.
     */
    fun getDownloadSize(manga: Manga): Long {
        renewCache()

        return rootDownloadsDir.sourceDirs[manga.source]
            ?.let { sourceDir ->
                mangaDirsFor(sourceDir, manga.title, manga.id).sumOf { it.dir?.size() ?: 0L }
            } ?: 0
    }

    /**
     * Adds a chapter that has just been download to this cache.
     *
     * @param chapterDirName the downloaded chapter's directory name.
     * @param mangaUniFile the directory of the manga.
     * @param manga the manga of the chapter.
     */
    suspend fun addChapter(chapterDirName: String, mangaUniFile: UniFile, manga: Manga) {
        restoreFinished.await()
        rootDownloadsDirMutex.withLock {
            // Retrieve the cached source directory or cache a new one
            var sourceDir = rootDownloadsDir.sourceDirs[manga.source]
            if (sourceDir == null) {
                val source = sourceManager.get(manga.source) ?: return
                val sourceUniFile = provider.findSourceDir(source) ?: return
                sourceDir = SourceDirectory(sourceUniFile)
                rootDownloadsDir.sourceDirs += manga.source to sourceDir
            }

            // Retrieve the cached manga directory or cache a new one
            val mangaDirName = provider.getMangaDirName(manga.title, manga.id)
            var mangaDir = sourceDir.mangaDirs[mangaDirName]
            if (mangaDir == null) {
                mangaDir = MangaDirectory(mangaUniFile)
                sourceDir.mangaDirs += mangaDirName to mangaDir
            }

            // Save the chapter directory (C-M6: copy-on-write - readers iterate the set without
            // the mutex, so it must never be mutated in place)
            mangaDir.chapterDirs = (mangaDir.chapterDirs + chapterDirName).toMutableSet()
        }

        notifyChanges()
    }

    /**
     * Removes a chapter that has been deleted from this cache.
     *
     * @param chapter the chapter to remove.
     * @param manga the manga of the chapter.
     */
    suspend fun removeChapter(chapter: Chapter, manga: Manga) {
        restoreFinished.await()
        rootDownloadsDirMutex.withLock {
            val sourceDir = rootDownloadsDir.sourceDirs[manga.source] ?: return
            mangaDirsFor(sourceDir, manga.title, manga.id).forEach { mangaDir ->
                provider.getValidChapterDirNames(chapter.name, chapter.scanlator, chapter.id).forEach {
                    if (it in mangaDir.chapterDirs) {
                        mangaDir.chapterDirs = (mangaDir.chapterDirs - it).toMutableSet()
                    }
                }
            }
        }

        notifyChanges()
    }

    /**
     * Removes a list of chapters that have been deleted from this cache.
     *
     * @param chapters the list of chapter to remove.
     * @param manga the manga of the chapter.
     */
    suspend fun removeChapters(chapters: List<Chapter>, manga: Manga) {
        restoreFinished.await()
        rootDownloadsDirMutex.withLock {
            val sourceDir = rootDownloadsDir.sourceDirs[manga.source] ?: return
            mangaDirsFor(sourceDir, manga.title, manga.id).forEach { mangaDir ->
                chapters.forEach { chapter ->
                    provider.getValidChapterDirNames(chapter.name, chapter.scanlator, chapter.id).forEach {
                        if (it in mangaDir.chapterDirs) {
                            mangaDir.chapterDirs = (mangaDir.chapterDirs - it).toMutableSet()
                        }
                    }
                }
            }
        }

        notifyChanges()
    }

    /**
     * Removes a manga that has been deleted from this cache.
     *
     * @param manga the manga to remove.
     */
    suspend fun removeManga(manga: Manga) {
        restoreFinished.await()
        rootDownloadsDirMutex.withLock {
            val sourceDir = rootDownloadsDir.sourceDirs[manga.source] ?: return
            listOfNotNull(
                provider.getMangaDirName(manga.title, manga.id),
                provider.getLegacyMangaDirName(manga.title),
            ).distinct().forEach { name ->
                if (sourceDir.mangaDirs.containsKey(name)) {
                    sourceDir.mangaDirs -= name
                }
            }
        }

        notifyChanges()
    }

    suspend fun removeSource(source: MangaSource) {
        restoreFinished.await()
        rootDownloadsDirMutex.withLock {
            rootDownloadsDir.sourceDirs -= source.id
        }

        notifyChanges()
    }

    fun invalidateCache() {
        val generation = synchronized(renewalLock) {
            val next = storageGeneration.incrementAndGet()
            lastRenew = 0L
            rootDownloadsDir.lastSuccessfulScanAt = 0L
            renewalJob?.cancel()
            renewalJob = null
            next
        }
        scope.launch {
            restoreFinished.await()
            diskCacheMutex.withLock {
                if (storageGeneration.get() == generation) {
                    AtomicFile(diskCacheFile).delete()
                }
            }
            if (storageGeneration.get() == generation) renewCache()
        }
    }

    /** Only one renewal is admitted, including calls racing with disk restore. */
    private fun renewCache() {
        synchronized(renewalLock) {
            if (lastRenew + renewInterval >= System.currentTimeMillis() || renewalJob?.isActive == true) {
                return
            }
            val generation = storageGeneration.get()
            val job = scope.launch(start = CoroutineStart.LAZY) {
                var attempted = false
                var succeeded = false
                try {
                    restoreFinished.await()
                    if (storageGeneration.get() != generation ||
                        lastRenew + renewInterval >= System.currentTimeMillis()
                    ) {
                        return@launch
                    }
                    attempted = true
                    _isInitializing.value = lastRenew == 0L

                    val sources = withTimeoutOrNull(30.seconds) {
                        extensionManager.isInitialized.first { it }
                        sourceManager.isInitialized.first { it }
                        getSources()
                    }
                    if (sources.isNullOrEmpty()) {
                        logcat(LogPriority.WARN) { "DownloadCache: sources unavailable, keeping snapshot" }
                        return@launch
                    }
                    val downloadsDirNow = storageManager.getDownloadsDirectory()
                    if (downloadsDirNow == null) {
                        logcat(LogPriority.WARN) { "DownloadCache: storage unavailable, keeping snapshot" }
                        return@launch
                    }

                    val sourceMap = sources.associate { provider.getSourceDirName(it).lowercase() to it.id }

                    rootDownloadsDirMutex.withLock {
                        val updatedRootDir = RootDirectory(downloadsDirNow)

                        updatedRootDir.sourceDirs = updatedRootDir.dir?.listFiles().orEmpty()
                            .filter { it.isDirectory && !it.name.isNullOrBlank() }
                            .mapNotNull { dir ->
                                val sourceId = sourceMap[dir.name!!.lowercase()]
                                sourceId?.let { it to SourceDirectory(dir) }
                            }
                            .toMap()

                        updatedRootDir.sourceDirs.values.map { sourceDir ->
                            async {
                                sourceDir.mangaDirs = sourceDir.dir?.listFiles().orEmpty()
                                    .filter { it.isDirectory && !it.name.isNullOrBlank() }
                                    .associate { it.name!! to MangaDirectory(it) }
                                sourceDir.mangaDirs.values.forEach { mangaDir ->
                                    val chapterDirs = mangaDir.dir?.listFiles().orEmpty()
                                        .mapNotNull {
                                            when {
                                                // Ignore incomplete downloads; C-M4: reap STALE ones.
                                                // Crashed/errored downloads left "_tmp" dirs and
                                                // ".cbz_tmp" files with no cleaner anywhere. Active
                                                // downloads keep touching their files, so the 24h
                                                // last-modified guard never hits a live download.
                                                it.name?.endsWith(MangaDownloader.TMP_DIR_SUFFIX) == true -> {
                                                    val lastModified = runCatching {
                                                        it.lastModified()
                                                    }.getOrDefault(0L)
                                                    if (lastModified > 0L &&
                                                        System.currentTimeMillis() - lastModified > STALE_TMP_MILLIS
                                                    ) {
                                                        runCatching { it.delete() }
                                                    }
                                                    null
                                                }
                                                // Folder of images
                                                it.isDirectory -> it.name
                                                // CBZ files
                                                it.isFile && it.extension == "cbz" -> it.nameWithoutExtension
                                                // Anything else is irrelevant
                                                else -> null
                                            }
                                        }
                                        .toMutableSet()

                                    mangaDir.chapterDirs = chapterDirs
                                }
                            }
                        }
                            .awaitAll()

                        ensureActive()
                        synchronized(renewalLock) {
                            if (storageGeneration.get() != generation) return@launch
                            updatedRootDir.lastSuccessfulScanAt = System.currentTimeMillis()
                            rootDownloadsDir = updatedRootDir
                        }
                    }
                    succeeded = true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "DownloadCache: failed to create cache" }
                } finally {
                    synchronized(renewalLock) {
                        if (storageGeneration.get() == generation) {
                            if (attempted) {
                                val now = System.currentTimeMillis()
                                lastRenew = if (succeeded) {
                                    now
                                } else {
                                    now - renewInterval + failedRenewRetryInterval
                                }
                            }
                            _isInitializing.value = false
                        }
                    }
                    if (storageGeneration.get() == generation) {
                        notifyChanges(persist = succeeded)
                    }
                }
            }
            renewalJob = job
            job.start()
        }
    }

    private fun getSources(): List<MangaSource> {
        return sourceManager.getOnlineSources() + sourceManager.getStubSources()
    }

    private fun notifyChanges(persist: Boolean = true) {
        _changes.tryEmit(Unit)
        if (persist) updateDiskCache()
    }

    private var updateDiskCacheJob: Job? = null
    private fun updateDiskCache() {
        synchronized(writerLock) {
            updateDiskCacheJob?.cancel()
            updateDiskCacheJob = scope.launchIO {
                delay(1000L)
                restoreFinished.await()
                try {
                    diskCacheMutex.withLock {
                        val generation = storageGeneration.get()
                        val snapshot = rootDownloadsDirMutex.withLock {
                            rootDownloadsDir.copyForDisk()
                        }
                        val bytes = ProtoBuf.encodeToByteArray(snapshot)
                        ensureActive()
                        if (storageGeneration.get() != generation) return@withLock
                        val file = AtomicFile(diskCacheFile)
                        val output = file.startWrite()
                        try {
                            output.write(bytes)
                            file.finishWrite(output)
                        } catch (e: Exception) {
                            file.failWrite(output)
                            throw e
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to write disk cache file" }
                }
            }
        }
    }
}

/**
 * Class to store the files under the root downloads directory.
 */
@Serializable
internal class RootDirectory(
    @Serializable(with = UniFileAsStringSerializer::class)
    val dir: UniFile?,
    @Volatile var sourceDirs: Map<Long, SourceDirectory> = mapOf(),
    @Volatile var lastSuccessfulScanAt: Long = 0L,
)

/** C-M4: incomplete-download artifacts older than this are reaped during cache renewal. */
private const val STALE_TMP_MILLIS = 24L * 60 * 60 * 1000

/**
 * Class to store the files under a source directory.
 */
@Serializable
internal class SourceDirectory(
    @Serializable(with = UniFileAsStringSerializer::class)
    val dir: UniFile?,
    @Volatile var mangaDirs: Map<String, MangaDirectory> = mapOf(),
)

/**
 * Class to store the files under a manga directory.
 */
@Serializable
internal class MangaDirectory(
    @Serializable(with = UniFileAsStringSerializer::class)
    val dir: UniFile?,
    @Volatile var chapterDirs: MutableSet<String> = mutableSetOf(),
)

private object UniFileAsStringSerializer : KSerializer<UniFile?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("UniFile", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UniFile?) {
        return if (value == null) {
            encoder.encodeNull()
        } else {
            encoder.encodeString(value.uri.toString())
        }
    }

    override fun deserialize(decoder: Decoder): UniFile? {
        return if (decoder.decodeNotNullMark()) {
            UniFile.fromUri(Injekt.get<Application>(), decoder.decodeString().toUri())
        } else {
            decoder.decodeNull()
        }
    }
}

internal fun <T : Any> cachedMangaDirectories(
    directories: Map<String, T>,
    scopedName: String?,
    legacyName: String,
    mangaId: Long?,
): List<T> {
    val names = buildSet {
        scopedName?.let(::add)
        add(legacyName)
        mangaId?.let { id ->
            val suffix = " [$id]"
            directories.keys.filterTo(this) { it.endsWith(suffix) }
        }
    }
    return names.mapNotNull { directories[it] }
}

/** Call under rootDownloadsDirMutex; the encoder must never walk the live tree. */
internal fun RootDirectory.copyForDisk(): RootDirectory = RootDirectory(
    dir = dir,
    sourceDirs = sourceDirs.mapValues { (_, source) ->
        SourceDirectory(
            dir = source.dir,
            mangaDirs = source.mangaDirs.mapValues { (_, manga) ->
                MangaDirectory(manga.dir, manga.chapterDirs.toMutableSet())
            },
        )
    },
    lastSuccessfulScanAt = lastSuccessfulScanAt,
)
