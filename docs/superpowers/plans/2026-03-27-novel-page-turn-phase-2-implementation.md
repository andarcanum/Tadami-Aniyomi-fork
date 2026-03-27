# Novel Page Turn Phase 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement real `Book` and `Curl` page-turn styles for the novel reader using a compose-native page curl engine, plus a small collapsed tuning section for speed, turn intensity, and shadow intensity.

**Architecture:** Keep `NovelReaderScreen` as the single owner of page preparation, progress, and chapter navigation. Replace the placeholder `PageTurnPageRenderer` with a real page-turn engine adapter for `Book/Curl`, while leaving `ComposePagerPageRenderer` responsible for `Instant/Slide/Depth`. Add preference-backed tuning values and expose them only inside a collapsed `Page Turn Tuning` UI section when `Book` or `Curl` is selected.

**Tech Stack:** Kotlin, Jetpack Compose, Compose Foundation Pager, `oleksandrbalan/pagecurl`, Gradle Kotlin DSL, moko resources, JUnit debug unit tests.

---

## File Map

### Existing files to modify

- `app/build.gradle.kts`
  Adds the page-curl dependency if it is not already represented in the version catalog.
- `gradle/libs.versions.toml`
  Adds the library version and alias for the chosen compose page curl dependency.
- `app/src/main/java/eu/kanade/tachiyomi/ui/reader/novel/setting/NovelReaderPreferences.kt`
  Adds new preference keys, defaults, enums, and settings resolution fields for page-turn tuning.
- `app/src/main/java/eu/kanade/presentation/reader/novel/NovelReaderScreen.kt`
  Keeps renderer routing, actual fallback decisions, and page-turn config wiring at the reader layer.
- `app/src/main/java/eu/kanade/presentation/reader/novel/PageTurnPageRenderer.kt`
  Replaces placeholder slide fallback rendering with the real page-turn engine adapter.
- `app/src/main/java/eu/kanade/presentation/reader/novel/NovelReaderSettingsDialog.kt`
  Adds the collapsed tuning section to quick settings.
- `app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsNovelReaderScreen.kt`
  Adds the collapsed tuning section to global reader settings.
- `app/src/main/java/eu/kanade/presentation/reader/novel/NovelPageTransitionStyleUi.kt`
  Adds summaries and labels for `Book/Curl` tuning if needed.
- `i18n-aniyomi/src/commonMain/moko-resources/base/strings.xml`
  Adds strings for the new section and settings.
- `i18n-aniyomi/src/commonMain/moko-resources/ru/strings.xml`
  Adds Russian strings for the same labels.
- `app/src/test/java/eu/kanade/presentation/reader/novel/NovelReaderUiVisibilityTest.kt`
  Adds routing, UI visibility, collapsed-section, and preset behavior tests.
- `app/src/test/java/eu/kanade/tachiyomi/ui/reader/novel/setting/NovelReaderPreferencesTest.kt`
  Adds preference round-trip coverage for the new tuning values.
- `app/src/test/java/eu/kanade/tachiyomi/ui/reader/novel/NovelReaderScreenModelTest.kt`
  Adds regression tests proving page-mode progress stays stable across page-turn styles.

### New files to create

- `app/src/main/java/eu/kanade/presentation/reader/novel/NovelPageTurnPreset.kt`
  Focused preset/config translation layer between high-level reader settings and the page curl library.
- `app/src/main/java/eu/kanade/presentation/reader/novel/NovelPageTurnTuningUi.kt`
  Small reusable UI for the collapsed tuning section used by both quick settings and global settings.

---

### Task 1: Add the Page Curl Dependency

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Write the failing compile expectation into the plan**

Expected failure after wiring the future renderer references:

```text
Unresolved reference to PageCurl or PageCurlState
```

- [ ] **Step 2: Add the version catalog entry**

Add entries similar to:

```toml
pagecurl = "1.5.1"
compose-pagecurl = { module = "io.github.oleksandrbalan:pagecurl", version.ref = "pagecurl" }
```

- [ ] **Step 3: Add the app dependency**

In `app/build.gradle.kts`, add:

```kotlin
implementation(libs.compose.pagecurl)
```

- [ ] **Step 4: Run compile to verify dependency resolution**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: dependency resolves, no unresolved import failures caused by the new library alias

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "[reader] add novel page curl dependency"
```

### Task 2: Add Preference Models for Page-Turn Tuning

**Files:**
- Modify: `app/src/main/java/eu/kanade/tachiyomi/ui/reader/novel/setting/NovelReaderPreferences.kt`
- Test: `app/src/test/java/eu/kanade/tachiyomi/ui/reader/novel/setting/NovelReaderPreferencesTest.kt`

- [ ] **Step 1: Write failing preference tests**

Add tests similar to:

```kotlin
@Test
fun `page turn tuning preferences round trip persisted values`() {
    preferences.pageTurnSpeed().set(NovelPageTurnSpeed.FAST)
    preferences.pageTurnIntensity().set(NovelPageTurnIntensity.HIGH)
    preferences.pageTurnShadowIntensity().set(NovelPageTurnShadowIntensity.LOW)

    assertEquals(NovelPageTurnSpeed.FAST, preferences.pageTurnSpeed().get())
    assertEquals(NovelPageTurnIntensity.HIGH, preferences.pageTurnIntensity().get())
    assertEquals(NovelPageTurnShadowIntensity.LOW, preferences.pageTurnShadowIntensity().get())
}
```

- [ ] **Step 2: Run the targeted preference tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferencesTest"`
Expected: FAIL because new enums/preferences do not yet exist

- [ ] **Step 3: Add enums and preference accessors**

Add compact enums like:

```kotlin
enum class NovelPageTurnSpeed { SLOW, NORMAL, FAST }
enum class NovelPageTurnIntensity { LOW, MEDIUM, HIGH }
enum class NovelPageTurnShadowIntensity { LOW, MEDIUM, HIGH }
```

Add preference accessors similar to existing enum-backed transition style storage:

```kotlin
fun pageTurnSpeed() = preferenceStore.getEnum("novel_reader_page_turn_speed", NovelPageTurnSpeed.NORMAL)
fun pageTurnIntensity() = preferenceStore.getEnum("novel_reader_page_turn_intensity", NovelPageTurnIntensity.MEDIUM)
fun pageTurnShadowIntensity() = preferenceStore.getEnum("novel_reader_page_turn_shadow_intensity", NovelPageTurnShadowIntensity.MEDIUM)
```

- [ ] **Step 4: Thread the values through resolved settings**

Add fields into the resolved settings data objects and override resolution paths so the current reader state can consume them.

- [ ] **Step 5: Run the preference tests again**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferencesTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/eu/kanade/tachiyomi/ui/reader/novel/setting/NovelReaderPreferences.kt app/src/test/java/eu/kanade/tachiyomi/ui/reader/novel/setting/NovelReaderPreferencesTest.kt
git commit -m "[reader] add novel page turn tuning preferences"
```

### Task 3: Introduce a High-Level Preset Translation Layer

**Files:**
- Create: `app/src/main/java/eu/kanade/presentation/reader/novel/NovelPageTurnPreset.kt`
- Test: `app/src/test/java/eu/kanade/presentation/reader/novel/NovelReaderUiVisibilityTest.kt`

- [ ] **Step 1: Write failing preset tests**

Add tests proving:

```kotlin
@Test
fun `book and curl resolve to distinct page turn presets`() { /* ... */ }

@Test
fun `page turn tuning values modify preset output without changing selected style`() { /* ... */ }
```

- [ ] **Step 2: Run the targeted tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "eu.kanade.presentation.reader.novel.NovelReaderUiVisibilityTest"`
Expected: FAIL for unresolved preset builder

- [ ] **Step 3: Create the preset/config model**

Create focused data like:

```kotlin
internal data class NovelPageTurnPreset(
    val animationDurationMillis: Int,
    val curlStrength: Float,
    val shadowAlpha: Float,
    val backPageAlpha: Float,
)
```

- [ ] **Step 4: Add preset resolution helpers**

Implement helpers that map:

- `Book` -> softer defaults
- `Curl` -> stronger defaults
- tuning values -> bounded modifications on top of the style preset

- [ ] **Step 5: Run the tests again**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "eu.kanade.presentation.reader.novel.NovelReaderUiVisibilityTest"`
Expected: PASS for the new preset tests

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/eu/kanade/presentation/reader/novel/NovelPageTurnPreset.kt app/src/test/java/eu/kanade/presentation/reader/novel/NovelReaderUiVisibilityTest.kt
git commit -m "[reader] add novel page turn presets"
```

### Task 4: Replace the Placeholder PageTurn Renderer

**Files:**
- Modify: `app/src/main/java/eu/kanade/presentation/reader/novel/PageTurnPageRenderer.kt`
- Modify: `app/src/main/java/eu/kanade/presentation/reader/novel/NovelReaderScreen.kt`
- Test: `app/src/test/java/eu/kanade/presentation/reader/novel/NovelReaderUiVisibilityTest.kt`

- [ ] **Step 1: Write failing routing/fallback tests**

Add tests proving:

```kotlin
@Test
fun `book and curl do not resolve to slide fallback when page turn renderer is available`() { /* ... */ }

@Test
fun `page turn renderer progress contract stays on page index semantics`() { /* ... */ }
```

- [ ] **Step 2: Run the targeted tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "eu.kanade.presentation.reader.novel.NovelReaderUiVisibilityTest"`
Expected: FAIL because the placeholder still falls back to `Slide`

- [ ] **Step 3: Implement a real page-turn renderer**

Replace the placeholder body with a `PageCurl`-based renderer that:

- consumes the normalized `contentPages`
- uses the reader-owned current page as the source of truth
- maps current page changes back to reader callbacks
- renders each page using the same plain/rich block composables used by page mode

- [ ] **Step 4: Keep reader-owned fallback decisions explicit**

In `NovelReaderScreen.kt`, preserve route selection and fallback policy at the screen layer so renderer failures can still fall back to `Slide`.

- [ ] **Step 5: Preserve tap zones and chapter edge switching**

Wire:

- `onToggleUi`
- `onMoveBackward`
- `onMoveForward`
- `onOpenPreviousChapter`
- `onOpenNextChapter`

so page-turn styles keep the same behavioral contract as current page mode.

- [ ] **Step 6: Run the targeted tests again**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "eu.kanade.presentation.reader.novel.NovelReaderUiVisibilityTest"`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/eu/kanade/presentation/reader/novel/PageTurnPageRenderer.kt app/src/main/java/eu/kanade/presentation/reader/novel/NovelReaderScreen.kt app/src/test/java/eu/kanade/presentation/reader/novel/NovelReaderUiVisibilityTest.kt
git commit -m "[reader] implement novel page turn renderer"
```

### Task 5: Preserve Progress Semantics Across Book and Curl

**Files:**
- Modify: `app/src/main/java/eu/kanade/presentation/reader/novel/NovelReaderScreen.kt`
- Test: `app/src/test/java/eu/kanade/tachiyomi/ui/reader/novel/NovelReaderScreenModelTest.kt`

- [ ] **Step 1: Write failing progress regression tests**

Add tests proving:

```kotlin
@Test
fun `page turn styles keep page reader progress stable across reopen`() { /* ... */ }

@Test
fun `page turn styles keep restore independent from native and web progress`() { /* ... */ }
```

- [ ] **Step 2: Run the targeted tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreenModelTest"`
Expected: FAIL if new route changes progress semantics

- [ ] **Step 3: Make the page-turn engine use page index semantics only**

Ensure the active page reported by the curl engine is converted back into the same page index contract already used by page mode.

- [ ] **Step 4: Run the model tests again**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreenModelTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/eu/kanade/presentation/reader/novel/NovelReaderScreen.kt app/src/test/java/eu/kanade/tachiyomi/ui/reader/novel/NovelReaderScreenModelTest.kt
git commit -m "[reader] preserve novel page turn progress semantics"
```

### Task 6: Add Reusable Collapsed Tuning UI

**Files:**
- Create: `app/src/main/java/eu/kanade/presentation/reader/novel/NovelPageTurnTuningUi.kt`
- Modify: `app/src/main/java/eu/kanade/presentation/reader/novel/NovelReaderSettingsDialog.kt`
- Modify: `app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsNovelReaderScreen.kt`
- Test: `app/src/test/java/eu/kanade/presentation/reader/novel/NovelReaderUiVisibilityTest.kt`

- [ ] **Step 1: Write failing UI visibility tests**

Add tests proving:

```kotlin
@Test
fun `page turn tuning section is hidden for compose pager styles`() { /* ... */ }

@Test
fun `page turn tuning section is collapsed by default for book and curl`() { /* ... */ }
```

- [ ] **Step 2: Run the targeted tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "eu.kanade.presentation.reader.novel.NovelReaderUiVisibilityTest"`
Expected: FAIL because the tuning section does not exist yet

- [ ] **Step 3: Create the reusable collapsed section composable**

Add a focused composable that accepts:

- current style
- current tuning values
- expanded/collapsed local UI state
- callbacks to update preferences

- [ ] **Step 4: Integrate into quick settings**

Show the section only when style is `Book` or `Curl`, collapsed by default.

- [ ] **Step 5: Integrate into global settings**

Mirror the same visibility and collapsed behavior in the settings screen.

- [ ] **Step 6: Run the UI tests again**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "eu.kanade.presentation.reader.novel.NovelReaderUiVisibilityTest"`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/eu/kanade/presentation/reader/novel/NovelPageTurnTuningUi.kt app/src/main/java/eu/kanade/presentation/reader/novel/NovelReaderSettingsDialog.kt app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsNovelReaderScreen.kt app/src/test/java/eu/kanade/presentation/reader/novel/NovelReaderUiVisibilityTest.kt
git commit -m "[reader] add collapsed novel page turn tuning controls"
```

### Task 7: Add Strings and UI Labels

**Files:**
- Modify: `i18n-aniyomi/src/commonMain/moko-resources/base/strings.xml`
- Modify: `i18n-aniyomi/src/commonMain/moko-resources/ru/strings.xml`
- Modify: `app/src/main/java/eu/kanade/presentation/reader/novel/NovelPageTransitionStyleUi.kt`

- [ ] **Step 1: Add the required string keys**

Add labels and summaries for:

- `Page Turn Tuning`
- `Page turn speed`
- `Turn intensity`
- `Shadow intensity`
- collapsed/expanded summary text if needed

- [ ] **Step 2: Update the transition style UI helpers**

Ensure `Book` and `Curl` summaries remain accurate once they are real renderers rather than placeholders.

- [ ] **Step 3: Run compile verification**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: PASS with no missing moko resource references

- [ ] **Step 4: Commit**

```bash
git add i18n-aniyomi/src/commonMain/moko-resources/base/strings.xml i18n-aniyomi/src/commonMain/moko-resources/ru/strings.xml app/src/main/java/eu/kanade/presentation/reader/novel/NovelPageTransitionStyleUi.kt
git commit -m "[reader] add novel page turn tuning strings"
```

### Task 8: Add Reader-Level Fallback Coverage

**Files:**
- Modify: `app/src/main/java/eu/kanade/presentation/reader/novel/NovelReaderScreen.kt`
- Test: `app/src/test/java/eu/kanade/presentation/reader/novel/NovelReaderUiVisibilityTest.kt`

- [ ] **Step 1: Write failing fallback tests**

Add tests for cases like:

```kotlin
@Test
fun `page turn renderer falls back to slide on explicit unsupported route`() { /* ... */ }
```

- [ ] **Step 2: Run the targeted tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "eu.kanade.presentation.reader.novel.NovelReaderUiVisibilityTest"`
Expected: FAIL until fallback conditions are reader-owned and explicit

- [ ] **Step 3: Implement the reader-level fallback path**

Keep fallback in `NovelReaderScreen.kt`, not inside the renderer, so route and UI state stay predictable.

- [ ] **Step 4: Run the tests again**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "eu.kanade.presentation.reader.novel.NovelReaderUiVisibilityTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/eu/kanade/presentation/reader/novel/NovelReaderScreen.kt app/src/test/java/eu/kanade/presentation/reader/novel/NovelReaderUiVisibilityTest.kt
git commit -m "[reader] keep novel page turn fallback explicit"
```

### Task 9: Run Focused Regression Verification

**Files:**
- No code changes expected

- [ ] **Step 1: Run reader unit tests**

Run:

```bash
./gradlew.bat :app:testDebugUnitTest --tests "eu.kanade.presentation.reader.novel.NovelReaderUiVisibilityTest" --tests "eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreenModelTest" --tests "eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferencesTest"
```

Expected: PASS

- [ ] **Step 2: Run Kotlin compile verification**

Run:

```bash
./gradlew.bat :app:compileDebugKotlin
```

Expected: PASS

- [ ] **Step 3: Run debug APK assembly**

Run:

```bash
./gradlew.bat :app:assembleDebug
```

Expected: PASS

- [ ] **Step 4: Commit verification-only updates if needed**

Only commit if verification surfaced and required tiny follow-up fixes.

### Task 10: Manual QA Checklist

**Files:**
- No code changes expected

- [ ] **Step 1: Verify `Book` render path**

Check:

- page drag is fluid
- page turn looks softer than `Curl`
- progress restores correctly after reopen

- [ ] **Step 2: Verify `Curl` render path**

Check:

- page drag is fluid
- curl geometry and shadow feel stronger than `Book`
- chapter edge switching still works

- [ ] **Step 3: Verify tuning controls**

Check:

- section hidden for `Instant/Slide/Depth`
- section visible and collapsed by default for `Book/Curl`
- changing speed/intensity/shadow immediately affects rendering

- [ ] **Step 4: Verify compatibility**

Check:

- plain paged chapter
- rich-native paged chapter
- tap zones
- chapter boundary swipes
- reopen and restore page index

- [ ] **Step 5: Final commit if manual QA requires tiny cleanup**

```bash
git add <files>
git commit -m "[reader] polish novel page turn qa fixes"
```
