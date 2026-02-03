# Implementation Plan - Update Home Icon Style & Animation

## Phase 1: Asset Creation
- [x] Task: Create `anim_home_enter.xml`
    - [x] Create the vector drawable for the "Filled" Home icon state.
    - [x] Create the vector drawable for the "Outlined" Home icon state.
    - [x] Define the `animated-vector` resource combining both states with a transition.
    - [x] Verify the resource is placed in `app/src/main/res/drawable/`.
    - [x] [checkpoint: 52a1b3c]

## Phase 2: UI Implementation
- [x] Task: Update `HomeHubTab.kt`
    - [x] Import `androidx.compose.animation.graphics.res.animatedVectorResource`.
    - [x] Import `androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter`.
    - [x] Update `options` to check `LocalTabNavigator.current.current` for selection state.
    - [x] Replace `rememberVectorPainter` with `rememberAnimatedVectorPainter`.
    - [x] Load `R.drawable.anim_home_enter`.
    - [x] [checkpoint: 8f9e0d1]

## Phase 3: Verification
- [x] Task: Manual Verification
    - [x] Build and run the app.
    - [x] Verify the Home icon is "Filled" on the Home tab.
    - [x] Verify the Home icon is "Outlined" when navigating to Updates or Browse.
    - [x] Confirm the animation plays correctly during transitions.

- [x] Task: Conductor - User Manual Verification 'Verification' (Protocol in workflow.md)