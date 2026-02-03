# Tadami Android Project - Codebase Index

## Project Overview
**Tadami** is a fork of Aniyomi - an Android media reader application for anime and manga with a modern Aurora UI theme. Application ID: `com.tadami.aurora`, version 0.22 (code 135).

## Tech Stack
- **Language**: Kotlin with Coroutines + Flow
- **UI**: Jetpack Compose with Material3, Voyager 1.0.1 for navigation
- **DI**: Injekt (com.github.mihonapp:injekt) - lightweight Kotlin DI
- **Database**: SqlDelight 2.0.2 with SQLite (3 databases: manga.db, animedb, achievements.db)
- **Image Loading**: Coil 3.1.0
- **Networking**: OkHttp 5.0.0-alpha.14
- **Serialization**: Kotlinx Serialization JSON/Protobuf
- **Testing**: JUnit 5 (Jupiter), Kotest, MockK
- **Build**: Gradle with Kotlin DSL, version catalogs

## Module Structure
- **app**: Main application with UI and DI
- **domain**: Business logic, use cases (interactors), repository interfaces
- **data**: Repository implementations, database handlers, SqlDelight schemas
- **core/common**: Shared utilities
- **core-metadata**: Metadata functionality
- **source-api**: Extension API interfaces
- **source-local**: Local source implementation
- **presentation-core**: Shared UI components
- **i18n**: Internationalization

## Architecture Patterns

### Clean Architecture
- Domain layer has no Android dependencies
- Repository pattern: interfaces in domain, implementations in data
- Interactors/Use Cases for business logic

### Navigation (Voyager)
- Screens extend `Screen` interface with `@Composable Content()` method
- ScreenModels extend `StateScreenModel<State>` (NOT Android ViewModel)
- Navigation via `LocalNavigator.currentOrThrow.pop()`

### Dependency Injection (Injekt)
- Modules: `AppModule` (data layer), domain modules for use cases
- Registration via `addSingletonFactory {}` and `addFactory {}`
- Injection via `by injectLazy()` or `Injekt.get<T>()`

### Reactive Programming
- Kotlin Flow with `combine`, `distinctUntilChanged`
- StateScreenModel with Flow for reactive UI updates

## Achievement System

### Architecture
Event-driven gamification system with:
- **10 Achievement Types**: QUANTITY, EVENT, DIVERSITY, STREAK, LIBRARY, META, BALANCED, SECRET, TIME_BASED, FEATURE_BASED
- **4 Categories**: ANIME, MANGA, BOTH, SECRET
- **Tiered Achievements**: Multi-level achievements with progressive rewards
- **Points & Rewards**: XP system with unlockable themes and badges

### Key Components
- `AchievementHandler` - Main event processor (subscribes to EventBus)
- `AchievementEventBus` - SharedFlow event bus
- `PointsManager` - Points and level management
- `DiversityAchievementChecker` - Genre/source diversity tracking
- `StreakAchievementChecker` - Daily streak tracking
- `AchievementScreenModel` - StateScreenModel with Flow combine

### Event Flow
```
User Action -> AchievementEvent -> EventBus -> Handler -> Checker -> Repository -> UI Update
```

### Events
- `ChapterRead`, `EpisodeWatched` - Content consumption
- `LibraryAdded`, `LibraryRemoved` - Library management
- `MangaCompleted`, `AnimeCompleted` - Completion events
- `AppStart`, `SessionEnd` - Time-based
- `FeatureUsed` - Feature usage tracking

## Database Schema (SqlDelight)

### Manga Database (tachiyomi.db)
- `mangas` table: source, url, title, genre, favorite, status, etc.
- `chapters` table: manga_id, name, read, bookmark, chapter_number, etc.
- `history` table: chapter_id, last_read, time_read
- `categories`, `manga_sync`, `excluded_scanlators`, etc.

### Anime Database (tachiyomi.animedb)
- `animes` table: similar to mangas
- `episodes` table: anime_id, name, seen, bookmark, episode_number, total_seconds, etc.
- `animehistory`, `animecategories`, etc.

### Achievements Database (achievements.db)
- `achievements` table: id, type, category, threshold, points, title, etc.
- `achievement_progress` table: achievement_id, current_progress, is_unlocked, unlocked_at
- `achievement_activity` table: date, chapters_read, episodes_watched
- `user_points` table: total_points, current_level

## Key File Locations

### Build Configuration
- `build.gradle.kts` - Root build script
- `settings.gradle.kts` - Module inclusion
- `gradle/libs.versions.toml` - Library versions
- `app/build.gradle.kts` - App module

### DI Configuration
- `app/src/main/java/eu/kanade/tachiyomi/di/AppModule.kt` - Data layer DI
- `app/src/main/java/eu/kanade/tachiyomi/App.kt` - Application class, initializes DI

### Achievement System
- `data/src/main/java/tachiyomi/data/achievement/handler/AchievementHandler.kt` - Main handler
- `domain/src/main/java/tachiyomi/domain/achievement/model/Achievement.kt` - Domain model
- `app/src/main/assets/achievements/achievements.json` - Achievement configuration
- `app/src/main/java/eu/kanade/presentation/achievement/` - UI components

### Database
- `data/src/main/sqldelight/data/*.sq` - Manga database schemas
- `data/src/main/sqldelightanime/dataanime/*.sq` - Anime database schemas
- `data/src/main/sqldelightachievements/tachiyomi/data/achievement/*.sq` - Achievement schemas

## Naming Conventions
- **Packages**: `eu.kanade.*` for app layer, `tachiyomi.*` for domain/data
- **Interactors**: `{Action}{Entity}.kt` (e.g., `SetReadStatus.kt`, `GetAnime.kt`)
- **Repositories**: `{Entity}Repository` interface, `{Entity}RepositoryImpl` implementation
- **ScreenModels**: `{Feature}ScreenModel` extending `StateScreenModel`
- **Screens**: `{Feature}ScreenVoyager` for Voyager wrapper, `{Feature}Screen` for UI

## Build Commands
```bash
./gradlew --no-daemon build                    # Build all modules
./gradlew --no-daemon assembleDebug           # Build debug APK
./gradlew --no-daemon test                    # All tests
./gradlew --no-daemon spotlessCheck           # Check formatting
./gradlew --no-daemon spotlessApply           # Apply formatting fixes
```

## Testing
- Unit tests in `data/src/test/java/`
- UI tests in `app/src/androidTest/java/`
- Uses JUnit 5, Kotest assertions, MockK for mocking

## Documentation
- `docs/README.md` - System overview (Russian)
- `docs/ACHIEVEMENTS_ARCHITECTURE.md` - Achievement architecture
- `docs/ACHIEVEMENTS_GUIDE.md` - How to add achievements
- `CLAUDE.md` - Project guidance for Claude Code
