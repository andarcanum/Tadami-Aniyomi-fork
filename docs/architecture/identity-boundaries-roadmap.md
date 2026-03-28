# Identity & Boundaries Cleanup Roadmap

This note captures the remaining high-value cleanup work in the order it should be done.
The goal is to finish the visible identity and boundary work without turning it into a full repository-wide rename.

## Step 1: Keep the public identity and docs consistent

What to do:
- Keep `README.md` aligned with the current app name, version, and module map.
- Keep `codebase-index.md` aligned with the current dependency versions and the real module layout.
- Keep `app/build.gradle.kts` explicit about the `namespace` / `applicationId` relationship.

Why this comes first:
- It removes the most obvious mismatch a new contributor sees.
- It keeps the rest of the cleanup work anchored to a single source of truth.

How to verify:
- Run `./gradlew assembleDebug`
- Confirm the build still succeeds after docs and metadata edits.

## Step 2: Keep the stack-boundaries note tracked

What to do:
- Keep `docs/architecture/stack-boundaries.md` in a tracked location.
- Make sure it stays focused on the mixed-stack reality:
  - Compose-first UI surface
  - legacy View / Fragment bridge surfaces
  - JS runtime split

Why this matters:
- The repo needs one short place that explains why some old APIs still exist.
- Without this note, the bridge code keeps looking accidental.

How to verify:
- Run `./gradlew assembleDebug`
- Confirm the new note does not introduce any build drift.

## Step 3: Mark the bridge points in code

What to do:
- Keep short ownership comments in the explicit bridge files:
  - `ReaderActivity.kt`
  - `PlayerActivity.kt`
  - `SettingsSecurityScreen.kt`
  - `MangaDownloadAdapter.kt`
  - `NovelJsRuntime.kt`
  - `NovelJsPromiseShim.kt`

Why this comes before deeper refactors:
- It makes the legacy surfaces obvious to future maintainers.
- It prevents these files from quietly accumulating more old-stack dependencies.

How to verify:
- Run `./gradlew compileDebugKotlin`
- Confirm the bridge comments do not change behavior.

## Step 4: Narrow `core/common`

What to do:
- Keep `core/common` focused on shared primitives only.
- Avoid exposing dependency details wider than necessary.
- Keep Rx and OkHttp helpers behind the smallest practical surface.

Why this is a high-value cleanup:
- `core/common` is the easiest place for accidental coupling to spread.
- If this module stays narrow, the rest of the codebase gets easier to reason about.

How to verify:
- Run `./gradlew compileDebugKotlin`
- Confirm the shared-core boundary still compiles with the narrower exposure.

## Step 5: Keep the novel JS/runtime boundary explicit

What to do:
- Keep the runtime choice hidden behind `NovelJsRuntime.kt`.
- Keep `NovelJsPromiseShim.kt` close to that boundary.
- Do not let engine-specific details leak upward into the app layer.

Why this is separate from the core cleanup:
- The JS/runtime setup is a special-case compatibility layer.
- It should stay deliberate and isolated, not spread through the rest of the app.

How to verify:
- Run `./gradlew compileDebugKotlin`
- Confirm the novel runtime still works through the narrow entrypoint.

## Step 6: Stop before a broad package rename

What to do:
- Do not expand this cleanup into a full rename of internal `data` / `domain` packages.
- Do not rename SQLDelight generated package names unless a later task truly needs it.

Why stop here:
- The visible identity work is already done.
- The remaining internal naming debt is real, but the cost of a full rename is much higher than the practical benefit.

How to verify:
- Run `./gradlew assembleDebug`
- Confirm the repo stays healthy after the cleanup pass.

## Final Rule

If the build is green, the docs are consistent, and the bridge surfaces are explicit, stop.
Any deeper rename work should be treated as a separate project, not a continuation of this one.
