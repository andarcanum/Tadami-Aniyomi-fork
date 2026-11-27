package eu.kanade.tachiyomi.ui.reels

import eu.kanade.tachiyomi.animesource.model.FeedVideoQualityVariant
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import tachiyomi.domain.reels.anime.interactor.ReelsQualityDecision
import tachiyomi.domain.reels.anime.model.ReelsQualityPreference
import java.io.Serializable

/**
 * Screen-session identity for quality state. [token] changes with the ScreenModel/session,
 * [sourceId]/[accountId] fence results from a previous account or source.
 */
data class ReelsQualityOwner(
    val token: String,
    val sourceId: Long,
    val accountId: String?,
) : Serializable

/** The actually bound playback, never the requested preference. */
data class ReelsQualityPlayback(
    val owner: ReelsQualityOwner,
    val itemId: String,
    val revision: Long,
    val decision: ReelsQualityDecision,
    val videoUrl: String,
    val cacheIdentity: String,
    val cacheKey: String,
)

/** Frozen actual selection for a download/resume operation. */
data class ReelsQualityDownloadSnapshot(
    val owner: ReelsQualityOwner,
    val item: ShortVideoItem,
    val variant: FeedVideoQualityVariant,
    val cacheIdentity: String,
    val headers: Map<String, String>,
) : Serializable

enum class ReelsQualityCatalogStatus {
    UNREQUESTED,
    LOADING,
    READY,
    SOURCE_ONLY,
    ERROR,
    UNAVAILABLE,
    UNSUPPORTED,
}

sealed interface ReelsQualityFailure {
    data class Retry(val retryAtEpochMillis: Long?) : ReelsQualityFailure
    data object NeedsAuth : ReelsQualityFailure
    data object ContextChanged : ReelsQualityFailure
    data object Unavailable : ReelsQualityFailure
    data object Unsupported : ReelsQualityFailure
}

data class ReelsQualityItemState(
    val owner: ReelsQualityOwner,
    val itemId: String,
    val requested: ReelsQualityPreference,
    val usingGlobal: Boolean,
    val catalogStatus: ReelsQualityCatalogStatus,
    val options: List<FeedVideoQualityVariant>,
    val actual: ReelsQualityPlayback?,
    val switching: Boolean,
    val failure: ReelsQualityFailure?,
    val revision: Long,
    val forceSd: Boolean = false,
)

data class ReelsQualityState(
    val owner: ReelsQualityOwner? = null,
    val global: ReelsQualityPreference = ReelsQualityPreference.Source,
    val items: Map<String, ReelsQualityItemState> = emptyMap(),
    val selectionRevision: Long = 0,
)
