# External Integrations

**Analysis Date:** 2026-03-14

## APIs & External Services

**Translation Services (Novel Reader):**
- Google Gemini API - AI translation for novels
  - Implementation: `app/src/main/java/eu/kanade/tachiyomi/ui/reader/novel/translation/GeminiTranslationService.kt`
  - Auth: API key configured in settings
- OpenRouter - AI translation aggregator
  - Implementation: `app/src/main/java/eu/kanade/tachiyomi/ui/reader/novel/translation/OpenRouterTranslationService.kt`
  - Auth: API key configured in settings
- DeepSeek - AI translation
  - Implementation: `app/src/main/java/eu/kanade/tachiyomi/ui/reader/novel/translation/DeepSeekTranslationService.kt`
  - Auth: API key configured in settings
- Airforce - AI translation
  - Implementation: `app/src/main/java/eu/kanade/tachiyomi/ui/reader/novel/translation/AirforceTranslationService.kt`
  - Auth: API key configured in settings

**Manga/Anime Tracking Services:**
- AniList - Anime and manga tracking
  - Implementation: `app/src/main/java/eu/kanade/tachiyomi/data/track/anilist/`
  - Auth: OAuth2
- MyAnimeList (MAL) - Anime and manga tracking
  - Implementation: `app/src/main/java/eu/kanade/tachiyomi/data/track/myanimelist/`
  - Auth: OAuth2
- Kitsu - Anime and manga tracking
  - Implementation: `app/src/main/java/eu/kanade/tachiyomi/data/track/kitsu/`
  - Auth: API key
- Shikimori - Anime and manga tracking
  - Implementation: `app/src/main/java/eu/kanade/tachiyomi/data/track/shikimori/`
  - Auth: OAuth2
- Bangumi - Anime and manga tracking
  - Implementation: `app/src/main/java/eu/kanade/tachiyomi/data/track/bangumi/`
  - Auth: OAuth2
- Simkl - Anime and manga tracking
  - Implementation: `app/src/main/java/eu/kanade/tachiyomi/data/track/simkl/`
  - Auth: API key
- MangaUpdates - Manga tracking
  - Implementation: `app/src/main/java/eu/kanade/tachiyomi/data/track/mangaupdates/`
  - Auth: Credentials
- Jellyfin - Media server integration (anime)
  - Implementation: `app/src/main/java/eu/kanade/tachiyomi/data/track/jellyfin/`
  - Auth: API key
- Kavita - Media server integration (manga)
  - Implementation: `app/src/main/java/eu/kanade/tachiyomi/data/track/kavita/`
  - Auth: API key
- Komga - Media server integration (manga)
  - Implementation: `app/src/main/java/eu/kanade/tachiyomi/data/track/komga/`
  - Auth: Credentials
- Suwayomi - Sync tracking
  - Implementation: `app/src/main/java/eu/kanade/tachiyomi/data/track/suwayomi/`

## Data Storage

**Databases:**
- SQLite (local)
  - Connection: Internal app storage
  - Client: SqlDelight with Requery wrapper
  - Location: `data/src/main/java/tachiyomi/data/`

**File Storage:**
- Local filesystem (device storage)
- Unifile library for external storage access
- DiskLRUcache for caching

**Caching:**
- OkHttp HTTP cache
- DiskLRUcache for image/data caching

## Authentication & Identity

**Auth Providers:**
- OAuth2 - AniList, MyAnimeList, Shikimori
- API Keys - Kitsu, Simkl, Jellyfin, Kavita, DeepSeek, OpenRouter
- Basic Auth - MangaUpdates, Komga
- Custom tokens - Translation services

## Monitoring & Observability

**Error Tracking:**
- Logcat logging (squareup/logcat)
- No remote crash reporting detected in codebase

**Logs:**
- Android Logcat via logcat library
- OkHttp logging interceptor (debug builds)

## CI/CD & Deployment

**Build:**
- Gradle wrapper based builds
- Not detected: GitHub Actions, Jenkins, or other CI

**Distribution:**
- APK splits by ABI (armeabi-v7a, arm64-v8a, x86, x86_64)
- Universal APK available

## Environment Configuration

**Required env vars:**
- None hardcoded - all configurable at runtime
- SDK path via local.properties (not committed)

**Secrets location:**
- API keys stored in app settings (shared preferences)
- Build-time secrets via local.properties

## Webhooks & Callbacks

**Incoming:**
- None detected

**Outgoing:**
- Tracker sync callbacks (track updates)
- Extension repo updates

## Extension System

**Extension Repositories:**
- Custom extension repo support
- Network-based plugin downloader
- Implementation: `data/src/main/java/tachiyomi/data/extension/`
- Novel extensions: `data/src/main/java/tachiyomi/data/extension/novel/`

---

*Integration audit: 2026-03-14*
