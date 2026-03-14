# Coding Conventions

**Analysis Date:** 2026-03-14

## Naming Patterns

**Files:**
- PascalCase for class/object/interface files: `NovelRepository.kt`, `NetworkToLocalNovel.kt`
- Lowercase with underscores for utility files: N/A (not observed)
- Test files: `<ClassName>Test.kt` pattern: `NovelTest.kt`, `NetworkToLocalNovelTest.kt`

**Packages:**
- Hierarchical by layer: `tachiyomi.domain.entries.novel.model`, `tachiyomi.data.entries.novel`
- Prefix indicates module: `tachiyomi.*` (core/domain/data), `eu.kanade.*` (app/presentation)

**Functions:**
- camelCase: `getNovelById`, `await`, `partialUpdateNovel`
- Use descriptive verbs: `get`, `insert`, `update`, `delete`, `fetch`
- Coroutine entry points use `await` suffix: `getNovelById()`, `await()` pattern

**Variables/Properties:**
- camelCase: `novelId`, `libraryFlow`, `testDispatcher`
- Nullable types use descriptive names: `existing`, `persisted` (not `maybeNovel`)

**Types/Classes:**
- PascalCase: `Novel`, `NovelRepository`, `NetworkToLocalNovel`
- Interfaces often use suffix: `Repository`, `Source`, `Interactor`
- Model suffix for domain models: `NovelModel`, `LibraryNovel`

## Code Style

**Formatting:**
- Tool: ktlint (via Spotless)
- Config: `.editorconfig` - 4-space indentation for Kotlin
- Max line length: 120 characters
- Intellij IDEA style: `ktlint_code_style = intellij_idea`

**Linting Rules (disabled in .editorconfig):**
- `ktlint_standard_class-signature = disabled`
- `ktlint_standard_function-expression-body = disabled`
- `ktlint_standard_function-signature = disabled`
- `ktlint_standard_discouraged-comment-location = disabled`
- `ktlint_function_naming_ignore_when_annotated_with = Composable`

**Trailing Commas:**
- Enabled: `ij_kotlin_allow_trailing_comma = true`
- Enabled for call site: `ij_kotlin_allow_trailing_comma_on_call_site = true`

**Star Imports:**
- High threshold: `ij_kotlin_name_count_to_use_star_import = 2147483647`
- Member imports also use high threshold

## Import Organization

**Order (observed pattern):**
1. Kotlin standard library: `kotlinx.coroutines.flow.Flow`
2. Android/Compose: `android.content.Context`, `androidx.compose.runtime.Immutable`
3. Domain models: `tachiyomi.domain.entries.novel.model.Novel`
4. Repository interfaces: `tachiyomi.domain.entries.novel.repository.NovelRepository`
5. Data layer: `tachiyomi.data.entries.novel.NovelRepositoryImpl`
6. External libraries: `io.kotest.matchers.shouldBe`, `io.mockk.mockk`
7. Logcat: `logcat.LogPriority`, `logcat.logcat`

## Error Handling

**Repository Pattern:**
- Return `Boolean` for operations that can fail: `updateNovel(): Boolean`
- Try-catch with logging for silent failures:
```kotlin
override suspend fun updateNovel(update: NovelUpdate): Boolean {
    return try {
        partialUpdateNovel(update)
        true
    } catch (e: Exception) {
        logcat(LogPriority.ERROR, e)
        false
    }
}
```

**Domain Layer:**
- Throw explicit exceptions for unrecoverable errors:
```kotlin
throw IllegalStateException(
    "Failed to insert novel for source=${novel.source}, url=${novel.url}",
)
```

**UI Layer:**
- Use `Throwable.formattedMessage()` extension for user-friendly error display
- Context-specific error messages via `when` on exception type:
```kotlin
when (this) {
    is HttpException -> return appContext.stringResource(MR.strings.exception_http, code)
    is UnknownHostException -> { ... }
    is NoChaptersException, is NoEpisodesException -> { ... }
}
```

## Logging

**Framework:** logcat (square/logcat)

**Usage:**
```kotlin
import logcat.LogPriority
import logcat.logcat

logcat(LogPriority.ERROR, e) { "Failed to update novel: ${e.message}" }
```

**Priority Levels:**
- `LogPriority.ERROR` - Failures that need attention
- `LogPriority.WARN` - Recoverable issues
- `LogPriority.DEBUG` - Debug information

## Comments

**When to Comment:**
- Complex business logic decisions
- Non-obvious workarounds
- TODO comments for technical debt: `// TODO: refactor this`
- KDoc on public API: `@Immutable data class Novel(...)`

**JSDoc/TSDoc:**
- Use for public domain models and repository interfaces
- Compose functions use `@Composable` annotation (automatically documented)

## Function Design

**Size:** Small, single-responsibility functions

**Parameters:**
- Constructor injection for dependencies: `class NetworkToLocalNovel(private val novelRepository: NovelRepository)`
- Suspend functions for async operations
- Avoid nullable primitives; use sentinel values or Optional pattern

**Return Values:**
- Domain models for successful operations
- Nullable (`?`) when absence is valid
- `Boolean` for operations with success/failure only

## Module Design

**Exports:**
- Single class per file (mostly)
- No explicit `internal` observed - defaults to visible within module

**Barrel Files:**
- Not observed - imports use full paths

## Data Classes

**Domain Models:**
- Use `@Immutable` annotation from Compose for all domain models
- Use `data class` for all models
- Factory method pattern via companion object `create()`:
```kotlin
@Immutable
data class Novel(...) {
    companion object {
        fun create() = Novel(...)
    }
}
```

**Database Models:**
- Separate from domain models
- Use adapters for column mapping

## Architecture Patterns

**Clean Architecture Layers:**
- **Domain:** Interactors, models, repository interfaces
- **Data:** Repository implementations, database handlers, mappers
- **Presentation:** ScreenModels, UI components

**Dependency Direction:**
- Domain has no dependencies on other layers
- Data implements domain repository interfaces
- Presentation depends on domain

---

*Convention analysis: 2026-03-14*
