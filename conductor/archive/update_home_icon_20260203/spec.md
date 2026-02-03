# Specification: Update Home Icon Style & Animation

## Overview
Update the "Home" icon in the main navigation bar to align with the visual style and implementation pattern of neighboring tabs (Browse, Updates, History). The new icon will feature a "Filled vs Outlined" state transition, animated via an Android `animated-vector` resource, ensuring a cohesive and polished user experience.

## Functional Requirements
1.  **Icon Asset:**
    -   Create a new `animated-vector` drawable resource (e.g., `anim_home_enter.xml`).
    -   The icon must display a "Filled" Home icon when the tab is active/selected.
    -   The icon must display an "Outlined" Home icon when the tab is inactive/unselected.
    -   The transition between states should be animated (e.g., a crossfade or morph).

2.  **UI Integration:**
    -   Update `HomeHubTab.kt` to replace the static `rememberVectorPainter(Icons.Filled.Home)` with `rememberAnimatedVectorPainter`.
    -   Load the new `anim_home_enter` resource.
    -   Ensure the icon state (selected/unselected) is correctly passed to the painter to trigger the animation.

## Non-Functional Requirements
-   **Consistency:** The implementation must strictly follow the pattern used in `BrowseTab.kt`, `UpdatesTab.kt`, and `HistoriesTab.kt`.
-   **Performance:** The animation should be lightweight and not impact navigation performance.
-   **Visuals:** The icon style (stroke width, size) must match the existing Material Design 3 icons used in the app.

## Acceptance Criteria
-   [ ] The Home tab icon is "Filled" when the user is on the Home screen.
-   [ ] The Home tab icon is "Outlined" when the user is on any other screen (e.g., Library, Updates).
-   [ ] Tapping the Home tab triggers a smooth animation from Outlined to Filled.
-   [ ] Navigating away from the Home tab triggers a smooth animation from Filled to Outlined.
-   [ ] The code follows the project's existing patterns for animated icons.
