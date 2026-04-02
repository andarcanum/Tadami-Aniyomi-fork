# Quick task 260402-qwi: Keep short page blocks together - Summary

## Root Cause

My first fix overcorrected by moving whole blocks to the next page. The actual requirement is to keep pagination line-level: fill the page as much as possible, then let overflow lines continue on the next page.

## Fix

Removed the whole-block fit guard and kept the existing line-level slice pagination.
Kept regression coverage for paragraph continuation and page assembly.

## Verification

- `./gradlew :app:testDebugUnitTest --tests "eu.kanade.presentation.reader.novel.NovelReaderUiVisibilityTest"`
- `./gradlew assembleDebug`

## Commit

pending
