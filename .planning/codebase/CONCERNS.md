# Codebase Concerns

**Analysis Date:** 2026-03-14

## Tech Debt

### RxJava Legacy Code
- **Issue:** Extensive use of RxJava (`Single`, `Completable`, `Observable`, `.subscribe()`) throughout the codebase despite migration to Kotlin Coroutines
- **Files:** 287+ matches including `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/online/HttpSource.kt`, `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/online/AnimeHttpSource.kt`, `source-local/src/androidMain/kotlin/tachiyomi/source/local/entries/anime/LocalAnimeSource.kt`
- **Impact:** Maintenance burden, potential memory leaks from unmanaged subscriptions, mixing paradigms
- **Fix approach:** Complete migration to coroutines. Mark deprecated RxJava methods with `@Deprecated` and remove after extension library bump to ensure extensions also migrate

### Deprecated Source APIs
- **Issue:** Multiple deprecated methods in `HttpSource`, `CatalogueSource`, `AnimeHttpSource`, and `NovelSource` classes
- **Files:** `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/online/HttpSource.kt` (lines 111, 142, 183, 218, 256, 301, 339), `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/online/AnimeHttpSource.kt` (lines 112, 146, 186, 224, 262, 441, 489, 506)
- **Impact:** Confusing API for extension developers, technical debt accumulates
- **Fix approach:** Remove deprecated methods after ensuring all extensions have migrated to newer APIs (coordinate with ext-lib version bump)

### Massive ViewModel Files
- **Issue:** Extremely large files that are difficult to maintain and test
- **Files:**
  - `app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt` - 2308 lines
  - `app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt` - 1328 lines
  - `app/src/main/java/eu/kanade/tachiyomi/ui/reader/novel/NovelReaderScreenModel.kt` - 118KB
- **Impact:** Hard to understand, test, and modify. High risk of bugs when making changes
- **Fix approach:** Split into smaller, focused classes using composition. Extract player state, playback control, and UI state into separate classes

### Legacy Migration Code
- **Issue:** 50+ migration classes for backward compatibility with old data formats
- **Files:** `app/src/main/java/mihon/core/migration/migrations/*.kt`
- **Impact:** Complex codebase, migration code runs on every app start checking conditions
- **Fix approach:** Remove migrations for versions that are no longer supported (e.g., 2+ major versions old)

### Preference Migration Overload
- **Issue:** Multiple legacy preference migration methods scattered across preference classes
- **Files:** `app/src/main/java/eu/kanade/tachiyomi/ui/reader/novel/setting/NovelReaderPreferences.kt` (lines 285-306, 535+), `app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReaderPreferences.kt` (line 160)
- **Impact:** Difficult to maintain, increases app startup time
- **Fix approach:** Consolidate migrations, remove after sufficient time has passed

## Known Issues

### TODO Items Not Scheduled
- **Issue:** 82+ TODO comments throughout codebase, many referring to specific versions (e.g., "TODO(1.6)")
- **Files:** Various - see grep results for TODO patterns
- **Impact:** Features remain incomplete, technical debt grows
- **Fix approach:** Create tracking issues for each TODO, prioritize based on user impact

### Hack Comments Indicating Workarounds
- **Issue:** Several "HACK" and workaround comments indicating fragile code
- **Files:**
  - `app/src/main/java/eu/kanade/presentation/more/settings/screen/SearchableSettings.kt` line 40 - "HACK: for the background blipping thingy"
  - `app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt` line 514 - "TODO: I think this is a bad hack"
- **Impact:** Fragile code that may break with Android version updates
- **Fix approach:** Replace with proper implementations

### Extension Compatibility Workarounds
- **Issue:** Code to handle old extension library versions creates complexity
- **Files:**
  - `app/src/main/java/eu/kanade/tachiyomi/ui/player/loader/EpisodeLoader.kt` (lines 87, 201)
  - `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/Video.kt` (lines 47, 52, 56, 73)
- **Impact:** Bloated code, harder to maintain
- **Fix approach:** Set minimum extension library version and remove compatibility code

## Security Considerations

### API Keys in Preferences
- **Issue:** API keys for translation services stored in SharedPreferences
- **Files:** `app/src/main/java/eu/kanade/tachiyomi/ui/reader/novel/setting/NovelReaderPreferences.kt` (lines 428, 488, 497, 503)
- **Current mitigation:** Stored in app-private preferences, not committed to git
- **Recommendations:** Consider Android Keystore for additional protection, but current approach is acceptable for personal use apps

### Secret Hall Feature
- **Issue:** "Secret Hall" feature uses encoded queries and password protection
- **Files:** `app/src/main/java/eu/kanade/presentation/browse/SecretHallQuery.kt`, `app/src/main/java/eu/kanade/presentation/browse/SecretHallSceneConfig.kt`
- **Current mitigation:** Uses fallback stubs when implementation absent
- **Recommendations:** Document the security model, ensure no sensitive data leakage through error messages

## Performance Bottlenecks

### Database Query Patterns
- **Issue:** Some repository methods use generic exception handling that may hide performance issues
- **Files:** `domain/src/main/java/tachiyomi/domain/track/*/interactor/*.kt` - multiple catch blocks swallowing exceptions
- **Cause:** Exception-based control flow instead of proper null handling or Result types
- **Improvement path:** Use sealed classes or Result<T> for error handling

### Widget Update Frequency
- **Issue:** Home screen widgets may trigger frequent database queries
- **Files:** `presentation-widget/src/main/java/tachiyomi/presentation/widget/entries/manga/MangaWidgetManager.kt`, `presentation-widget/src/main/java/tachiyomi/presentation/widget/entries/anime/AnimeWidgetManager.kt`
- **Impact:** Battery drain if widget update interval is too frequent
- **Improvement path:** Review WorkManager intervals, consider reducing update frequency

### Achievement Handler Heavy Processing
- **Issue:** Achievement checking runs on many app events with complex queries
- **Files:** `data/src/main/java/tachiyomi/data/achievement/handler/AchievementHandler.kt` (900+ lines)
- **Cause:** Synchronous processing of achievement events, multiple database queries
- **Improvement path:** Batch achievement checks, run on background thread with lower priority

## Fragile Areas

### Exception Swallowing in Trackers
- **Issue:** Tracker repository methods catch generic Exception and silently fail
- **Files:** `domain/src/main/java/tachiyomi/domain/track/anime/interactor/*.kt`, `domain/src/main/java/tachiyomi/domain/track/manga/interactor/*.kt`, `domain/src/main/java/tachiyomi/domain/track/novel/interactor/*.kt`
- **Why fragile:** Failures are silent - users don't know when tracking fails
- **Safe modification:** Add error reporting/logging before catching, consider user-visible error notifications
- **Test coverage:** Limited - most tests don't verify error scenarios

### Null Safety Inconsistencies
- **Issue:** 390+ `return null` statements throughout codebase, inconsistent null handling
- **Files:** Many - particularly in reader and player code
- **Why fragile:** Hard to track which functions can return null, leads to NPEs
- **Safe modification:** Use Kotlin's null-safe operators consistently, consider sealed classes for optional results

### Legacy Category Migration
- **Issue:** Complex legacy category migration logic with multiple fallback paths
- **Files:** `data/src/main/java/tachiyomi/data/category/novel/NovelCategoryRepositoryImpl.kt` (lines 19-102)
- **Why fragile:** Edge cases in migration may cause data loss
- **Safe modification:** Add comprehensive tests for migration scenarios, add data validation

## Scaling Limits

### In-Memory Preference Store Testing
- **Current capacity:** Limited by device memory for large libraries
- **Limit:** May cause OOM with 10,000+ entries in library
- **Scaling path:** Implement pagination for library views, lazy-load metadata

### Database Schema
- **Current capacity:** SQLite performs well up to ~100,000 entries
- **Limit:** Large libraries with extensive history may slow down
- **Scaling path:** Consider archiving old history, adding indexes on frequently queried columns

## Dependencies at Risk

### RxJava to Coroutines Migration Incomplete
- **Risk:** RxJava still in use despite coroutines being preferred
- **Impact:** Security vulnerabilities in RxJava may not be patched, maintaining two async paradigms
- **Migration plan:** Complete coroutine migration, remove RxJava dependency after extension library bump

### Extension Library Version Compatibility
- **Risk:** Code maintains compatibility with extension library versions back to ~1.4
- **Impact:** Bloated codebase, harder to use new APIs
- **Migration plan:** Bump minimum supported version, remove compatibility shims

## Missing Critical Features

### Comprehensive Error Handling
- **Problem:** Generic exception catching throughout, no unified error handling strategy
- **Blocks:** User-friendly error messages, proper error recovery

### Offline Support for Trackers
- **Problem:** Tracker sync requires network, no offline queue
- **Blocks:** Users with intermittent connectivity lose tracking data

### Migration Testing Automation
- **Problem:** Migration code runs once per user, edge cases rare but impactful
- **Blocks:** Confidence in migration reliability

## Test Coverage Gaps

### Error Path Testing
- **What's not tested:** Network failures, database errors, permission denials
- **Files:** Most repository implementations lack error scenario tests
- **Risk:** Silent failures in edge cases
- **Priority:** High - error handling is fragile

### Migration Path Testing
- **What's not tested:** All migration edge cases, especially with corrupted/missing data
- **Files:** Migration classes mostly untested
- **Risk:** Data loss during migration
- **Priority:** High - migrations are one-way operations

### Player State Testing
- **What's not tested:** Complex player state transitions, background/foreground transitions
- **Files:** `PlayerViewModel.kt`, `PlayerActivity.kt`
- **Risk:** State corruption, playback issues
- **Priority:** Medium - affects core functionality

### Achievement System Testing
- **What's not tested:** Complex achievement conditions, achievement calculation edge cases
- **Files:** `data/src/main/java/tachiyomi/data/achievement/handler/*.kt`
- **Risk:** Incorrect achievement awards, progress not saving
- **Priority:** Medium - affects gamification feature

---

*Concerns audit: 2026-03-14*
