package eu.kanade.tachiyomi.data.download.anime

import android.app.Application
import android.content.Context
import android.util.AtomicFile
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.util.size
import kotlinx.coroutines.CancellationException
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
import tachiyomi.core.common.storage.extension
import tachiyomi.core.common.storage.nameWithoutExtension
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.storage.service.StorageManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * Cache where we dump the downloads directory from the filesystem. This class is needed because
 * directory checking is expensive and it slows downs the app. The cache is invalidated by the time
 * defined in [renewInterval] as we don't have any control over the filesystem and the user can
 * delete the folders at any time without the app noticing.
 */
class AnimeDownloadCache(
    private val context: Context,
    private val provider: AnimeDownloadProvider = Injekt.get(),
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val extensionManager: AnimeExtensionManager = Injekt.get(),
    private val storageManager: StorageManager = Injekt.get(),
) {

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error ->
            logcat(LogPriority.ERROR, error) { "AnimeDownloadCache: uncaught coroutine error" }
        },
    )

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
    private val renewalLock = Any()
    private var renewalJob: Job? = null

    private val _isInitializing = MutableStateFlow(false)
    val isInitializing = _isInitializing
        .debounce(1000L) // Don't notify if it finishes quickly enough
        .stateIn(scope, SharingStarted.WhileSubscribed(), false)

    private val diskCacheFile: File
        get() = File(context.cacheDir, "dl_anime_index_cache_v3")

    private val rootDownloadsDirMutex = Mutex()
    private val diskCacheMutex = Mutex()
    private val writerLock = Any()

    @Volatile
    private var rootDownloadsDir = RootDirectory(storageManager.getDownloadsDirectory())

    init {
        _changes.tryEmit(Unit)

        // Attempt to read cache file
        scope.launch {
            diskCacheMutex.withLock {
                rootDownloadsDirMutex.withLock {
                    try {
                        val atomicFile = AtomicFile(diskCacheFile)
                        if (atomicFile.baseFile.exists()) {
                            val diskCache = ProtoBuf.decodeFromByteArray<RootDirectory>(atomicFile.readFully())
                            rootDownloadsDir = diskCache
                            lastRenew = System.currentTimeMillis()
                        }
                    } catch (e: Throwable) {
                        logcat(LogPriority.ERROR, e) { "Failed to initialize disk cache" }
                        AtomicFile(diskCacheFile).delete()
                    }
                }
            }
        }

        storageManager.changes
            .onEach { invalidateCache() }
            .launchIn(scope)
    }

    /**
     * Returns true if the episode is downloaded.
     *
     * @param episodeName the name of the episode to query.
     * @param episodeScanlator scanlator of the episode to query
     * @param animeTitle the title of the anime to query.
     * @param sourceId the id of the source of the episode.
     */
    fun isEpisodeDownloaded(
        episodeName: String,
        episodeScanlator: String?,
        animeTitle: String,
        sourceId: Long,
        skipCache: Boolean,
    ): Boolean {
        if (skipCache) {
            val source = sourceManager.getOrStub(sourceId)
            return provider.findEpisodeDir(episodeName, episodeScanlator, animeTitle, source) != null
        }

        renewCache()

        val sourceDir = rootDownloadsDir.sourceDirs[sourceId]
        if (sourceDir != null) {
            val animeDir = sourceDir.animeDirs[provider.getAnimeDirName(animeTitle)]
            if (animeDir != null) {
                return provider.getValidEpisodeDirNames(
                    episodeName,
                    episodeScanlator,
                ).any { it in animeDir.episodeDirs }
            }
        }
        return false
    }

    /**
     * Returns the amount of downloaded episodes.
     */
    fun getTotalDownloadCount(): Int {
        renewCache()

        return rootDownloadsDir.sourceDirs.values.sumOf { sourceDir ->
            sourceDir.animeDirs.values.sumOf { animeDir ->
                animeDir.episodeDirs.size
            }
        }
    }

    /**
     * Returns the amount of downloaded episodes for an anime.
     *
     * @param anime the anime to check.
     */
    fun getDownloadCount(anime: Anime): Int {
        renewCache()

        val sourceDir = rootDownloadsDir.sourceDirs[anime.source]
        if (sourceDir != null) {
            val animeDir = sourceDir.animeDirs[provider.getAnimeDirName(anime.title)]
            if (animeDir != null) {
                return animeDir.episodeDirs.size
            }
        }
        return 0
    }

    /**
     * Returns the total size of downloaded episodes.
     */
    fun getTotalDownloadSize(): Long {
        renewCache()

        return rootDownloadsDir.sourceDirs.values.sumOf { sourceDir ->
            sourceDir.dir?.size() ?: 0L
        }
    }

    /**
     * Returns the total size of downloaded chapters for an anime.
     *
     * @param anime the anime to check.
     */
    fun getDownloadSize(anime: Anime): Long {
        renewCache()

        return rootDownloadsDir.sourceDirs[anime.source]?.animeDirs?.get(
            provider.getAnimeDirName(
                anime.title,
            ),
        )?.dir?.size() ?: 0
    }

    /**
     * Adds an episode that has just been download to this cache.
     *
     * @param episodeDirName the downloaded episode's directory name.
     * @param animeUniFile the directory of the anime.
     * @param anime the anime of the episode.
     */
    suspend fun addEpisode(episodeDirName: String, animeUniFile: UniFile, anime: Anime) {
        rootDownloadsDirMutex.withLock {
            // Retrieve the cached source directory or cache a new one
            var sourceDir = rootDownloadsDir.sourceDirs[anime.source]
            if (sourceDir == null) {
                val source = sourceManager.get(anime.source) ?: return
                val sourceUniFile = provider.findSourceDir(source) ?: return
                sourceDir = SourceDirectory(sourceUniFile)
                rootDownloadsDir.sourceDirs += anime.source to sourceDir
            }

            // Retrieve the cached anime directory or cache a new one
            val animeDirName = provider.getAnimeDirName(anime.title)
            var animeDir = sourceDir.animeDirs[animeDirName]
            if (animeDir == null) {
                animeDir = AnimeDirectory(animeUniFile)
                sourceDir.animeDirs += animeDirName to animeDir
            }

            // Save the chapter directory
            animeDir.episodeDirs += episodeDirName
        }

        notifyChanges()
    }

    /**
     * Removes an episode that has been deleted from this cache.
     *
     * @param episode the episode to remove.
     * @param anime the anime of the episode.
     */
    suspend fun removeEpisode(episode: Episode, anime: Anime) {
        rootDownloadsDirMutex.withLock {
            val sourceDir = rootDownloadsDir.sourceDirs[anime.source] ?: return
            val animeDir = sourceDir.animeDirs[provider.getAnimeDirName(anime.title)] ?: return
            provider.getValidEpisodeDirNames(episode.name, episode.scanlator).forEach {
                if (it in animeDir.episodeDirs) {
                    animeDir.episodeDirs -= it
                }
            }
        }

        notifyChanges()
    }

    /**
     * Removes a list of episodes that have been deleted from this cache.
     *
     * @param episodes the list of episode to remove.
     * @param anime the anime of the episode.
     */
    suspend fun removeEpisodes(episodes: List<Episode>, anime: Anime) {
        if (episodes.isEmpty()) return
        val namesToRemove = HashSet<String>(episodes.size * 2)
        for (episode in episodes) {
            namesToRemove.addAll(provider.getValidEpisodeDirNames(episode.name, episode.scanlator))
        }
        rootDownloadsDirMutex.withLock {
            val sourceDir = rootDownloadsDir.sourceDirs[anime.source] ?: return
            val animeDir = sourceDir.animeDirs[provider.getAnimeDirName(anime.title)] ?: return
            if (animeDir.episodeDirs.any { it in namesToRemove }) {
                animeDir.episodeDirs = (animeDir.episodeDirs - namesToRemove).toMutableSet()
            }
        }

        notifyChanges()
    }

    /**
     * Removes an anime that has been deleted from this cache.
     *
     * @param anime the anime to remove.
     */
    suspend fun removeAnime(anime: Anime) {
        rootDownloadsDirMutex.withLock {
            val sourceDir = rootDownloadsDir.sourceDirs[anime.source] ?: return
            val animeDirName = provider.getAnimeDirName(anime.title)
            if (sourceDir.animeDirs.containsKey(animeDirName)) {
                sourceDir.animeDirs -= animeDirName
            }
        }

        notifyChanges()
    }

    suspend fun removeSource(source: AnimeSource) {
        rootDownloadsDirMutex.withLock {
            rootDownloadsDir.sourceDirs -= source.id
        }

        notifyChanges()
    }

    fun invalidateCache() {
        synchronized(renewalLock) {
            lastRenew = 0L
            renewalJob?.cancel()
            renewalJob = null
        }
        scope.launch {
            diskCacheMutex.withLock {
                AtomicFile(diskCacheFile).delete()
            }
            renewCache()
        }
    }

    /**
     * Renews the downloads cache. Synchronized so concurrent cold-start lookups cannot launch
     * overlapping full filesystem walks.
     */
    private fun renewCache() {
        synchronized(renewalLock) {
            if (lastRenew + renewInterval >= System.currentTimeMillis() || renewalJob?.isActive == true) {
                return
            }

            val job = scope.launch(start = CoroutineStart.LAZY) {
                var renewalSucceeded = false
                try {
                    if (lastRenew == 0L) {
                        _isInitializing.emit(true)
                    }

                    // Try to wait until extensions and sources have loaded
                    val sources = withTimeoutOrNull(30.seconds) {
                        extensionManager.isInitialized.first { it }
                        sourceManager.isInitialized.first { it }
                        getSources()
                    }
                    if (sources == null || sources.isEmpty()) {
                        logcat(LogPriority.WARN) {
                            "AnimeDownloadCache: sources unavailable, keeping previous cache snapshot"
                        }
                        return@launch
                    }

                    val sourceMap = sources.associate {
                        provider.getSourceDirName(it).lowercase() to it.id
                    }

                    rootDownloadsDirMutex.withLock {
                        val updatedRootDir = RootDirectory(storageManager.getDownloadsDirectory())

                        updatedRootDir.sourceDirs = updatedRootDir.dir?.listFiles().orEmpty()
                            .filter { it.isDirectory && !it.name.isNullOrBlank() }
                            .mapNotNull { dir ->
                                val sourceId = sourceMap[dir.name!!.lowercase()]
                                sourceId?.let { it to SourceDirectory(dir) }
                            }
                            .toMap()

                        updatedRootDir.sourceDirs.values.map { sourceDir ->
                            async {
                                sourceDir.animeDirs = sourceDir.dir?.listFiles().orEmpty()
                                    .filter { it.isDirectory && !it.name.isNullOrBlank() }
                                    .associate { it.name!! to AnimeDirectory(it) }
                                sourceDir.animeDirs.values.forEach { animeDir ->
                                    val episodeDirs = animeDir.dir?.listFiles().orEmpty()
                                        .mapNotNull {
                                            when {
                                                // Ignore incomplete downloads
                                                it.name?.endsWith(AnimeDownloader.TMP_DIR_SUFFIX) == true -> null
                                                // Folder of videos
                                                it.isDirectory -> it.name
                                                // MP4 files
                                                it.isFile && it.extension == "mp4" -> it.nameWithoutExtension
                                                // MKV files
                                                it.isFile && it.extension == "mkv" -> it.nameWithoutExtension
                                                // Anything else is irrelevant
                                                else -> null
                                            }
                                        }
                                        .toMutableSet()

                                    animeDir.episodeDirs = episodeDirs
                                }
                            }
                        }
                            .awaitAll()

                        ensureActive()
                        rootDownloadsDir = updatedRootDir
                    }

                    renewalSucceeded = true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to create download cache" }
                } finally {
                    _isInitializing.value = false
                    val now = System.currentTimeMillis()
                    lastRenew = if (renewalSucceeded) {
                        now
                    } else {
                        now - renewInterval + failedRenewRetryInterval
                    }
                    notifyChanges(persist = renewalSucceeded)
                }
            }
            renewalJob = job
            job.start()
        }

        // Mainly to notify the indexing notifier UI
        notifyChanges(persist = false)
    }

    private fun getSources(): List<AnimeSource> {
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
                try {
                    diskCacheMutex.withLock {
                        val snapshot = rootDownloadsDirMutex.withLock {
                            rootDownloadsDir.copyForDisk()
                        }
                        val bytes = ProtoBuf.encodeToByteArray(snapshot)
                        ensureActive()
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
                } catch (e: Throwable) {
                    logcat(
                        priority = LogPriority.ERROR,
                        throwable = e,
                        message = { "Failed to write disk cache file" },
                    )
                }
            }
        }
    }
}

/**
 * Class to store the files under the root downloads directory.
 */
@Serializable
private class RootDirectory(
    @Serializable(with = UniFileAsStringSerializer::class)
    val dir: UniFile?,
    @Volatile var sourceDirs: Map<Long, SourceDirectory> = mapOf(),
) {
    fun copyForDisk(): RootDirectory {
        return RootDirectory(
            dir = dir,
            sourceDirs = sourceDirs.mapValues { (_, sourceDir) ->
                SourceDirectory(
                    dir = sourceDir.dir,
                    animeDirs = sourceDir.animeDirs.mapValues { (_, animeDir) ->
                        AnimeDirectory(
                            dir = animeDir.dir,
                            episodeDirs = animeDir.episodeDirs.toMutableSet(),
                        )
                    },
                )
            },
        )
    }
}

/**
 * Class to store the files under a source directory.
 */
@Serializable
private class SourceDirectory(
    @Serializable(with = UniFileAsStringSerializer::class)
    val dir: UniFile?,
    @Volatile var animeDirs: Map<String, AnimeDirectory> = mapOf(),
)

/**
 * Class to store the files under an anime directory.
 */
@Serializable
private class AnimeDirectory(
    @Serializable(with = UniFileAsStringSerializer::class)
    val dir: UniFile?,
    @Volatile var episodeDirs: MutableSet<String> = mutableSetOf(),
)

private object UniFileAsStringSerializer : KSerializer<UniFile?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UniFile", PrimitiveKind.STRING)

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
