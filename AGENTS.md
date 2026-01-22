# AGENTS.md

Agent-facing guidance for the Tadami (Aniyomi fork) repository.
Prefer repository conventions over generic Kotlin guidance.
Primary references: `CLAUDE.md`, `.editorconfig`, and `buildSrc/src/main/kotlin/mihon.code.lint.gradle.kts`.

## Project Snapshot
- App: Android, Kotlin, Gradle (Kotlin DSL).
- UI: Jetpack Compose with Aurora theme.
- Architecture: multi-module clean architecture (app, domain, data, core, presentation).
- DB: SQLDelight; schema files live in the data module.
- i18n: Moko Resources (generated Kotlin code).
- Namespace roots: `eu.kanade.tachiyomi.*`, `eu.kanade.domain.*`, `tachiyomi.domain.*`, `aniyomi.*`.

## Build / Run / Test
All commands are run from repo root.

### Build
```bash
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew assemblePreview
./gradlew assemble
./gradlew clean
```

### Tests
```bash
./gradlew test
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
./gradlew check
```

### Single Module / Single Variant Tests
```bash
./gradlew :domain:test
./gradlew :data:test
./gradlew :app:testDebugUnitTest
```

### Lint / Formatting
```bash
./gradlew lint
./gradlew lintDebug
./gradlew lintFix
./gradlew spotlessCheck
./gradlew spotlessApply
```

## Code Style
Formatting is enforced by ktlint (IntelliJ style) via Spotless.

### Formatting
- Indent: 4 spaces for Kotlin, 4 spaces for XML, 2 spaces default elsewhere.
- Max line length: 120 for Kotlin.
- Always use spaces (no tabs), trim trailing whitespace, end with newline.
- Trailing commas are allowed and commonly used in multiline lists.
- Use IntelliJ/Android Studio formatter; avoid manual alignment.

### Imports
- No star imports (editorconfig sets huge threshold).
- Keep imports sorted and grouped by the IDE formatter.
- Avoid unused imports; ktlint/spotless will fail on them.

### Kotlin Conventions
- Prefer `val` over `var`; use `var` only when mutation is required.
- Prefer expression bodies for small functions, but keep readability first.
- Use named arguments for long parameter lists and when passing literals.
- Use data classes for immutable models and sealed interfaces/classes for state.
- Favor extension functions for helpers tied to a single type or screen.
- Prefer `when` for exhaustive branching on sealed types or enums.
- Avoid magic numbers; introduce constants in companion objects or files.

### Naming
- Classes/objects: UpperCamelCase.
- Functions/variables: lowerCamelCase.
- Constants: UPPER_SNAKE_CASE (top-level or companion).
- Compose functions may use UpperCamelCase with `@Composable` (ktlint allows).
- File names mirror the primary type or screen.

### Compose / UI
- Use `@Composable` for UI functions; keep side effects in `LaunchedEffect` or flows.
- Prefer stateless UI composables with state hoisted to screen models.
- Keep `Modifier` parameters defaulted (often `Modifier = Modifier`).
- Use theme colors/typography from `AuroraTheme`/`MaterialTheme`.
- Keep UI in `app/src/main/java/eu/kanade/tachiyomi/ui/` and `presentation-*` modules.

### Coroutines / Flows
- Use structured concurrency (`viewModelScope`, `lifecycleScope`) and dispatchers from common utilities.
- Prefer `Flow` for streams; collect via `collectAsState` in Compose.
- Avoid blocking calls on the main thread; use `withContext`/`launchIO` helpers.

### Error Handling
- Handle errors at module boundaries (data and domain) and surface user-friendly UI states.
- Prefer explicit error states (sealed UI models) over throwing from UI code.
- Use `try/catch` around network/IO operations; log and recover when possible.
- Avoid swallowing exceptions silently; log with context.

### Logging
- Use the `logcat` helper (`tachiyomi.core.common.util.system.logcat`) instead of `Log`.
- Include meaningful tags/messages; avoid noisy logs in hot paths.

### Preferences / DI
- Use `PreferenceStore` wrappers for settings (see `BasePreferences`).
- DI uses Injekt/Koin style modules; follow existing patterns in `app/.../di`.

### Resources / i18n
- Moko Resources generate Kotlin; do not edit generated files.
- Prefer string resources (`MR.strings.*`) over hardcoded UI text.

### Database / SQLDelight
- Schema files live under the `data` module.
- After modifying `.sq` files, rebuild to regenerate Kotlin code.

### XML
- XML uses 4-space indentation and is formatted by Spotless.
- Excludes build outputs and certain Moko resource paths from XML formatting.

## AI Tooling Rules
- No `.cursorrules` or `.cursor/rules/*` files found.
- No `.github/copilot-instructions.md` found.
- If new agent rules are added, update this file.

## Module Boundaries
- `:app` contains UI screens, DI, app entry points.
- `:domain` contains interactors and pure models; avoid Android dependencies.
- `:data` contains repositories, sources, and SQLDelight access.
- `:presentation-core` shares UI logic/components; keep it Android-free where possible.
- `:source-api` changes affect extensions; keep backward compatible.

## Testing Notes
- Unit tests live under `:domain/src/test` and `:app/src/test`.
- Instrumentation tests use `connectedDebugAndroidTest` and need a device.
- Macrobenchmark tests live in `:macrobenchmark`.
- Prefer adding unit tests for domain logic; keep UI tests targeted.

## Quality Checklist
- Run `./gradlew spotlessCheck` before pushing formatting-heavy changes.
- Run `./gradlew lint` when modifying UI or Android resources.
- Run module tests for the areas you touched.
- Avoid editing generated or build output files.

## Notes for Agents
- Respect Aurora theme customizations when syncing upstream Aniyomi changes.
- Use existing names and patterns for manga/anime parallel features.
- Keep modifications scoped; prefer adding new helpers to the right module.
- If unsure, search for a similar screen and follow its structure.

## Contact
- For help, see `CONTRIBUTING.md` (Discord link and contribution notes).
