# Specification: Fix AniList Large Cover 404 Error

## Context
When the AniList metadata source is enabled, fetching the "large" cover image (poster) sometimes results in a 404 error. This happens because not all entries have a large image variant available, or the URL provided is invalid.

## Goal
Ensure that the application gracefully handles 404 errors when fetching AniList cover images by falling back to a valid image URL provided by the API (e.g., "medium" or standard size).

## Requirements
1.  **Intercept 404s:** Detect 404 errors specifically when requesting AniList cover images.
2.  **Fallback Mechanism:** If the large image fails, retry with the next best available quality (medium/default) from the AniList API response.
3.  **UI Feedback:** Ensure the user does not see a broken image icon if a fallback is successfully loaded.
4.  **Logging:** Log instances where the fallback was triggered for debugging purposes.

## Out of Scope
-   Caching improvements (unless directly related to the fix).
-   UI changes to the cover image display component itself.

## Acceptance Criteria
-   [ ] When a large cover image URL returns 404, the app automatically attempts to load the fallback image.
-   [ ] The fallback image is displayed correctly in the UI.
-   [ ] If both large and fallback images fail, the standard error placeholder is shown.
