# Reels Feed Contract

**Current version: 25** (`extensionLib` 12.0–23.0 accepted by the host; v24 and v25 are
purely additive capabilities, so `LIB_VERSION_MAX` stays at 23.0) ·
Owner module: [`:source-api`](build.gradle.kts) ·
API surface: [`AnimeFeedSource`](src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/AnimeFeedSource.kt),
[`AnimeCreatorFeedSource`](src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/AnimeCreatorFeedSource.kt),
[`AnimeReelsFeedbackSource`](src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/AnimeReelsFeedbackSource.kt),
[`AnimeFeedLoginSource`](src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/AnimeFeedLoginSource.kt),
[`AnimeCustomFeedSource`](src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/AnimeCustomFeedSource.kt),
[`CustomFeedRef`](src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/CustomFeedRef.kt),
[`CustomFeedDetail`](src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/CustomFeedDetail.kt),
[`FeedPage`](src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/FeedPage.kt),
[`ShortVideoItem`](src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/ShortVideoItem.kt),
[`AnimeFeedWebLoginSource`](src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/AnimeFeedWebLoginSource.kt),
[`AnimeFeedOriginScopedSessionSource`](src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/AnimeFeedOriginScopedSessionSource.kt),
[`WebLoginCookie`](src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/WebLoginCookie.kt),
[`AnimeFeedMediaDescriptorSource`](src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/AnimeFeedMediaDescriptorSource.kt),
[`FeedMediaDescriptor`](src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/FeedMediaDescriptor.kt),
[`FeedMediaKind`](src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/FeedMediaKind.kt),
[`AnimeFeedErrorClassifierSource`](src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/AnimeFeedErrorClassifierSource.kt),
[`FeedError`](src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/FeedError.kt),
[`AnimeFeedBrowseSource`](src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/AnimeFeedBrowseSource.kt),
[`AnimeCategorizedSearchSource`](src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/AnimeCategorizedSearchSource.kt),
[`FeedCategory`](src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/FeedCategory.kt),
[`FeedCategoryPage`](src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/FeedCategoryPage.kt),
[`SearchSuggestion`](src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/SearchSuggestion.kt),
[`SearchSuggestions`](src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/SearchSuggestions.kt),
[`AnimeCategoryFeedOrderSource`](src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/AnimeCategoryFeedOrderSource.kt)

This document is the standalone reference for authors of short-video feed
(“Reels”) extensions. The Kotlin contracts live in `:source-api`; this file
explains the *protocol* rules that the signatures alone cannot express.
Whenever the contract classes change incompatibly, the host bumps
`AnimeExtensionLoader.LIB_VERSION_MAX`, and every feed plugin must be rebuilt
and reinstalled against the new `source-api`.

## Contract classes (`eu.kanade.tachiyomi.animesource`)

```kotlin
interface AnimeFeedSource : AnimeSource {
    val isFeedSource: Boolean            // default true — feed-source marker
    val supportsTags: Boolean            // default true; false => the host hides search UI
    fun getFilterList(): AnimeFilterList // default empty
    suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage
    suspend fun getSearchFeed(page: Int, cursor: String?, query: String, filters: AnimeFilterList): FeedPage
        = getFeed(page, cursor, filters)  // no search support? just don't override
}

data class FeedPage(
    val videos: List<ShortVideoItem>,
    val hasNextPage: Boolean,
    val nextCursor: String? = null,
)

interface AnimeCreatorFeedSource {
    suspend fun getCreatorFeed(creator: String, page: Int, cursor: String?): FeedPage
}

interface AnimeReelsFeedbackSource {
    suspend fun onVideoViewed(itemId: String, secondsWatched: Double, duration: Double)
    suspend fun onVideoLiked(itemId: String, liked: Boolean)
}
```

## Creator feeds (v18 capability interface)

`AnimeCreatorFeedSource` is an **optional capability**: a feed source that can also
serve one creator's own video feed. The host detects support with
`rawSource is AnimeCreatorFeedSource` (instanceof) and only then shows the creator
page, the follow button and the Following aggregation. Old plugins are untouched —
they simply don't opt in.

- The [sticky pagination protocol](#pagination-the-sticky-protocol-the-important-part)
  applies **per stream**: the creator page and each stream of the host's aggregated
  Following feed lock their own cursor independently.
- `creator` is exactly a `ShortVideoItem.author` value previously returned by this
  source; implementations must replay safely — the host retries failed pages with
  the identical `(page, cursor)` pair.
- A nonexistent/deleted creator should return an empty `FeedPage`, not throw.
- Implementing the capability means bumping `extensionLib` to 18.0.

## View & like feedback (v18 capability interface)

`AnimeReelsFeedbackSource` is an **optional capability**: the source accepts
per-video signals so the remote service can adapt its recommendations to the
viewer's taste. The host detects support with `rawSource is AnimeReelsFeedbackSource`
(instanceof); sources without the interface are simply never asked for feedback,
and no default members are added to existing interfaces (same rule as creator feeds).

- `onVideoViewed` is called **once** when a clip is left (swiped away) or finishes:
  `secondsWatched` is the actual playback time, `duration` the full clip length
  (both seconds). A source that personalizes remotely pushes a "view" event here.
- `onVideoLiked` is called on every like/unlike toggle (`liked` reflects the new
  state). Whether unlike maps to a distinct remote action is up to the source.
- Both calls are fire-and-forget from the host's perspective: failures must be
  swallowed, never surfaced to the user or written into feed state.
- Implementing the capability does **not** change `LIB_VERSION` — it is additive
  and detected with instanceof only.

## Web login (v20 capability interface)

`AnimeFeedWebLoginSource` is an **optional capability** for services without password
credentials (email + one-time code, magic link, ...). Two cooperating paths: (1) the service's
own SPA runs in the host's WebView (`webLoginUrl()` = the web app) and the finished session is
lifted via `importWebSession(cookies, localStorage)`; (2) `webLoginUrl()` composes the source's
own OAuth2 authorize URL (PKCE + state) — the host intercepts only `isOwnLoginRedirect(url)`
state-matched redirects, skips loading them and calls `importWebRedirect(url, cookies)` (the
CookieManager dump taken at intercept time) so the SPA cannot consume the single-use code. The host retries the session import on every page-finished event
back on the service domain and on the dialog's explicit "Done" action; `false` keeps the WebView
open. Sources with this capability also implement `AnimeFeedLoginSource` for session state, and
the host never shows them the password dialog.

## Origin-scoped session import (v24 capability interface)

`AnimeFeedOriginScopedSessionSource` is an optional refinement of `AnimeFeedWebLoginSource`
for services that keep their session on more than one origin (web app + api host, or the same
host on two ports). The legacy dump is one merged `name → value` map, so same-named cookies
from different origins collapse there (last origin wins) and the source cannot tell which
value belongs to which service. Sources implementing this capability receive
`List<WebLoginCookie>` instead: each record carries `origin` (`scheme://host[:port]`, default
ports folded, path/query never included) next to `name`/`value`.

Host behavior: it checks `source is AnimeFeedOriginScopedSessionSource` at import time and
calls `importWebSessionScoped(cookies, localStorage)` / `importWebRedirectScoped(url, cookies)`
instead of the legacy methods. The dump origin set is the source's explicit
`extraSessionCookieOrigins` (contract v22) when supplied; otherwise the host seeds the start
origin plus its `api.` sibling (`www.` stripped) and grows the set with every page the WebView
visits. A source that implements only this capability but not `AnimeFeedWebLoginSource` is
never offered the web login; the unscoped methods must stay functional for hosts that predate
v24, because those hosts will call them.

Implementing the capability is additive: instanceof detection only, no `LIB_VERSION` bump,
and every member is abstract (no default members on existing interfaces, see the v22 note in
the evolution rules).

## Category browse (v20 capability interface)

`AnimeFeedBrowseSource` serves a public category directory (`getBrowseCategories`) and one
video feed per category (`getCategoryFeed`), both on the v17 sticky pagination protocol. The
host renders a category grid (preview image + name + count) and a per-category feed page.

## Categorized search (v20 capability interface)

`AnimeCategorizedSearchSource.getCategorizedSearch(query)` returns the search hits split into
niches/creators/tags sections with previews. The host renders them as tabs next to the flat
`getSearchFeed` stream; tapping a NICHE opens its category feed, a CREATOR the creator feed,
a TAG a plain search with the tag as query.

## Category subscriptions (v20 capability interface)

`AnimeCategorySubscriptionSource` exposes per-account category follows: the host shows a
follow/unfollow toggle on category feeds (mirroring the creator-follow toggle) and queries the
followed set via `getSubscribedCategoryIds`. Login-gated: logged-out sources answer with an
empty set / false.

## Content preferences (v20 capability interface)

`AnimeContentPreferencesSource` exposes the account's content-type toggles
(`getContentPreferences` / `setContentPreferences`) for services that shape the personalized
feed by per-account content preferences. Requires login; logged-out sources answer with an
empty list / false. The host renders them as a switch sheet from the account hub.

## Blocked tags (v20 capability interface)

`AnimeBlockedTagsSource` exposes the account's blocked-tag list (`getBlockedTags` /
`setBlockedTags`, replace-semantics). Login-gated; the host renders a tag editor sheet from
the account hub.

## Category feed ordering (v21 capability interface)

`AnimeCategoryFeedOrderSource` lets a browse-capable source expose source-defined category-feed
orders (niche hot/latest/top): the host renders `categoryFilters()` in the filter sheet while in
NICHE mode and passes the selected `AnimeFilterList` into the four-arg `getCategoryFeed`.
Instanceof-detected; sources without it keep the plain three-arg category feed and no filter
sheet in NICHE mode.

## Session instrumentation (v22 capability interface)

`AnimeFeedLoginInstrumentationSource` moves the service-specific WebView session scraping OUT of
the host: the source supplies its own idempotent `sessionInstrumentationJs()`, the extra
cookie origins it needs (`extraSessionCookieOrigins()`, e.g. `api.` siblings) and the origins to
purge on logout (`logoutCookieOrigins()`). The host evaluates the snippet before its generic
cookie/DOM-storage dump and hands the dump to `AnimeFeedWebLoginSource.importWebSession` exactly
as before. Sources without the capability keep the plain dump. Instanceof-detected, no default
members on existing interfaces; each answer is optional (null/empty = generic fallback).

## Video URL re-resolution (v22 capability interface)

`AnimeFeedVideoResolverSource.resolveVideoUrl(itemId, hd)` gives the host a fresh playable URL
for a stable item id, so saved favorites (CDN links captured at like-time) and the offline
playlist can recover when those links expire. The host calls it lazily per item, keeps the
stored URL on null/failure, and never surfaces resolver errors. Instanceof-detected; sources
without it keep storing and replaying the original URLs.

## Pagination: the sticky protocol (the important part)

Two modes. The mode is locked by the **host** for the whole feed *generation*
(a generation = everything up to the next search / filter change / source
switch / reset):

| Host sends | Source returns | Host action |
|---|---|---|
| `cursor = null` (page-int mode) | `nextCursor = null` | stays in page-int mode; `page` is authoritative |
| `cursor = null` | `nextCursor = "T"` | **locks cursor mode**; the next request is `page+1, cursor = "T"` |
| `cursor = "T"` (cursor mode) | `nextCursor = "T2"` | continues with `page+1, cursor = "T2"` |
| `cursor = "T"` | `nextCursor = null` and `hasNextPage = true` | **protocol violation**: WARN log, pagination stops, the feed stays usable |
| anything | `hasNextPage = false` | terminal, always authoritative |

Rules for sources:

- Page-int APIs (RedGIFs, classic pagers): ignore `cursor`, always return
  `nextCursor = null`.
- Cursor APIs (TikTok-like): ignore `page` (it arrives as a hint only and
  resets to 1 per generation); live on tokens. **Once you have entered cursor
  mode, do not return null until the token stream is truly finished.**
- `page` arrives 1-based and resets to 1 on every generation reset
  (search/filter/source switch) — `cursor` is `null` in that first request,
  even if the previous generation was cursor-based.
- **Opaque cursors expire.** A cursor the source can no longer resolve (TTL,
  LRU eviction, logout, account/source-set change) must **not** be silently
  restarted from the head under the same `page` number. Return a terminal page
  (`hasNextPage = false`, `nextCursor = null`); the next generation starts with
  `cursor = null`. `FeedPage` has no error channel: throwing a plain exception
  makes the host treat it as a transient failure and retry the same dead cursor
  on every scroll (there is no per-cursor backoff). Sources implementing the
  [typed error classifier](#typed-feed-errors-v25-capability-interface) may
  instead throw `FeedError.ExpiredCursor`: the host then stops the append
  silently and waits for a manual refresh.
- The host dedupes by `ShortVideoItem.id` across pages (first occurrence
  wins), so seam duplicates from cursor APIs are safe — but `id` values must
  be stable between requests.

## Video: `ShortVideoItem` (URL semantics changed in v17!)

- `videoUrl: String` — the **playable base URL** (lowest/only quality
  variant). Required; the only exception is a source opting into lazy signing
  through the v25 [media descriptor](#media-descriptor-v25-capability-interface)
  (`lazyResolution = true` + `AnimeFeedVideoResolverSource`), which may return a
  blank string and let the host sign the URL just in time.
- `videoUrlHd: String?` — an optional **upgrade**. Strictly better than the
  base, or null. Never invert the two.
- With HD on, the host plays `videoUrlHd ?: videoUrl`; with HD off it always
  plays `videoUrl`. Single-URL source: `videoUrl = url, videoUrlHd = null`.
  Lazy HD: a failed `resolveVideoUrl(id, hd = true)` may retry the base variant
  when the descriptor leaves `hdFallback = true`.
- `posterUrlVertical` — vertical poster if available (ideal for the feed).
- Temporary CDN links: always set `webUrl` (the watch page) — sharing uses it.

## Media descriptor (v25 capability interface)

`AnimeFeedMediaDescriptorSource.mediaDescriptor(item): FeedMediaDescriptor` is an optional
per-item contract for the media semantics `ShortVideoItem` cannot express:

| Field | Default | Host behavior |
|---|---|---|
| `kind` | `null` | `STILL` renders the URL as an image even without an image extension (signed/opaque photo URLs); `VIDEO` suppresses extension sniffing; `null` keeps the legacy URL-based guess. |
| `lazyResolution` | `true` | A blank `videoUrl` is signed through `AnimeFeedVideoResolverSource`. `false` makes a blank URL a terminal playback error instead of an endless poster/spinner (no resolver, no retry loop). |
| `hdFallback` | `true` | A failed HD resolution retries the base variant (`resolveVideoUrl(id, hd = false)`). A present base `videoUrl` is always preferred over HD already. |
| `offlineSupported` | `true` | `false` refuses downloads for the item; removing an existing copy still works. |

Instanceof-detected, no default members on existing interfaces; the host falls back to the
legacy rules when the capability is absent, and the `ShortVideoItem` constructor is unchanged.
`mediaDescriptor` must be cheap and side-effect free — it may run for preloaded pages.

## Typed feed errors (v25 capability interface)

`AnimeFeedErrorClassifierSource.classifyFeedError(error): FeedError?` lets a source classify
its own failures instead of relying on the host's error-text matching. Returning `null` means
"no verdict" and the host keeps its legacy 401/403 and 429/Retry-After heuristics; a throwing
classifier is logged and treated as `null`.

| Verdict | Host action |
|---|---|
| `ExpiredCursor` | Append: pagination stops silently, the dead cursor is never retried; refresh starts a fresh generation. Reset load: surfaced as a regular terminal error. |
| `AuthRequired` | The host starts its web-login session bootstrap (web-login-capable sources) even when the message has no status-like text. |
| `RateLimited(retryAtEpochMillis)` | The host hides the automatic retry until the given wall-clock time (null = its default backoff) and still caps consecutive automatic retries. |
| `Transient` | Surfaced to the user; no bootstrap and no automatic backoff. |
| `Terminal` | Surfaced to the user; **no** bootstrap and **no** automatic retry, even if the message contains `401`, `403` or `429`. |

Instanceof-detected, no default members on existing interfaces. A classifier verdict
overrides text heuristics; return `null` when unsure.

## Evolution rules (discipline)

1. Any change to signatures/constructors of the contract classes is **breaking**
   for already-built plugin APKs (Kotlin default parameters do not save you —
   the JVM descriptor changes). Such changes happen **only with an
   extensionLib bump** and a synchronized rebuild of every plugin.
2. After public sources exist, **any** new field on a contract data class is
   breaking (old constructor/copy descriptors disappear; defaults do not
   help). Extend via `Map`-valued fields inside existing data, or wait for a
   planned version bump.
3. Never repurpose a field's semantics while keeping its number/type (lesson:
   the v16→v17 hd/sd inversion shipped together with a version bump and a
   recreate-table DB migration).
4. Extend capabilities **only** via new marker-style interfaces checked with
   `instanceof` (like `AnimeCreatorFeedSource` in v18) — never by adding default
   members to existing interfaces. Kotlin interface default methods are not
   binary-safe for plugin classes compiled without `-Xjvm-default=all`: a missing
   bridge becomes `AbstractMethodError` on the old plugin at runtime (same class
   of incident as the v17 `ShortVideoItem` break). This is the established Mihon
   pattern (`is CatalogueSource`, `isStub`).
   The same binary logic applies to default members on a **new** interface (v22
   `AnimeFeedLoginInstrumentationSource` ships two): they are safe only while
   host and plugin repos compile in the same jvm-default mode (both currently
   emit `DefaultImpls`). A plugin built with `-Xjvm-default=all` that does not
   override such a member would hit `AbstractMethodError` against a
   `DefaultImpls`-mode API jar. Rule of thumb for plugin authors: **override
   every member of every capability interface you implement** (RedGIFs does),
   and for API authors: prefer abstract members; defaults on new interfaces are
   a conscious, documented exception.

## Rebuilding a plugin against a new API (the chain)

```powershell
# in the app worktree:
./gradlew :source-api:bundleAndroidMainClassesToCompileJar --no-daemon
# copy classes.jar -> reels-plugins/libs/source-api.jar
# in the plugin: update override signatures, extensionLib in AndroidManifest, versionCode++
# in reels-plugins:
gradlew.bat :src:all:redgifs:assembleDebug --no-daemon
```

## Source skeleton

```kotlin
class MyFeed : AnimeFeedSource {
    override val id = 123L
    override val name = "My Feed"
    override val lang = "all"

    override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage {
        val token = cursor ?: bootstrapToken()          // cursor mode
        val resp = api.fetch(token)                     // page is a hint, ignored
        return FeedPage(
            videos = resp.items.map {
                ShortVideoItem(
                    id = it.stableId,
                    videoUrl = it.lowBitrateUrl,
                    videoUrlHd = it.topUrl.takeIf { t -> t != it.lowBitrateUrl },
                    posterUrl = it.thumb,
                    webUrl = it.pageUrl,
                    durationSec = it.seconds,
                    hasAudio = it.audio,
                    tags = it.tags,
                )
            },
            hasNextPage = resp.hasMore,
            nextCursor = resp.nextToken,                // null => host stops once in cursor mode
        )
    }
    // getSearchFeed is not overridden: the default delegates to getFeed
    // (supportsTags = false => the host hides the search UI)
}
```

## Version history

| Version | Change |
|---|---|
| 25 | Optional capabilities `AnimeFeedMediaDescriptorSource` (per-item `FeedMediaDescriptor`: media kind, lazy-resolution permission, HD fallback, offline support) and `AnimeFeedErrorClassifierSource` (typed `FeedError` verdicts: `ExpiredCursor`, `AuthRequired`, `RateLimited(retryAtEpochMillis)`, `Transient`, `Terminal`). Both instanceof-detected, no default members, existing constructors unchanged; `null` verdicts and absent capabilities keep the legacy host behavior. Additive: `LIB_VERSION_MAX` stays at 23.0. |
| 24 | Optional capability `AnimeFeedOriginScopedSessionSource` (`importWebSessionScoped` / `importWebRedirectScoped`) plus model `WebLoginCookie`: origin-scoped session import that preserves same-named cookies from multiple origins and non-default ports. Instanceof-detected, no default members; the legacy `Map<String, String>` methods remain the fallback for pre-v24 hosts. Additive: `LIB_VERSION_MAX` stays at 23.0. |
| 23 | Optional capability `AnimeAlbumSearchSource.searchAlbums(query, page, cursor): FeedCategoryPage` — paginated album-oriented search for bunkr-like sources; the host renders album cards (open / save to collection / long-press content preview) instead of the flat video feed when present. Instanceof-detected, no default members added to existing interfaces. Additive: existing feed plugins keep their pre-v23 search; `LIB_VERSION_MAX` → 23.0 as the discipline stamp. |
| 22 | Optional capability `AnimeFeedLoginInstrumentationSource` (source-supplied session instrumentation JS + extra dump origins + logout purge origins) and `AnimeFeedVideoResolverSource` (stable-id → playable URL re-resolution for favorites). Both instanceof-detected, no default members added to existing interfaces. Additive: existing feed plugins keep working; `LIB_VERSION_MAX` → 22.0 as the discipline stamp. |
| 21 | Optional capability `AnimeCategoryFeedOrderSource` (`categoryFilters()` + four-arg `getCategoryFeed`): source-defined category-feed orders rendered by the host in NICHE mode. Instanceof-detected, no default members added to existing interfaces. Additive: existing feed plugins keep working; `LIB_VERSION_MAX` → 21.0 as the discipline stamp. |
| 20 | Optional capabilities `AnimeFeedWebLoginSource` (hosted web login via WebView session import), `AnimeFeedBrowseSource` (category directory + per-category feeds) and `AnimeCategorizedSearchSource` (sectioned search hits with previews), plus models `FeedCategory`/`FeedCategoryPage`/`SearchSuggestion`/`SearchSuggestions`. All instanceof-detected, no default members added to existing interfaces. Additive: existing feed plugins keep working; `LIB_VERSION_MAX` → 20.0 as the discipline stamp. |
| 19+ | v19 addendum: optional search-hints capability `AnimeSearchHintsSource.getSearchHints()` (instanceof-detected, no default members added to existing interfaces); the host renders the returned tags as chips in the reels search bar. Additive: `LIB_VERSION` untouched, existing feed plugins keep working. |
| 19 | Optional feed-source login capability `AnimeFeedLoginSource` (`login`/`isLoggedIn`/`loggedInAccount`/`logout`) and custom-feed capability `AnimeCustomFeedSource` (`getCustomFeeds`/`getCustomFeed`/`getCustomFeedTags`/`getCustomFeedDetail`/`createCustomFeed`/`updateCustomFeed`/`deleteCustomFeed`, plus `CustomFeedRef`/`CustomFeedDetail`). Both instanceof-detected, no default members added to existing interfaces. Additive: existing feed plugins keep working; `LIB_VERSION_MAX` → 19.0 as the discipline stamp. |
| 18 | Optional creator-feed capability: `AnimeCreatorFeedSource.getCreatorFeed(creator, page, cursor)` and optional feedback capability `AnimeReelsFeedbackSource` (both instanceof-detected, no default members added to existing interfaces). Additive: existing feed plugins keep working; `LIB_VERSION_MAX` → 18.0 as the discipline stamp. |
| 17 | Sticky cursor pagination (`FeedPage.nextCursor`, `cursor` parameters), `getSearchFeed` default, URL semantics flip (`videoUrl` base + optional `videoUrlHd`). Breaking: all feed plugins rebuild. |
| ≤16 | Initial feed contract (page-int pagination, `videoUrlHd` required). |
