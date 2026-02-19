# Build Warnings Fix Plan

## Context

This file records the release build warnings observed on 2026-02-19 so we can fix them in a separate pass.

Main command used:

```bash
./gradlew :app:minifyReleaseWithR8 --stacktrace --rerun-tasks
```

## Collected Warning Groups

1. Kotlin metadata parsing warning in R8:
   - `An error occurred when parsing kotlin metadata...`
2. Missing class warnings from optional dependencies:
   - `androidx.window.extensions.*`
   - `androidx.window.sidecar.*`
   - `com.oracle.svm.core.annotate.*`
   - `org.graalvm.nativeimage.hosted.*`
   - `org.jspecify.annotations.NullMarked`
3. Rule warning:
   - `The type "<1>$*" is used in a field rule...`

## Fix Plan (High Level)

1. Confirm toolchain compatibility
   - Check Kotlin/AGP/R8 compatibility matrix and align versions if needed.
   - Re-run `:app:minifyReleaseWithR8` and verify metadata warnings drop.

2. Clean up missing-class noise from optional APIs
   - Add explicit `-dontwarn` rules for optional runtime-only classes (Window Extensions/Sidecar, GraalVM/SVM, jspecify), scoped to known packages.
   - Keep rules narrow and documented in `app/proguard-rules.pro` or release-specific ProGuard file.

3. Validate suspicious field rule warning
   - Identify exact source of `"<1>$*"` rule from transitive consumer rules.
   - If harmless, document it; if not, override with safer keep rule.

4. Revisit current global warning suppression
   - Audit `-ignorewarnings` usage.
   - Replace blanket suppression with targeted `-dontwarn` where possible.

5. Add release guardrails
   - Add/adjust CI or local release check command to run R8 minification and fail on new critical warnings.
   - Keep warning baseline documented to detect regressions.

## Done Criteria

- Release minify task completes without unexpected missing-class spam.
- Kotlin metadata warning is resolved or justified with pinned compatible versions.
- ProGuard configuration has targeted, documented rules (no blind suppression).
- A short troubleshooting note is added for future upgrades.
