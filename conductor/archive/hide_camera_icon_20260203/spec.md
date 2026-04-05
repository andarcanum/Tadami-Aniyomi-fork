# Specification: Hide Camera Icon on Avatar Set

## Overview
Currently, a camera icon is displayed overlaid on the user's avatar on the Home/Hub screen, indicating that the avatar can be changed. The goal of this track is to improve the UI by hiding this camera icon once the user has successfully set a custom avatar image. This will provide a cleaner look for personalized profiles.

## Functional Requirements
1.  **Avatar State Detection:** The system must detect whether a custom avatar image has been set by the user.
2.  **Icon Visibility:**
    -   **If no custom avatar is set (default state):** The camera icon MUST be visible overlaid on the default avatar placeholder.
    -   **If a custom avatar IS set:** The camera icon MUST be hidden.
3.  **Interaction:** The avatar itself (image or placeholder) must remain clickable to trigger the image selection/change process, regardless of whether the camera icon is visible.

## Non-Functional Requirements
-   **UI Consistency:** The transition or state change should be instant and reflect immediately upon setting the avatar.
-   **Performance:** The check for the avatar state should be efficient and not cause any UI lag on the Home screen.

## Acceptance Criteria
-   [ ] When the app is fresh or no avatar is set, the camera icon is visible on the Home screen avatar.
-   [ ] Upon selecting and setting a new avatar image, the camera icon disappears immediately.
-   [ ] The avatar remains clickable to change the image even when the camera icon is hidden.
-   [ ] If the user removes the avatar (if such functionality exists or is added) or resets it, the camera icon reappears.
