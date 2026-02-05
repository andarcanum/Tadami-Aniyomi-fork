# Tadami Ranobe Branch + JS-Plugin Runtime (LNReader-Compatible)

**Summary**  
Implement a third content branch for ranobe (light novels) in Tadami with its own data model, UI, and reader. Plugins remain LNReader-style JS, executed via QuickJS at runtime. We add a novel plugin repository system, install/update flow, checksum verification, and a converter tool that builds repo JSON from your list of plugin URLs. MVP includes search, popular, filters (all 5 types), pagination, novel details, chapter list, **hybrid reader** (text reader default + optional WebView), auto update checks, and backup/restore.

## Architecture Overview
- **New content type**: `Novel` is a first-class content type, parallel to Manga and Anime.
- **JS plugin runtime**: QuickJS embedded. Plugins are installed/updated at runtime from repos.
- **Hybrid reader**: Default **text reader** (HTML → text), optional **WebView** for complex chapters; JS enabled only for plugin `customJS`. Custom CSS supported.
- **Data flow**:
  - Repo index -> install JS -> JS runtime loads plugin -> fetch/search/parse -> persist Novel/Chapter -> UI.
- **Auto updates**: Periodic novel library update job similar to manga.
- **Backup**: Include novel library, progress, plugin repos, and installed plugin metadata.

## Public APIs / Interfaces (Additions)
- `novelsource` API (parallel to `source` and `animesource`)
  - `NovelSource` with `getPopular`, `search`, `getNovelDetails`, `getChapterList`, `getChapterText`.
  - `NovelCatalogueSource` with filter list and paging.
- `Novel` / `NovelChapter` domain models.
- `NovelFilter` mapping to LNReader filter types:
  - `Text`, `Picker`, `Checkbox`, `Switch`, `XCheckbox`.
- `NovelPlugin` runtime API mapping:
  - `popularNovels`, `searchNovels`, `parseNovel`, `parseChapter`, `parsePage`.
- Plugin repo JSON schema:
  - `id`, `name`, `site`, `lang`, `version`, `url`, `iconUrl`, optional `customJS`, `customCSS`, `hasSettings`, `sha256`.

## Major Components and Changes

### 1) JS Plugin Runtime (QuickJS)
- Add QuickJS dependency and a `NovelJsRuntime` service.
- Implement `require` shim with supported modules:
  - `cheerio`, `htmlparser2`, `dayjs`, `urlencode`
  - `@libs/fetch` (OkHttp bridge)
  - `@libs/storage` (key-value storage per plugin)
  - `@libs/filterInputs` (FilterTypes mapping)
  - `@libs/novelStatus`, `@libs/defaultCover`
- Provide module cache and per-plugin QuickJS context isolation.
- Enforce timeouts and memory caps per execution to avoid plugin hangs.

### 2) Plugin Repository & Install/Update
- **Repo model**: new `novel_extension_repos` table or separate domain repository.
- UI screen under Settings > Browse: `Novel Extension Repos`.
- Install/update flow:
  - Fetch repo JSON list.
  - Verify SHA256 checksum.
  - Store plugin JS, customJS, customCSS in internal storage.
  - Keep metadata in DB for installed plugins and update status.
- Support pinned/installed filtering similar to LNReader, but scoped to novels.

### 3) Converter Tool (Build-time)
- Add CLI tool (Node or Kotlin) under `tools/lnreader-plugin-converter`.
- Input: your JSON list of plugin URLs with minimal metadata.
- Output: repo JSON with checksums and normalized metadata Tadami consumes.
- Optional: batch download `customJS/customCSS`.
- No runtime conversion; the app consumes repo index directly.

### 4) Data Layer (Novel DB)
- Create `noveldb` with SqlDelight:
  - `novels` table
  - `novel_chapters` table
  - `novel_history` table
  - `novel_categories` + mapping
- Repositories:
  - `NovelRepository`, `NovelChapterRepository`, `NovelHistoryRepository`.
- Interactors:
  - Get novel, chapter list, update read status, set bookmark, update library, etc.

### 5) UI & Navigation
- **Bottom tab**: add `Ranobe` (third tab).
- Screens:
  - `NovelLibraryScreen`
  - `NovelBrowseScreen`
  - `NovelEntryScreen` (details + chapter list, manga-style layout)
  - `NovelReaderScreen` (text reader default + WebView fallback)
  - `NovelSourcesScreen` + `NovelExtensionsScreen` (installed/available)
- Novel categories separate from manga.
- Novel updates screen similar to manga updates.

### 6) Reader
- **Default text reader** (HTML parsed to text).
- **Optional WebView** for complex chapters.
- JS enabled only for plugin `customJS`.
- Minimal settings:
  - font size, line height, margins, light/dark theme.
- Progress tracking:
  - Mark read on scroll-end threshold; store last position for resume.
- WebView reuses existing infra; add “Open in WebView” action from text reader.

### 7) Library Updates
- Scheduled update job for novel library.
- Uses plugin `parseNovel` to fetch latest chapters.
- Notify updates similar to manga updates.

### 8) Backup/Restore
- Add novel library, chapters, history, categories, and plugin repos to backup.
- Include installed plugin metadata, but not raw JS in backup (re-download on restore).

## Testing & Scenarios
- Unit tests:
  - Plugin checksum verification.
  - JS runtime module shims.
  - Filter mapping conversion.
  - Repo parsing and update logic.
- Integration tests:
  - Install/update plugin from repo.
  - Fetch novel list, details, chapter text.
  - Reader scroll progress to “read”.
- Manual QA:
  - Install/uninstall plugins.
  - Repo add/edit/remove.
  - Novel browsing, search, filters, paging.
  - Read chapter, exit/reopen, progress persisted.
  - Auto update new chapter notification.

## Assumptions & Defaults
- JS runtime: QuickJS.
- Hybrid reader: text reader default + WebView fallback (JS enabled only for plugin customJS).
- All 5 LNReader filter types supported.
- No tracking (AniList/MAL) for novels in MVP.
- No offline download support in MVP.
- Separate novel categories.
- Auto-updates enabled.
- Plugin integrity verified via SHA256 in repo JSON.
