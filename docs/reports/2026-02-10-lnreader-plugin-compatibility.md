# Compatibility Check: `lnreader-plugins` Live Smoke vs `ranobe-aniyomi`

Date: 2026-02-10  
Scenario: live network smoke using handler-presence detection plus generic HTTP checks (`popular -> search -> novel -> chapters -> chapterText` stage model).

## Inputs

- Plugin index: `D:\lnreader\INDREADER\lnreader-plugins\.dist\plugins.min.json`
- Compiled plugin scripts: `D:\lnreader\INDREADER\lnreader-plugins\.js\plugins`
- Live smoke report JSON: `docs/reports/compat-data/lnreader-plugins-live-smoke.json`
- Override candidate JSON: `docs/reports/compat-data/lnreader-plugins-override-candidates.json`

## Before/After Summary

- Before (earlier static compatibility check report): total `251`, compatible `251`, incompatible `0`.
- After (regenerated live smoke on 2026-02-10): total plugins `251`, passed all stages `0`, failed plugins `251`.
- Aggregation note: `failureCodes` in the live smoke summary are counted once per plugin per code (deduplicated), not per stage occurrence.

## Live Smoke Summary (Regenerated)

- Stage failures:
  - `popular`: `110`
  - `search`: `250`
  - `novel`: `1`
  - `chapters`: `0`
  - `chapterText`: `0`
- Top failure codes (`failureCodes` in live smoke artifact):
  - Semantics: deduplicated per plugin+code pair (single plugin can contribute at most `1` count to a given code, even if multiple stages emit that code).
  - Because of this deduplication, these values will not equal the raw sum of `plugins[*].stages[*].code` occurrences.
  - `request_failed`: `141`
  - `network_error`: `76`
  - `invalid_json`: `44`
  - `request_timeout`: `5`
  - `invalid_search_url`: `1`
- Top skip codes (aggregated from plugin stage skip statuses):
  - `novel_unavailable`: `251`
  - `chapters_unavailable`: `251`
  - `search_unavailable`: `250`

## Override Candidates + Reviewed Application Decision

- Generated candidate file: `docs/reports/compat-data/lnreader-plugins-override-candidates.json`
- Candidate summary:
  - `totalPlugins`: `251`
  - `candidateCount`: `0`
  - `skippedExistingOverrides`: `2`
- Reviewed decision:
  - No new safe override entries were produced by the candidate generator.
  - `app/src/main/assets/novel-plugin-overrides.json` was not modified in this task.

## Commands Used

```bash
node tools/compat/live-smoke-lnreader-plugins.js --index "D:\lnreader\INDREADER\lnreader-plugins\.dist\plugins.min.json" --plugins-dir "D:\lnreader\INDREADER\lnreader-plugins\.js\plugins" --output "docs/reports/compat-data/lnreader-plugins-live-smoke.json" --concurrency 8 --timeout-ms 15000

node tools/compat/generate-override-candidates.js --live-smoke "docs/reports/compat-data/lnreader-plugins-live-smoke.json" --existing-overrides "app/src/main/assets/novel-plugin-overrides.json" --output "docs/reports/compat-data/lnreader-plugins-override-candidates.json"

node --test tools/compat/generate-override-candidates.test.js

.\gradlew.bat :app:testDebugUnitTest --tests "eu.kanade.tachiyomi.extension.novel.runtime.NovelPluginScriptOverridesApplierTest"

.\gradlew.bat :app:testDebugUnitTest --tests "eu.kanade.tachiyomi.extension.novel.runtime.NovelDomainAliasResolverTest"
```
