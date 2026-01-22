# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Tadami** is a fork of Aniyomi, an Android application for anime and manga. This fork focuses on UI/UX improvements with the Aurora theme. The project is built with Kotlin and uses Jetpack Compose for the UI layer.

**Key Technologies:**
- Kotlin with Gradle (Kotlin DSL)
- Jetpack Compose for UI
- Multi-module Android architecture
- SQLDelight for database
- Moko Resources for i18n

**Application ID:** `com.tadami.aurora` (release), `com.tadami.aurora.dev` (debug)

## Build Commands

### Building the App
```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK (minified/shrunk if enabled)
./gradlew assembleRelease

# Build preview variant (release config with debug signing)
./gradlew assemblePreview

# Build all variants
./gradlew assemble

# Clean build
./gradlew clean
```

### Testing
```bash
# Run all unit tests
./gradlew test

# Run unit tests for debug variant
./gradlew testDebugUnitTest

# Run connected tests (requires device/emulator)
./gradlew connectedDebugAndroidTest

# Run all checks (tests + lint)
./gradlew check
```

### Code Quality
```bash
# Run lint checks
./gradlew lint

# Run lint on specific variant
./gradlew lintDebug

# Apply automatic lint fixes
./gradlew lintFix

# Check code formatting (Spotless)
./gradlew spotlessCheck

# Apply code formatting
./gradlew spotlessApply
```

### Running Tests for Specific Modules
```bash
# Test a specific module
./gradlew :domain:test
./gradlew :data:test
./gradlew :app:testDebugUnitTest
```

## Project Architecture

### Module Structure

The project follows a clean architecture approach with clear separation of concerns:

**Core Modules:**
- `:app` - Main application module, contains UI screens, dependency injection, and application entry point
- `:domain` - Business logic layer with use cases (interactors) and domain models
- `:data` - Data layer with repositories and data sources
- `:core:common` - Common utilities and shared code
- `:core:archive` - Archive file handling (CBZ, ZIP, etc.)
- `:core-metadata` - Metadata extraction and processing

**Presentation Modules:**
- `:presentation-core` - Core presentation layer components, shared UI logic
- `:presentation-widget` - Android widgets and app shortcuts

**Source Modules:**
- `:source-api` - API definitions for content sources (extensions)
- `:source-local` - Local content source implementation

**Internationalization:**
- `:i18n` - Manga-related translations (Moko Resources)
- `:i18n-aniyomi` - Anime-related translations (Moko Resources)

**Other:**
- `:macrobenchmark` - Performance benchmarking tests
- `buildSrc` - Build logic and custom Gradle plugins

### Package Structure

The main application code follows this namespace pattern:

- `eu.kanade.tachiyomi.*` - Main app package (legacy namespace from Tachiyomi)
- `eu.kanade.tachiyomi.ui.*` - UI screens and components
- `eu.kanade.tachiyomi.data.*` - Data layer implementations
- `eu.kanade.tachiyomi.di.*` - Dependency injection modules
- `eu.kanade.domain.*` - Domain layer (use cases and models)
- `mihon.*` - Build logic and utilities (from Mihon fork)
- `tachiyomi.domain.*` - Pure domain models in domain module
- `aniyomi.*` - Anime-specific utilities

### Key Architectural Patterns

**Clean Architecture Layers:**
1. **Presentation** (`app/ui`, `presentation-core`) - Jetpack Compose screens, ViewModels
2. **Domain** (`domain`) - Interactors (use cases), pure business logic, domain models
3. **Data** (`data`) - Repositories, data sources, database access (SQLDelight)

**Dual Content Types:**
The app handles both anime and manga, so many features have parallel implementations:
- `domain/entries/anime` and `domain/entries/manga`
- `domain/download/anime` and `domain/download/manga`
- `domain/extension/anime` and `domain/extension/manga`

**Dependency Injection:**
The app uses Koin or similar DI framework (modules in `app/src/main/java/eu/kanade/tachiyomi/di/`).

### Version Catalogs

Dependencies are managed through Gradle version catalogs:
- `gradle/kotlinx.versions.toml` - Kotlin and KotlinX libraries
- `gradle/androidx.versions.toml` - AndroidX libraries
- `gradle/compose.versions.toml` - Jetpack Compose dependencies
- `gradle/aniyomi.versions.toml` - Aniyomi-specific libraries

### Build Variants

- **debug** - Debug build with `.dev` suffix, includes pseudo-locales
- **release** - Production build with optional code shrinking (ProGuard)
- **preview** - Release configuration with debug signing for testing
- **benchmark** - Profileable build for performance testing

### Build Configuration

Custom build logic in `buildSrc` includes:
- `mihon.android.application` - Application module configuration
- `mihon.android.application.compose` - Compose-specific setup
- Version info helpers: `getCommitCount()`, `getGitSha()`, `getBuildTime()`

### Important Files

- `app/shortcuts.xml` - Android app shortcuts configuration
- `app/proguard-rules.pro` - ProGuard/R8 rules for release builds
- `local.properties` - Local SDK path (not committed to git)

## Development Workflow

### Making Changes to Extensions API
Changes to `:source-api` affect all extensions. Ensure backward compatibility or coordinate with extension developers.

### Working with Translations
Translations are managed externally via Weblate. The i18n modules use Moko Resources which generate Kotlin code from resource files. Don't manually edit generated files.

### Database Changes
The project uses SQLDelight. Schema files are in the `data` module. After modifying `.sq` files, rebuild to regenerate Kotlin code.

### Compose UI Development
The app uses Jetpack Compose. UI code is primarily in `app/src/main/java/eu/kanade/tachiyomi/ui/` and `presentation-core`.

### Testing Strategy
- Unit tests for domain logic in `:domain/src/test/`
- App-level tests in `:app/src/test/`
- Connected tests for Android-specific functionality
- Benchmark tests in `:macrobenchmark/` for performance

## Fork-Specific Notes

This is the **Tadami** fork with these customizations:
- Application ID changed to `com.tadami.aurora`
- **Aurora UI** theme implementation
- Focused on UI/UX improvements over upstream Aniyomi

When syncing with upstream Aniyomi, be careful to preserve Tadami-specific UI changes and branding.
