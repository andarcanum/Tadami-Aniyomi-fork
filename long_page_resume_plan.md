# Long Page Resume Plan

- [x] Reproduce and narrow down the issue: restore happens only after first manual scroll.
- [x] Replace short attempt-based retry with duration-based retry so restore does not stop before long page fully settles.
- [x] Harden pending restore target resolution by chapter id + page index to avoid stale adapter-position mismatches.
- [x] Require a strong ready signal (decoded image callback or sustained READY fallback) before clearing pending restore.
- [x] Add regression test for scenario: slight scroll on long page, exit, reopen, page height changes with screen size, restore lands on same relative position.
- [x] Add regression tests for settle/clear policy to prevent early or missing finalization.
- [x] Run targeted unit tests for `MangaReaderProgressCodecTest`.
- [x] Build debug APK for on-device validation.
