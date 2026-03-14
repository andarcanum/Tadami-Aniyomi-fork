# Architecture

**Analysis Date:** 2026-03-14

## Pattern Overview

**Overall:** Clean Architecture with Multi-Module Gradle Project

**Key Characteristics:**
- **Domain-driven design** with clear separation between domain, data, and presentation layers
- **Multi-module Gradle structure** with 15+ modules
- **MVVM-like presentation** using Jetpack Compose and Voyager navigation
- **Repository pattern** for data abstraction
- **Use case pattern** (interactors) for business logic encapsulation

## Layers

### Domain Layer (`domain/`)
- **Purpose:** Business logic and entities, free of Android/framework dependencies
- **Location:** `domain/src/main/java/`
- **Contains:** 
  - Models (`domain/*/model/*.kt`)
  - Repository interfaces (`domain/*/repository/*.kt`)
  - Interactors (use cases) (`domain/*/interactor/*.kt`)
  - Preferences (`domain/*/service/*.kt`, `domain/*/store/*.kt`)
- **Depends on:** Only Kotlin standard library
- **Used by:** Presentation layer (app module)

**Sub-packages by feature:**
- `entries/` - Anime, Manga, Novel entry management
- `items/` - Chapters, Episodes, Novel chapters
- `library/` - Library management and display modes
- `category/` - Category organization
- `history/` - Reading/Watching history
- `track/` - Third-party tracking integration (AniList, MyAnimeList, etc.)
- `source/` - Source management
- `download/` - Download preferences
- `backup/` - Backup service preferences
- `achievement/` - Achievement system
- `updates/` - Update tracking

### Data Layer (`data/`)
- **Purpose:** Data persistence, API calls, and implementation of domain repositories
- **Location:** `data/src/main/java/`
- **Contains:**
  - Repository implementations (`data/*/*RepositoryImpl.kt`)
  - Database handlers (SQLDelight) (`data/handlers/`)
  - Mappers (`data/*/*Mapper.kt`)
  - Caches (`data/cache/`)
  - Downloads (`data/download/`)
  - Network clients
  - Achievement system implementation
- **Depends on:** Domain layer, SQLDelight, OkHttp, Coil
- **Used by:** App module

**Database structure (SQLDelight):**
- Separate handlers for anime, manga, and novel: `MangaDatabaseHandler`, `AnimeDatabaseHandler`, `NovelDatabaseHandler`
- Each with generated Queries classes

### Presentation Layer (`app/`)
- **Purpose:** UI implementation using Jetpack Compose
- **Location:** `app/src/main/java/`
- **Contains:**
  - Activities (`ui/main/`, `ui/reader/`, `ui/player/`, etc.)
  - Screen Models (ViewModels) (`ui/*/ *ScreenModel.kt`)
  - Screens/Composables (`ui/*/ *Screen.kt`)
  - Navigation (Voyager-based)
- **Depends on:** Domain, Presentation-Core, Presentation-Widget

### Core Modules

**`core/common/`**
- **Purpose:** Shared utilities, preferences, network, storage
- **Location:** `core/common/src/main/java/`
- **Contains:**
  - Preferences system (`core/common/preference/`)
  - Network utilities (`core/common/network/`)
  - Storage utilities (`core/common/storage/`)
  - i18n helpers
- **Used by:** All modules

**`core/archive/`**
- **Purpose:** Archive handling (ZIP, EPUB reading)
- **Location:** `core/archive/src/main/kotlin/`

**`core-metadata/`**
- **Purpose:** App metadata constants

### Presentation Libraries

**`presentation-core/`**
- **Purpose:** Reusable Compose components, theme, utilities
- **Location:** `presentation-core/src/main/java/`
- **Contains:**
  - Common components (`presentation-core/components/`)
  - Theme (`presentation-core/theme/`)
  - Utilities
  - Icons

**`presentation-widget/`**
- **Purpose:** Android Glance widgets for home screen
- **Location:** `presentation-widget/src/main/java/`

### Source Modules

**`source-api/`**
- **Purpose:** Abstract interfaces for sources (anime, manga, novel)
- **Location:** `source-api/src/main/java/`

**`source-local/`**
- **Purpose:** Local source implementation for reading downloaded content

### Internationalization

**`i18n/`** and **`i18n-aniyomi/`**
- **Purpose:** String resources and localization
- Uses Moko Resources for multi-platform i18n

## Data Flow

**Reading a manga from source:**

1. **UI Layer** (`app/`): User opens MangaScreen
2. **Screen Model**: Calls domain interactor (e.g., `GetMangaWithChapters`)
3. **Domain Interactor**: Orchestrates domain logic, calls repository interface
4. **Repository Interface** (`domain/entries/manga/repository/MangaRepository.kt`): Abstract contract
5. **Repository Implementation** (`data/entries/manga/MangaRepositoryImpl.kt`): 
   - Uses `MangaDatabaseHandler` (SQLDelight)
   - Maps database entities to domain models
6. **Database**: SQLite via SQLDelight

**Updating manga:**

1. **UI Layer**: User triggers refresh
2. **Screen Model**: Calls `NetworkToLocalManga` interactor
3. **Domain Interactor**: Fetches from source, updates local DB via repository
4. **Repository Implementation**: Updates via SQLDelight queries
5. **Achievement Event**: Published via `AchievementEventBus` for achievements

## Key Abstractions

### Repository Pattern
- **Purpose:** Abstract data sources (local DB, network, cache)
- **Interface location:** `domain/*/repository/`
- **Implementation location:** `data/*/`
- **Examples:** 
  - `domain/entries/manga/repository/MangaRepository`
  - `data/entries/manga/MangaRepositoryImpl`

### Interactor/Use Case Pattern
- **Purpose:** Encapsulate single business logic operation
- **Location:** `domain/*/interactor/`
- **Examples:**
  - `GetManga.kt` - Fetch single manga
  - `NetworkToLocalManga.kt` - Sync manga from remote
  - `GetLibraryManga.kt` - Get library entries

### Screen Model (ViewModel)
- **Purpose:** UI state management, bridges UI and domain
- **Location:** `app/src/main/java/eu/kanade/tachiyomi/ui/*/ *ScreenModel.kt`
- **Pattern:** Uses Kotlin Flows for state, domain interactors

### Navigator (Voyager)
- **Purpose:** Screen navigation
- **Entry:** `HomeScreen` with `TabNavigator`
- **Pattern:** Stack-based navigation with `Navigator` component

## Entry Points

### Main Application Entry
- **Location:** `app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt`
- **Triggers:** App launch
- **Responsibilities:**
  - Splash screen management
  - Migration handling
  - Navigator setup (HomeScreen)
  - Intent handling (deep links, shortcuts)

### Screen Navigation
- **Location:** `app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeScreen.kt`
- **Pattern:** Voyager TabNavigator
- **Tabs:** HomeHub, Library (Manga/Anime/Novel), Updates, Browse, More

### Reader Entry
- **Location:** `app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt` (Manga)
- **Location:** `app/src/main/java/eu/kanade/tachiyomi/ui/reader/novel/` (Novel reader composables)

### Player Entry
- **Location:** `app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt`

## Error Handling

**Strategy:** Result types and Exception propagation

**Patterns:**
- Repository methods return nullable types or throw domain exceptions
- Interactors catch exceptions and return Result or throw
- Screen Models collect flows and handle errors in UI state
- Network errors handled via OkHttp interceptors (Cloudflare, Rate limiting)

**Domain Exceptions:**
- `NoChaptersException` - `domain/items/chapter/model/NoChaptersException.kt`
- `NoEpisodesException` - `domain/items/episode/model/NoEpisodesException.kt`
- `NoSeasonsException` - `domain/entries/anime/model/NoSeasonsException.kt`

## Cross-Cutting Concerns

**Logging:** logcat via `tachiyomi.core.common.util.system.logcat`

**Preferences:** 
- Domain preferences in `domain/*/service/` and `domain/*/store/`
- Android preferences in `core/common/preference/`
- Injected via Injekt

**Authentication:** 
- Trackers use OAuth (AniList, MyAnimeList, Shikimori)
- Custom button authentication via activities

**Dependency Injection:** Injekt (Koin-like)

---

*Architecture analysis: 2026-03-14*
