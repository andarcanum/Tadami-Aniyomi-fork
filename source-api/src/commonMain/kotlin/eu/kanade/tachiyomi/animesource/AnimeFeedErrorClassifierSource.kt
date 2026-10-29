package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.FeedError

/**
 * Capability interface (contract v25): a feed source that classifies its own failures into
 * [FeedError] verdicts, replacing the host's error-text heuristics.
 *
 * Instanceof-detected (the marker-interface pattern; no default members on existing
 * interfaces). Returning null means "no verdict": the host falls back to its legacy text
 * matching (401/403 web-session bootstrap, 429/Retry-After backoff), so a source can classify
 * only the cases it is sure about.
 *
 * The method must be cheap, side-effect free and non-throwing; a throwing classifier is logged
 * and treated as null by the host.
 */
interface AnimeFeedErrorClassifierSource {

    fun classifyFeedError(error: Throwable): FeedError?
}
