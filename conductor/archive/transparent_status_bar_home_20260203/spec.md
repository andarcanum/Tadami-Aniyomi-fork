# Specification: Transparent Status Bar on Home Screen

## Overview
The user desires to make the status bar transparent or background-less specifically on the "Home" screen. This aligns with the behavior already present on other screens (like "More"), creating a more immersive, full-screen visual experience where the content (or background) extends behind the status bar.

## Functional Requirements
1.  **Transparent Status Bar:** The status bar on the Home screen must be transparent.
2.  **Edge-to-Edge Display:** The content of the Home screen (specifically the background) must draw behind the status bar area to create a seamless look.
3.  **Scope:** This change applies *only* to the Home screen (HomeHub), as it is already implemented on other screens.

## Non-Functional Requirements
-   **Visual Consistency:** The implementation should match the transparency and behavior seen on the "More" screen.
-   **Readability:** Ensure that status bar icons (time, battery, etc.) remain legible against the Home screen background.

## Acceptance Criteria
-   [ ] Launching the app and viewing the Home screen shows the background extending behind the status bar.
-   [ ] The status bar itself has no solid background color.
-   [ ] Navigating to other tabs and back to Home maintains the correct status bar appearance.
