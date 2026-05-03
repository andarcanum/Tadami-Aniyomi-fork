# Aurora Library: Swipe Category Override

**Date:** 2026-05-03
**Status:** Draft

## Summary

Add a toggle setting that overrides the horizontal swipe gesture behavior on the
Aurora library screen. When enabled, swiping left/right switches categories
within the current section instead of switching sections (Anime/Manga/Novel).

## Motivation

Currently, horizontal swipe on the Aurora library content area scrolls through
sections (Anime → Manga → Novel). Users who primarily use one section with many
categories may prefer to swipe through categories instead, keeping the current
section fixed.

## Design

### Preference

New boolean preference in `LibraryPreferences`:

- **Key:** `"aurora_swipe_switches_categories"`
- **Default:** `false`
- **Method:** `auroraSwipeSwitchesCategories(): Preference<Boolean>`

### Settings UI

In `SettingsLibraryScreen.kt`, under the existing "Aurora" preference group (next
to "Aurora immersive mode"), add a `SwitchPreference`:

| Property | Value |
|----------|-------|
| Title | `pref_aurora_swipe_categories` |
| Summary | `pref_aurora_swipe_categories_summary` |
| Preference | `libraryPreferences.auroraSwipeSwitchesCategories()` |

String resources to add:

- `pref_aurora_swipe_categories` → `"Swipe switches categories"`
- `pref_aurora_swipe_categories_summary` → `"Horizontal swipe on the library screen switches categories instead of sections"`

### TabbedScreenAurora changes

Add two optional parameters:

```kotlin
swipeOverride: Boolean = false,
onSwipeOverrideDirection: ((isLeftSwipe: Boolean) -> Unit)? = null,
```

When `swipeOverride = true`:
1. `HorizontalPager.userScrollEnabled = false` — sections stop scrolling
2. A `detectHorizontalDragGestures` pointer input detects horizontal swipes
   (reuses the same mechanic as `instantTabSwitching`)
3. On threshold-crossing swipe:
   - Left → `onSwipeOverrideDirection?.invoke(isLeftSwipe = true)`
   - Right → `onSwipeOverrideDirection?.invoke(isLeftSwipe = false)`
4. The pager page index stays unchanged

Swipe threshold and edge bounce match `instantTabSwitching` (120dp threshold,
16dp edge bounce with animation).

`TabbedScreenAurora` remains agnostic about categories — it only passes the
direction upstream.

### AnimeLibraryTab wiring

In `AnimeLibraryTab.kt`:

```kotlin
val swipeSwitchesCategories by
    libraryPreferences.auroraSwipeSwitchesCategories().collectAsState()

// Pass to TabbedScreenAurora:
TabbedScreenAurora(
    ...
    swipeOverride = swipeSwitchesCategories,
    onSwipeOverrideDirection = { isLeftSwipe ->
        handleAuroraCategorySwipe(isLeftSwipe, ...)
    },
)
```

The `handleAuroraCategorySwipe` function:
1. Resolves current section's categories from the active screen model
2. Computes new category index from swipe direction
3. Calls `onAuroraCategorySelected(newIndex)` if different from current

```kotlin
fun handleAuroraCategorySwipe(isLeftSwipe: Boolean) {
    val categories = when (auroraCurrentSection) {
        Section.Anime -> screenModelState.value?.library?.keys?.toList().orEmpty()
        Section.Manga -> mangaScreenModel.state.value?.library?.keys?.toList().orEmpty()
        Section.Novel -> novelCategoriesState
    }
    if (categories.size <= 1) return
    val currentIndex = activeCategoryIndex
    val newIndex = if (isLeftSwipe) {
        (currentIndex + 1).coerceAtMost(categories.lastIndex)
    } else {
        (currentIndex - 1).coerceAtLeast(0)
    }
    if (newIndex != currentIndex) {
        onAuroraCategorySelected(newIndex)
    }
}
```

### Category wrapping behavior

Categories do **not** wrap around. At the first category, swiping right does
nothing (edge bounce only). At the last category, swiping left does nothing.
This matches the section-switching behavior.

### Edge cases

| Case | Behavior |
|------|----------|
| Only 1 or 0 categories available | Swipe ignored (no categories to switch) |
| All categories hidden | Swipe ignored |
| Section has no content loaded yet | Categories list empty, swipe ignored |
| Toggle switched ON while mid-swipe | Next gesture uses new behavior |
| User clicks section tab while in category-swipe mode | Section changes normally (tabs remain clickable) |
| User clicks category chip while in category-swipe mode | Category changes normally (chips remain clickable) |

### Dependencies

- `LibraryPreferences.kt` — new preference method
- `TabbedScreenAurora.kt` — new parameters + gesture override
- `AnimeLibraryTab.kt` — wire preference to gesture behavior
- `SettingsLibraryScreen.kt` — settings toggle UI
- String resources — new pref title/summary

### Non-goals

- Per-section gesture configuration (the toggle is global for the Aurora library)
- Category pagination animation (category change is instant, same as clicking a chip)
- Wrap-around category navigation
