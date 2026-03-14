# Codebase Structure

**Analysis Date:** 2026-03-14

## Directory Layout

```
project-root/
├── app/                          # Main application module (Android)
│   └── src/
│       ├── main/java/
│       │   ├── eu/kanade/        # Main package (UI, DI, extensions)
│       │   └── mihon/            # Mihon-specific features
│       ├── test/                  # Unit tests
│       └── androidTest/           # Android tests
├── domain/                        # Domain layer (business logic)
│   └── src/
│       ├── main/java/
│       │   └── tachiyomi/domain/ # Domain packages
│       └── test/
├── data/                          # Data layer (repositories, DB)
│   └── src/
│       ├── main/java/
│       │   └── tachiyomi/data/   # Data implementations
│       └── test/
├── presentation-core/             # Shared Compose components
├── presentation-widget/           # Android Glance widgets
├── core/
│   ├── common/                    # Common utilities
│   └── archive/                   # Archive handling
├── source-api/                    # Source interfaces
├── source-local/                  # Local source implementation
├── i18n/                          # i18n framework
├── i18n-aniyomi/                 # App strings
├── buildSrc/                      # Gradle build logic
└── gradle/                        # Gradle configs and versions
```

## Directory Purposes

### App Module (`app/`)
- **Purpose:** Main Android application, UI layer, DI configuration
- **Contains:**
  - Activities (`ui/main/`, `ui/reader/`, `ui/player/`)
  - Screen implementations (`ui/*/ *Screen.kt`)
  - ScreenModels (`ui/*/ *ScreenModel.kt`)
  - DI modules (`di/AppModule.kt`, `di/PreferenceModule.kt`)
  - Extension managers
  - Source managers
- **Key files:** 
  - `app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt`
  - `app/src/main/java/eu/kanade/tachiyomi/di/AppModule.kt`

### Domain Module (`domain/`)
- **Purpose:** Business logic, entities, repository interfaces
- **Contains:**
  - Models by feature (`entries/`, `items/`, `library/`, `category/`, etc.)
  - Repository interfaces (`*/repository/`)
  - Interactors (`*/interactor/`)
  - Preferences services
- **Key files:**
  - `domain/src/main/java/tachiyomi/domain/entries/manga/model/Manga.kt`
  - `domain/src/main/java/tachiyomi/domain/entries/manga/repository/MangaRepository.kt`

### Data Module (`data/`)
- **Purpose:** Repository implementations, database, caching
- **Contains:**
  - Repository implementations (`*/ *RepositoryImpl.kt`)
  - Database handlers (`handlers/`)
  - Mappers (`*/ *Mapper.kt`)
  - Achievement system
  - Caches
  - Downloads
- **Key files:**
  - `data/src/main/java/tachiyomi/data/entries/manga/MangaRepositoryImpl.kt`
  - `data/src/main/java/tachiyomi/data/handlers/manga/MangaDatabaseHandler.kt`

### Presentation Core (`presentation-core/`)
- **Purpose:** Reusable Compose UI components
- **Contains:**
  - Common components (`components/material/`, `components/`)
  - Theme (`theme/`)
  - Utilities (`util/`)
  - Icons (`icons/`)
  - Screens (`screens/`)

### Presentation Widget (`presentation-widget/`)
- **Purpose:** Android Glance home screen widgets
- **Contains:**
  - Widget implementations
  - Widget managers

### Core Modules
- **`core/common/`**: Preferences, network, storage utilities
- **`core/archive/`**: ZIP/EPUB archive handling

### Source Modules
- **`source-api/`**: Abstract source interfaces
- **`source-local/`**: Local filesystem source

### i18n Modules
- **`i18n/`**: i18n framework
- **`i18n-aniyomi/`**: String resources

## Key File Locations

### Entry Points
- `app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt` - Main app entry
- `app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeScreen.kt` - Home navigation

### Configuration
- `settings.gradle.kts` - Module definitions
- `build.gradle.kts` (root) - Root build config
- `app/build.gradle.kts` - App module config
- `gradle/libs.versions.toml` - Version catalog
- `local.properties` - Local SDK paths (NOT committed)

### Dependency Injection
- `app/src/main/java/eu/kanade/tachiyomi/di/AppModule.kt` - Main DI module
- `app/src/main/java/eu/kanade/tachiyomi/di/PreferenceModule.kt` - Preferences

### Database
- SQLDelight `.sq` files in `data/src/main/sqldelight/`

## Naming Conventions

### Files
- **Screen Models:** `*ScreenModel.kt` - e.g., `MangaScreenModel.kt`
- **Screens:** `*Screen.kt` - e.g., `MangaScreen.kt`
- **Tabs:** `*Tab.kt` - e.g., `MangaLibraryTab.kt`
- **Activities:** `*Activity.kt` - e.g., `ReaderActivity.kt`
- **Repository Interfaces:** `*Repository.kt` - e.g., `MangaRepository.kt`
- **Repository Implementations:** `*RepositoryImpl.kt` - e.g., `MangaRepositoryImpl.kt`
- **Interactors:** `Verb*Noun*.kt` - e.g., `GetManga.kt`, `NetworkToLocalManga.kt`
- **Models:** `*.kt` (singular, PascalCase) - e.g., `Manga.kt`, `Chapter.kt`
- **Mappers:** `*Mapper.kt` - e.g., `MangaMapper.kt`

### Packages
- **Domain layer:** `tachiyomi.domain.<feature>.<type>`
  - `tachiyomi.domain.entries.manga.model`
  - `tachiyomi.domain.entries.manga.repository`
  - `tachiyomi.domain.entries.manga.interactor`
- **Data layer:** `tachiyomi.data.<feature>`
  - `tachiyomi.data.entries.manga`
  - `tachiyomi.data.handlers.manga`
- **UI layer:** `eu.kanade.tachiyomi.ui.<feature>`
  - `eu.kanade.tachiyomi.ui.entries.manga`
  - `eu.kanade.tachiyomi.ui.library.manga`

### Classes
- **Screens:** Extend `Screen` (Voyager)
- **Screen Models:** Typically `*State` data class + ViewModel pattern
- **Tabs:** Extend `Tab` (Voyager)

## Where to Add New Code

### New Feature (Anime/Manga/Novel)
1. **Domain Models:** Add to `domain/src/main/java/tachiyomi/domain/entries/<type>/model/`
2. **Domain Repository Interface:** Add to `domain/src/main/java/tachiyomi/domain/entries/<type>/repository/`
3. **Domain Interactors:** Add to `domain/src/main/java/tachiyomi/domain/entries/<type>/interactor/`
4. **Data Repository Implementation:** Add to `data/src/main/java/tachiyomi/data/entries/<type>/`
5. **UI Screen:** Add to `app/src/main/java/eu/kanade/tachiyomi/ui/<feature>/`
6. **UI ScreenModel:** Add to same location
7. **Navigation:** Update relevant tab/navigator

### New Component/Module
- **New feature area:** Add to `app/src/main/java/eu/kanade/tachiyomi/ui/`
- **New shared component:** Add to `presentation-core/src/main/java/tachiyomi/presentation/core/components/`
- **New utility:** Add to relevant core module

### New Domain Logic
- **New use case:** Add interactor to `domain/src/main/java/tachiyomi/domain/<feature>/interactor/`
- **New entity:** Add model to `domain/src/main/java/tachiyomi/domain/<feature>/model/`

### New Data Source
- **New repository method:** Add to domain repository interface, implement in data layer
- **New database table:** Add `.sq` file to `data/src/main/sqldelight/`

### Tests
- **Unit tests:** `domain/src/test/java/` or `data/src/test/java/`
- **UI tests:** `app/src/test/java/` or `app/src/androidTest/java/`

### Preferences
- **Domain preferences:** Add service in `domain/src/main/java/tachiyomi/domain/<feature>/service/`
- **Android preferences:** Add in `core/common/src/main/java/tachiyomi/core/common/preference/`

## Special Directories

### `.planning/codebase/`
- **Purpose:** Architecture documentation (this file, ARCHITECTURE.md)
- **Generated:** Yes (by GSD mapping)
- **Committed:** No

### `build/`, `.gradle/`
- **Purpose:** Build artifacts
- **Generated:** Yes
- **Committed:** No (in .gitignore)

### `i18n-aniyomi/src/commonMain/moko-resources/`
- **Purpose:** String resources for localization
- **Generated:** No (hand-written XML)
- **Committed:** Yes

### Private Modules (via `local.properties`)
- **Purpose:** Private extensions (e.g., Gemini bridge)
- **Location:** Configured via `private.gemini.module.dir` property

---

*Structure analysis: 2026-03-14*
