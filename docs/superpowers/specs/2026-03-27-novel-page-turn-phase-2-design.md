# Novel Page Turn Phase 2 Design

**Date:** 2026-03-27

**Goal:** Implement real `Book` and `Curl` page-turn styles for the novel reader's page mode while preserving the existing pagination, progress restore, chapter navigation, and rich-native text pipeline.

## Problem

Phase 1 introduced:

- `Page transition style`
- a hybrid engine split between compose pager transitions and a future page-turn renderer
- `Book` and `Curl` as user-facing styles

At the moment, `Book` and `Curl` still route through a placeholder renderer that falls back to `Slide`. Phase 2 should replace that placeholder with a real page-turn engine and expose only the right amount of tuning to the user.

## Design Choice

Phase 2 will use a compose-native page curl library as the base rendering engine for `Book` and `Curl`.

Recommended base:

- `oleksandrbalan/pagecurl`

Why this direction:

- it fits the existing Compose-based novel reader
- it can render the current page-mode content contract without rewriting pages into OpenGL textures
- it keeps progress, pagination, rich-native rendering, and reader UI ownership in the existing reader layer

The older OpenGL `PageFlip` route is intentionally not chosen for this phase because it would add a much heavier rendering boundary and a much higher integration cost for a Compose-first reader.

## User-Facing Behavior

The existing `Page transition style` setting keeps the same values:

- `Instant`
- `Slide`
- `Depth`
- `Book`
- `Curl`

Behavior:

- `Instant`, `Slide`, `Depth` continue to use `ComposePagerPageRenderer`
- `Book`, `Curl` use a real `PageTurnPageRenderer`

`Book` and `Curl` are both first-class styles in this phase. Neither should be treated as a placeholder or experimental fallback-only mode.

## Settings

Phase 2 adds three page-turn tuning settings:

- `Page turn speed`
- `Turn intensity`
- `Shadow intensity`

These settings are shared by both `Book` and `Curl`.

They do not expose the full raw library surface. Instead, they modify higher-level presets.

### Visibility Rules

The page-turn tuning section is hidden unless `Book` or `Curl` is selected.

When visible, the section is collapsed by default.

This keeps the normal page-mode settings light for users who only want standard transitions.

### Preset Semantics

`Book` preset:

- softer curl geometry
- calmer motion
- lower default visual aggression
- more paper-like feel

`Curl` preset:

- stronger page bend
- deeper shadows
- more expressive back-page effect
- more dramatic page-turn motion

The three shared tuning settings adjust those presets without replacing them.

## Architecture

`NovelReaderScreen` remains the owner of reader behavior:

- chapter loading
- page preparation
- progress restore and persistence
- fullscreen UI state
- tap navigation
- previous/next chapter navigation
- fallback decisions

Renderer split:

- `ComposePagerPageRenderer`
  - `Instant`
  - `Slide`
  - `Depth`
- `PageTurnPageRenderer`
  - `Book`
  - `Curl`

`PageTurnPageRenderer` must consume the existing normalized page-mode content contract rather than inventing a separate chapter pipeline.

This means the page-turn engine should receive:

- the ordered page list
- the current page index
- callbacks for page changes
- callbacks for toggle UI, move backward, move forward, previous chapter, next chapter

The renderer must remain a UI engine, not a second reader.

## Page-Turn Engine Contract

The page-turn engine should map the normalized novel page list into a page-curl presentation without changing the meaning of progress.

Key requirements:

- the active page index must remain compatible with existing page-mode progress storage
- programmatic moves from tap-zones must still work
- boundary chapter switching must still work
- rich-native and plain page content must both render through the same normalized page content model

The renderer may internally maintain library-specific state, but the source of truth stays in the reader layer.

## Fallback Rules

Fallback stays explicit and reader-owned.

If `Book` or `Curl` is selected but the page-turn renderer cannot safely run, the active route falls back to `Slide`.

Possible fallback triggers:

- unsupported runtime/device behavior
- renderer initialization failure
- unsupported integration state

This phase should not fall back just because the chapter is rich text. The goal is to preserve the current page-mode contract for both plain and rich page content.

## UI Changes

Files expected to own most of the user-facing changes:

- `NovelReaderSettingsDialog`
- `SettingsNovelReaderScreen`
- `NovelPageTransitionStyleUi`
- `NovelReaderPreferences`
- `strings.xml` resources if additional labels or summaries are needed

The page-turn tuning controls should appear under the transition-style area and use a collapsed section pattern.

## Implementation Shape

Phase 2 should introduce:

- a real `PageTurnPageRenderer`
- a small page-turn preset/config model for `Book` and `Curl`
- preference-backed values for:
  - speed
  - intensity
  - shadow intensity
- UI controls for those values
- adapter logic between novel page content and the chosen page-curl library

The renderer should preserve current behavior for:

- tap zones
- chapter edge switching
- reader UI toggling
- progress restore
- re-pagination after settings changes

## Testing

Phase 2 should add or update tests for:

- style-to-engine routing for `Book` and `Curl`
- preset resolution for the two styles
- tuning value round-trips in preferences
- collapsed-section visibility rules
- fallback behavior
- progress index stability when using page-turn styles

Manual verification focus:

- fluidity of drag gesture
- chapter switching at boundaries
- tap zone compatibility
- restore position after reopen
- behavior on both plain and rich-native paged chapters

## Non-Goals

Phase 2 does not aim to:

- expose every low-level page-curl library parameter
- add multiple independent curl engines
- redesign the existing page pagination pipeline
- replace compose pager transitions for `Instant`, `Slide`, or `Depth`

## Outcome

After this phase:

- `Book` and `Curl` are real page-turn modes
- users can tune them with a small, high-signal control set
- tuning stays out of the way unless explicitly needed
- the novel reader keeps a single page-mode content and progress model across all transition engines
