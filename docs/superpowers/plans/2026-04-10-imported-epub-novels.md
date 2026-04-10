# Imported EPUB Novels Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an `Import EPUB` flow to the novel library that creates a local novel entry with local HTML chapters and makes imported novels easy to distinguish from source-backed novels.

**Architecture:** Add a built-in `Imported EPUB` novel source backed by app-managed storage instead of user-managed folders. The library UI launches a document picker, an import pipeline parses and normalizes EPUB files into stored HTML chapters plus assets, and the new source exposes those imported entries through the existing novel/library/reader flow.

**Tech Stack:** Kotlin, Jetpack Compose, Android SAF document picker, existing Tachiyomi/Aniyomi novel domain models, SQL-backed novel/chapter repositories, `./gradlew` test/build tasks, existing archive/EPUB reader utilities.

---

## File Structure

**Create**
- `app/src/main/java/eu/kanade/tachiyomi/source/novel/ImportedEpubNovelSource.kt`
- `app/src/main/java/eu/kanade/tachiyomi/source/novel/ImportedEpubNovelConstants.kt`
- `app/src/main/java/eu/kanade/tachiyomi/source/novel/importer/ImportedEpubStorage.kt`
- `app/src/main/java/eu/kanade/tachiyomi/source/novel/importer/ImportedEpubParser.kt`
- `app/src/main/java/eu/kanade/tachiyomi/source/novel/importer/ImportedEpubImporter.kt`
- `app/src/main/java/eu/kanade/tachiyomi/source/novel/importer/model/ImportedEpubBook.kt`
- `app/src/main/java/eu/kanade/tachiyomi/source/novel/importer/model/ImportedEpubChapter.kt`
- `app/src/main/java/eu/kanade/tachiyomi/source/novel/importer/model/ImportedEpubAsset.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/library/novel/NovelLibraryImportState.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/library/novel/NovelLibraryImportCoordinator.kt`
- `app/src/test/java/eu/kanade/tachiyomi/source/novel/ImportedEpubNovelSourceTest.kt`
- `app/src/test/java/eu/kanade/tachiyomi/source/novel/importer/ImportedEpubParserTest.kt`
- `app/src/test/java/eu/kanade/tachiyomi/source/novel/importer/ImportedEpubImporterTest.kt`
- `app/src/test/java/eu/kanade/tachiyomi/ui/library/novel/NovelLibraryImportCoordinatorTest.kt`
- `app/src/test/java/eu/kanade/tachiyomi/ui/library/novel/NovelLibrarySourceFilterTest.kt`

**Modify**
- `app/src/main/java/eu/kanade/tachiyomi/source/novel/AndroidNovelSourceManager.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/library/novel/NovelLibraryScreenModel.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/library/anime/AnimeLibraryTab.kt`
- `app/src/main/java/eu/kanade/presentation/library/novel/NovelLibraryAurora.kt`
- `app/src/main/java/eu/kanade/presentation/library/novel/NovelLibraryBadgeState.kt`
- `app/src/main/java/eu/kanade/domain/DomainModule.kt`
- `i18n-aniyomi/src/commonMain/moko-resources/base/strings.xml`
- `i18n-aniyomi/src/commonMain/moko-resources/ru/strings.xml`

**Verify existing references while implementing**
- `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/novelsource/NovelSource.kt`
- `domain/src/main/java/tachiyomi/domain/entries/novel/interactor/NetworkToLocalNovel.kt`
- `source-local/src/androidMain/kotlin/tachiyomi/source/local/entries/manga/LocalMangaSource.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/entries/novel/NovelScreen.kt`

### Task 1: Verify EPUB parsing capabilities and integration constraints

**Files:**
- Verify: `core/archive/src/main/kotlin/mihon/core/archive/UniFileExtensions.kt`
- Verify: `source-local/src/androidMain/kotlin/tachiyomi/source/local/entries/manga/LocalMangaSource.kt`
- Verify: `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/novelsource/NovelSource.kt`
- Verify: `app/src/main/java/eu/kanade/tachiyomi/ui/entries/novel/NovelScreen.kt`
- Modify: `docs/superpowers/plans/2026-04-10-imported-epub-novels.md`

- [x] **Step 1: Inspect existing EPUB helpers and reader contract**

Run:
- `rtk read core/archive/src/main/kotlin/mihon/core/archive/UniFileExtensions.kt`
- `rtk read source-local/src/androidMain/kotlin/tachiyomi/source/local/entries/manga/LocalMangaSource.kt`
- `rtk read source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/novelsource/NovelSource.kt`
- `rtk grep "getChapterText|html|WebView|sanitize" app/src/main/java/eu/kanade/tachiyomi/ui/entries/novel app/src/main/java/eu/kanade/presentation/reader/novel .`

Expected: Clear answer on whether existing EPUB helpers already expose metadata, nav/spine ordering, and raw HTML chapter content suitable for novel reader consumption

- [x] **Step 2: Record the adapter decision in the plan before coding**

```text
Analysis: Existing EpubReader provides:
- Package OPF parsing for metadata (title, author, etc.)
- Spine-based chapter ordering (not nav-based)
- Image extraction from pages
- InputStream access to individual files

Missing for novel import:
- Nav document parsing for preferred chapter order
- Direct access to raw HTML content of chapters
- Asset reference extraction from HTML

Decision: Helpers are insufficient - add a thin parser adapter inside ImportedEpubParser that reads container/package/nav documents directly.
```

- [x] **Step 3: Update any affected task file lists and tests**

```text
No changes needed - ImportedEpubParser and ImportedEpubHtmlNormalizer already planned for nav/spine parsing and HTML normalization.
```

- [x] **Step 4: Sanity-check the updated plan**

Run: `rtk read docs/superpowers/plans/2026-04-10-imported-epub-novels.md`
Expected: The plan explicitly documents parser capability assumptions instead of hiding them

- [x] **Step 5: Commit**

```bash
git add docs/superpowers/plans/2026-04-10-imported-epub-novels.md
git commit -m "docs: clarify epub parser integration plan"
```

### Task 2: Lock down the importer domain model

**Files:**
- Create: `app/src/main/java/eu/kanade/tachiyomi/source/novel/importer/model/ImportedEpubBook.kt`
- Create: `app/src/main/java/eu/kanade/tachiyomi/source/novel/importer/model/ImportedEpubChapter.kt`
- Create: `app/src/main/java/eu/kanade/tachiyomi/source/novel/importer/model/ImportedEpubAsset.kt`
- Test: `app/src/test/java/eu/kanade/tachiyomi/source/novel/importer/ImportedEpubParserTest.kt`

- [ ] **Step 1: Write the failing parser model test**

```kotlin
@Test
fun `parser result keeps metadata chapters and assets together`() {
    val book = ImportedEpubBook(
        title = "Demo",
        author = "Author",
        description = "Desc",
        coverFileName = "cover.jpg",
        chapters = listOf(ImportedEpubChapter(title = "Chapter 1", sourcePath = "text/ch1.xhtml")),
        assets = listOf(ImportedEpubAsset(sourcePath = "images/cover.jpg", targetFileName = "cover.jpg")),
    )

    assertEquals("Demo", book.title)
    assertEquals(1, book.chapters.size)
    assertEquals(1, book.assets.size)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "*ImportedEpubParserTest"`
Expected: FAIL with unresolved `ImportedEpubBook` / `ImportedEpubChapter` / `ImportedEpubAsset`

- [ ] **Step 3: Write the minimal models**

```kotlin
internal data class ImportedEpubBook(
    val title: String,
    val author: String? = null,
    val description: String? = null,
    val coverFileName: String? = null,
    val chapters: List<ImportedEpubChapter>,
    val assets: List<ImportedEpubAsset>,
)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "*ImportedEpubParserTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/eu/kanade/tachiyomi/source/novel/importer/model/ app/src/test/java/eu/kanade/tachiyomi/source/novel/importer/ImportedEpubParserTest.kt
git commit -m "feat: add imported epub models"
```

### Task 3: Parse EPUB metadata and chapter order

**Files:**
- Create: `app/src/main/java/eu/kanade/tachiyomi/source/novel/importer/ImportedEpubParser.kt`
- Create: `app/src/main/java/eu/kanade/tachiyomi/source/novel/importer/ImportedEpubHtmlNormalizer.kt`
- Test: `app/src/test/java/eu/kanade/tachiyomi/source/novel/importer/ImportedEpubParserTest.kt`

- [ ] **Step 1: Write the failing parser tests**

```kotlin
@Test
fun `parser prefers navigation order and falls back to file name title`() { /* fixture-based test */ }

@Test
fun `parser falls back to spine order when nav is missing`() { /* fixture-based test */ }

@Test
fun `parser falls back to epub file name when title metadata is blank`() { /* fixture-based test */ }

@Test
fun `parser returns chapter html plus referenced assets needed by the reader`() { /* fixture-based test */ }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "*ImportedEpubParserTest"`
Expected: FAIL because parser is missing

- [ ] **Step 3: Implement the minimal parser**

```kotlin
internal class ImportedEpubParser {
    fun parse(epubUri: Uri, fallbackFileName: String): ImportedEpubBook {
        // Read package metadata, nav/spine order, chapter HTML, and referenced asset paths.
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "*ImportedEpubParserTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/eu/kanade/tachiyomi/source/novel/importer/ImportedEpubParser.kt app/src/main/java/eu/kanade/tachiyomi/source/novel/importer/ImportedEpubHtmlNormalizer.kt app/src/test/java/eu/kanade/tachiyomi/source/novel/importer/ImportedEpubParserTest.kt
git commit -m "feat: parse epub novels into import model"
```

### Task 4: Persist imported EPUB files into stable app storage

**Files:**
- Create: `app/src/main/java/eu/kanade/tachiyomi/source/novel/importer/ImportedEpubStorage.kt`
- Test: `app/src/test/java/eu/kanade/tachiyomi/source/novel/importer/ImportedEpubImporterTest.kt`

- [ ] **Step 1: Write the failing storage test**

```kotlin
@Test
fun `storage writes chapter html assets and cover into novel directory`() { /* temp-dir test */ }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "*ImportedEpubImporterTest"`
Expected: FAIL because storage helper is missing

- [ ] **Step 3: Implement the minimal storage helper**

```kotlin
internal class ImportedEpubStorage(
    private val fileSystem: FileSystem,
) {
    fun novelDirectory(novelId: Long): File = TODO()
    fun writeChapter(novelId: Long, chapterId: Long, html: String) { /* writes index.html */ }
    fun writeAsset(novelId: Long, asset: ImportedEpubAsset, bytes: ByteArray) { /* writes shared asset */ }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "*ImportedEpubImporterTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/eu/kanade/tachiyomi/source/novel/importer/ImportedEpubStorage.kt app/src/test/java/eu/kanade/tachiyomi/source/novel/importer/ImportedEpubImporterTest.kt
git commit -m "feat: add imported epub storage"
```

### Task 5: Create the database-backed import pipeline

**Files:**
- Create: `app/src/main/java/eu/kanade/tachiyomi/source/novel/importer/ImportedEpubImporter.kt`
- Modify: `app/src/main/java/eu/kanade/domain/DomainModule.kt`
- Test: `app/src/test/java/eu/kanade/tachiyomi/source/novel/importer/ImportedEpubImporterTest.kt`

- [ ] **Step 1: Write the failing importer tests**

```kotlin
@Test
fun `importer inserts local novel and chapters with imported epub source id`() { /* fake repository test */ }

@Test
fun `importer keeps chapter source order consistent with parsed book order`() { /* fake repository test */ }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "*ImportedEpubImporterTest"`
Expected: FAIL because importer and DI bindings are missing

- [ ] **Step 3: Implement the minimal importer**

```kotlin
internal class ImportedEpubImporter(
    private val parser: ImportedEpubParser,
    private val storage: ImportedEpubStorage,
    private val novelRepository: NovelRepository,
    private val chapterRepository: NovelChapterRepository,
) {
    suspend fun import(uri: Uri, fileName: String): Long { /* parse -> insert novel -> insert chapters -> persist files */ }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "*ImportedEpubImporterTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/eu/kanade/tachiyomi/source/novel/importer/ImportedEpubImporter.kt app/src/main/java/eu/kanade/domain/DomainModule.kt app/src/test/java/eu/kanade/tachiyomi/source/novel/importer/ImportedEpubImporterTest.kt
git commit -m "feat: import epub novels into local storage"
```

### Task 6: Verify stored HTML matches the novel reader contract

**Files:**
- Create: `app/src/test/java/eu/kanade/tachiyomi/source/novel/ImportedEpubReaderContractTest.kt`
- Modify: `app/src/main/java/eu/kanade/tachiyomi/source/novel/importer/ImportedEpubHtmlNormalizer.kt`
- Verify: `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/novelsource/NovelSource.kt`
- Verify: `app/src/main/java/eu/kanade/tachiyomi/ui/entries/novel/NovelScreen.kt`

- [ ] **Step 1: Write the failing reader-contract tests**

```kotlin
@Test
fun `normalized chapter html rewrites image and stylesheet paths to stored local assets`() { /* fixture-based test */ }

@Test
fun `normalized html stays compatible with novel source chapter text contract`() { /* source contract test */ }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "*ImportedEpubReaderContractTest"`
Expected: FAIL because HTML normalization and source contract are not fully defined

- [ ] **Step 3: Implement the minimal normalization contract**

```kotlin
internal class ImportedEpubHtmlNormalizer {
    fun normalize(rawHtml: String, chapterAssetMap: Map<String, String>): String {
        // Rewrite relative href/src values to the stored asset layout expected by the reader.
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "*ImportedEpubReaderContractTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/eu/kanade/tachiyomi/source/novel/importer/ImportedEpubHtmlNormalizer.kt app/src/test/java/eu/kanade/tachiyomi/source/novel/ImportedEpubReaderContractTest.kt
git commit -m "test: lock imported epub reader html contract"
```

### Task 7: Expose imported books through a built-in novel source

**Files:**
- Create: `app/src/main/java/eu/kanade/tachiyomi/source/novel/ImportedEpubNovelConstants.kt`
- Create: `app/src/main/java/eu/kanade/tachiyomi/source/novel/ImportedEpubNovelSource.kt`
- Modify: `app/src/main/java/eu/kanade/tachiyomi/source/novel/AndroidNovelSourceManager.kt`
- Test: `app/src/test/java/eu/kanade/tachiyomi/source/novel/ImportedEpubNovelSourceTest.kt`
- Test: `app/src/test/java/eu/kanade/tachiyomi/source/novel/AndroidNovelSourceManagerTest.kt`

- [ ] **Step 1: Write the failing source tests**

```kotlin
@Test
fun `imported epub source returns chapters for imported novel`() { /* fake repo + storage test */ }

@Test
fun `source manager exposes imported epub source without extension install`() { /* manager test */ }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "*ImportedEpubNovelSourceTest" --tests "*AndroidNovelSourceManagerTest"`
Expected: FAIL because source and registration are missing

- [ ] **Step 3: Implement the minimal source and registration**

```kotlin
internal class ImportedEpubNovelSource(
    private val novelRepository: NovelRepository,
    private val chapterRepository: NovelChapterRepository,
    private val storage: ImportedEpubStorage,
) : NovelSource {
    override val id: Long = IMPORTED_EPUB_NOVEL_SOURCE_ID
    override val name: String = "Imported EPUB"
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "*ImportedEpubNovelSourceTest" --tests "*AndroidNovelSourceManagerTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/eu/kanade/tachiyomi/source/novel/ImportedEpubNovelConstants.kt app/src/main/java/eu/kanade/tachiyomi/source/novel/ImportedEpubNovelSource.kt app/src/main/java/eu/kanade/tachiyomi/source/novel/AndroidNovelSourceManager.kt app/src/test/java/eu/kanade/tachiyomi/source/novel/ImportedEpubNovelSourceTest.kt app/src/test/java/eu/kanade/tachiyomi/source/novel/AndroidNovelSourceManagerTest.kt
git commit -m "feat: add imported epub novel source"
```

### Task 8: Add import coordinator state and source-origin filter logic

**Files:**
- Create: `app/src/main/java/eu/kanade/tachiyomi/ui/library/novel/NovelLibraryImportState.kt`
- Create: `app/src/main/java/eu/kanade/tachiyomi/ui/library/novel/NovelLibraryImportCoordinator.kt`
- Modify: `app/src/main/java/eu/kanade/tachiyomi/ui/library/novel/NovelLibraryScreenModel.kt`
- Test: `app/src/test/java/eu/kanade/tachiyomi/ui/library/novel/NovelLibraryImportCoordinatorTest.kt`
- Test: `app/src/test/java/eu/kanade/tachiyomi/ui/library/novel/NovelLibrarySourceFilterTest.kt`

- [ ] **Step 1: Write the failing screen-model tests**

```kotlin
@Test
fun `library filter local shows only imported epub novels`() { /* screen model state test */ }

@Test
fun `coordinator reports in progress success and failure states`() { /* coroutine test */ }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "*NovelLibraryImportCoordinatorTest" --tests "*NovelLibrarySourceFilterTest"`
Expected: FAIL because filter state and coordinator are missing

- [ ] **Step 3: Implement the minimal state and filtering**

```kotlin
internal enum class NovelLibrarySourceFilter { ALL, DEFAULT, LOCAL }

internal data class NovelLibraryImportState(
    val isImporting: Boolean = false,
    val currentFileName: String? = null,
    val errorMessage: String? = null,
)
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "*NovelLibraryImportCoordinatorTest" --tests "*NovelLibrarySourceFilterTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/eu/kanade/tachiyomi/ui/library/novel/NovelLibraryImportState.kt app/src/main/java/eu/kanade/tachiyomi/ui/library/novel/NovelLibraryImportCoordinator.kt app/src/main/java/eu/kanade/tachiyomi/ui/library/novel/NovelLibraryScreenModel.kt app/src/test/java/eu/kanade/tachiyomi/ui/library/novel/NovelLibraryImportCoordinatorTest.kt app/src/test/java/eu/kanade/tachiyomi/ui/library/novel/NovelLibrarySourceFilterTest.kt
git commit -m "feat: add novel library import state"
```

### Task 9: Integrate source-origin filters with existing novel library state

**Files:**
- Modify: `app/src/main/java/eu/kanade/tachiyomi/ui/library/novel/NovelLibraryScreenModel.kt`
- Modify: `app/src/main/java/eu/kanade/presentation/library/novel/NovelLibraryAurora.kt`
- Test: `app/src/test/java/eu/kanade/tachiyomi/ui/library/novel/NovelLibrarySourceFilterTest.kt`

- [ ] **Step 1: Write the failing interaction tests**

```kotlin
@Test
fun `source-origin filter composes with search and category selection`() { /* screen model test */ }

@Test
fun `source-origin filter survives state refresh without resetting category tab`() { /* screen model test */ }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "*NovelLibrarySourceFilterTest"`
Expected: FAIL because the filter is not integrated with the real library state pipeline

- [ ] **Step 3: Implement the minimal integration**

```kotlin
// Apply source-origin filtering after category selection and search query resolution,
// and persist the selected source-origin chip in screen state.
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "*NovelLibrarySourceFilterTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/eu/kanade/tachiyomi/ui/library/novel/NovelLibraryScreenModel.kt app/src/main/java/eu/kanade/presentation/library/novel/NovelLibraryAurora.kt app/src/test/java/eu/kanade/tachiyomi/ui/library/novel/NovelLibrarySourceFilterTest.kt
git commit -m "feat: integrate novel library source-origin filters"
```

### Task 10: Add the library picker entry point and import actions

**Files:**
- Modify: `app/src/main/java/eu/kanade/tachiyomi/ui/library/anime/AnimeLibraryTab.kt`
- Modify: `app/src/main/java/eu/kanade/tachiyomi/ui/library/novel/NovelLibraryScreenModel.kt`
- Test: `app/src/test/java/eu/kanade/tachiyomi/ui/library/novel/NovelLibraryTabImportTest.kt`

- [ ] **Step 1: Write the failing UI logic test**

```kotlin
@Test
fun `novel library exposes import action when novel section is active`() { /* library tab test */ }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "*NovelLibraryTabImportTest"`
Expected: FAIL because import action is not wired into the novel library flow

- [ ] **Step 3: Implement the minimal picker wiring**

```kotlin
val importLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
) { uri ->
    if (uri != null) novelScreenModel.importEpub(uri)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "*NovelLibraryTabImportTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/eu/kanade/tachiyomi/ui/library/anime/AnimeLibraryTab.kt app/src/main/java/eu/kanade/tachiyomi/ui/library/novel/NovelLibraryScreenModel.kt app/src/test/java/eu/kanade/tachiyomi/ui/library/novel/NovelLibraryTabImportTest.kt
git commit -m "feat: add epub import action to novel library"
```

### Task 11: Distinguish local/imported novels visually in the library

**Files:**
- Modify: `app/src/main/java/eu/kanade/presentation/library/novel/NovelLibraryBadgeState.kt`
- Modify: `app/src/main/java/eu/kanade/presentation/library/novel/NovelLibraryAurora.kt`
- Test: `app/src/test/java/eu/kanade/presentation/library/novel/NovelLibraryBadgeStateTest.kt`

- [ ] **Step 1: Write the failing badge test**

```kotlin
@Test
fun `badge state marks imported epub novels as local`() { /* pure state test */ }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "*NovelLibraryBadgeStateTest"`
Expected: FAIL because imported source is not surfaced as a badge

- [ ] **Step 3: Implement the minimal badge support**

```kotlin
internal data class NovelLibraryBadgeState(
    val unreadCount: Int = 0,
    val isLocal: Boolean = false,
)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "*NovelLibraryBadgeStateTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/eu/kanade/presentation/library/novel/NovelLibraryBadgeState.kt app/src/main/java/eu/kanade/presentation/library/novel/NovelLibraryAurora.kt app/src/test/java/eu/kanade/presentation/library/novel/NovelLibraryBadgeStateTest.kt
git commit -m "feat: badge imported novels as local"
```

### Task 12: Add user-facing strings for import flow and filters

**Files:**
- Modify: `i18n-aniyomi/src/commonMain/moko-resources/base/strings.xml`
- Modify: `i18n-aniyomi/src/commonMain/moko-resources/ru/strings.xml`
- Test: `app/src/test/java/eu/kanade/tachiyomi/ui/library/novel/NovelLibrarySourceFilterTest.kt`

- [ ] **Step 1: Write the failing string usage assertions**

```kotlin
@Test
fun `source filter labels resolve imported epub copy`() { /* string resource backed test or snapshot assertion */ }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "*NovelLibrarySourceFilterTest"`
Expected: FAIL because the new labels do not exist

- [ ] **Step 3: Add the minimal strings**

```xml
<string name="novel_library_import_epub">Import EPUB</string>
<string name="novel_library_filter_all">All</string>
<string name="novel_library_filter_default">Default</string>
<string name="novel_library_filter_local">Local</string>
<string name="novel_library_badge_local">Local</string>
<string name="novel_library_import_failed">Failed to import EPUB</string>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "*NovelLibrarySourceFilterTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add i18n-aniyomi/src/commonMain/moko-resources/base/strings.xml i18n-aniyomi/src/commonMain/moko-resources/ru/strings.xml app/src/test/java/eu/kanade/tachiyomi/ui/library/novel/NovelLibrarySourceFilterTest.kt
git commit -m "feat: add novel epub import strings"
```

### Task 13: Handle unhappy paths and duplicate imports safely

**Files:**
- Modify: `app/src/main/java/eu/kanade/tachiyomi/source/novel/importer/ImportedEpubImporter.kt`
- Modify: `app/src/main/java/eu/kanade/tachiyomi/ui/library/novel/NovelLibraryImportCoordinator.kt`
- Test: `app/src/test/java/eu/kanade/tachiyomi/source/novel/importer/ImportedEpubImporterTest.kt`
- Test: `app/src/test/java/eu/kanade/tachiyomi/ui/library/novel/NovelLibraryImportCoordinatorTest.kt`

- [ ] **Step 1: Write the failing unhappy-path tests**

```kotlin
@Test
fun `importer surfaces clear error when epub has no readable chapters`() { /* parser/importer test */ }

@Test
fun `coordinator resets progress after import failure`() { /* coordinator test */ }

@Test
fun `re-importing the same epub creates a second independent local entry in v1`() { /* importer test */ }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "*ImportedEpubImporterTest" --tests "*NovelLibraryImportCoordinatorTest"`
Expected: FAIL because error mapping and reset behavior are incomplete

- [ ] **Step 3: Implement the minimal guardrails**

```kotlin
if (parsedBook.chapters.isEmpty()) {
    throw ImportedEpubException("No readable chapters found")
}

// v1 duplicate policy: allow duplicate imports and treat each import as a distinct local entry.
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "*ImportedEpubImporterTest" --tests "*NovelLibraryImportCoordinatorTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/eu/kanade/tachiyomi/source/novel/importer/ImportedEpubImporter.kt app/src/main/java/eu/kanade/tachiyomi/ui/library/novel/NovelLibraryImportCoordinator.kt app/src/test/java/eu/kanade/tachiyomi/source/novel/importer/ImportedEpubImporterTest.kt app/src/test/java/eu/kanade/tachiyomi/ui/library/novel/NovelLibraryImportCoordinatorTest.kt
git commit -m "feat: harden epub import error handling"
```

### Task 14: Run focused verification on unit tests and Kotlin compilation

**Files:**
- Verify: `app/src/test/java/eu/kanade/tachiyomi/source/novel/ImportedEpubNovelSourceTest.kt`
- Verify: `app/src/test/java/eu/kanade/tachiyomi/source/novel/importer/ImportedEpubParserTest.kt`
- Verify: `app/src/test/java/eu/kanade/tachiyomi/source/novel/importer/ImportedEpubImporterTest.kt`
- Verify: `app/src/test/java/eu/kanade/tachiyomi/source/novel/ImportedEpubReaderContractTest.kt`
- Verify: `app/src/test/java/eu/kanade/tachiyomi/ui/library/novel/NovelLibraryImportCoordinatorTest.kt`
- Verify: `app/src/test/java/eu/kanade/tachiyomi/ui/library/novel/NovelLibrarySourceFilterTest.kt`
- Verify: `app/src/test/java/eu/kanade/tachiyomi/ui/library/novel/NovelLibraryTabImportTest.kt`

- [ ] **Step 1: Run the focused unit suite**

Run: `./gradlew testDebugUnitTest --tests "*ImportedEpubNovelSourceTest" --tests "*ImportedEpubParserTest" --tests "*ImportedEpubImporterTest" --tests "*ImportedEpubReaderContractTest" --tests "*NovelLibraryImportCoordinatorTest" --tests "*NovelLibrarySourceFilterTest" --tests "*NovelLibraryTabImportTest"`
Expected: PASS

- [ ] **Step 2: Run Kotlin compilation**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Fix compilation or test regressions minimally**

```kotlin
// Only patch failing call sites, imports, or nullability issues found by compileDebugKotlin.
```

- [ ] **Step 4: Re-run verification**

Run: `./gradlew testDebugUnitTest --tests "*ImportedEpubNovelSourceTest" --tests "*ImportedEpubParserTest" --tests "*ImportedEpubImporterTest" --tests "*ImportedEpubReaderContractTest" --tests "*NovelLibraryImportCoordinatorTest" --tests "*NovelLibrarySourceFilterTest" --tests "*NovelLibraryTabImportTest" && ./gradlew compileDebugKotlin`
Expected: PASS then BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/ i18n-aniyomi/
git commit -m "test: verify imported epub novel flow"
```

### Task 15: Run final app-level verification and smoke checks

**Files:**
- Verify: `app/src/main/java/eu/kanade/tachiyomi/ui/library/anime/AnimeLibraryTab.kt`
- Verify: `app/src/main/java/eu/kanade/presentation/library/novel/NovelLibraryAurora.kt`
- Verify: `app/src/main/java/eu/kanade/tachiyomi/source/novel/ImportedEpubNovelSource.kt`

- [ ] **Step 1: Build the debug app**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Manual smoke-check imported EPUB flow**

Run:
- Launch debug build
- Open novel library
- Tap `Import EPUB`
- Pick a known-good `.epub`
- Confirm imported entry appears under `Local`
- Open the entry and verify chapter list order
- Open a chapter and confirm HTML text renders with images/CSS assets

Expected: Import completes and the reader loads imported chapters without network access

- [ ] **Step 3: Manual smoke-check mixed library behavior**

Run:
- Keep at least one source-backed novel and one imported novel in library
- Switch between `Default` and `Local`
- Confirm local badge is visible for imported entry

Expected: Source-backed novels stay in `Default`, imported novels stay in `Local`

- [ ] **Step 4: Record any follow-up issues without expanding scope**

```text
Examples: duplicate handling, multi-select import, progress UX polish, delete imported novel cleanup
```

- [ ] **Step 5: Commit**

```bash
git add app/ docs/superpowers/plans/2026-04-10-imported-epub-novels.md i18n-aniyomi/
git commit -m "feat: support imported epub novels"
```

## Notes for Execution

- Keep the first version EPUB-only. Do not add FB2/TXT/PDF import in this plan.
- Prefer one imported-source ID constant shared across importer, library filters, badge logic, and source manager.
- Reuse existing EPUB/archive helpers where possible instead of adding a new parsing stack.
- Keep imported novel storage app-managed; do not require users to manage folders manually.
- Do not add network update logic for imported novels.
- v1 duplicate policy is explicit: importing the same EPUB twice creates two separate local entries unless future product requirements say otherwise.
- If delete cleanup is risky, ship v1 with imported files preserved and add cleanup in a follow-up plan.
