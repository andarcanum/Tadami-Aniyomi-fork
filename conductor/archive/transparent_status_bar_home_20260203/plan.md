# Implementation Plan - Transparent Status Bar on Home Screen

## Phase 1: Investigation & Logic Implementation
- [x] Task: Investigate existing transparent status bar implementation.
    - [x] Check `MoreTab.kt` or `SettingsScreen.kt` to see how the edge-to-edge/transparent status bar is currently implemented.
    - [x] Look for usages of `WindowInsets`, `systemBars`, or specific scaffold configurations.
- [x] Task: Apply transparency to `HomeHubTab.kt`.
    - [x] Open `app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeHubTab.kt`.
    - [x] Locate the main container composable (likely `HomeHubScreen` or `TabbedScreenAurora`).
    - [x] Modify the configuration to allow drawing behind the system bars (specifically the status bar).
    - [x] Ensure `contentPadding` or `WindowInsets` are handled correctly so interactive elements don't overlap with the status bar, but the background does.
    - [x] [checkpoint: b0f5859]

## Phase 2: Refinement (User Feedback)
- [ ] Task: Add padding to Home screen content.
    - [ ] The user reported that elements are too close to the status bar (overlap).
    - [ ] Modify `HomeHubTab.kt` -> `HomeHubScreen`.
    - [ ] Add a `Spacer` with `Modifier.windowInsetsTopHeight(WindowInsets.statusBars)` as the first item in the `LazyColumn`.
    - [ ] This will push content down initially but allow it to scroll behind the status bar.

## Phase 3: Verification
- [ ] Task: Manual Verification
    - [ ] Build and install the app.
    - [ ] Open the Home screen.
    - [ ] Verify the status bar is transparent and the background extends to the top edge of the screen.
    - [ ] Verify that the greeting/avatar/content starts *below* the status bar (no overlap).
    - [ ] Scroll up and verify the content goes *behind* the status bar.

- [ ] Task: Conductor - User Manual Verification 'Verification' (Protocol in workflow.md)
