# Quick task 260402-qwi: Keep short page blocks together - Summary

## Root Cause

`a503894f1` removed the guard that skipped starting a block on the current page when that block would fit on a fresh page. That let the last paragraph line get packed against the bottom edge instead of moving cleanly to the next page.

## Fix

Restored the fit guard in both plain and rich page pagination paths.
Added regression tests that prove a whole block stays together when it fits on the next page.

## Verification

- `./gradlew :app:testDebugUnitTest --tests "eu.kanade.presentation.reader.novel.NovelReaderUiVisibilityTest"`
- `./gradlew assembleDebug`

## Commit

pending
