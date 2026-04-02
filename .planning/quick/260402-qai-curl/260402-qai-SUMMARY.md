# Quick task 260402-qai: Diagnose curl page turn speed - Summary

## Root Cause

`NovelPageTurnPreset.animationDurationMillis` was computed from the speed setting, but curl edge navigation still called `PageCurlState.prev()/next()` with the library default animation, so the visible duration never changed.

## Fix

Added a custom page-curl animation block that uses the preset duration for both edge taps and curl chapter handoff.

## Verification

- `./gradlew compileDebugKotlin`
- `./gradlew :app:testDebugUnitTest --tests "eu.kanade.presentation.reader.novel.NovelReaderUiVisibilityTest"`
- `./gradlew assembleDebug`
