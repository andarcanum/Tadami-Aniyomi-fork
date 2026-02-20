# Build Warnings Troubleshooting (Release/R8)

## Repro Command

```bash
./gradlew :app:minifyReleaseWithR8 --stacktrace --rerun-tasks
```

## Toolchain Snapshot (current)

- Gradle wrapper: `8.13` (`gradle/wrapper/gradle-wrapper.properties`)
- AGP: `8.10.0` (`gradle/androidx.versions.toml`)
- Kotlin: `2.2.0` (`gradle/kotlinx.versions.toml`)
- AboutLibraries Gradle plugin: `13.2.1` (`gradle/libs.versions.toml`)
- AboutLibraries runtime UI library: `11.6.3` (`gradle/libs.versions.toml`)

## Warning Groups and Status

1. Missing class warnings (optional APIs)
- Source: transitive references from `androidx.window`, OkHttp GraalVM integration, and jsoup jspecify annotations.
- Status: handled via targeted `-dontwarn` rules in `app/proguard-rules.pro`.

2. Field-rule warning (`"<1>$*"`)
- Source: transitive consumer rule from
  `.../META-INF/com.android.tools/r8/kotlinx-serialization-common.pro`.
- Status: treated as external/transitive rule warning; accepted as a single known warning (`field_rule <= 1`).

3. Kotlin metadata parsing warning spam
- Message: `An error occurred when parsing kotlin metadata...`
- Source: R8/Kotlin metadata compatibility gap with AGP `8.9.0` + Kotlin `2.2.0`.
- Status: fixed after toolchain alignment by upgrading AGP to `8.10.0`.
- Reference: https://developer.android.com/studio/build/kotlin-d8-r8-versions

4. Global warning suppression
- Source: `-ignorewarnings` in `app/proguard-android-optimize.txt`.
- Status: removed; release minify still succeeds with targeted warning handling only.

5. Gradle problem report noise from project plugins
- Source A (fixed): legacy AboutLibraries `collectDependencies*` tasks (variant-resolution ambiguity and `Task.project` execution-time access).
- Fix: upgraded AboutLibraries Gradle plugin to `13.2.1` while keeping runtime AboutLibraries UI dependency at `11.6.3` to avoid `compileSdk` uplift requirements.
- Source B (fixed): `:app:prepareReleaseShortcutXML` from `com.github.zellius.shortcut-helper`.
- Fix: removed shortcut-helper plugin and moved shortcuts declaration to static resource `app/src/main/res/xml/shortcuts.xml`.
- Remaining: 2 Gradle deprecation entries from AGP internals (`com.android.internal.application` boolean `is-` properties during `:buildSrc:generatePrecompiledScriptPluginAccessors`). These are external/plugin-side and not project-code fixable.

## Current Baseline (2026-02-20)

From (latest full rerun):

```bash
./gradlew :app:minifyReleaseWithR8 --stacktrace --rerun-tasks
python tools/ci/report-release-warnings.py r8_minify_release_final_after_gradle_warning_fixes.log --enforce
```

Expected counts:
- `kotlin_metadata=0`
- `field_rule=1`
- `context_receivers_warning=0`
- `aapt_non_positional_format=0`
- `missing_class=0`

Gradle Problems report (`build/reports/problems/problems-report.html`):
- `totalProblemCount=2`
- both entries are AGP-internal `declaring-an-is-property-with-a-boolean-type` deprecations
- no `:app:collectDependencies*`, no `dependency-variant-resolution`, no `:app:prepareReleaseShortcutXML`

## Verification Checklist

After changing ProGuard/R8 config, run:

```bash
./gradlew :app:minifyReleaseWithR8 --stacktrace --rerun-tasks
python tools/ci/report-release-warnings.py <log-file> --enforce
```

Check that:
- no unexpected new `Missing class ...` groups appear;
- existing field-rule warning stays at `<= 1`;
- metadata warning count remains `0`.
- Gradle problems report does not include app/plugin tasks previously fixed (`collectDependencies*`, `prepareReleaseShortcutXML`).

CI integration:
- `.github/workflows/build_pull_request.yml` and `.github/workflows/build_push.yml`
  run `report-release-warnings.py --enforce` on the `assembleRelease` log.
