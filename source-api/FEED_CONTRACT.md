# Reels Feed Contract

**Current version: 17** (`extensionLib` 12.0–17.0 accepted by the host) ·
Owner module: [`:source-api`](build.gradle.kts) ·
API surface: [`AnimeFeedSource`](src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/AnimeFeedSource.kt),
[`FeedPage`](src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/FeedPage.kt),
[`ShortVideoItem`](src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/ShortVideoItem.kt)

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
```

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
- The host dedupes by `ShortVideoItem.id` across pages (first occurrence
  wins), so seam duplicates from cursor APIs are safe — but `id` values must
  be stable between requests.

## Video: `ShortVideoItem` (URL semantics changed in v17!)

- `videoUrl: String` — the **guaranteed playable base URL** (lowest/only
  quality variant). Required.
- `videoUrlHd: String?` — an optional **upgrade**. Strictly better than the
  base, or null. Never invert the two.
- With HD on, the host plays `videoUrlHd ?: videoUrl`; with HD off it always
  plays `videoUrl`. Single-URL source: `videoUrl = url, videoUrlHd = null`.
- `posterUrlVertical` — vertical poster if available (ideal for the feed).
- Temporary CDN links: always set `webUrl` (the watch page) — sharing uses it.

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
| 17 | Sticky cursor pagination (`FeedPage.nextCursor`, `cursor` parameters), `getSearchFeed` default, URL semantics flip (`videoUrl` base + optional `videoUrlHd`). Breaking: all feed plugins rebuild. |
| ≤16 | Initial feed contract (page-int pagination, `videoUrlHd` required). |
