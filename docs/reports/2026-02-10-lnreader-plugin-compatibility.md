# Compatibility Check: `lnreader-plugins` Live Smoke vs `ranobe-aniyomi`

Date: 2026-02-11  
Scenario: live network smoke using handler-presence detection plus generic HTTP checks (`popular -> search -> novel -> chapters -> chapterText` stage model).

## Inputs

- Plugin index: `D:\lnreader\INDREADER\lnreader-plugins\.dist\plugins.min.json`
- Compiled plugin scripts: `D:\lnreader\INDREADER\lnreader-plugins\.js\plugins`
- Static compatibility report: `docs/reports/compat-data/lnreader-plugins-compat.json`
- Live smoke report: `docs/reports/compat-data/lnreader-plugins-live-smoke.json`
- Override candidate report: `docs/reports/compat-data/lnreader-plugins-override-candidates.json`
- HTTP fixture set (top-20 targets): `docs/reports/compat-data/http-fixtures-top20/`

## Static Compatibility Summary (251 plugins)

- `totalEntries`: `251`
- `compatiblePluginCount`: `251`
- `incompatiblePluginCount`: `0`
- Contract stage failures (`popular/search/novel/chapters/chapterText`): all `0`
- Notes:
  - `pluginsUsingFetchProto`: `1` (`wuxiaworld`)
  - `entriesMissingSha256`: `251` (metadata quality issue in source index, not runtime blocker)

## Live Smoke Summary (Regenerated 2026-02-11)

- `totalPlugins`: `251`
- `passedAllStages`: `0`
- `failedPlugins`: `251`
- Stage failures:
  - `popular`: `107`
  - `search`: `250`
  - `novel`: `1`
  - `chapters`: `0`
  - `chapterText`: `0`
- Top failure codes:
  - `request_failed`: `146`
  - `network_error`: `72`
  - `invalid_json`: `43`
  - `request_timeout`: `5`
  - `invalid_search_url`: `1`

Interpretation: runtime-contract compatibility is complete, but broad live-network reliability remains dominated by external website behavior, anti-bot restrictions, and endpoint variance.

## Override Candidates + Review

- Candidate file: `docs/reports/compat-data/lnreader-plugins-override-candidates.json`
- Summary:
  - `totalPlugins`: `251`
  - `candidateCount`: `0`
  - `skippedExistingOverrides`: `2`
- Active reviewed overrides remain in `app/src/main/assets/novel-plugin-overrides.json` (`rulate`, `erolate`).

## HTTP Fixtures (Top-20 Targets)

- Target id list: `docs/reports/compat-data/http-fixture-top20-ids.json`
- Collection script: `tools/compat/collect-http-fixtures.js`
- Result: fixtures written for 20 plugins into `docs/reports/compat-data/http-fixtures-top20/` with per-plugin `manifest.json` and global `fixtures-summary.json`.

## Commands Used

```bash
node tools/compat/check-lnreader-plugins.js --index "D:\lnreader\INDREADER\lnreader-plugins\.dist\plugins.min.json" --plugins-dir "D:\lnreader\INDREADER\lnreader-plugins\.js\plugins" --output "docs/reports/compat-data/lnreader-plugins-compat.json"

node tools/compat/live-smoke-lnreader-plugins.js --index "D:\lnreader\INDREADER\lnreader-plugins\.dist\plugins.min.json" --plugins-dir "D:\lnreader\INDREADER\lnreader-plugins\.js\plugins" --output "docs/reports/compat-data/lnreader-plugins-live-smoke.json" --concurrency 8 --timeout-ms 15000

node tools/compat/generate-override-candidates.js --live-smoke "docs/reports/compat-data/lnreader-plugins-live-smoke.json" --existing-overrides "app/src/main/assets/novel-plugin-overrides.json" --output "docs/reports/compat-data/lnreader-plugins-override-candidates.json"

node tools/compat/collect-http-fixtures.js --index "D:\lnreader\INDREADER\lnreader-plugins\.dist\plugins.min.json" --output-dir "docs/reports/compat-data/http-fixtures-top20" --ids "<top20 ids csv>" --query "love" --timeout-ms 15000
```
