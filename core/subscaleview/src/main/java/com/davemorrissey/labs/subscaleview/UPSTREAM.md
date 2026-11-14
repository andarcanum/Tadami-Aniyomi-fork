# Source-pinned Mihon image viewer

Upstream: https://github.com/mihonapp/subsampling-scale-image-view/tree/72a0537151a5514b2c15b8b6ee7308d684291c42

Apache-2.0; license included in app/src/main/assets/licenses/mihon-ssiv-LICENSE.txt. Original authors: Dave Morrissey and Tachiyomi/Mihon contributors. Imported 2026-11-09.

Only the Java/Kotlin view, image-source, state and provider surface is vendored. This is not a new reader: the public view API, gestures and image-coordinate model are retained.

Local changes (keep small and review when rebasing):
- extracted `:core:subscaleview` module `R` / resource declaration; no separate native crop library.
- shared core NativeImageDecoder adapter (imagedecoder18, deterministic lifecycle, bounded border analysis).
- explicit external viewport/display scale for webtoon parent transforms; bounded base preview and fine tiles.
- stale async decode ownership must be rejected/recycled.

Do not blindly update from main, add reflective access, or bring back old libimagedecoder.so alongside the new artifact. All nontrivial local changes need focused regression coverage.
