# LNReader Plugin Parity (ranobe-novel) Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make `ranobe-novel` consume `lnreader-plugins` directly and reliably (install/list/update), with measurable compatibility and a repeatable override workflow.

**Architecture:** We will unify repo index resolution around a "candidate URL list" strategy so both `plugins.min.json` (LNReader style) and `index.min.json` (legacy style) work from the same base URL. Then we will remove duplicate-entry side effects by normalizing fetched entries before update checks and listing rendering. Finally, we will harden compatibility validation tooling so reports reflect real plugin behavior instead of smoke-test false negatives.

**Tech Stack:** Kotlin (app/runtime), OkHttp, kotlinx.serialization, JUnit5 + Kotest + MockK, Node.js (`node:test`) compatibility scripts.

**Target Branch:** `ranobe-novel`

**Skill References:** `@test-driven-development`, `@verification-before-completion`

---

### Task 1: Add repo index candidate URL resolver

**Files:**
- Create: `app/src/test/java/eu/kanade/tachiyomi/extension/novel/repo/NovelPluginRepoUrlsTest.kt`
- Modify: `app/src/main/java/eu/kanade/tachiyomi/extension/novel/repo/NovelPluginRepoUrls.kt`
- Test: `app/src/test/java/eu/kanade/tachiyomi/extension/novel/repo/NovelPluginRepoUrlsTest.kt`

**Step 1: Write the failing test**

```kotlin
package eu.kanade.tachiyomi.extension.novel.repo

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelPluginRepoUrlsTest {
    @Test
    fun `base url resolves to both index candidates`() {
        resolveNovelPluginRepoIndexUrls("https://repo.example.org/") shouldBe listOf(
            "https://repo.example.org/index.min.json",
            "https://repo.example.org/plugins.min.json",
        )
    }

    @Test
    fun `json url is kept as-is`() {
        resolveNovelPluginRepoIndexUrls("https://repo.example.org/plugins.min.json") shouldBe listOf(
            "https://repo.example.org/plugins.min.json",
        )
    }
}
```

**Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "eu.kanade.tachiyomi.extension.novel.repo.NovelPluginRepoUrlsTest"`
Expected: FAIL with unresolved reference `resolveNovelPluginRepoIndexUrls`.

**Step 3: Write minimal implementation**

```kotlin
package eu.kanade.tachiyomi.extension.novel.repo

internal fun resolveNovelPluginRepoIndexUrls(baseUrl: String): List<String> {
    val normalized = baseUrl.trim().trimEnd('/')
    if (normalized.isEmpty()) return emptyList()
    if (normalized.endsWith(".json", ignoreCase = true)) return listOf(normalized)
    return listOf(
        "$normalized/index.min.json",
        "$normalized/plugins.min.json",
    )
}

internal fun resolveNovelPluginRepoIndexUrl(baseUrl: String): String {
    return resolveNovelPluginRepoIndexUrls(baseUrl).firstOrNull().orEmpty()
}
```

**Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "eu.kanade.tachiyomi.extension.novel.repo.NovelPluginRepoUrlsTest"`
Expected: PASS.

**Step 5: Commit**

```bash
git add app/src/main/java/eu/kanade/tachiyomi/extension/novel/repo/NovelPluginRepoUrls.kt
git add app/src/test/java/eu/kanade/tachiyomi/extension/novel/repo/NovelPluginRepoUrlsTest.kt
git commit -m "feat(novel): resolve both index.min and plugins.min repo urls"
```

---

### Task 2: Use URL candidates in listing flow and dedupe by plugin id

**Files:**
- Modify: `app/src/main/java/eu/kanade/tachiyomi/extension/novel/repo/NovelExtensionListingInteractor.kt`
- Modify: `app/src/test/java/eu/kanade/tachiyomi/extension/novel/repo/NovelExtensionListingInteractorTest.kt`
- Test: `app/src/test/java/eu/kanade/tachiyomi/extension/novel/repo/NovelExtensionListingInteractorTest.kt`

**Step 1: Write failing tests**

Add tests for:
1. Repo base URL fetches both `index.min.json` and `plugins.min.json`.
2. Duplicate plugin IDs from both indexes are reduced to one entry with highest version.

```kotlin
coEvery { repoService.fetch("https://example.org/index.min.json") } returns listOf(oldEntry)
coEvery { repoService.fetch("https://example.org/plugins.min.json") } returns listOf(newEntry)
...
listing.available.shouldContainExactly(newEntry)
coVerify { repoService.fetch("https://example.org/index.min.json") }
coVerify { repoService.fetch("https://example.org/plugins.min.json") }
```

**Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "eu.kanade.tachiyomi.extension.novel.repo.NovelExtensionListingInteractorTest"`
Expected: FAIL (only `index.min.json` is fetched, duplicates are not collapsed).

**Step 3: Implement listing changes**

```kotlin
val available = getExtensionRepo.getAll()
    .flatMap { repo ->
        resolveNovelPluginRepoIndexUrls(repo.baseUrl)
            .flatMap { repoService.fetch(it) }
    }
    .groupBy { it.id }
    .map { (_, entries) -> entries.maxBy { it.version } }
```

**Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "eu.kanade.tachiyomi.extension.novel.repo.NovelExtensionListingInteractorTest"`
Expected: PASS.

**Step 5: Commit**

```bash
git add app/src/main/java/eu/kanade/tachiyomi/extension/novel/repo/NovelExtensionListingInteractor.kt
git add app/src/test/java/eu/kanade/tachiyomi/extension/novel/repo/NovelExtensionListingInteractorTest.kt
git commit -m "fix(novel): fetch both repo index formats in listing and dedupe entries"
```

---

### Task 3: Use URL candidates in update check flow

**Files:**
- Modify: `app/src/main/java/eu/kanade/tachiyomi/extension/novel/api/NovelExtensionApi.kt`
- Modify: `app/src/test/java/eu/kanade/tachiyomi/extension/novel/api/NovelExtensionApiTest.kt`
- Modify: `app/src/main/java/eu/kanade/tachiyomi/extension/novel/repo/NovelPluginRepoUpdateInteractor.kt`
- Modify: `app/src/test/java/eu/kanade/tachiyomi/extension/novel/repo/NovelPluginRepoUpdateInteractorTest.kt`
- Test: `app/src/test/java/eu/kanade/tachiyomi/extension/novel/api/NovelExtensionApiTest.kt`
- Test: `app/src/test/java/eu/kanade/tachiyomi/extension/novel/repo/NovelPluginRepoUpdateInteractorTest.kt`

**Step 1: Write failing tests**

Add/adjust tests to expect `findUpdates()` called with both URLs when repo base URL is not `.json`.

```kotlin
coEvery {
    repoUpdateInteractor.findUpdates(
        listOf(
            "https://example.org/index.min.json",
            "https://example.org/plugins.min.json",
        ),
    )
} returns listOf(entry)
```

Also add interactor test that duplicate IDs across URLs return one newest update entry.

**Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "eu.kanade.tachiyomi.extension.novel.api.NovelExtensionApiTest" --tests "eu.kanade.tachiyomi.extension.novel.repo.NovelPluginRepoUpdateInteractorTest"`
Expected: FAIL (single URL behavior and duplicate results).

**Step 3: Implement minimal changes**

```kotlin
val repoUrls = getExtensionRepo.getAll()
    .flatMap { resolveNovelPluginRepoIndexUrls(it.baseUrl) }
    .distinct()
```

And in `NovelPluginRepoUpdateInteractor`:

```kotlin
val available = repoUrls
    .flatMap { repoService.fetch(it) }
    .groupBy { it.id }
    .map { (_, entries) -> entries.maxBy { it.version } }
```

**Step 4: Run tests to verify they pass**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "eu.kanade.tachiyomi.extension.novel.api.NovelExtensionApiTest" --tests "eu.kanade.tachiyomi.extension.novel.repo.NovelPluginRepoUpdateInteractorTest"`
Expected: PASS.

**Step 5: Commit**

```bash
git add app/src/main/java/eu/kanade/tachiyomi/extension/novel/api/NovelExtensionApi.kt
git add app/src/test/java/eu/kanade/tachiyomi/extension/novel/api/NovelExtensionApiTest.kt
git add app/src/main/java/eu/kanade/tachiyomi/extension/novel/repo/NovelPluginRepoUpdateInteractor.kt
git add app/src/test/java/eu/kanade/tachiyomi/extension/novel/repo/NovelPluginRepoUpdateInteractorTest.kt
git commit -m "fix(novel): include plugins.min fallback in update checks"
```

---

### Task 4: Add fallback behavior to network index fetcher

**Files:**
- Modify: `app/src/main/java/eu/kanade/tachiyomi/extension/novel/api/NetworkNovelPluginIndexFetcher.kt`
- Modify: `app/src/test/java/eu/kanade/tachiyomi/extension/novel/api/NetworkNovelPluginIndexFetcherTest.kt`
- Test: `app/src/test/java/eu/kanade/tachiyomi/extension/novel/api/NetworkNovelPluginIndexFetcherTest.kt`

**Step 1: Write failing tests**

Add test: when base URL is used and first index candidate fails with 404, fetcher retries second candidate and returns payload.

```kotlin
server.enqueue(MockResponse().setResponseCode(404))
server.enqueue(MockResponse().setBody("[]"))

val payload = fetcher.fetch(server.url("/").toString().trimEnd('/'))
payload shouldBe "[]"
server.takeRequest().path shouldBe "/plugins.min.json"
server.takeRequest().path shouldBe "/index.min.json"
```

**Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "eu.kanade.tachiyomi.extension.novel.api.NetworkNovelPluginIndexFetcherTest"`
Expected: FAIL (current fetcher makes a single request).

**Step 3: Implement fallback**

```kotlin
val candidates = if (repoUrl.trimEnd('/').endsWith(".json")) {
    listOf(repoUrl.trimEnd('/'))
} else {
    listOf(
        "${repoUrl.trimEnd('/')}/plugins.min.json",
        "${repoUrl.trimEnd('/')}/index.min.json",
    )
}
for (candidate in candidates) {
    runCatching {
        client.newCall(GET(candidate)).awaitSuccess().use { return@withContext it.body?.string().orEmpty() }
    }
}
error("Failed to fetch novel plugin index from all candidates")
```

**Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "eu.kanade.tachiyomi.extension.novel.api.NetworkNovelPluginIndexFetcherTest"`
Expected: PASS.

**Step 5: Commit**

```bash
git add app/src/main/java/eu/kanade/tachiyomi/extension/novel/api/NetworkNovelPluginIndexFetcher.kt
git add app/src/test/java/eu/kanade/tachiyomi/extension/novel/api/NetworkNovelPluginIndexFetcherTest.kt
git commit -m "fix(novel): retry alternate repo index filenames in network fetcher"
```

---

### Task 5: Reduce false negatives in live compatibility smoke tests

**Files:**
- Modify: `tools/compat/live-smoke-lnreader-plugins.js`
- Modify: `tools/compat/live-smoke-lnreader-plugins.test.js`
- Test: `tools/compat/live-smoke-lnreader-plugins.test.js`

**Step 1: Write failing tests**

Add tests that:
1. Use multiple search endpoint templates before reporting `invalid_json`/`request_failed`.
2. Mark `search` stage as skipped when plugin has no search handler instead of hard fail.

```js
assert.equal(result.stages.search.status, 'skip');
assert.equal(result.stages.search.code, 'missing_handler');
```

**Step 2: Run test to verify it fails**

Run: `node --test tools/compat/live-smoke-lnreader-plugins.test.js`
Expected: FAIL (current script hardcodes one autocomplete endpoint and fails missing handlers).

**Step 3: Implement minimal script changes**

```js
const SEARCH_PATH_TEMPLATES = [
  '/search/autocomplete?query={query}',
  '/search?query={query}',
  '/search?keyword={query}',
];

if (!hasSearch) {
  stages.search = { status: 'skip', code: 'missing_handler' };
}
```

Try templates sequentially, stop on first usable candidate list.

**Step 4: Run test to verify it passes**

Run: `node --test tools/compat/live-smoke-lnreader-plugins.test.js`
Expected: PASS.

**Step 5: Commit**

```bash
git add tools/compat/live-smoke-lnreader-plugins.js
git add tools/compat/live-smoke-lnreader-plugins.test.js
git commit -m "test(compat): reduce live smoke false negatives for lnreader plugins"
```

---

### Task 6: Regenerate compatibility artifacts and update override candidates

**Files:**
- Modify: `docs/reports/compat-data/lnreader-plugins-live-smoke.json`
- Modify: `docs/reports/compat-data/lnreader-plugins-override-candidates.json` (create if missing)
- Modify: `docs/reports/2026-02-10-lnreader-plugin-compatibility.md`
- Modify: `app/src/main/assets/novel-plugin-overrides.json` (only reviewed entries that are safe)
- Test: `tools/compat/generate-override-candidates.test.js`
- Test: `app/src/test/java/eu/kanade/tachiyomi/extension/novel/runtime/NovelPluginScriptOverridesApplierTest.kt`

**Step 1: Run compatibility scripts (baseline + new smoke)**

Run:

```bash
node tools/compat/live-smoke-lnreader-plugins.js --index "D:\lnreader\INDREADER\lnreader-plugins\.dist\plugins.min.json" --plugins-dir "D:\lnreader\INDREADER\lnreader-plugins\.js\plugins" --output "docs/reports/compat-data/lnreader-plugins-live-smoke.json" --concurrency 8 --timeout-ms 15000
node tools/compat/generate-override-candidates.js --live-smoke "docs/reports/compat-data/lnreader-plugins-live-smoke.json" --existing-overrides "app/src/main/assets/novel-plugin-overrides.json" --output "docs/reports/compat-data/lnreader-plugins-override-candidates.json"
```

Expected: both commands succeed and write JSON files.

**Step 2: Write/adjust tests first for override safety**

Add test cases for:
1. No duplicate patches after merge.
2. Existing override entries are not re-suggested.

Run: `node --test tools/compat/generate-override-candidates.test.js`
Expected: PASS.

**Step 3: Apply reviewed overrides**

Move only verified candidates into `app/src/main/assets/novel-plugin-overrides.json` (no bulk import without manual verification).

**Step 4: Verify app/runtime tests**

Run:
- `.\gradlew.bat :app:testDebugUnitTest --tests "eu.kanade.tachiyomi.extension.novel.runtime.NovelPluginScriptOverridesApplierTest"`
- `.\gradlew.bat :app:testDebugUnitTest --tests "eu.kanade.tachiyomi.extension.novel.runtime.NovelDomainAliasResolverTest"`

Expected: PASS.

**Step 5: Update report and commit**

Document before/after counts (`passedAllStages`, top failure codes, candidate overrides reviewed) in `docs/reports/2026-02-10-lnreader-plugin-compatibility.md`.

```bash
git add docs/reports/compat-data/lnreader-plugins-live-smoke.json
git add docs/reports/compat-data/lnreader-plugins-override-candidates.json
git add docs/reports/2026-02-10-lnreader-plugin-compatibility.md
git add app/src/main/assets/novel-plugin-overrides.json
git commit -m "chore(compat): refresh lnreader plugin smoke report and reviewed overrides"
```

---

### Task 7: Final verification gate before merge

**Files:**
- No code changes expected
- Verification targets:
  - `app/src/test/java/eu/kanade/tachiyomi/extension/novel/**`
  - `tools/compat/*.test.js`

**Step 1: Run focused Kotlin novel extension suite**

Run:

```bash
.\gradlew.bat :app:testDebugUnitTest --tests "eu.kanade.tachiyomi.extension.novel.api.*" --tests "eu.kanade.tachiyomi.extension.novel.repo.*" --tests "eu.kanade.tachiyomi.extension.novel.runtime.*"
```

Expected: PASS, no regressions in API/repo/runtime novel modules.

**Step 2: Run Node compatibility tests**

Run:

```bash
node --test tools/compat/check-lnreader-plugins.test.js tools/compat/live-smoke-lnreader-plugins.test.js tools/compat/generate-override-candidates.test.js
```

Expected: PASS.

**Step 3: Sanity-check changed files**

Run: `git status --short`
Expected: only intended files are modified.

**Step 4: Final commit (if verification-only adjustments were needed)**

```bash
git add -A
git commit -m "chore(novel): final verification for lnreader plugin parity"
```

