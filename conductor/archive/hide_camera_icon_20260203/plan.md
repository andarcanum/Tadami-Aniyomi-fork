# Implementation Plan - Hide Camera Icon on Avatar Set

## Phase 1: Logic Implementation
- [x] Task: Modify `HomeHubTab.kt` to conditionally render the camera icon.
    - [x] Locate the `Box` composable containing the avatar image and the camera icon in `HomeHubTab.kt` (specifically within the `HomeHubScreen` composable).
    - [x] Identify the state variable holding the user's avatar URL (likely `state.userAvatar`).
    - [x] Wrap the `Box` containing the camera icon in an `if` condition that checks if `state.userAvatar` is empty or null.
    - [x] Ensure the click listener on the parent `Box` (or the avatar image) remains intact so the user can still change the avatar.
    - [x] [checkpoint: e89ac42]

## Phase 2: Verification
- [ ] Task: Manual Verification
    - [ ] Launch the app with a fresh install or cleared data (no avatar set).
    - [ ] Verify the camera icon is visible.
    - [ ] Click the avatar and set a custom image.
    - [ ] Verify the camera icon disappears immediately.
    - [ ] Click the avatar again (without the icon) and verify the image picker still opens.

- [ ] Task: Conductor - User Manual Verification 'Verification' (Protocol in workflow.md)